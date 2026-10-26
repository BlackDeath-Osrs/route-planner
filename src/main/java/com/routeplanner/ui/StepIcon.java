package com.routeplanner.ui;

import com.routeplanner.model.ItemMode;
import com.routeplanner.model.RouteStep;
import net.runelite.client.util.ColorUtil;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * A small drawn glyph for what a step asks of the player (talk, buy, gather, kill...), on a dark
 * tile. Drawn rather than loaded so it stays crisp at any size and needs no image resources.
 */
public final class StepIcon implements Icon {

    public enum Kind {
        TALK(new Color(143, 193, 255)), BUY(new Color(255, 211, 77)), SELL(new Color(255, 211, 77)),
        GATHER(new Color(111, 214, 196)), PICKUP(new Color(111, 214, 196)), BANK(new Color(214, 180, 120)),
        AGILITY(new Color(140, 227, 110)), SKILL(new Color(140, 227, 110)), KILL(new Color(255, 122, 107)),
        QUEST(new Color(111, 168, 255)), TELEPORT(new Color(201, 160, 255)), NOTE(new Color(216, 196, 154)),
        WALK(new Color(201, 160, 255));

        public final Color color;
        Kind(Color color) { this.color = color; }
    }

    private static final Color TILE = new Color(20, 20, 20);

    private final Kind kind;
    private final int size;
    private final boolean dim;

    public StepIcon(Kind kind, int size, boolean dim) {
        this.kind = kind;
        this.size = size;
        this.dim = dim;
    }

