package com.routeplanner;

import com.routeplanner.agility.AgilityTask;
import com.routeplanner.agility.AgilityObstacle;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

public class RouteHudOverlay extends Overlay {

    private final Client client;
    private final RoutePlannerPlugin plugin;
    private final RoutePlannerConfig config;
    private final com.routeplanner.bank.BankItemManager bankItemManager;

    private static final Color BG_COLOR      = new Color(52, 40, 24, 64);   // OSRS brown @ ~25% opacity
    private static final Color TITLE_COLOR   = new Color(255, 152, 31);     // OSRS orange
    private static final Color CURRENT_COLOR = new Color(255, 235, 200);    // warm cream
    private static final Color NEXT_COLOR    = new Color(190, 170, 140);    // muted tan
    private static final Color BORDER_COLOR  = new Color(160, 130, 85, 180); // tan/gold edge
    private static final Color HAVE_COLOR    = new Color(110, 210, 90);     // soft green

    @Inject
    public RouteHudOverlay(Client client, RoutePlannerPlugin plugin, RoutePlannerConfig config,
                           com.routeplanner.bank.BankItemManager bankItemManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.bankItemManager = bankItemManager;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.MED);
        setMovable(true);
        setResizable(false);
        setSnappable(true);
    }

    private java.util.List<String> wrapText(String s, int maxChars) {
        java.util.List<String> out = new java.util.ArrayList<>();
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

    @Override
    public Dimension render(Graphics2D graphics) {
        Route activeRoute = plugin.getActiveRoute();
        AgilityTask agilityTask = plugin.getAgilityTaskManager().getActiveTask();

        if (activeRoute == null && agilityTask == null) return null;

        int fontStyle = config.hudFontBold() ? Font.BOLD : Font.PLAIN;
        graphics.setFont(new Font("Arial", fontStyle, config.hudFontSize()));
        FontMetrics fm = graphics.getFontMetrics();
        int lineHeight = fm.getHeight() + 3;
        int padding = 7;

        // Build content: [text, color]
        java.util.List<String[]> lines = new java.util.ArrayList<>();

        // Determine if we should show route steps or agility task
        boolean routeHasIncompleteStep = false;
        int routeCurrentIdx = -1;
        List<RouteStep> steps = activeRoute != null ? activeRoute.getSteps() : new java.util.ArrayList<>();

        if (activeRoute != null) {
            for (int i = 0; i < steps.size(); i++) {
                if (!steps.get(i).isCompleted()) {
                    routeCurrentIdx = i;
                    routeHasIncompleteStep = true;
                    break;
                }
            }
        }

        // Show route name as title always
        if (activeRoute != null) {
            lines.add(new String[]{activeRoute.getName(), "TITLE"});
        }

        // Show route location step if there is one incomplete
        if (routeHasIncompleteStep) {
            RouteStep current = steps.get(routeCurrentIdx);
            lines.add(new String[]{">> " + (routeCurrentIdx + 1) + ". " + current.getName(), "CURRENT"});
            if (routeCurrentIdx + 1 < steps.size()) {
                RouteStep next = steps.get(routeCurrentIdx + 1);
                lines.add(new String[]{"   " + (routeCurrentIdx + 2) + ". " + next.getName(), "NEXT"});
            }
        } else if (agilityTask != null) {
            // Route complete or no route — show agility task
            List<AgilityObstacle> obstacles = agilityTask.getCourse().getObstacles();
            int currentIdx = agilityTask.getCurrentObstacleIndex();

            AgilityObstacle current = agilityTask.getCurrentObstacle();
            if (current != null) {
                // Skilling progress inline
                com.routeplanner.model.RouteStep curStep = steps.get(currentIdx);
                String stepLine;
                if (curStep.getType() == com.routeplanner.model.StepType.SKILLING && curStep.getSkillingGoalType() != null) {
                    String sk = curStep.getSkillingSkill() != null ? curStep.getSkillingSkill().substring(0,1).toUpperCase() + curStep.getSkillingSkill().substring(1).toLowerCase() : "?";
                    String gt = curStep.getSkillingGoalType();
                    if (gt.equals("XP_GAIN")) {
                        stepLine = ">> " + (currentIdx+1) + ". " + sk + ": +" + String.format("%,d", curStep.getSkillingProgress()) + "/+" + String.format("%,d", curStep.getSkillingGoalValue()) + " xp";
                    } else if (gt.equals("XP_TARGET")) {
                        net.runelite.api.Skill apiSk = null; try { apiSk = net.runelite.api.Skill.valueOf(curStep.getSkillingSkill().toUpperCase()); } catch (Exception e2) {}
                        long curXp = apiSk != null ? client.getSkillExperience(apiSk) : 0;
                        stepLine = ">> " + (currentIdx+1) + ". " + sk + ": " + String.format("%,d", curXp) + "/" + String.format("%,d", curStep.getSkillingGoalValue()) + " xp";
                    } else if (gt.equals("LEVEL")) {
                        net.runelite.api.Skill apiSk = null; try { apiSk = net.runelite.api.Skill.valueOf(curStep.getSkillingSkill().toUpperCase()); } catch (Exception e2) {}
                        int curLvl = apiSk != null ? client.getRealSkillLevel(apiSk) : 0;
                        stepLine = ">> " + (currentIdx+1) + ". " + sk + " level: " + curLvl + "/" + curStep.getSkillingGoalValue();
                    } else { stepLine = ">> " + (currentIdx+1) + ". " + curStep.getName(); }
                } else { stepLine = ">> " + (currentIdx+1) + ". " + curStep.getName(); }
                lines.add(new String[]{stepLine, "CURRENT"});
            }
            int nextIdx = (currentIdx + 1) % obstacles.size();
            AgilityObstacle next = obstacles.get(nextIdx);
            lines.add(new String[]{"   " + (nextIdx + 1) + ". " + next.getName(), "NEXT"});
        } else if (!routeHasIncompleteStep && activeRoute != null) {
            lines.add(new String[]{"Route Complete!", "CURRENT"});
        }

        // Item checklist section for ITEM steps
        if (routeHasIncompleteStep) {
            RouteStep curStep = steps.get(routeCurrentIdx);
            if (curStep.getType() == com.routeplanner.model.StepType.ITEM
                    && curStep.getItemList() != null && !curStep.getItemList().trim().isEmpty()) {
                lines.add(new String[]{"---", "DIM"});
                String mode = curStep.getItemMode();
                if ("SELL".equals(mode)) {
                    lines.add(new String[]{"Sell:", "TITLE"});
                    for (String[] st : bankItemManager.getSellStatusList(curStep.getItemList())) {
                        boolean sold = st[1].equals("SOLD");
                        String mark = sold ? "[x] " : "[  ] ";
                        lines.add(new String[]{"  " + mark + st[0], sold ? "HAVE" : "CURRENT"});
                    }
                } else {
                    String header = "SHOP".equals(mode) ? "Buy:" : ("PICKUP".equals(mode) ? "Pick up:" : "Gather:");
                    lines.add(new String[]{header, "TITLE"});
                    for (String[] st : bankItemManager.getItemStatusList(curStep.getItemList())) {
                        boolean have = st[1].equals("HAVE");
                        String mark = have ? "[x] " : "[  ] ";
                        lines.add(new String[]{"  " + mark + st[0], have ? "HAVE" : "CURRENT"});
                    }
                }
            }
        }

        // Note step section (informational, not tracked)
        if (routeHasIncompleteStep) {
            RouteStep curStep = steps.get(routeCurrentIdx);
            if (curStep.getType() == com.routeplanner.model.StepType.NOTE) {
                lines.add(new String[]{"---", "DIM"});
                String note = (curStep.getNoteText() != null && !curStep.getNoteText().isEmpty())
                    ? curStep.getNoteText() : curStep.getName();
                for (String para : note.split("\\r?\\n")) {
                    if (para.trim().isEmpty()) continue;
                    for (String wrapped : wrapText(para, 42)) {
                        lines.add(new String[]{wrapped, "CURRENT"});
                    }
                }
                if (curStep.getNpcKillCount() > 0) {
                    lines.add(new String[]{"Kills: " + curStep.getNpcKillProgress()
                        + " / " + curStep.getNpcKillCount(), "TITLE"});
                } else {
                    lines.add(new String[]{"(mark complete to continue)", "DIM"});
                }
            }
        }

        // Add XP tracker section if there is an active skilling step
        if (plugin.getActiveRoute() != null) {
            for (com.routeplanner.model.RouteStep s : plugin.getActiveRoute().getSteps()) {
                if (!s.isCompleted() && s.getType() == com.routeplanner.model.StepType.SKILLING
                        && s.getSkillingGoalType() != null) {
                    String sk = s.getSkillingSkill() != null
                        ? s.getSkillingSkill().substring(0,1).toUpperCase()
                          + s.getSkillingSkill().substring(1).toLowerCase() : "?";
                    net.runelite.api.Skill apiSk = null;
                    try { apiSk = net.runelite.api.Skill.valueOf(
                        s.getSkillingSkill().toUpperCase()); } catch (Exception ignored) {}
                    int curLvl = apiSk != null ? client.getRealSkillLevel(apiSk) : 0;
                    long curXp = apiSk != null ? client.getSkillExperience(apiSk) : 0;

                    lines.add(new String[]{"---", "DIM"});
                    String gt = s.getSkillingGoalType();
                    if (gt.equals("XP_GAIN")) {
                        lines.add(new String[]{sk + " XP Gained:", "TITLE"});
                        lines.add(new String[]{"  +" + String.format("%,d", s.getSkillingProgress())
                            + " / +" + String.format("%,d", s.getSkillingGoalValue()), "CURRENT"});
                    } else if (gt.equals("XP_TARGET")) {
                        lines.add(new String[]{sk + " Total XP:", "TITLE"});
                        lines.add(new String[]{"  " + String.format("%,d", curXp)
                            + " / " + String.format("%,d", s.getSkillingGoalValue()), "CURRENT"});
                    } else if (gt.equals("LEVEL")) {
                        lines.add(new String[]{sk + " Level:", "TITLE"});
                        lines.add(new String[]{"  " + curLvl + " / " + s.getSkillingGoalValue(), "CURRENT"});
                    }
                    lines.add(new String[]{"Lvl: " + curLvl, "NEXT"});
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
        com.routeplanner.HudTheme theme = config.hudTheme();
        Color bgC, borderC, titleC, currentC, nextC, haveC, dimC, dividerC;
        if (theme == com.routeplanner.HudTheme.QUEST_HELPER) {
            bgC      = new Color(70, 61, 50, 156);   // RuneLite standard overlay background
            borderC  = new Color(60, 60, 60, 140);
            titleC   = new Color(255, 255, 255);
            currentC = new Color(255, 255, 255);
            nextC    = new Color(190, 190, 190);
            haveC    = new Color(0, 255, 0);
            dimC     = new Color(170, 170, 170);
            dividerC = new Color(255, 255, 255, 40);
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

        // Background
        graphics.setColor(bgC);
        graphics.fillRoundRect(0, 0, width, totalHeight, 6, 6);
        graphics.setColor(borderC);
        graphics.setStroke(new BasicStroke(1));
        graphics.drawRoundRect(0, 0, width - 1, totalHeight - 1, 6, 6);

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

        return new Dimension(width, totalHeight);
    }
}
