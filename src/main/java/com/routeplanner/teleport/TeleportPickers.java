package com.routeplanner.teleport;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.util.RouteTextValidator;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** The teleport choosers (spell, item, Portal Nexus), shared by both step editors. */
public final class TeleportPickers {

    private static final Map<Integer, BufferedImage> SPRITES = new HashMap<>();

    private TeleportPickers() {}

    /** A spell's spellbook icon at 20px; null until it loads, then {@code onLoaded} runs. */
    public static Icon spellIcon(RoutePlannerPlugin plugin, TeleportSpell sp, Runnable onLoaded) {
        Integer key = sp.getSpriteId();
        BufferedImage cached = SPRITES.get(key);
        if (cached != null) return new ImageIcon(cached.getScaledInstance(20, 20, Image.SCALE_SMOOTH));
        try {
            plugin.getSpriteManager().getSpriteAsync(sp.getSpriteId(), 0, img -> {
                if (img != null) {
                    SPRITES.put(key, img);
                    SwingUtilities.invokeLater(onLoaded);
                }
            });
        } catch (RuntimeException ignored) {
            // no icon is fine; the name still shows
        }
        return null;
    }

    /** A teleport item's inventory icon at 20px, or null. */
    public static Icon itemIcon(RoutePlannerPlugin plugin, TeleportItem it) {
        try {
            BufferedImage img = plugin.getItemManager().getImage(it.getIconId());
            return img != null ? new ImageIcon(img.getScaledInstance(20, 20, Image.SCALE_SMOOTH)) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** A list of teleport spells with their icons. Null when cancelled. */
    public static TeleportSpell pickSpell(Component parent, RoutePlannerPlugin plugin) {
        DefaultListModel<TeleportSpell> model = new DefaultListModel<>();
        for (TeleportSpell s : TeleportSpells.ALL) model.addElement(s);
        JList<TeleportSpell> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(15);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                TeleportSpell sp = (TeleportSpell) v;
                setText(sp.getName());
                Integer key = sp.getSpriteId();
                BufferedImage cached = SPRITES.get(key);
                if (cached != null) {
                    setIcon(new ImageIcon(cached.getScaledInstance(20, 20, Image.SCALE_SMOOTH)));
                } else {
                    setIcon(null);
                    plugin.getSpriteManager().getSpriteAsync(sp.getSpriteId(), 0, img -> {
                        if (img != null) {
                            SPRITES.put(key, img);
                            SwingUtilities.invokeLater(list::repaint);
                        }
                    });
                }
                return this;
            }
        });
        JScrollPane sc = new JScrollPane(list);
        sc.setPreferredSize(new Dimension(240, 400));
        int r = JOptionPane.showConfirmDialog(parent, sc, "Select teleport spell", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION ? list.getSelectedValue() : null;
    }

    /** A list of teleport items (jewellery, tablets...) with their icons. Null when cancelled. */
    public static TeleportItem pickItem(Component parent, RoutePlannerPlugin plugin) {
        DefaultListModel<TeleportItem> model = new DefaultListModel<>();
        for (TeleportItem it : TeleportItems.ALL) model.addElement(it);
        JList<TeleportItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(15);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                TeleportItem it = (TeleportItem) v;
                setText(it.getDisplayName());
                try {
                    BufferedImage img = plugin.getItemManager().getImage(it.getIconId());
                    setIcon(img != null ? new ImageIcon(img.getScaledInstance(20, 20, Image.SCALE_SMOOTH)) : null);
                } catch (Exception ex) { setIcon(null); }
                return this;
            }
        });
        JScrollPane sc = new JScrollPane(list);
        sc.setPreferredSize(new Dimension(240, 400));
        int r = JOptionPane.showConfirmDialog(parent, sc, "Select item teleport", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION ? list.getSelectedValue() : null;
    }

    /** The Portal Nexus destination as named in the nexus menu. Null when cancelled or invalid. */
    public static String pickNexus(Component parent, String current) {
        String dest = (String) JOptionPane.showInputDialog(parent,
            "Portal Nexus destination, as it's named in your nexus menu (e.g. Varrock):",
            "Portal Nexus teleport", JOptionPane.PLAIN_MESSAGE, null, null, current != null ? current : "");
        if (dest == null || dest.trim().isEmpty()) return null;
        String err = RouteTextValidator.checkField("Nexus destination", dest.trim());
        if (err != null) {
            JOptionPane.showMessageDialog(parent, err, "Links not allowed", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return dest.trim();
    }
}
