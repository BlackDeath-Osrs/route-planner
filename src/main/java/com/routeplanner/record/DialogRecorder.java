package com.routeplanner.record;

import com.routeplanner.dialog.DialogOptions;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.LocationNames;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The conversation half of Record mode: dialogue choices and finished quests.
 *
 *  - Talking to an NPC becomes a "Talk to ..." step that highlights them.
 *  - Each option picked in a chat menu is added to that step's dialogue sequence ("2,1,3"),
 *    which playback highlights in turn. A mouse pick is read from the clicked line; a keyboard
 *    pick from the player repeating that line in the next chat box. A pick that can't be read
 *    either way is recorded as 0 (no highlight) so the rest of the sequence stays in step.
 *  - "Congratulations, you've completed a quest" puts that quest on the step, set to complete
 *    when the quest is finished.
 *
 * Like the rest of the recorder it only reads what's on screen; it never answers for the player.
 */
class DialogRecorder {

    /** A Talk-to click explains a conversation that opens within this many ticks (walking there). */
    private static final int TALK_WINDOW_TICKS = 30;
    /** After a menu closes, the player's echo of their choice shows up within this many ticks. */
    private static final int ECHO_WINDOW_TICKS = 4;
    /** The conversation is over once no chat box has shown for this many ticks. */
    private static final int END_TICKS = 3;
    private static final int[] DIALOGUE_GROUPS = {219, 231, 217, 229, 233, 193};
    private static final String QUEST_DONE = "congratulations, you've completed a quest:";
    private static final String MINIQUEST_DONE = "congratulations, you've completed a miniquest:";

    private final Client client;
    private final RouteRecorder host;

    private String talkNpc;
    private int talkTick = -1000;

    private boolean inConversation;
    /** A crewmate on the boat was just clicked; the chat it opens isn't part of the route. */
    private int crewTick = -1000;
    private boolean muted;
    private int lastOpenTick;
    private RouteStep step;          // the step this conversation's picks go on (made on demand)
    private String convNpc;

    /** The options menu currently (or last) on screen, and whether its pick is known yet. */
    private List<String> menu = new ArrayList<>();
    private boolean menuOpen;
    private boolean picked;
    private int closedTick = -1;

    DialogRecorder(Client client, RouteRecorder host) {
        this.client = client;
        this.host = host;
    }

    void start() {
        talkNpc = null;
        talkTick = -1000;
        crewTick = -1000;
        muted = false;
        inConversation = false;
        step = null;
        menu = new ArrayList<>();
        menuOpen = false;
        closedTick = -1;
    }

    /** An NPC was clicked; "Talk-to" means a conversation is coming. */
    /**
     * A crewmate or a facility on the player's boat was clicked: the conversation or menu that follows
     * (chatting to the crew, where to send the net's fish) isn't recorded.
     */
    void onCrewClick(int tick) {
        crewTick = tick;
        talkNpc = null;
    }

    void onNpcClick(String option, String target, int tick) {
        if (!"Talk-to".equalsIgnoreCase(option)) return;
        talkNpc = target.replaceAll("\\s*\\(level-\\d+\\)", "").trim();
        talkTick = tick;
    }

    /** A click inside the options menu: the clicked line is the pick. */
    void onMenuClick(int widgetId, int childIndex, WorldPoint pos) {
        if (widgetId != InterfaceID.Chatmenu.OPTIONS || !menuOpen) return;
        Widget container = DialogOptions.container(client);
        Widget line = container != null && childIndex >= 0 ? container.getChild(childIndex) : null;
        String text = line != null ? DialogOptions.clean(line.getText()) : null;
        int idx = text == null ? -1 : menu.indexOf(text);
        host.trace("DIALOG click child=" + childIndex + " text='" + text + "' -> option " + (idx + 1));
        if (idx >= 0) pick(idx + 1, pos);
    }

    void onTick(WorldPoint pos, int tick) {
        List<String> now = DialogOptions.texts(client);
        boolean open = !now.isEmpty();

        // Picking a line (key or mouse) first swaps it for "Please wait..." in the same menu:
        // that's the pick. Keep the original lines as the menu so the swap isn't a new menu.
        int waiting = open && menuOpen ? waitIndex(menu, now) : -1;
        if (waiting >= 0) {
            if (!picked) {
                host.trace("DIALOG wait on line " + (waiting + 1) + " -> option " + (waiting + 1));
                pick(waiting + 1, pos);
            }
        } else if (open && menuOpen && !now.equals(menu)) {
            // A menu replaced straight by a different one: the old pick can't be read any more.
            resolveUnread(pos);
            menu = now;
            picked = false;
            host.trace("DIALOG menu " + menu);
        } else if (open && !menuOpen) {
            menu = now;
            picked = false;
            host.trace("DIALOG menu " + menu);
        } else if (!open && menuOpen && !picked) {
            closedTick = tick;          // pick still unknown: watch for the echo
        }
        menuOpen = open;

        // Keyboard picks: the player says the chosen line in the next chat box.
        if (!menuOpen && !picked && closedTick >= 0) {
            String said = playerLine();
            int idx = said == null ? -1 : indexOfSaid(said);
            if (idx >= 0) {
                host.trace("DIALOG echo '" + said + "' -> option " + (idx + 1));
                pick(idx + 1, pos);
            } else if (tick - closedTick > ECHO_WINDOW_TICKS) {
                resolveUnread(pos);
            }
        }

        boolean anyOpen = anyDialogueOpen();
        if (anyOpen) {
            if (!inConversation) beginConversation(pos, tick);
            lastOpenTick = tick;
        } else if (inConversation && tick - lastOpenTick > END_TICKS && closedTick < 0) {
            inConversation = false;
            muted = false;
            step = null;
            closedTick = -1;
        }
    }

