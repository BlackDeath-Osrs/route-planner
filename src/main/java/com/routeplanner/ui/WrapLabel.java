package com.routeplanner.ui;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Word-wrapped text at a fixed width, drawn directly: much cheaper than an HTML JLabel, which
 * parses and lays out a whole HTML document per label.
 */
public class WrapLabel extends JComponent {

    private final String text;
    private final int wrapWidth;
    private List<String> lines;
    private FontMetrics linesFor;

    public WrapLabel(String text, int wrapWidth) {
        this.text = text == null ? "" : text;
        this.wrapWidth = wrapWidth;
        setFont(UIManager.getFont("Label.font"));
        setForeground(UIManager.getColor("Label.foreground"));
    }

    private List<String> lines(FontMetrics fm) {
        if (lines != null && fm == linesFor) return lines;
        lines = wrap(text, fm, wrapWidth);
        linesFor = fm;
        return lines;
    }

    /** {@code text} broken into lines no wider than {@code width} (a too-long word gets its own line). */
    public static List<String> wrap(String text, FontMetrics fm, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : (text == null ? "" : text).split(" ")) {
            if (word.isEmpty()) continue;
            String tryLine = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && fm.stringWidth(tryLine) > width) {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(tryLine);
            }
        }
        if (line.length() > 0 || out.isEmpty()) out.add(line.toString());
        return out;
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(wrapWidth, lines(fm).size() * fm.getHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        // Same text smoothing as a JLabel gets.
        Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
        if (hints instanceof Map) g2.addRenderingHints((Map<?, ?>) hints);
        g2.setFont(getFont());
        g2.setColor(getForeground());
        FontMetrics fm = getFontMetrics(getFont());
        List<String> ls = lines(fm);
        int y = (getHeight() - ls.size() * fm.getHeight()) / 2 + fm.getAscent();
        for (String l : ls) {
            g2.drawString(l, 0, y);
            y += fm.getHeight();
        }
        g2.dispose();
    }
}
