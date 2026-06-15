package com.routeplanner;

import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RoutePlannerPanel extends PluginPanel {

    private RoutePlannerPlugin plugin;
    private net.runelite.client.game.SpriteManager spriteManager;
    private RouteImportExport importExport;
    private final JPanel routeListPanel = new JPanel();
    private final JPanel stepListPanel = new JPanel();
    private final JLabel activeRouteLabel = new JLabel("No active route");
    private JButton addRouteBtn;
    private JButton exportBtn;
    private JButton addStepHeaderBtn;
    private JButton addSectionBtn;
    private String selectedSectionId = null;
    private boolean reorderMode = false;
    private javax.swing.JToggleButton reorderToggle;
    private com.routeplanner.model.RouteStep draggedStep = null;
    private com.routeplanner.model.RouteSection draggedSection = null;
    private final java.util.Map<java.awt.Component, com.routeplanner.model.RouteStep> rowStepMap = new java.util.HashMap<>();
    private final java.util.Map<java.awt.Component, com.routeplanner.model.RouteSection> rowSectionMap = new java.util.HashMap<>();
    private java.awt.Component dropTargetRow = null;
    private java.awt.Color dropTargetOrigBg = null;

    public String getSelectedSectionId() { return selectedSectionId; }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void handleStepDrop(java.awt.Point pInList) {
        if (draggedStep == null || draggedSection == null) return;
        com.routeplanner.model.RouteStep target = null;
        int best = Integer.MAX_VALUE;
        for (java.awt.Component comp : stepListPanel.getComponents()) {
            com.routeplanner.model.RouteStep s = rowStepMap.get(comp);
            if (s == null || rowSectionMap.get(comp) != draggedSection) continue;
            java.awt.Rectangle b = comp.getBounds();
            int center = b.y + b.height / 2;
            int dist = Math.abs(center - pInList.y);
            if (dist < best) { best = dist; target = s; }
        }
        if (target == null || target == draggedStep) return;
        java.util.List<com.routeplanner.model.RouteStep> steps = draggedSection.getSteps();
        int from = steps.indexOf(draggedStep);
        int to = steps.indexOf(target);
        if (from < 0 || to < 0 || from == to) return;
        steps.remove(from);
        steps.add(to, draggedStep);
        plugin.saveRoutesPublic();
        refresh();
    }

    private java.awt.Component nearestDropRow(int y) {
        java.awt.Component best = null;
        int bestDist = Integer.MAX_VALUE;
        for (java.awt.Component comp : stepListPanel.getComponents()) {
            if (rowStepMap.get(comp) == null) continue;
            if (rowSectionMap.get(comp) != draggedSection) continue;
            java.awt.Rectangle b = comp.getBounds();
            int center = b.y + b.height / 2;
            int dist = Math.abs(center - y);
            if (dist < bestDist) { bestDist = dist; best = comp; }
        }
        return best;
    }

    @Inject
    public RoutePlannerPanel() {
        super(false);
    }

    public RoutePlannerPlugin getPlugin() { return plugin; }

    public void init(RoutePlannerPlugin plugin) {
        this.plugin = plugin;
        this.spriteManager = plugin.getSpriteManager();
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Route Planner");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        add(title, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Routes section with + button in header
        routeListPanel.setLayout(new BoxLayout(routeListPanel, BoxLayout.Y_AXIS));
        routeListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Custom header panel with Routes label + green + button
        JPanel routeHeader = new JPanel(new BorderLayout());
        routeHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);
        routeHeader.setBorder(new EmptyBorder(2, 4, 2, 4));
        JLabel routesLabel = new JLabel("Routes");
        routesLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        routesLabel.setFont(routesLabel.getFont().deriveFont(Font.BOLD, 16f));

        addRouteBtn = new JButton("+ Add Route");
        addRouteBtn.setForeground(new Color(0, 200, 0));
        addRouteBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        addRouteBtn.setBorderPainted(false);
        addRouteBtn.setFocusPainted(false);
        addRouteBtn.setFont(addRouteBtn.getFont().deriveFont(Font.BOLD, 18f));
        addRouteBtn.setToolTipText("Add Route");
        addRouteBtn.addActionListener(e -> onNewRoute());
        addRouteBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        // Top row: Routes label + Add Route button
        JPanel routeTopRow = new JPanel(new BorderLayout());
        routeTopRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        routeTopRow.add(routesLabel, BorderLayout.WEST);
        routeTopRow.add(addRouteBtn, BorderLayout.EAST);
        routeHeader.add(routeTopRow, BorderLayout.NORTH);
        // Import button row
        JPanel routeBottomRow = new JPanel(new GridLayout(1, 2, 4, 0));
        routeBottomRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JButton importBtn = new JButton("Import");
        importBtn.setForeground(new Color(100, 180, 255));
        importBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        importBtn.setBorderPainted(false);
        importBtn.setFocusPainted(false);
        importBtn.setFont(importBtn.getFont().deriveFont(Font.PLAIN, 15f));
        importBtn.setToolTipText("Import Route from JSON");
        importBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        importBtn.addActionListener(e -> importExport.importRoute());
        routeBottomRow.add(importBtn);

        exportBtn = new JButton("Export");
        exportBtn.setForeground(new Color(220, 50, 50));
        exportBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        exportBtn.setBorderPainted(false);
        exportBtn.setFocusPainted(false);
        exportBtn.setFont(exportBtn.getFont().deriveFont(Font.PLAIN, 15f));
        exportBtn.setToolTipText("Export active route to JSON");
        exportBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        exportBtn.addActionListener(e -> {
            if (plugin.getActiveRoute() != null) {
                importExport.exportRoute(plugin.getActiveRoute());
            } else {
                JOptionPane.showMessageDialog(null, "No active route to export.");
            }
        });
        routeBottomRow.add(exportBtn);

        routeHeader.add(routeBottomRow, BorderLayout.SOUTH);

        JPanel routeContainer = new JPanel(new BorderLayout());
        routeContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        routeContainer.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        routeContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        routeContainer.add(routeHeader, BorderLayout.NORTH);

        JScrollPane routeScroll = new JScrollPane(routeListPanel);
        routeScroll.setPreferredSize(new Dimension(0, 120));
        routeScroll.setBorder(null);
        routeContainer.add(routeScroll, BorderLayout.CENTER);
        centerPanel.add(routeContainer);

        // Active route label
        activeRouteLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        activeRouteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        activeRouteLabel.setBorder(new EmptyBorder(4, 4, 2, 4));
        centerPanel.add(activeRouteLabel);

        // Steps section directly below with header + Add Step button
        stepListPanel.setLayout(new BoxLayout(stepListPanel, BoxLayout.Y_AXIS));
        stepListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel stepHeader = new JPanel(new BorderLayout());
        stepHeader.setBackground(ColorScheme.DARK_GRAY_COLOR);
        stepHeader.setBorder(new EmptyBorder(2, 4, 2, 4));
        JLabel stepsLabel = new JLabel("Steps");
        stepsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        stepsLabel.setFont(stepsLabel.getFont().deriveFont(Font.BOLD, 16f));
        reorderToggle = new javax.swing.JToggleButton("\u21C5 Reorder");
        reorderToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        reorderToggle.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        reorderToggle.setBorderPainted(false);
        reorderToggle.setFocusPainted(false);
        reorderToggle.setFont(reorderToggle.getFont().deriveFont(Font.BOLD, 11f));
        reorderToggle.setToolTipText("Drag steps to reorder within a section");
        reorderToggle.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        reorderToggle.addActionListener(e -> {
            reorderMode = reorderToggle.isSelected();
            reorderToggle.setForeground(reorderMode ? new Color(255, 180, 80) : ColorScheme.LIGHT_GRAY_COLOR);
            refresh();
        });

        JPanel stepsLabelRow = new JPanel(new BorderLayout());
        stepsLabelRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        stepsLabelRow.add(stepsLabel, BorderLayout.WEST);
        stepsLabelRow.add(reorderToggle, BorderLayout.EAST);
        stepHeader.add(stepsLabelRow, BorderLayout.SOUTH);


        addStepHeaderBtn = new JButton("+ Add Step");
        addStepHeaderBtn.setForeground(new Color(0, 200, 0));
        addStepHeaderBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        addStepHeaderBtn.setBorderPainted(false);
        addStepHeaderBtn.setFocusPainted(false);
        addStepHeaderBtn.setFont(addStepHeaderBtn.getFont().deriveFont(Font.BOLD, 18f));
        addStepHeaderBtn.setToolTipText("Add Step");
        addStepHeaderBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        addStepHeaderBtn.addActionListener(e -> onAddStep());
        addSectionBtn = new JButton("+ Section");
        addSectionBtn.setForeground(new Color(120, 170, 255));
        addSectionBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        addSectionBtn.setBorderPainted(false);
        addSectionBtn.setFocusPainted(false);
        addSectionBtn.setFont(addSectionBtn.getFont().deriveFont(Font.BOLD, 18f));
        addSectionBtn.setToolTipText("Add Section");
        addSectionBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        addSectionBtn.addActionListener(e -> onAddSection());
        JPanel headerBtns = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
        headerBtns.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerBtns.add(addSectionBtn);
        headerBtns.add(addStepHeaderBtn);
        stepHeader.add(headerBtns, BorderLayout.NORTH);

        JPanel stepContainer = new JPanel(new BorderLayout());
        stepContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        stepContainer.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        stepContainer.add(stepHeader, BorderLayout.NORTH);

        JScrollPane stepScroll = new JScrollPane(stepListPanel);
        stepScroll.setBorder(null);
        stepContainer.add(stepScroll, BorderLayout.CENTER);
        centerPanel.add(stepContainer);

        add(centerPanel, BorderLayout.CENTER);


        plugin.getAgilityTaskManager().setPanel(this);
        importExport = new RouteImportExport(plugin, this);

        refresh();
    }

    private void applyMode() {
        boolean dev = plugin.getConfig() == null
            || plugin.getConfig().mode() == com.routeplanner.RouteMode.DEVELOPER;
        if (addRouteBtn != null) addRouteBtn.setVisible(dev);
        if (exportBtn != null) exportBtn.setVisible(dev);
        if (addStepHeaderBtn != null) addStepHeaderBtn.setVisible(dev);
        if (addSectionBtn != null) addSectionBtn.setVisible(dev);
        if (reorderToggle != null) {
            reorderToggle.setVisible(dev);
            if (!dev) {
                reorderMode = false;
                reorderToggle.setSelected(false);
                reorderToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            }
        }
    }

    public void refresh() {
        SwingUtilities.invokeLater(() -> {
            applyMode();
            routeListPanel.removeAll();
            stepListPanel.removeAll();

            // Populate route list
            for (Route route : plugin.getRoutes()) {
                JPanel row = new JPanel(new BorderLayout());
                row.setBackground(route == plugin.getActiveRoute()
                    ? ColorScheme.BRAND_ORANGE_TRANSPARENT
                    : ColorScheme.DARKER_GRAY_COLOR);
                row.setBorder(new EmptyBorder(4, 6, 4, 6));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

                JLabel name = new JLabel(route.getName());
                name.setForeground(Color.WHITE);
                row.add(name, BorderLayout.CENTER);

                // Right-click context menu for route
                JPopupMenu routeMenu = new JPopupMenu();
                JMenuItem deleteRoute = new JMenuItem("Delete Route");
                deleteRoute.addActionListener(e -> plugin.deleteRoute(route));
                JMenuItem resetRoute = new JMenuItem("Reset Progress");
                resetRoute.addActionListener(e -> { plugin.resetRoute(route); });
                routeMenu.add(resetRoute);
                routeMenu.add(deleteRoute);
                row.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            plugin.setActiveRoute(route);
                        }
                        if (e.isPopupTrigger()) {
                            routeMenu.show(row, e.getX(), e.getY());
                        }
                    }
                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (e.isPopupTrigger()) {
                            routeMenu.show(row, e.getX(), e.getY());
                        }
                    }
                });

                routeListPanel.add(row);
                routeListPanel.add(Box.createVerticalStrut(2));
            }

            // Steps section directly below routes
            Route active = plugin.getActiveRoute();
            if (active != null) {
                activeRouteLabel.setText("Active: " + active.getName()
                    + " (" + active.getSteps().stream().filter(RouteStep::isCompleted).count()
                    + "/" + active.getSteps().size() + ")");

                // Drop a stale section selection from a previous route
                if (selectedSectionId != null && active.findSection(selectedSectionId) == null) {
                    selectedSectionId = null;
                }

                rowStepMap.clear();
                rowSectionMap.clear();
                for (com.routeplanner.model.RouteSection section : active.getSections()) {
                    boolean dev = plugin.getConfig() == null
                        || plugin.getConfig().mode() == com.routeplanner.RouteMode.DEVELOPER;

                    JPanel secRow = new JPanel(new BorderLayout());
                    boolean isSelected = section.getId().equals(selectedSectionId);
                    secRow.setBackground(isSelected ? new Color(70, 55, 25) : new Color(45, 45, 45));
                    secRow.setBorder(new EmptyBorder(5, 6, 5, 6));
                    secRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

                    int total = section.getSteps().size();
                    long done = section.getSteps().stream().filter(RouteStep::isCompleted).count();
                    String arrow = section.isCollapsed() ? "\u25B8 " : "\u25BE ";
                    JLabel secLabel = new JLabel(arrow + section.getName() + "  (" + done + "/" + total + ")");
                    secLabel.setForeground(isSelected ? new Color(255, 180, 80) : Color.WHITE);
                    secLabel.setFont(secLabel.getFont().deriveFont(Font.BOLD, 13f));
                    secRow.add(secLabel, BorderLayout.CENTER);

                    JPopupMenu secMenu = new JPopupMenu();
                    JMenuItem renameSec = new JMenuItem("Rename Section");
                    renameSec.addActionListener(ev -> {
                        String nn = JOptionPane.showInputDialog(this, "Section name:", section.getName());
                        if (nn != null && !nn.trim().isEmpty()) {
                            section.setName(nn.trim());
                            plugin.saveRoutesPublic();
                            refresh();
                        }
                    });
                    JMenuItem deleteSec = new JMenuItem("Delete Section");
                    deleteSec.addActionListener(ev -> {
                        int c = JOptionPane.showConfirmDialog(this,
                            "Delete section \"" + section.getName() + "\" and its "
                                + section.getSteps().size() + " step(s)?",
                            "Delete Section", JOptionPane.YES_NO_OPTION);
                        if (c == JOptionPane.YES_OPTION) {
                            active.getSections().remove(section);
                            if (section.getId().equals(selectedSectionId)) selectedSectionId = null;
                            plugin.saveRoutesPublic();
                            refresh();
                        }
                    });
                    if (dev) { secMenu.add(renameSec); secMenu.add(deleteSec); }

                    final JPanel finalSecRow = secRow;
                    final com.routeplanner.model.RouteSection finalSection = section;
                    MouseAdapter secMa = new MouseAdapter() {
                        @Override public void mousePressed(MouseEvent e) {
                            if (e.isPopupTrigger()) { secMenu.show(finalSecRow, e.getX(), e.getY()); return; }
                            if (SwingUtilities.isLeftMouseButton(e)) {
                                selectedSectionId = finalSection.getId();
                                finalSection.setCollapsed(!finalSection.isCollapsed());
                                plugin.saveRoutesPublic();
                                refresh();
                            }
                        }
                        @Override public void mouseReleased(MouseEvent e) {
                            if (e.isPopupTrigger()) secMenu.show(finalSecRow, e.getX(), e.getY());
                        }
                    };
                    secRow.addMouseListener(secMa);
                    secLabel.addMouseListener(secMa);

                    stepListPanel.add(secRow);
                    stepListPanel.add(Box.createVerticalStrut(2));

                    if (section.isCollapsed()) continue;

                    int num = 0;
                    for (RouteStep step : section.getSteps()) {
                        num++;
                        JPanel row = new JPanel(new BorderLayout());
                        row.setBackground(step.isCompleted() ? new Color(30, 60, 30) : ColorScheme.DARKER_GRAY_COLOR);
                        row.setBorder(new EmptyBorder(5, 14, 5, 8));

                        String mark = step.isCompleted() ? "\u2713" : "\u25CB";
                        String markColor = step.isCompleted() ? "#7fdf7f" : "#9a9a9a";
                        String typeTag = dev
                            ? " <font color='#8a8a8a'>[" + step.getType().name() + "]</font>" : "";
                        String html = "<html><body style='width:168px'>"
                            + "<font color='" + markColor + "'>" + mark + "</font> "
                            + num + ". " + htmlEscape(step.getName())
                            + typeTag
                            + "</body></html>";
                        JLabel stepLabel = new JLabel(html);
                        stepLabel.setForeground(step.isCompleted() ? new Color(150, 230, 150) : Color.WHITE);
                        row.add(stepLabel, BorderLayout.CENTER);
                        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));


                        JPopupMenu stepMenu = new JPopupMenu();
                        JMenuItem deleteStep = new JMenuItem("Delete Step");
                        deleteStep.addActionListener(ev -> plugin.removeStep(active, step));
                        JMenuItem editStep = new JMenuItem("Edit Step");
                        editStep.addActionListener(ev -> onEditStep(step));
                        JMenuItem completeStep = new JMenuItem(step.isCompleted() ? "Mark Incomplete" : "Mark Complete");
                        completeStep.addActionListener(ev -> {
                            if (step.isCompleted()) {
                                step.setCompleted(false);
                                step.setNpcKillProgress(0);
                                plugin.saveRoutesPublic();
                                refresh();
                            } else {
                                plugin.completeStep(step);
                            }
                        });
                        stepMenu.add(completeStep);
                        if (dev) { stepMenu.add(editStep); stepMenu.add(deleteStep); }

                        final JPanel finalRow = row;
                        MouseAdapter ma = new MouseAdapter() {
                            @Override public void mousePressed(MouseEvent e) {
                                if (e.isPopupTrigger()) { stepMenu.show(finalRow, e.getX(), e.getY()); return; }
                                if (reorderMode && SwingUtilities.isLeftMouseButton(e)) {
                                    draggedStep = step;
                                    draggedSection = finalSection;
                                    finalRow.setBackground(new Color(95, 95, 95));
                                    return;
                                }
                                if (!dev && SwingUtilities.isLeftMouseButton(e)) {
                                    if (step.isCompleted()) {
                                        step.setCompleted(false);
                                        step.setNpcKillProgress(0);
                                        plugin.saveRoutesPublic();
                                        refresh();
                                    } else {
                                        plugin.completeStep(step);
                                    }
                                }
                            }
                            @Override public void mouseDragged(MouseEvent e) {
                                if (!reorderMode || draggedStep == null) return;
                                java.awt.Point pInList = SwingUtilities.convertPoint(
                                    (java.awt.Component) e.getSource(), e.getPoint(), stepListPanel);
                                java.awt.Component target = nearestDropRow(pInList.y);
                                if (target == dropTargetRow) return;
                                if (dropTargetRow != null) dropTargetRow.setBackground(dropTargetOrigBg);
                                dropTargetRow = null;
                                if (target != null && rowStepMap.get(target) != draggedStep) {
                                    dropTargetOrigBg = target.getBackground();
                                    target.setBackground(new Color(255, 140, 0));
                                    dropTargetRow = target;
                                }
                            }
                            @Override public void mouseReleased(MouseEvent e) {
                                if (e.isPopupTrigger()) { stepMenu.show(finalRow, e.getX(), e.getY()); return; }
                                if (reorderMode && draggedStep != null) {
                                    java.awt.Point pInList = SwingUtilities.convertPoint(
                                        (java.awt.Component) e.getSource(), e.getPoint(), stepListPanel);
                                    if (dropTargetRow != null) dropTargetRow.setBackground(dropTargetOrigBg);
                                    dropTargetRow = null;
                                    handleStepDrop(pInList);
                                    draggedStep = null;
                                    draggedSection = null;
                                    refresh();
                                }
                            }
                        };
                        row.addMouseListener(ma);
                        row.addMouseMotionListener(ma);
                        stepLabel.addMouseListener(ma);
                        stepLabel.addMouseMotionListener(ma);
                        rowStepMap.put(row, step);
                        rowSectionMap.put(row, finalSection);

                        stepListPanel.add(row);
                        stepListPanel.add(Box.createVerticalStrut(2));
                    }
                }
            } else {
                activeRouteLabel.setText("No active route - click one above");
            }

            // Agility task section
            com.routeplanner.agility.AgilityTask agilityTask =
                plugin.getAgilityTaskManager().getActiveTask();
            if (agilityTask != null) {
                stepListPanel.add(Box.createVerticalStrut(6));

                JPanel agilityHeader = new JPanel(new BorderLayout());
                agilityHeader.setBackground(new Color(80, 50, 0));
                agilityHeader.setBorder(new EmptyBorder(4, 6, 4, 6));
                agilityHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                JLabel agilityTitle = new JLabel("Agility: " + agilityTask.getCourse().getName());
                agilityTitle.setForeground(new Color(255, 165, 0));
                agilityTitle.setFont(agilityTitle.getFont().deriveFont(Font.BOLD));
                agilityHeader.add(agilityTitle, BorderLayout.CENTER);

                JPopupMenu agilityMenu = new JPopupMenu();
                JMenuItem stopAgility = new JMenuItem("Stop Agility Task");
                stopAgility.addActionListener(e -> {
                    plugin.getAgilityTaskManager().stopTask();
                    refresh();
                });
                agilityMenu.add(stopAgility);
                stepListPanel.add(agilityHeader);

                java.util.List<com.routeplanner.agility.AgilityObstacle> obstacles =
                    agilityTask.getCourse().getObstacles();
                for (int i = 0; i < obstacles.size(); i++) {
                    com.routeplanner.agility.AgilityObstacle obs = obstacles.get(i);
                    boolean isCurrent = i == agilityTask.getCurrentObstacleIndex();
                    JPanel obsRow = new JPanel(new BorderLayout());
                    obsRow.setBackground(isCurrent ? new Color(100, 60, 0) : ColorScheme.DARKER_GRAY_COLOR);
                    obsRow.setBorder(new EmptyBorder(3, 10, 3, 6));
                    obsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                    String obsPrefix = isCurrent ? ">> " : "   ";
                    JLabel obsLabel = new JLabel(obsPrefix + (i + 1) + ". " + obs.getName());
                    obsLabel.setForeground(isCurrent ? new Color(255, 165, 0) : Color.LIGHT_GRAY);
                    obsRow.add(obsLabel, BorderLayout.CENTER);
                    stepListPanel.add(obsRow);
                    stepListPanel.add(Box.createVerticalStrut(1));
                }

                JPanel progressRow = new JPanel(new BorderLayout());
                progressRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                progressRow.setBorder(new EmptyBorder(4, 6, 4, 6));
                progressRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                int level = plugin.getClient().getRealSkillLevel(net.runelite.api.Skill.AGILITY);
                long xp = plugin.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                JLabel progressLabel = new JLabel("Laps: " + agilityTask.getLapsCompleted()
                    + "  |  " + agilityTask.getProgressString(level, xp));
                progressLabel.setForeground(Color.CYAN);
                progressRow.add(progressLabel, BorderLayout.CENTER);
                stepListPanel.add(progressRow);

                if (agilityTask.getCurrentObstacleIndex() == 0 && agilityTask.getLapsCompleted() == 0) {
                    JPanel hintRow = new JPanel(new BorderLayout());
                    hintRow.setBackground(new Color(0, 60, 0));
                    hintRow.setBorder(new EmptyBorder(3, 6, 3, 6));
                    hintRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
                    JLabel hintLabel = new JLabel("Go to the START obstacle and click it!");
                    hintLabel.setForeground(Color.GREEN);
                    hintRow.add(hintLabel, BorderLayout.CENTER);
                    stepListPanel.add(hintRow);
                }
            }

            routeListPanel.revalidate();
            routeListPanel.repaint();
            stepListPanel.revalidate();
            stepListPanel.repaint();
        });
    }

    
    private void onNewRoute() {
        String name = JOptionPane.showInputDialog(this, "Route name:", "New Route", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            plugin.createRoute(name.trim());
        }
    }

    private void onEditStep(RouteStep step) {
        com.routeplanner.model.StepType t = step.getType();
        if (t == StepType.ITEM) {
            String items = (String) JOptionPane.showInputDialog(this,
                "Edit items (comma separated, / for alternatives):",
                "Edit Item Step", JOptionPane.PLAIN_MESSAGE, null, null, step.getItemList());
            if (items == null || items.trim().isEmpty()) return;
            items = items.trim();
            step.setItemList(items);
            String mode = step.getItemMode();
            String prefix = "SHOP".equals(mode) ? "Buy: "
                : "SELL".equals(mode) ? "Sell: "
                : "PICKUP".equals(mode) ? "Pickup: " : "Get: ";
            String firstName = items.split(",")[0].split("/")[0].trim();
            step.setName(prefix + firstName
                + (items.contains(",") ? " +" + (items.split(",").length - 1) + " more" : ""));
        } else if (t == StepType.NOTE) {
            javax.swing.JTextArea area = new javax.swing.JTextArea(5, 24);
            area.setText(step.getNoteText() != null ? step.getNoteText() : "");
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            int res = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
                new javax.swing.JScrollPane(area), "Edit Note",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (res != JOptionPane.OK_OPTION) return;
            String text = area.getText();
            if (text == null || text.trim().isEmpty()) return;
            text = text.trim();
            step.setNoteText(text);
            String firstLine = text.split("\\r?\\n")[0];
            step.setName("Note: " + (firstLine.length() > 30 ? firstLine.substring(0, 28) + ".." : firstLine));
            String npc = (String) JOptionPane.showInputDialog(this,
                "Highlight NPC (optional, blank = none):", "Edit NPC Highlight",
                JOptionPane.PLAIN_MESSAGE, null, null,
                step.getNpcHighlight() != null ? step.getNpcHighlight() : "");
            step.setNpcHighlight(npc != null && !npc.trim().isEmpty() ? npc.trim() : null);
            if (step.getNpcHighlight() != null) {
                String cnt = (String) JOptionPane.showInputDialog(this,
                    "Kills to auto-complete (0 = manual):", "Edit Kill Count",
                    JOptionPane.PLAIN_MESSAGE, null, null, String.valueOf(step.getNpcKillCount()));
                if (cnt != null && !cnt.trim().isEmpty()) {
                    try { step.setNpcKillCount(Math.max(0, Integer.parseInt(cnt.trim()))); }
                    catch (NumberFormatException ignored) {}
                }
            } else {
                step.setNpcKillCount(0);
            }
        } else {
            String name = (String) JOptionPane.showInputDialog(this,
                "Step label:", "Edit Step", JOptionPane.PLAIN_MESSAGE, null, null, step.getName());
            if (name == null || name.trim().isEmpty()) return;
            step.setName(name.trim());
        }
        plugin.saveRoutesPublic();
        refresh();
        if (step.getType() == StepType.NOTE || step.getType() == StepType.SKILLING) {
            plugin.rebuildNpcHighlights();
        }
    }

    private void onAddSection() {

        if (plugin.getActiveRoute() == null) {
            JOptionPane.showMessageDialog(this, "Select or create a route first.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Section name:", "New Section", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        com.routeplanner.model.RouteSection sec = new com.routeplanner.model.RouteSection(
            java.util.UUID.randomUUID().toString(), name.trim());
        plugin.getActiveRoute().getSections().add(sec);
        selectedSectionId = sec.getId();
        plugin.saveRoutesPublic();
        refresh();
    }

    private void onAddStep() {
        if (plugin.getActiveRoute() == null) {
            JOptionPane.showMessageDialog(this, "Select or create a route first.");
            return;
        }

        String[] options = {"Location", "Item", "Skilling Goal", "Note"};
        int choice = JOptionPane.showOptionDialog(this,
            "What type of step?", "Add Step",
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
            null, options, options[0]);

        if (choice == 3) { showNoteDialog(); return; }
        if (choice == 0) {
            // Location sub-menu
            String[] locOptions = {"Walk to Location", "Teleport"};
            int locChoice = JOptionPane.showOptionDialog(this,
                "Location type:", "Add Location Step",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, locOptions, locOptions[0]);
            if (locChoice == 1) { showTeleportDialog(); return; }
            if (locChoice < 0) return;
            // Location - handled by right-click, or manual entry
            String name = JOptionPane.showInputDialog(this, "Step name:");
            if (name == null || name.trim().isEmpty()) return;
            String xStr = JOptionPane.showInputDialog(this, "World X:");
            String yStr = JOptionPane.showInputDialog(this, "World Y:");
            if (xStr == null || yStr == null) return;
            try {
                int x = Integer.parseInt(xStr.trim());
                int y = Integer.parseInt(yStr.trim());
                plugin.addStep(plugin.getActiveRoute(),
                    new RouteStep(name.trim(), StepType.LOCATION, new WorldPoint(x, y, 0), -1));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid coordinates.");
            }
            return;
        }

        if (choice == 1) {
            // Item step - ask Bank, Buy, or Sell mode
            Object[] modeOptions = {"Fetch from Bank", "Buy Item (Shop)", "Sell to Shop", "Pick up from Ground"};
            int modeChoice = JOptionPane.showOptionDialog(this,
                "How will these items be handled?", "Item Step",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, modeOptions, modeOptions[0]);
            String mode;
            String promptVerb;
            String prefix;
            String title;
            if (modeChoice == 1) {
                mode = "SHOP"; promptVerb = "buy"; prefix = "Buy: "; title = "Buy Item Step";
            } else if (modeChoice == 2) {
                mode = "SELL"; promptVerb = "sell"; prefix = "Sell: "; title = "Sell Item Step";
            } else if (modeChoice == 0) {
                mode = "BANK"; promptVerb = "gather"; prefix = "Get: "; title = "Bank Item Step";
            } else if (modeChoice == 3) {
                mode = "PICKUP"; promptVerb = "pick up"; prefix = "Pickup: "; title = "Pickup Item Step";
            } else {
                return;
            }

            String items = JOptionPane.showInputDialog(this,
                "<html>Enter items to " + promptVerb + " (comma separated)<br>" +
                "Use / for alternatives, prefix a quantity for stacks e.g:<br>" +
                "<b>Spade, 1mil Coins, Rune pouch/Divine Rune pouch</b></html>",
                title, JOptionPane.PLAIN_MESSAGE);
            if (items == null || items.trim().isEmpty()) return;
            String firstName = items.split(",")[0].split("/")[0].trim();
            String stepName = prefix + firstName + (items.contains(",") ? " +" + (items.split(",").length - 1) + " more" : "");
            RouteStep step = new RouteStep(stepName, StepType.ITEM, null, -1);
            step.setItemList(items.trim());
            step.setItemMode(mode);
            plugin.addStep(plugin.getActiveRoute(), step);
        } else if (choice == 2) {
            showSkillingDialog();
        }
    }

    
    private long parseXpValue(String input) throws NumberFormatException {
        if (input == null || input.trim().isEmpty()) throw new NumberFormatException("empty");
        String s = input.trim().toLowerCase()
            .replace(",", "").replace(" ", "");

        double multiplier = 1;
        if (s.endsWith("mil") || s.endsWith("m")) {
            multiplier = 1_000_000;
            s = s.replaceAll("mil$|m$", "");
        } else if (s.endsWith("k")) {
            multiplier = 1_000;
            s = s.replaceAll("k$", "");
        }

        return (long) (Double.parseDouble(s) * multiplier);
    }

    private void showTeleportDialog() {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }

            java.util.List<com.routeplanner.teleport.TeleportSpell> allSpells =
                com.routeplanner.teleport.TeleportSpells.ALL;

            // Use TeleportSpell directly in list
            javax.swing.DefaultListModel<com.routeplanner.teleport.TeleportSpell> listModel =
                new javax.swing.DefaultListModel<>();
            for (com.routeplanner.teleport.TeleportSpell s : allSpells) listModel.addElement(s);

            javax.swing.JList<com.routeplanner.teleport.TeleportSpell> jlist =
                new javax.swing.JList<>(listModel);
            jlist.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
            jlist.setSelectedIndex(0);
            jlist.setVisibleRowCount(15);
            jlist.setBackground(new Color(45, 45, 45));
            jlist.setForeground(Color.WHITE);
            jlist.setFont(new Font("Arial", Font.PLAIN, 13));
            jlist.setFixedCellHeight(28);
            jlist.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(
                        javax.swing.JList<?> list, Object value, int index,
                        boolean isSelected, boolean hasFocus) {
                    super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
                    com.routeplanner.teleport.TeleportSpell spell =
                        (com.routeplanner.teleport.TeleportSpell) value;
                    setText(spell.getName());
                    setForeground(isSelected ? Color.WHITE : Color.LIGHT_GRAY);
                    setBackground(isSelected ? new Color(255, 140, 0)
                        : index % 2 == 0 ? new Color(40, 40, 40) : new Color(50, 50, 50));
                    setFont(getFont().deriveFont(Font.PLAIN, 13f));
                    setBorder(new EmptyBorder(2, 4, 2, 4));
                    setOpaque(true);
                    try {
                        String path = "/com/routeplanner/spells/" + spell.getIconResource() + ".png";
                        java.net.URL url = RoutePlannerPanel.class.getResource(path);
                        if (url != null) {
                            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(url);
                            if (img != null) setIcon(new javax.swing.ImageIcon(
                                img.getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH)));
                            else setIcon(null);
                        } else setIcon(null);
                    } catch (Exception ex) { setIcon(null); }
                    return this;
                }
            });

            // Search field
            JTextField searchField = new JTextField();
            searchField.setBackground(new Color(60, 60, 60));
            searchField.setForeground(Color.WHITE);
            searchField.setCaretColor(Color.WHITE);

            javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane(jlist);
            scrollPane.setPreferredSize(new Dimension(320, 380));

            JPanel panel = new JPanel(new BorderLayout(4, 4));
            panel.add(new JLabel("Search:"), BorderLayout.NORTH);
            panel.add(searchField, BorderLayout.CENTER);

            JPanel main = new JPanel(new BorderLayout(4, 8));
            main.add(panel, BorderLayout.NORTH);
            main.add(scrollPane, BorderLayout.CENTER);

            // Filter on type
            searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void filter() {
                    String q = searchField.getText().toLowerCase();
                    listModel.clear();
                    for (com.routeplanner.teleport.TeleportSpell s : allSpells) {
                        if (s.getName().toLowerCase().contains(q)
                            || s.getBook().toLowerCase().contains(q)) {
                            listModel.addElement(s);
                        }
                    }
                    if (!listModel.isEmpty()) jlist.setSelectedIndex(0);
                }
                public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            });

            int result = JOptionPane.showConfirmDialog(
                SwingUtilities.getWindowAncestor(this),
                main, "Select Teleport Spell",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result != JOptionPane.OK_OPTION) return;

            com.routeplanner.teleport.TeleportSpell spell = jlist.getSelectedValue();
            if (spell == null) return;

            RouteStep step = new RouteStep(spell.getName(), StepType.TELEPORT, null, -1);
            step.setTeleportSpell(spell.getName());
            step.setTeleportDestination(spell.getDestination());
            plugin.addStep(plugin.getActiveRoute(), step);
        });
    }

    private void showNoteDialog() {
        if (plugin.getActiveRoute() == null) {
            JOptionPane.showMessageDialog(this, "Select or create a route first.");
            return;
        }
        javax.swing.JTextArea area = new javax.swing.JTextArea(5, 24);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        javax.swing.JScrollPane sp = new javax.swing.JScrollPane(area);
        int res = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(this), sp,
            "Add Note (info only, not tracked)",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (res != JOptionPane.OK_OPTION) return;
        String text = area.getText();
        if (text == null || text.trim().isEmpty()) return;
        text = text.trim();
        String firstLine = text.split("\\r?\\n")[0];
        String stepName = "Note: " + (firstLine.length() > 30 ? firstLine.substring(0, 28) + ".." : firstLine);
        RouteStep step = new RouteStep(stepName, StepType.NOTE, null, -1);
        step.setNoteText(text);
        String npc = JOptionPane.showInputDialog(this,
            "Highlight an NPC for this note? (optional)\n"
            + "Enter the NPC name exactly (e.g. Chicken), or leave blank.",
            "Highlight NPC (optional)", JOptionPane.PLAIN_MESSAGE);
        if (npc != null && !npc.trim().isEmpty()) {
            step.setNpcHighlight(npc.trim());
            String cntStr = JOptionPane.showInputDialog(this,
                "Auto-complete after how many kills?\n0 or blank = highlight only (mark complete manually).",
                "Kill Count (optional)", JOptionPane.PLAIN_MESSAGE);
            if (cntStr != null && !cntStr.trim().isEmpty()) {
                try {
                    int cnt = Integer.parseInt(cntStr.trim());
                    if (cnt > 0) step.setNpcKillCount(cnt);
                } catch (NumberFormatException ignored) {}
            }
        }
        String dlg = JOptionPane.showInputDialog(this,
            "Highlight chat dialogue options in order? (optional)\n"
            + "Enter the option numbers to press, comma-separated (e.g. 3,1).\n"
            + "Leave blank for none.",
            "Dialogue Options (optional)", JOptionPane.PLAIN_MESSAGE);
        if (dlg != null && !dlg.trim().isEmpty()) {
            step.setDialogOptions(dlg.trim());
        }
        // Optional location: a located note becomes a pathfinding waypoint

        String[] locOpts = {"No location", "Use my current tile", "Enter coordinates"};
        int locChoice = JOptionPane.showOptionDialog(this,
            "Attach a location to this note?\n(The path will route to it.)",
            "Note Location", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
            null, locOpts, locOpts[0]);
        if (locChoice == 1) {
            WorldPoint here = plugin.getLastPlayerLocation();
            if (here != null) {
                step.setWorldPoint(here);
            } else {
                JOptionPane.showMessageDialog(this,
                    "Couldn't read your position (are you logged in?). Note added without a location.");
            }
        } else if (locChoice == 2) {
            String xStr = JOptionPane.showInputDialog(this, "World X:");
            String yStr = JOptionPane.showInputDialog(this, "World Y:");
            String pStr = JOptionPane.showInputDialog(this, "Plane (0-3, blank = 0):");
            if (xStr != null && yStr != null) {
                try {
                    int x = Integer.parseInt(xStr.trim());
                    int y = Integer.parseInt(yStr.trim());
                    int p = (pStr == null || pStr.trim().isEmpty()) ? 0 : Integer.parseInt(pStr.trim());
                    step.setWorldPoint(new WorldPoint(x, y, p));
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid coordinates — note added without a location.");
                }
            }
        }
        plugin.addStep(plugin.getActiveRoute(), step);
    }

    private static final String[] ALL_SKILLS = {
        "Agility", "Construction", "Herblore", "Thieving", "Crafting",
        "Fletching", "Mining", "Smithing", "Fishing",
        "Cooking", "Firemaking", "Woodcutting"
    };

    private void showSkillingDialog() {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }

            // Step 1: Pick skill from full list
            String skill = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Select skill to train:", "Add Skilling Step - 1/3",
                JOptionPane.PLAIN_MESSAGE, null, ALL_SKILLS, ALL_SKILLS[0]);
            if (skill == null) return;
            if (skill.equalsIgnoreCase("Agility")) {
                showAgilityTaskDialog();
                return;
            }
            if (skill.equalsIgnoreCase("Herblore")) {
                showHerbloreDialog();
                return;
            }
            if (skill.equalsIgnoreCase("Cooking")) {
                showCookingDialog();
                return;
            }
            if (skill.equalsIgnoreCase("Firemaking")) {
                showFiremakingDialog();
                return;
            }
            if (skill.equalsIgnoreCase("Smithing")) {
                showSmithingDialog();
                return;
            }
            if (skill.equalsIgnoreCase("Crafting")) {
                showCraftingDialog();
                return;
            }
            if (skill.equalsIgnoreCase("Fletching")) {
                showFletchingDialog();
                return;
            }

            // Step 2: Pick goal type
            String[] goalTypes = {"Gain XP amount", "Reach XP total", "Reach Level"};
            String goalType = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Goal type for " + skill + ":", "Add Skilling Step - 2/3",
                JOptionPane.PLAIN_MESSAGE, null, goalTypes, goalTypes[0]);
            if (goalType == null) return;

            // Step 3: Enter value
            String hint = goalType.equals("Reach Level") ? "(1-99)"
                : goalType.equals("Gain XP amount") ? "(e.g. 5k, 100k, 1m)" : "(e.g. 100k, 1m)";
            String valStr = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Enter goal value " + hint + ":",
                "Add Skilling Step - 3/3", JOptionPane.PLAIN_MESSAGE);
            if (valStr == null || valStr.trim().isEmpty()) return;

            // Step 4: Thieving target (NPC or stall)
            String targetNpc = null;
            String targetObject = null;
            if (skill.equalsIgnoreCase("Thieving")) {
                String[] thievingModes = {"None (no highlight)", "Pickpocket NPC", "Steal from stall"};
                int tMode = JOptionPane.showOptionDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Highlight a Thieving target? (optional):",
                    "Add Skilling Step - Thieving",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                    null, thievingModes, thievingModes[0]);

                if (tMode == 1) {
                    String[] npcOptions = com.routeplanner.skilling.ThievingNpcs.PICKPOCKET_NPCS
                        .stream().map(Object::toString).toArray(String[]::new);
                    String pickedNpc = (String) JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Pickpocket NPC to highlight:",
                        "Add Skilling Step - NPC",
                        JOptionPane.PLAIN_MESSAGE, null, npcOptions, npcOptions[0]);
                    if (pickedNpc != null) {
                        targetNpc = pickedNpc.contains(" (")
                            ? pickedNpc.substring(0, pickedNpc.indexOf(" (")) : pickedNpc;
                    }
                } else if (tMode == 2) {
                    String[] stallOptions = com.routeplanner.skilling.ThievingStalls.STALLS
                        .stream().map(Object::toString).toArray(String[]::new);
                    String pickedStall = (String) JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Stall to highlight:",
                        "Add Skilling Step - Stall",
                        JOptionPane.PLAIN_MESSAGE, null, stallOptions, stallOptions[0]);
                    if (pickedStall != null) {
                        targetObject = pickedStall.contains(" (")
                            ? pickedStall.substring(0, pickedStall.indexOf(" (")) : pickedStall;
                    }
                }
            }

            // Step 4b: Fishing gear reminder
            String gearReminder = null;
            if (skill.equalsIgnoreCase("Fishing")) {
                String[] methodOptions = com.routeplanner.skilling.FishingMethods.METHODS
                    .stream().map(Object::toString).toArray(String[]::new);
                String pickedMethod = (String) JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Fishing method (sets a gear reminder):",
                    "Add Skilling Step - Fishing",
                    JOptionPane.PLAIN_MESSAGE, null, methodOptions, methodOptions[0]);
                if (pickedMethod != null) {
                    com.routeplanner.skilling.FishingMethods.Method m =
                        com.routeplanner.skilling.FishingMethods.getByLabel(pickedMethod);
                    if (m != null) {
                        gearReminder = m.gear;
                        JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(this),
                            "Remember to bring: " + m.gear,
                            "Fishing Gear", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }

            // Step 4c: Construction gear reminder
            if (skill.equalsIgnoreCase("Construction")) {
                String[] cOptions = com.routeplanner.skilling.ConstructionMethods.METHODS
                    .stream().map(Object::toString).toArray(String[]::new);
                String pickedC = (String) JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Construction method (sets a gear reminder):",
                    "Add Skilling Step - Construction",
                    JOptionPane.PLAIN_MESSAGE, null, cOptions, cOptions[0]);
                if (pickedC != null) {
                    com.routeplanner.skilling.ConstructionMethods.Method cm =
                        com.routeplanner.skilling.ConstructionMethods.getByLabel(pickedC);
                    if (cm != null) {
                        gearReminder = cm.gear;
                        JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(this),
                            "Remember to bring: " + cm.gear,
                            "Construction Gear", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }

            // Step 4d: Woodcutting tree highlight + Forestry world reminder
            if (skill.equalsIgnoreCase("Woodcutting")) {
                String[] treeOptions = com.routeplanner.skilling.WoodcuttingTrees.TREES
                    .stream().map(Object::toString).toArray(String[]::new);
                String pickedTree = (String) JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Tree to chop (highlights it in the world):",
                    "Add Skilling Step - Woodcutting",
                    JOptionPane.PLAIN_MESSAGE, null, treeOptions, treeOptions[0]);
                if (pickedTree != null) {
                    com.routeplanner.skilling.WoodcuttingTrees.Tree t =
                        com.routeplanner.skilling.WoodcuttingTrees.getByLabel(pickedTree);
                    if (t != null) {
                        targetObject = t.objectName;
                        gearReminder = "Best axe, World 444 (Forestry)";
                        JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(this),
                            "Chop on World 444 (the Forestry world). Bring your best axe.",
                            "Woodcutting", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }

            // Step 4e: Mining rock highlight
            if (skill.equalsIgnoreCase("Mining")) {
                String[] rockOptions = com.routeplanner.skilling.MiningRocks.ROCKS
                    .stream().map(Object::toString).toArray(String[]::new);
                String pickedRock = (String) JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Rock to mine (highlights it in the world):",
                    "Add Skilling Step - Mining",
                    JOptionPane.PLAIN_MESSAGE, null, rockOptions, rockOptions[0]);
                if (pickedRock != null) {
                    com.routeplanner.skilling.MiningRocks.Rock r =
                        com.routeplanner.skilling.MiningRocks.getByLabel(pickedRock);
                    if (r != null) {
                        targetObject = r.objectName;
                        gearReminder = "Best pickaxe for your level";
                        JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(this),
                            "Bring your best pickaxe.",
                            "Mining", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }

            try {
                long goalValue = parseXpValue(valStr);
                String goalTypeKey = goalType.equals("Gain XP amount") ? "XP_GAIN"
                    : goalType.equals("Reach XP total") ? "XP_TARGET" : "LEVEL";

                String npcPart = targetNpc != null ? " [" + targetNpc + "]"
                    : targetObject != null ? " [" + targetObject + "]" : "";
                String gearPart = gearReminder != null ? " [Bring: " + gearReminder + "]" : "";
                String stepName = skill + " - " + goalType + ": " + valStr.trim() + npcPart + gearPart;
                net.runelite.api.Skill apiSkill = getApiSkill(skill);
                long startXp = (goalTypeKey.equals("XP_GAIN") && apiSkill != null)
                    ? plugin.getClient().getSkillExperience(apiSkill) : 0;

                RouteStep step = new RouteStep(stepName, skill.toUpperCase(),
                    goalTypeKey, goalValue, startXp);
                if (targetNpc != null) step.setSkillingTargetNpc(targetNpc);
                if (targetObject != null) step.setSkillingTargetObject(targetObject);
                if (gearReminder != null) step.setGearReminder(gearReminder);
                plugin.addStep(plugin.getActiveRoute(), step);

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid value: use numbers like 5000, 5k, 1m");
            }
        });
    }

    private void showHerbloreDialog() {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }
            String[] potionOptions = com.routeplanner.skilling.PotionRecipes.POTIONS
                .stream().map(Object::toString).toArray(String[]::new);
            String picked = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Select potion to make:", "Add Herblore Step - 1/2",
                JOptionPane.PLAIN_MESSAGE, null, potionOptions, potionOptions[0]);
            if (picked == null) return;
            com.routeplanner.skilling.PotionRecipes.Potion pot =
                com.routeplanner.skilling.PotionRecipes.getByLabel(picked);
            if (pot == null) return;

            String qtyStr = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "How many " + pot.name + " to make? (e.g. 1000)",
                "Add Herblore Step - 2/2", JOptionPane.PLAIN_MESSAGE);
            if (qtyStr == null || qtyStr.trim().isEmpty()) return;

            try {
                long qty = parseXpValue(qtyStr);
                if (qty <= 0) return;
                long targetXp = Math.round(pot.xp * qty);
                String stepName = "Herblore - " + pot.name + " x" + qty;
                long startXp = plugin.getClient().getSkillExperience(net.runelite.api.Skill.HERBLORE);
                RouteStep step = new RouteStep(stepName, "HERBLORE", "XP_GAIN", targetXp, startXp);
                step.setHerblorePotion(pot.name);
                plugin.addStep(plugin.getActiveRoute(), step);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number: use values like 100, 1k, 5000");
            }
        });
    }

    private void showCookingDialog() {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }
            String[] foodOptions = com.routeplanner.skilling.CookingFoods.FOODS
                .stream().map(Object::toString).toArray(String[]::new);
            String picked = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Select food to cook:", "Add Cooking Step - 1/2",
                JOptionPane.PLAIN_MESSAGE, null, foodOptions, foodOptions[0]);
            if (picked == null) return;
            com.routeplanner.skilling.CookingFoods.Food f =
                com.routeplanner.skilling.CookingFoods.getByLabel(picked);
            if (f == null) return;

            String qtyStr = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "How many " + f.name + " to cook? (e.g. 1000)",
                "Add Cooking Step - 2/2", JOptionPane.PLAIN_MESSAGE);
            if (qtyStr == null || qtyStr.trim().isEmpty()) return;

            try {
                long qty = parseXpValue(qtyStr);
                if (qty <= 0) return;
                long targetXp = Math.round(f.xp * qty);
                String stepName = "Cooking - " + f.name + " x" + qty;
                long startXp = plugin.getClient().getSkillExperience(net.runelite.api.Skill.COOKING);
                RouteStep step = new RouteStep(stepName, "COOKING", "XP_GAIN", targetXp, startXp);
                step.setBankHighlightItems(f.rawItem);
                plugin.addStep(plugin.getActiveRoute(), step);
                JOptionPane.showMessageDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Cook on a range (the Hosidius range stops burns at higher levels).",
                    "Cooking", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number: use values like 100, 1k, 5000");
            }
        });
    }

    private void showFiremakingDialog() {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }
            String[] logOpts = com.routeplanner.skilling.FiremakingLogs.LOGS
                .stream().map(Object::toString).toArray(String[]::new);
            String[] methodOptions = new String[logOpts.length + 1];
            System.arraycopy(logOpts, 0, methodOptions, 0, logOpts.length);
            methodOptions[logOpts.length] = "Wintertodt (minigame)";

            String picked = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Firemaking method:", "Add Firemaking Step - 1/2",
                JOptionPane.PLAIN_MESSAGE, null, methodOptions, methodOptions[0]);
            if (picked == null) return;

            if (picked.startsWith("Wintertodt")) {
                String lvlStr = JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Target Firemaking level (1-99):",
                    "Wintertodt - 2/2", JOptionPane.PLAIN_MESSAGE);
                if (lvlStr == null || lvlStr.trim().isEmpty()) return;
                try {
                    long lvl = parseXpValue(lvlStr);
                    if (lvl < 1 || lvl > 99) {
                        JOptionPane.showMessageDialog(this, "Enter a level from 1 to 99.");
                        return;
                    }
                    String stepName = "Firemaking - Wintertodt to Lv " + lvl;
                    RouteStep step = new RouteStep(stepName, "FIREMAKING", "LEVEL", lvl, 0);
                    step.setGearReminder("Tinderbox, Axe, Knife, Food, Warm clothing");
                    step.setSkillingTargetObject("Brazier");
                    plugin.addStep(plugin.getActiveRoute(), step);
                    JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Wintertodt: bring a tinderbox, axe, knife, food, and warm clothing. The brazier is highlighted.",
                        "Wintertodt", JOptionPane.INFORMATION_MESSAGE);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid level: use a number 1-99.");
                }
                return;
            }

            com.routeplanner.skilling.FiremakingLogs.Log log =
                com.routeplanner.skilling.FiremakingLogs.getByLabel(picked);
            if (log == null) return;
            String qtyStr = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "How many " + log.name + " logs to burn? (e.g. 1000)",
                "Add Firemaking Step - 2/2", JOptionPane.PLAIN_MESSAGE);
            if (qtyStr == null || qtyStr.trim().isEmpty()) return;
            try {
                long qty = parseXpValue(qtyStr);
                if (qty <= 0) return;
                long targetXp = Math.round(log.xp * qty);
                String stepName = "Firemaking - " + log.name + " logs x" + qty;
                long startXp = plugin.getClient().getSkillExperience(net.runelite.api.Skill.FIREMAKING);
                RouteStep step = new RouteStep(stepName, "FIREMAKING", "XP_GAIN", targetXp, startXp);
                step.setBankHighlightItems("Tinderbox, " + log.item);
                plugin.addStep(plugin.getActiveRoute(), step);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number: use values like 100, 1k, 5000");
            }
        });
    }

    private void showSmithingDialog() {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }
            String[] modes = {
                "Smelt bars (ores into bars)",
                "Smith items (bars into gear)",
                "Giant's Foundry (minigame)"
            };
            String mode = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "What kind of Smithing?", "Add Smithing Step - 1/3",
                JOptionPane.PLAIN_MESSAGE, null, modes, modes[0]);
            if (mode == null) return;

            if (mode.startsWith("Giant's Foundry")) {
                String lvlStr = JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Target Smithing level (1-99):",
                    "Giant's Foundry", JOptionPane.PLAIN_MESSAGE);
                if (lvlStr == null || lvlStr.trim().isEmpty()) return;
                try {
                    long lvl = parseXpValue(lvlStr);
                    if (lvl < 1 || lvl > 99) {
                        JOptionPane.showMessageDialog(this, "Enter a level from 1 to 99.");
                        return;
                    }
                    String stepName = "Smithing - Giant's Foundry to Lv " + lvl;
                    RouteStep step = new RouteStep(stepName, "SMITHING", "LEVEL", lvl, 0);
                    step.setGearReminder("Metal bars (any type), Ice gloves or Smiths' gloves (i)");
                    plugin.addStep(plugin.getActiveRoute(), step);
                    JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Giant's Foundry: bring metal bars (more bars per mould = more XP). Ice gloves or Smiths' gloves (i) help handle the heat.",
                        "Giant's Foundry", JOptionPane.INFORMATION_MESSAGE);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid level: use a number 1-99.");
                }
                return;
            }

            boolean smelting = mode.startsWith("Smelt");

            java.util.List<com.routeplanner.skilling.SmithingBars.Bar> avail;
            if (smelting) {
                avail = com.routeplanner.skilling.SmithingBars.BARS;
            } else {
                avail = com.routeplanner.skilling.SmithingBars.BARS.stream()
                    .filter(b -> b.smithXp > 0)
                    .collect(java.util.stream.Collectors.toList());
            }
            String[] barOpts = avail.stream().map(Object::toString).toArray(String[]::new);
            String pickedBar = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                smelting ? "Bar to smelt:" : "Bar to smith into items:",
                "Add Smithing Step - 2/3",
                JOptionPane.PLAIN_MESSAGE, null, barOpts, barOpts[0]);
            if (pickedBar == null) return;
            com.routeplanner.skilling.SmithingBars.Bar bar =
                com.routeplanner.skilling.SmithingBars.getByLabel(pickedBar);
            if (bar == null) return;

            String qtyStr = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "How many " + bar.name + "s to " + (smelting ? "smelt" : "smith") + "? (e.g. 1000)",
                "Add Smithing Step - 3/3", JOptionPane.PLAIN_MESSAGE);
            if (qtyStr == null || qtyStr.trim().isEmpty()) return;
            try {
                long qty = parseXpValue(qtyStr);
                if (qty <= 0) return;
                double perBar = smelting ? bar.smeltXp : bar.smithXp;
                long targetXp = Math.round(perBar * qty);
                String stepName = "Smithing - " + (smelting ? "Smelt " : "Smith ") + bar.name + " x" + qty;
                long startXp = plugin.getClient().getSkillExperience(net.runelite.api.Skill.SMITHING);
                RouteStep step = new RouteStep(stepName, "SMITHING", "XP_GAIN", targetXp, startXp);
                step.setBankHighlightItems(smelting ? bar.ores : bar.name + ", Hammer");
                plugin.addStep(plugin.getActiveRoute(), step);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number: use values like 100, 1k, 5000");
            }
        });
    }

    private void showCraftingDialog() {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }
            String[] modes = {
                "Jewellery (furnace + mould)",
                "Glass (glassblowing pipe)",
                "Armour/weapons (leather & d'hide)"
            };
            String mode = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "What kind of Crafting?", "Add Crafting Step - 1/3",
                JOptionPane.PLAIN_MESSAGE, null, modes, modes[0]);
            if (mode == null) return;

            java.util.List<com.routeplanner.skilling.CraftingData.Item> list;
            if (mode.startsWith("Jewellery")) {
                list = com.routeplanner.skilling.CraftingData.JEWELRY;
            } else if (mode.startsWith("Glass")) {
                list = com.routeplanner.skilling.CraftingData.GLASS;
            } else {
                list = com.routeplanner.skilling.CraftingData.LEATHER;
            }
            String[] opts = list.stream().map(Object::toString).toArray(String[]::new);
            String picked = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Select item to craft:", "Add Crafting Step - 2/3",
                JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
            if (picked == null) return;
            com.routeplanner.skilling.CraftingData.Item it =
                com.routeplanner.skilling.CraftingData.getByLabel(list, picked);
            if (it == null) return;

            String qtyStr = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "How many " + it.name + " to make? (e.g. 1000)",
                "Add Crafting Step - 3/3", JOptionPane.PLAIN_MESSAGE);
            if (qtyStr == null || qtyStr.trim().isEmpty()) return;
            try {
                long qty = parseXpValue(qtyStr);
                if (qty <= 0) return;
                long targetXp = Math.round(it.xp * qty);
                String stepName = "Crafting - " + it.name + " x" + qty;
                long startXp = plugin.getClient().getSkillExperience(net.runelite.api.Skill.CRAFTING);
                RouteStep step = new RouteStep(stepName, "CRAFTING", "XP_GAIN", targetXp, startXp);
                step.setBankHighlightItems(it.materials);
                plugin.addStep(plugin.getActiveRoute(), step);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number: use values like 100, 1k, 5000");
            }
        });
    }

    private void showFletchingDialog() {
        SwingUtilities.invokeLater(() -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }
            String[] modes = {
                "Arrows & bolts",
                "Bows, crossbows & shields",
                "Vale Totems (minigame)"
            };
            String mode = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "What kind of Fletching?", "Add Fletching Step - 1/3",
                JOptionPane.PLAIN_MESSAGE, null, modes, modes[0]);
            if (mode == null) return;

            if (mode.startsWith("Vale Totems")) {
                String lvlStr = JOptionPane.showInputDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Target Fletching level (1-99):",
                    "Vale Totems", JOptionPane.PLAIN_MESSAGE);
                if (lvlStr == null || lvlStr.trim().isEmpty()) return;
                try {
                    long lvl = parseXpValue(lvlStr);
                    if (lvl < 1 || lvl > 99) {
                        JOptionPane.showMessageDialog(this, "Enter a level from 1 to 99.");
                        return;
                    }
                    String stepName = "Fletching - Vale Totems to Lv " + lvl;
                    RouteStep step = new RouteStep(stepName, "FLETCHING", "LEVEL", lvl, 0);
                    step.setGearReminder("Knife (or Fletching knife) + logs (oak+); needs Vale Totems miniquest & Auburnvale access (Varlamore), 20+ Fletching.");
                    plugin.addStep(plugin.getActiveRoute(), step);
                    JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Vale Totems (Auburnvale, Varlamore): bring/chop logs, fletch them and decorate the 8 totems for fast XP. Requires the Vale Totems miniquest and level 20 Fletching. The Plugin Hub 'Totem Fletching' plugin highlights trails and sites.",
                        "Vale Totems", JOptionPane.INFORMATION_MESSAGE);
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "Invalid level: use a number 1-99.");
                }
                return;
            }

            java.util.List<com.routeplanner.skilling.FletchingData.Item> list =
                mode.startsWith("Arrows")
                    ? com.routeplanner.skilling.FletchingData.AMMO
                    : com.routeplanner.skilling.FletchingData.BOWS;
            String[] opts = list.stream().map(Object::toString).toArray(String[]::new);
            String picked = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Select item to fletch:", "Add Fletching Step - 2/3",
                JOptionPane.PLAIN_MESSAGE, null, opts, opts[0]);
            if (picked == null) return;
            com.routeplanner.skilling.FletchingData.Item it =
                com.routeplanner.skilling.FletchingData.getByLabel(list, picked);
            if (it == null) return;

            String qtyStr = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "How many " + it.name + " to fletch? (e.g. 1000)",
                "Add Fletching Step - 3/3", JOptionPane.PLAIN_MESSAGE);
            if (qtyStr == null || qtyStr.trim().isEmpty()) return;
            try {
                long qty = parseXpValue(qtyStr);
                if (qty <= 0) return;
                long targetXp = Math.round(it.xp * qty);
                String stepName = "Fletching - " + it.name + " x" + qty;
                long startXp = plugin.getClient().getSkillExperience(net.runelite.api.Skill.FLETCHING);
                RouteStep step = new RouteStep(stepName, "FLETCHING", "XP_GAIN", targetXp, startXp);
                step.setBankHighlightItems(it.materials);
                plugin.addStep(plugin.getActiveRoute(), step);
                if (it.materials.contains("Knife")) {
                    JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Tip: a Fletching knife (Vale Totems reward) cuts bows/stocks/shields in 2 ticks instead of 3 - much faster than a regular knife if you cut a lot.",
                        "Fletching knife", JOptionPane.INFORMATION_MESSAGE);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number: use values like 100, 1k, 5000");
            }
        });
    }

    private net.runelite.api.Skill getApiSkill(String name) {
        try {
            return net.runelite.api.Skill.valueOf(name.toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    private void showAgilityTaskDialog() {
        SwingUtilities.invokeLater(() -> {
            // Step 1: pick course using plain showInputDialog (no JComboBox focus issues)
            java.util.List<com.routeplanner.agility.AgilityCourse> courses =
                com.routeplanner.agility.AgilityCoursePresets.ALL;
            String[] courseNames = courses.stream()
                .map(c -> c.getName() + " (Lv " + c.getLevelRequired() + ")")
                .toArray(String[]::new);

            String pickedCourse = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Select a course:", "Agility Task - Step 1/3",
                JOptionPane.PLAIN_MESSAGE, null, courseNames, courseNames[0]);
            if (pickedCourse == null) return;

            com.routeplanner.agility.AgilityCourse course = courses.stream()
                .filter(c -> pickedCourse.startsWith(c.getName()))
                .findFirst().orElse(null);
            if (course == null) return;

            // Step 2: pick goal type
            com.routeplanner.agility.GoalType[] goalTypes =
                com.routeplanner.agility.GoalType.values();
            String[] goalTypeNames = {"Target Level", "Target XP", "Number of Laps"};

            String pickedGoal = (String) JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Select goal type:", "Agility Task - Step 2/3",
                JOptionPane.PLAIN_MESSAGE, null, goalTypeNames, goalTypeNames[0]);
            if (pickedGoal == null) return;

            com.routeplanner.agility.GoalType goalType;
            if (pickedGoal.equals("Target Level")) goalType = com.routeplanner.agility.GoalType.TARGET_LEVEL;
            else if (pickedGoal.equals("Target XP")) goalType = com.routeplanner.agility.GoalType.TARGET_XP;
            else goalType = com.routeplanner.agility.GoalType.LAPS;

            // Step 3: enter goal value
            String hint = goalType == com.routeplanner.agility.GoalType.TARGET_LEVEL ? "(1-99)" :
                          goalType == com.routeplanner.agility.GoalType.TARGET_XP ? "(e.g. 100000)" : "(e.g. 10)";
            String goalStr = JOptionPane.showInputDialog(
                SwingUtilities.getWindowAncestor(this),
                "Enter goal value " + hint + ":", "Agility Task - Step 3/3",
                JOptionPane.PLAIN_MESSAGE);
            if (goalStr == null || goalStr.trim().isEmpty()) return;

            try {
                long goalValue = Long.parseLong(goalStr.trim());
                plugin.getAgilityTaskManager().startTask(course, goalType, goalValue);
                refresh();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid goal value.");
            }
        });
    }
}
