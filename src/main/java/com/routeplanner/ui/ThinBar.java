package com.routeplanner.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

/** A slim progress bar (route progress, item counts, kills). */
public class ThinBar extends JComponent {

    private double fraction;
    private Color color;

    public ThinBar(int height, Color color) {
        this.color = color;
        setPreferredSize(new Dimension(10, height));
        setMinimumSize(new Dimension(10, height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        setAlignmentX(LEFT_ALIGNMENT);
    }

    public void setProgress(long have, long need) {
        double f = need <= 0 ? 0 : Math.max(0, Math.min(1, (double) have / need));
        if (f != fraction) { fraction = f; repaint(); }
    }

    public void setColor(Color color) {
        this.color = color;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(ColorScheme.DARKER_GRAY_COLOR.darker());
        g.fillRect(0, 0, w, h);
        g.setColor(color);
        g.fillRect(0, 0, (int) Math.round(w * fraction), h);
    }
}