    /** Game messages: a finished quest goes on the step where it was finished. True if it was one. */
    boolean onGameMessage(String message, WorldPoint pos) {
        String msg = Text.removeTags(message).trim();
        String lower = msg.toLowerCase();
        String name = null;
        if (lower.startsWith(QUEST_DONE)) name = msg.substring(QUEST_DONE.length()).trim();
        else if (lower.startsWith(MINIQUEST_DONE)) name = msg.substring(MINIQUEST_DONE.length()).trim();
        if (name == null) return false;
        Quest quest = questNamed(name);
        host.trace("QUEST done '" + name + "' -> " + quest);
        if (quest == null) return true;
        RouteStep target = step != null ? step : host.lastDraft();
        if (target == null || target.hasQuest() || !host.isNear(target, pos, 15)) {
            target = host.stepAt(pos, "Finish " + quest.getName() + LocationNames.at(pos));
        }
        target.setQuestName(quest.name());
        target.setQuestComplete(true);
        return true;
    }

    private void beginConversation(WorldPoint pos, int tick) {
        inConversation = true;
        step = null;
        if (tick - crewTick <= TALK_WINDOW_TICKS) {
            muted = true;
            host.trace("DIALOG with a crewmate, not recorded");
            return;
        }
        convNpc = tick - talkTick <= TALK_WINDOW_TICKS ? talkNpc : null;
        talkNpc = null;
        if (convNpc != null) {
            step = host.stepAt(pos, "Talk to " + convNpc + LocationNames.at(pos));
            if (step.getSkillingTargetNpc() == null) step.setSkillingTargetNpc(convNpc);
        }
        host.trace("DIALOG start npc=" + convNpc + " step=" + RouteRecorder.describeStep(step));
    }

    private void pick(int option, WorldPoint pos) {
        picked = true;
        closedTick = -1;
        if (muted) return;
        if (step == null) {
            // Options without a Talk-to first (an object, an item, a cutscene): still a step.
            step = host.stepAt(pos, "Dialogue" + LocationNames.at(pos));
        }
        String seq = step.getDialogOptions();
        step.setDialogOptions(seq == null || seq.trim().isEmpty() ? String.valueOf(option) : seq + "," + option);
        host.trace("DIALOG sequence now " + step.getDialogOptions());
    }

    /** The pick for the last menu couldn't be read: keep the sequence aligned with a blank. */
    private void resolveUnread(WorldPoint pos) {
        if (picked || menu.isEmpty()) { closedTick = -1; return; }
        host.trace("DIALOG pick for " + menu + " not readable, recorded as 0");
        pick(0, pos);
    }

    /** The line swapped for "Please wait..." if {@code now} is {@code before} with just that change, else -1. */
    private static int waitIndex(List<String> before, List<String> now) {
        if (before.size() != now.size()) return -1;
        int found = -1;
        for (int i = 0; i < now.size(); i++) {
            if (now.get(i).equals(before.get(i))) continue;
            if (found >= 0 || !now.get(i).toLowerCase().startsWith("please wait")) return -1;
            found = i;
        }
        return found;
    }

    private int indexOfSaid(String said) {
        String s = normalize(said);
        for (int i = 0; i < menu.size(); i++) {
            String o = normalize(menu.get(i));
            if (!o.isEmpty() && (s.equals(o) || s.startsWith(o) || o.startsWith(s))) return i;
        }
        return -1;
    }

    private static String normalize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    /** What the player is saying in the chat box right now, if anything. */
    private String playerLine() {
        Widget w = client.getWidget(InterfaceID.ChatRight.TEXT);
        if (w == null || w.isHidden()) return null;
        String t = DialogOptions.clean(w.getText());
        return t.isEmpty() ? null : t;
    }

    private boolean anyDialogueOpen() {
        for (int g : DIALOGUE_GROUPS) {
            Widget w = client.getWidget(g, 0);
            if (w != null && !w.isHidden()) return true;
        }
        return false;
    }

    private static Quest questNamed(String name) {
        String n = name.replaceAll("[!.]+$", "").trim();
        for (Quest q : Quest.values()) if (q.getName().equalsIgnoreCase(n)) return q;
        return null;
    }

}
