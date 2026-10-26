package com.routeplanner.record;

import com.routeplanner.QuickStepDialog;
import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteSection;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.RouteTextValidator;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Review screen shown when a recording stops. Drafts are edited here (with the normal step
 * editor), then saved as a new section of the route the recording was started on. Nothing
 * reaches the route until Save, and closing with unsaved steps asks first.
 */
public class RecordReviewDialog extends JDialog {

    private final RoutePlannerPlugin plugin;
    private final Route route;
    private final List<RouteStep> drafts;
    private final DefaultListModel<RouteStep> model = new DefaultListModel<>();
    private final JList<RouteStep> list = new JList<>(model);

    public RecordReviewDialog(RoutePlannerPlugin plugin, Route route, List<RouteStep> drafts) {
        this.plugin = plugin;
        this.route = route;
        this.drafts = new ArrayList<>(drafts);

        setTitle("Review recording");
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onDiscard(); }
        });

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel header = new JLabel("<html>" + drafts.size() + " recorded steps for <b>"
            + escape(route.getName()) + "</b>. Edit or remove anything before saving.</html>");
        header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        root.add(header, BorderLayout.NORTH);

        for (RouteStep s : this.drafts) model.addElement(s);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new StepRenderer());
        list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) onEdit();
            }
        });
        if (!model.isEmpty()) list.setSelectedIndex(0);
        root.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel side = new JPanel(new GridLayout(0, 1, 0, 4));
        side.setBackground(ColorScheme.DARK_GRAY_COLOR);
        side.add(button("Edit", e -> onEdit()));
        side.add(button("Delete", e -> onDelete()));
        side.add(button("Move up", e -> move(-1)));
        side.add(button("Move down", e -> move(1)));
        JPanel sideWrap = new JPanel(new BorderLayout());
        sideWrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
        sideWrap.add(side, BorderLayout.NORTH);
        root.add(sideWrap, BorderLayout.EAST);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        bottom.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bottom.add(button("Discard", e -> onDiscard()));
        JButton save = button("Save to route", e -> onSave());
        save.setForeground(new Color(0, 200, 0));
        bottom.add(save);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(520, 420);
        setLocationRelativeTo(null);
    }

    private JButton button(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.addActionListener(action);
        return b;
    }

    private void onEdit() {
        RouteStep step = list.getSelectedValue();
        if (step == null) return;
        // The editor edits the draft in place; it isn't in the route yet, so nothing is saved.
        QuickStepDialog editor = new QuickStepDialog(plugin, route, step);
        editor.setOnSaved(list::repaint);
        editor.setVisible(true);
    }

    private void onDelete() {
        int i = list.getSelectedIndex();
        if (i < 0) return;
        model.remove(i);
        if (!model.isEmpty()) list.setSelectedIndex(Math.min(i, model.size() - 1));
    }

    private void move(int delta) {
        int i = list.getSelectedIndex();
        int j = i + delta;
        if (i < 0 || j < 0 || j >= model.size()) return;
        RouteStep s = model.remove(i);
        model.add(j, s);
        list.setSelectedIndex(j);
    }

    private void onSave() {
        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no steps left to save.");
            return;
        }
        if (!plugin.getRoutes().contains(route)) {
            JOptionPane.showMessageDialog(this, "The route \"" + route.getName()
                + "\" no longer exists, so these steps can't be saved to it.");
            return;
        }
        String defaultName = "Recording " + new SimpleDateFormat("MMM d HH:mm").format(new Date());
        String name = (String) JOptionPane.showInputDialog(this, "Section name:", "Save recording",
            JOptionPane.PLAIN_MESSAGE, null, null, defaultName);
        if (name == null || name.trim().isEmpty()) return;
        String err = RouteTextValidator.checkField("Section name", name.trim());
        if (err != null) {
            JOptionPane.showMessageDialog(this, err, "Links not allowed", JOptionPane.WARNING_MESSAGE);
            return;
        }

        plugin.getRouteHistory().push(route, "Record section \"" + name.trim() + "\"", plugin.getRouteGson());
        RouteSection section = new RouteSection(UUID.randomUUID().toString(), name.trim());
        route.getSections().add(section);
        for (int i = 0; i < model.size(); i++) route.addStepToSection(model.get(i), section.getId());
        plugin.saveRoutes();
        plugin.refreshHighlights();
        dispose();
    }

    private void onDiscard() {
        if (!model.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure? You will lose all " + model.size() + " recorded steps.",
                "Discard recording", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) return;
        }
        dispose();
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** One line per draft: its name plus tags for the components it carries. */
    private static class StepRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            RouteStep s = (RouteStep) value;
            StringBuilder tags = new StringBuilder();
            if (s.getTeleportMethod() != null) tags.append(" [Teleport]");
            else if (s.getWorldPoint() != null) tags.append(" [Location]");
            if (s.getTransitionPoint() != null) tags.append(" [Stairs]");
            if (s.hasBoarding()) tags.append(" [Boat]");
            if (s.hasItems()) tags.append(" [Items]");
            if (s.hasSkillGoal()) tags.append(" [Skill]");
            if (s.hasHighlight()) tags.append(" [Highlight]");
            if (s.getDialogOptions() != null && !s.getDialogOptions().trim().isEmpty()) tags.append(" [Dialog]");
            if (s.hasQuest()) tags.append(" [Quest]");
            if (s.hasNote()) tags.append(" [Note]");
            setText((index + 1) + ". " + s.getName() + tags);
            setBorder(new EmptyBorder(4, 6, 4, 6));
            return this;
        }
    }
}
