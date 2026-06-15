package com.routeplanner.agility;

import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AgilityTaskPanel extends JPanel {

    private final AgilityTaskManager manager;
    private JLabel statusLabel;
    private JLabel progressLabel;

    @Inject
    public AgilityTaskPanel(AgilityTaskManager manager) {
        this.manager = manager;
        setLayout(new BorderLayout(0, 6));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(8, 0, 0, 0));
        build();
    }

    private void build() {
        // Header
        JLabel header = new JLabel("Agility Task");
        header.setForeground(new Color(255, 165, 0));
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        add(header, BorderLayout.NORTH);

        // Status
        JPanel statusPanel = new JPanel(new BorderLayout(0, 4));
        statusPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        statusLabel = new JLabel("No active task");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusPanel.add(statusLabel, BorderLayout.NORTH);

        progressLabel = new JLabel("");
        progressLabel.setForeground(new Color(255, 165, 0));
        statusPanel.add(progressLabel, BorderLayout.CENTER);

        add(statusPanel, BorderLayout.CENTER);

        // Buttons
        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        btnPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton startBtn = new JButton("Start Agility Task");
        startBtn.addActionListener(e -> showStartDialog());
        btnPanel.add(startBtn);

        JButton stopBtn = new JButton("Stop Task");
        stopBtn.addActionListener(e -> {
            manager.stopTask();
            refresh();
        });
        btnPanel.add(stopBtn);

        add(btnPanel, BorderLayout.SOUTH);
    }

    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            AgilityTask task = manager.getActiveTask();
            if (task == null) {
                statusLabel.setText("No active task");
                progressLabel.setText("");
            } else {
                statusLabel.setText(task.getCourse().getName()
                    + " - Lap " + (task.getLapsCompleted() + 1));
                progressLabel.setText("Goal: " + task.getGoalValue()
                    + " (" + task.getGoalType().name() + ")");
            }
            revalidate();
            repaint();
        });
    }

    private void showStartDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this),
            "Start Agility Task", true);
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.setSize(300, 250);
        dialog.setLocationRelativeTo(this);

        JPanel form = new JPanel(new GridLayout(4, 2, 6, 6));
        form.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Course selector
        form.add(new JLabel("Course:"));
        JComboBox<AgilityCourse> courseBox = new JComboBox<>();
        for (AgilityCourse c : AgilityCoursePresets.ALL) {
            courseBox.addItem(c);
        }
        courseBox.setRenderer((list, value, index, selected, focused) -> {
            JLabel label = new JLabel(value == null ? "" :
                value.getName() + " (Lv " + value.getLevelRequired() + ")");
            if (selected) label.setBackground(list.getSelectionBackground());
            label.setOpaque(true);
            return label;
        });
        form.add(courseBox);

        // Goal type
        form.add(new JLabel("Goal type:"));
        JComboBox<GoalType> goalTypeBox = new JComboBox<>(GoalType.values());
        form.add(goalTypeBox);

        // Goal value
        form.add(new JLabel("Goal value:"));
        JTextField goalField = new JTextField("10");
        form.add(goalField);

        // Hint label
        form.add(new JLabel(""));
        JLabel hint = new JLabel("<html><small>Level: 1-99<br>XP: e.g. 100000<br>Laps: e.g. 10</small></html>");
        hint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        form.add(hint);

        dialog.add(form, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton startBtn = new JButton("Start");
        startBtn.addActionListener(e -> {
            try {
                AgilityCourse course = (AgilityCourse) courseBox.getSelectedItem();
                GoalType goalType = (GoalType) goalTypeBox.getSelectedItem();
                long goalValue = Long.parseLong(goalField.getText().trim());
                manager.startTask(course, goalType, goalValue);
                refresh();
                dialog.dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid goal value.");
            }
        });
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        btnPanel.add(cancelBtn);
        btnPanel.add(startBtn);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }
}