    /** What the step mainly asks for, in the order a player would describe it. */
    public static Kind kindOf(RouteStep s) {
        if (s.hasQuest()) return Kind.QUEST;
        if (s.getNpcKillCount() > 0) return Kind.KILL;
        boolean talk = (s.getDialogOptions() != null && !s.getDialogOptions().trim().isEmpty())
            || (s.getName() != null && s.getName().startsWith("Talk to"));
        if (talk) return Kind.TALK;
        if (s.hasItems()) {
            ItemMode mode = s.getItemMode();
            if (mode == ItemMode.SHOP) return Kind.BUY;
            if (mode == ItemMode.SELL) return Kind.SELL;
            if (mode == ItemMode.PICKUP) {
                return s.getSkillingTargetNpc() != null || s.getSkillingTargetObject() != null ? Kind.GATHER : Kind.PICKUP;
            }
            return Kind.BANK;
        }
        if (s.hasSkillGoal()) return "AGILITY".equalsIgnoreCase(s.getSkillingSkill()) ? Kind.AGILITY : Kind.SKILL;
        if (s.getTeleportMethod() != null) return Kind.TELEPORT;
        if (s.hasNote()) return Kind.NOTE;
        return Kind.WALK;
    }

    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        g2.setColor(TILE);
        g2.fillRoundRect(0, 0, size, size, 3, 3);
        // Glyphs are drawn on a 16-unit grid inside a small margin.
        double pad = size * 0.14;
        double s = (size - pad * 2) / 16.0;
        g2.translate(pad, pad);
        g2.scale(s, s);
        Color col = kind.color;
        if (dim) col = ColorUtil.colorWithAlpha(col, 110);
        g2.setColor(col);
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        draw(g2);
        g2.dispose();
    }

    private void draw(Graphics2D g) {
        Path2D p = new Path2D.Double();
        switch (kind) {
            case TALK:
                p.moveTo(2, 3); p.lineTo(14, 3); p.lineTo(14, 11); p.lineTo(7, 11); p.lineTo(4, 14); p.lineTo(4, 11); p.lineTo(2, 11); p.closePath();
                g.draw(p);
                break;
            case BUY:
            case SELL:
                g.draw(new Ellipse2D.Double(2.5, 2.5, 11, 11));
                p.moveTo(8, 5); p.lineTo(8, 11);
                p.moveTo(10, 6.5); p.lineTo(6.8, 6.5); p.curveTo(5.4, 6.5, 5.4, 8.9, 6.8, 8.9);
                p.lineTo(9.2, 8.9); p.curveTo(10.6, 8.9, 10.6, 11.3, 9.2, 11.3); p.lineTo(6, 11.3);
                g.draw(p);
                break;
            case GATHER:
                p.moveTo(1.5, 8); p.curveTo(4.5, 4, 9, 4, 11.5, 8); p.curveTo(9, 12, 4.5, 12, 1.5, 8); p.closePath();
                p.moveTo(11.5, 8); p.lineTo(14.5, 5); p.lineTo(14.5, 11); p.closePath();
                g.draw(p);
                break;
            case PICKUP:
                p.moveTo(4, 6); p.lineTo(12, 6); p.lineTo(13, 14); p.lineTo(3, 14); p.closePath();
                p.moveTo(6, 6); p.curveTo(6, 2.5, 10, 2.5, 10, 6);
                g.draw(p);
                break;
            case BANK:
                p.moveTo(2, 6); p.lineTo(8, 2.5); p.lineTo(14, 6); p.closePath();
                p.moveTo(4, 7.5); p.lineTo(4, 12); p.moveTo(8, 7.5); p.lineTo(8, 12); p.moveTo(12, 7.5); p.lineTo(12, 12);
                p.moveTo(2, 13.5); p.lineTo(14, 13.5);
                g.draw(p);
                break;
            case AGILITY:
                p.moveTo(1.5, 13.5); p.lineTo(14.5, 13.5);
                p.moveTo(4, 13.5); p.lineTo(4, 9); p.lineTo(12, 9); p.lineTo(12, 13.5);
                p.moveTo(3, 6); p.curveTo(5, 3, 11, 3, 13, 6);
                p.moveTo(11, 4.6); p.lineTo(13, 6); p.lineTo(11, 7.2);
                g.draw(p);
                break;
            case SKILL:
                p.moveTo(2, 13); p.lineTo(6, 9); p.lineTo(9, 11); p.lineTo(14, 4);
                p.moveTo(10.5, 4); p.lineTo(14, 4); p.lineTo(14, 7.5);
                g.draw(p);
                break;
            case KILL:
                p.moveTo(13.5, 2.5); p.lineTo(6, 10);
                p.moveTo(13.5, 2.5); p.lineTo(11, 2.5); p.moveTo(13.5, 2.5); p.lineTo(13.5, 5);
                p.moveTo(4, 9); p.lineTo(7, 12); p.moveTo(5.5, 10.5); p.lineTo(2.5, 13.5);
                g.draw(p);
                break;
            case QUEST:
                double[] xs = {8, 9.9, 14.2, 11.1, 11.8, 8, 4.2, 4.9, 1.8, 6.1};
                double[] ys = {1.8, 5.7, 6.3, 9.3, 13.6, 11.6, 13.6, 9.3, 6.3, 5.7};
                p.moveTo(xs[0], ys[0]);
                for (int i = 1; i < xs.length; i++) p.lineTo(xs[i], ys[i]);
                p.closePath();
                g.draw(p);
                break;
            case TELEPORT:
                g.draw(new Ellipse2D.Double(2.5, 2.5, 11, 11));
                g.draw(new Ellipse2D.Double(5.5, 5.5, 5, 5));
                break;
            case NOTE:
                p.moveTo(3, 2); p.lineTo(10, 2); p.lineTo(13, 5); p.lineTo(13, 14); p.lineTo(3, 14); p.closePath();
                p.moveTo(10, 2); p.lineTo(10, 5); p.lineTo(13, 5);
                p.moveTo(5.5, 8); p.lineTo(10.5, 8); p.moveTo(5.5, 10.5); p.lineTo(9, 10.5);
                g.draw(p);
                break;
            case WALK:
            default:
                p.moveTo(8, 14.5); p.curveTo(8, 14.5, 3.8, 10, 3.8, 6.6); p.curveTo(3.8, 1.2, 12.2, 1.2, 12.2, 6.6);
                p.curveTo(12.2, 10, 8, 14.5, 8, 14.5); p.closePath();
                g.draw(p);
                g.draw(new Ellipse2D.Double(6.5, 5.1, 3, 3));
                break;
        }
    }
}
