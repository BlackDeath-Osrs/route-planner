package com.routeplanner;

import com.routeplanner.bank.BankItemManager;
import com.routeplanner.model.*;
import com.routeplanner.sailing.SailingDocks;
import com.routeplanner.transport.Transport;
import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.*;
import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

public class RouteHudOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final RoutePlannerConfig config;
    private final BankItemManager bankItemManager;
    private final ConfigManager configManager;

    private static final Color BG_COLOR      = new Color(52, 40, 24, 64);   // OSRS brown @ ~25% opacity
    private static final Color TITLE_COLOR   = new Color(255, 152, 31);     // OSRS orange
    private static final Color CURRENT_COLOR = new Color(255, 235, 200);    // warm cream
    private static final Color NEXT_COLOR    = new Color(190, 170, 140);    // muted tan
    private static final Color BORDER_COLOR  = new Color(160, 130, 85, 180); // tan/gold edge
    private static final Color HAVE_COLOR    = new Color(110, 210, 90);     // soft green

    @Inject
    public RouteHudOverlay(Client client, RoutePlannerPlugin plugin, RoutePlannerConfig config,
                           BankItemManager bankItemManager, ConfigManager configManager) {
        this.configManager = configManager;
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.bankItemManager = bankItemManager;
        setPosition(OverlayPosition.TOP_LEFT);
        // Under the game's interfaces: an open bank, shop or chat box covers the HUD instead of
        // the HUD covering them.
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPriority(OverlayPriority.MED);
        setMovable(true);
        setResizable(true); // Alt + drag a corner or edge rescales the HUD
        setSnappable(true);
    }

    private List<String> wrapText(String s, int maxChars) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            if (cur.length() > 0 && cur.length() + word.length() + 1 > maxChars) {
                out.add(cur.toString());
                cur = new StringBuilder();
            }
            if (cur.length() > 0) cur.append(" ");
            cur.append(word);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** Bank-like windows the HUD steps aside for (it can't be drawn beneath them in every renderer). */
    private static final int[] COVERING_WINDOWS = {
        InterfaceID.Bankmain.UNIVERSE,
        InterfaceID.BankDepositbox.UNIVERSE,
        InterfaceID.Shopmain.UNIVERSE,
        InterfaceID.GeOffers.UNIVERSE,
        InterfaceID.SeedVault.UNIVERSE,
    };
    private int lastWidth, lastHeight; // size when last drawn; hidden frames report none

    /** Smallest and largest HUD Scale, in percent. */
    private static final int MIN_SCALE = 50, MAX_SCALE = 200;
    /** How long an Alt-drag resize must rest before the new size is saved. */
    private static final long SAVE_AFTER_MS = 500;

    /** The size an Alt-drag resize has reached but not yet saved (0 when not resizing), in percent. */
    private int resizeScale;
    private long resizedAt;
    /** Whether this drag follows the width (else the height); null until the drag has moved. */
    private Boolean dragByWidth;
    /** The HUD's size at 100% when last drawn. */
    private int naturalWidth, naturalHeight;

    /**
     * The HUD's size in percent. RuneLite's Alt-drag resize hands over a new size in pixels; it's
     * turned into a scale here, since the HUD's own size follows its text, and saved once the
     * drag rests.
     */
    private int scalePercent() {
        Dimension want = getPreferredSize();
        long now = System.currentTimeMillis();
        if (want != null) {
            setPreferredSize(null);
            if (naturalWidth > 0 && naturalHeight > 0) {
                // Follow one side: the width whenever it moved (a corner or side edge), the height
                // only for a top or bottom edge. Following both would jump between the two sizes
                // they imply, since dragging doesn't keep the HUD's shape.
                // The side is picked on the drag's first move and kept until it ends.
                if (dragByWidth == null && (want.width != lastWidth || want.height != lastHeight)) {
                    dragByWidth = want.width != lastWidth;
                }
                if (dragByWidth != null) {
                    double percent = dragByWidth ? 100.0 * want.width / naturalWidth : 100.0 * want.height / naturalHeight;
                    resizeScale = clampScale((int) Math.round(percent));
                    resizedAt = now;
                }
            }
        }
        if (resizeScale > 0 && now - resizedAt > SAVE_AFTER_MS) {
            int done = resizeScale;
            resizeScale = 0;
            dragByWidth = null;
            if (done != config.hudScale()) configManager.setConfiguration(RoutePlannerConfig.GROUP, "hudScale", done);
            return done;
        }
        return clampScale(resizeScale > 0 ? resizeScale : config.hudScale());
    }

    private static int clampScale(int percent) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, percent));
    }

    /** True when an open bank, shop, GE or similar window overlaps where the HUD sits. */
    private boolean coveredByWindow() {
        Rectangle at = getBounds();
        if (at == null) return false;
        Rectangle hud = new Rectangle(at.x, at.y, Math.max(at.width, lastWidth), Math.max(at.height, lastHeight));
        if (hud.width == 0 || hud.height == 0) return false;
        for (int id : COVERING_WINDOWS) {
            Widget w = client.getWidget(id);
            if (w != null && !w.isHidden() && w.getBounds() != null && w.getBounds().intersects(hud)) return true;
        }
        return false;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Route activeRoute = plugin.getActiveRoute();
        if (!config.showHud() || coveredByWindow()) return null;

        if (activeRoute == null) return null;

        int fontStyle = config.hudFontBold() ? Font.BOLD : Font.PLAIN;
        graphics.setFont(config.hudFontFamily().toFont(config.hudFontSize(), fontStyle));
        FontMetrics fm = graphics.getFontMetrics();
        boolean compact = config.hudCompact();
        int lineHeight = fm.getHeight() + (compact ? 1 : 3);
        int padding = compact ? 4 : 7;
        int wrap = compact ? 32 : 42;

        // Build content: [text, color]
        List<String[]> lines = new ArrayList<>();

        // Determine if we should show route steps or agility task
        boolean routeHasIncompleteStep = false;
        int routeCurrentIdx = -1;
        List<RouteStep> steps = activeRoute != null ? activeRoute.getSteps() : new ArrayList<>();

        if (activeRoute != null) {
            // The route's active step: the first unticked one, or a step under test
            RouteStep active = activeRoute.getActiveStep();
            for (int i = 0; i < steps.size(); i++) {
                if (steps.get(i) == active) { routeCurrentIdx = i; break; }
            }
            routeHasIncompleteStep = routeCurrentIdx >= 0;
        }

        // Route fully complete (or empty): hide the HUD entirely
        if (activeRoute != null && !routeHasIncompleteStep) {
            return null;
        }

        // Show route name as title always
        if (activeRoute != null && !compact) {
            lines.add(new String[]{activeRoute.getName(), "TITLE"});
        }

        // Show route location step if there is one incomplete
        if (routeHasIncompleteStep) {
            RouteStep current = steps.get(routeCurrentIdx);
            String curPrefix = compact ? "" : ">> " + (routeCurrentIdx + 1) + ". ";
            String indent = compact ? "" : "     ";
            List<String> curWrapped = wrapText(curPrefix + current.getName(), wrap);
            for (int i = 0; i < curWrapped.size(); i++) {
                String curLine = i == 0 ? curWrapped.get(i) : indent + curWrapped.get(i);
                lines.add(new String[]{curLine, "CURRENT"});
            }
            if (routeCurrentIdx + 1 < steps.size() && !compact) {
                RouteStep next = steps.get(routeCurrentIdx + 1);
                String nxtPrefix = "   " + (routeCurrentIdx + 2) + ". ";
                List<String> nxtWrapped = wrapText(nxtPrefix + next.getName(), wrap);
                for (int i = 0; i < nxtWrapped.size(); i++) {
                    String nxtLine = i == 0 ? nxtWrapped.get(i) : "     " + nxtWrapped.get(i);
                    lines.add(new String[]{nxtLine, "NEXT"});
                }
            }
        }

        // Getting on or off a boat (Sailing), or a teleport the route takes: say so, since the line
        // stops there and there's nothing in the world to highlight. Likewise "Use rope on Rock":
        // the object lights up, but it takes an item rather than a click.
        if (routeHasIncompleteStep) {
            Transport t = plugin.getPathfinderOverlay().getActiveTransition();
            if (t != null && t.actionText != null
                    && (t.teleport || t.actionText.startsWith(SailingDocks.BOARD)
                        || t.actionText.startsWith(SailingDocks.DISEMBARK)
                        || t.actionText.startsWith("Use "))) {
                lines.add(new String[]{"---", "DIM"});
                for (String wrapped : wrapText(t.actionText, wrap)) lines.add(new String[]{wrapped, "TITLE"});
            }
        }

        // Sailing countdowns and the voyage time
        if (routeHasIncompleteStep) {
            List<String> sailing = plugin.getSailingTimers().hudLines(
                plugin.getPathfinderOverlay().getCachedPath(), plugin.getSailingDocks());
            if (!sailing.isEmpty()) {
                lines.add(new String[]{"---", "DIM"});
                for (String line : sailing) lines.add(new String[]{line, "CURRENT"});
            }
        }

        // Item checklist section for ITEM steps
        if (routeHasIncompleteStep) {
            RouteStep curStep = steps.get(routeCurrentIdx);
            if (curStep.hasItems()) {
                lines.add(new String[]{"---", "DIM"});
                ItemMode mode = curStep.getItemMode();
                if (mode == ItemMode.USE_UP) {
                    lines.add(new String[]{"None left:", "TITLE"});
                    for (String[] st : bankItemManager.getUseUpStatusList(curStep.getItemList())) {
                        boolean done = st[1].equals("DONE");
                        lines.add(new String[]{"  " + (done ? "[x] " : "[  ] ") + st[0], done ? "HAVE" : "CURRENT"});
                    }
                } else if (mode == ItemMode.SELL) {
                    lines.add(new String[]{"Sell:", "TITLE"});
                    for (String[] st : bankItemManager.getSellStatusList(curStep.getItemList())) {
                        boolean sold = st[1].equals("SOLD");
                        String mark = sold ? "[x] " : "[  ] ";
                        lines.add(new String[]{"  " + mark + st[0], sold ? "HAVE" : "CURRENT"});
                    }
                } else {
                    boolean gathering = curStep.getSkillingTargetNpc() != null || curStep.getSkillingTargetObject() != null;
                    String header = mode == ItemMode.SHOP ? "Buy:"
                        : (mode == ItemMode.PICKUP && !gathering ? "Pick up:" : "Gather:");
                    lines.add(new String[]{header, "TITLE"});
                    Map<String, Long> baseline = curStep.countsGainedItems()
                        ? curStep.getPickupBaseline() : null;
                    for (String[] st : bankItemManager.getItemStatusList(curStep.getItemList(), baseline)) {
                        boolean have = st[1].equals("HAVE");
                        String mark = have ? "[x] " : "[  ] ";
                        lines.add(new String[]{"  " + mark + st[0], have ? "HAVE" : "CURRENT"});
                    }
                }
            }
        }

        // Farming patch
        if (routeHasIncompleteStep) {
            RouteStep curStep = steps.get(routeCurrentIdx);
            if (curStep.hasFarmPatch()) {
                lines.add(new String[]{"---", "DIM"});
                String what = curStep.isFarmReplant() ? "Harvest and replant the patch" : "Tend the patch";
                lines.add(new String[]{"  " + (curStep.isFarmDone() ? "[x] " : "[  ] ") + what,
                    curStep.isFarmDone() ? "HAVE" : "CURRENT"});
            }
        }

        // Catch goal (Sailing trawling)
        if (routeHasIncompleteStep) {
            RouteStep curStep = steps.get(routeCurrentIdx);
            if (curStep.hasCatchGoal()) {
                lines.add(new String[]{"---", "DIM"});
                lines.add(new String[]{"Catch:", "TITLE"});
                boolean done = curStep.getCatchProgress() >= curStep.getCatchGoal();
                lines.add(new String[]{"  " + Math.min(curStep.getCatchProgress(), curStep.getCatchGoal()) + " / "
                    + curStep.getCatchGoal() + " " + curStep.getCatchFish(), done ? "HAVE" : "CURRENT"});
            }
        }

        // Note step section (informational, not tracked)
        if (routeHasIncompleteStep) {
            RouteStep curStep = steps.get(routeCurrentIdx);
            if (curStep.hasNote()) {
                lines.add(new String[]{"---", "DIM"});
                String note = (curStep.getNoteText() != null && !curStep.getNoteText().isEmpty())
                    ? curStep.getNoteText() : curStep.getName();
                for (String para : note.split("\\r?\\n")) {
                    if (para.trim().isEmpty()) continue;
                    for (String wrapped : wrapText(para, wrap)) {
                        lines.add(new String[]{wrapped, "CURRENT"});
                    }
                }
                if (curStep.getNpcKillCount() > 0) {
                    lines.add(new String[]{"Kills: " + curStep.getNpcKillProgress()
                        + " / " + curStep.getNpcKillCount(), "TITLE"});
                } else {
                    // Only show "mark complete" if no component will auto-complete the step
                    boolean hasAutoComplete = curStep.hasItems() || curStep.hasSkillGoal()
                        || (curStep.hasQuest() && curStep.isQuestComplete())
                        || curStep.getWorldPoint() != null;
                    if (!hasAutoComplete) {
                        lines.add(new String[]{"(mark complete to continue)", "DIM"});
                    }
                }
            }
        }

        // XP tracker for the current step only; a later step's goal shouldn't show early
        if (routeHasIncompleteStep) {
            for (RouteStep s : Collections.singletonList(steps.get(routeCurrentIdx))) {
                if (s.hasSkillGoal()
                        && s.getSkillingGoalType() != null) {
                    String sk = s.getSkillingSkill() != null
                        ? s.getSkillingSkill().substring(0,1).toUpperCase()
                          + s.getSkillingSkill().substring(1).toLowerCase() : "?";
                    Skill apiSk = s.skill();
                    int curLvl = apiSk != null ? client.getRealSkillLevel(apiSk) : 0;
                    long curXp = apiSk != null ? client.getSkillExperience(apiSk) : 0;

                    lines.add(new String[]{"---", "DIM"});
                    SkillGoalType gt = s.getSkillingGoalType();
                    if (gt == SkillGoalType.XP_GAIN) {
                        lines.add(new String[]{sk + " XP Gained:", "TITLE"});
                        lines.add(new String[]{"  +" + String.format("%,d", s.getSkillingProgress())
                            + " / +" + String.format("%,d", s.getSkillingGoalValue()), "CURRENT"});
                    } else if (gt == SkillGoalType.XP_TARGET) {
                        lines.add(new String[]{sk + " Total XP:", "TITLE"});
                        lines.add(new String[]{"  " + String.format("%,d", curXp)
                            + " / " + String.format("%,d", s.getSkillingGoalValue()), "CURRENT"});
                    } else if (gt == SkillGoalType.LEVEL) {
                        lines.add(new String[]{sk + " Level:", "TITLE"});
                        lines.add(new String[]{"  " + curLvl + " / " + s.getSkillingGoalValue(), "CURRENT"});
                    }
                    if (!compact) lines.add(new String[]{"Lvl: " + curLvl, "NEXT"});
                    break;
                }
            }
        }

        // Calculate size
        int maxWidth = 0;
        for (String[] line : lines) {
            int w = fm.stringWidth(line[0]);
            if (w > maxWidth) maxWidth = w;
        }
        int width = maxWidth + padding * 2 + 4;
        int totalHeight = padding * 2 + lines.size() * lineHeight;

        // Theme palette
        HudTheme theme = config.hudTheme();
        Color bgC, borderC, titleC, currentC, nextC, haveC, dimC, dividerC;
        if (theme == HudTheme.QUEST_HELPER) {
            bgC      = new Color(70, 61, 50, 156);   // RuneLite standard overlay background
            borderC  = new Color(60, 60, 60, 140);
            titleC   = new Color(255, 255, 255);
            currentC = new Color(255, 255, 255);
            nextC    = new Color(190, 190, 190);
            haveC    = new Color(0, 255, 0);
            dimC     = new Color(170, 170, 170);
            dividerC = new Color(255, 255, 255, 40);
        } else if (theme == HudTheme.CUSTOM) {
            bgC      = config.hudBgColor();
            borderC  = config.hudBorderColor();
            titleC   = config.hudTitleColor();
            currentC = config.hudCurrentColor();
            nextC    = config.hudNextColor();
            haveC    = config.hudHaveColor();
            dimC     = config.hudDimColor();
            dividerC = config.hudDividerColor();
        } else { // OSRS_BROWN (default)
            bgC      = BG_COLOR;
            borderC  = BORDER_COLOR;
            titleC   = TITLE_COLOR;
            currentC = CURRENT_COLOR;
            nextC    = NEXT_COLOR;
            haveC    = HAVE_COLOR;
            dimC     = new Color(155, 135, 105);
            dividerC = new Color(185, 145, 85, 45);
        }

        // Everything below is drawn at the HUD's own size, then scaled as a whole
        double scale = scalePercent() / 100.0;
        AffineTransform unscaled = graphics.getTransform();
        graphics.scale(scale, scale);

        // Background
        graphics.setColor(bgC);
        graphics.fillRoundRect(0, 0, width, totalHeight, 6, 6);
        boolean drawOutline = theme != HudTheme.CUSTOM || config.hudOutline();
        if (drawOutline) {
            int thickness = theme == HudTheme.CUSTOM ? Math.max(1, config.hudBorderThickness()) : 1;
            graphics.setColor(borderC);
            graphics.setStroke(new BasicStroke(thickness));
            graphics.drawRoundRect(0, 0, width - 1, totalHeight - 1, 6, 6);
        }

        // Text
        int y = padding + fm.getAscent();
        for (String[] line : lines) {
            Color color;
            switch (line[1]) {
                case "TITLE":   color = titleC; break;
                case "CURRENT": color = currentC; break;
                case "NEXT":    color = nextC; break;
                case "HAVE":    color = haveC; break;
                default:        color = dimC; break;
            }
            if (line[0].equals("---")) {
                graphics.setColor(dividerC);
                graphics.drawLine(padding, y - fm.getAscent() / 2, width - padding, y - fm.getAscent() / 2);
                y += lineHeight / 2;
                continue;
            }
            // dark shadow for legibility over the transparent background
            graphics.setColor(new Color(0, 0, 0, 190));
            graphics.drawString(line[0], padding + 1, y + 1);
            graphics.setColor(color);
            graphics.drawString(line[0], padding, y);
            y += lineHeight;
        }

        graphics.setTransform(unscaled);
        naturalWidth = width;
        naturalHeight = totalHeight;
        width = (int) Math.ceil(width * scale);
        totalHeight = (int) Math.ceil(totalHeight * scale);
        lastWidth = width;
        lastHeight = totalHeight;
        return new Dimension(width, totalHeight);
    }
}
