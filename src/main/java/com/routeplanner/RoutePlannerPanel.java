package com.routeplanner;

import com.routeplanner.util.RouteTextValidator;

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
    private javax.swing.JButton undoButton;
    private javax.swing.JButton redoButton;
    private javax.swing.JCheckBox editModeToggle;
    private int dropLineY = -1; // y position of the drag drop-line indicator (-1 = hidden)
    private final JPanel stepListPanel = new JPanel() {
        @Override protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (dropLineY >= 0) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setColor(new Color(255, 160, 40));
                g2.setStroke(new java.awt.BasicStroke(2.5f));
                g2.drawLine(6, dropLineY, getWidth() - 6, dropLineY);
                g2.fillOval(2, dropLineY - 3, 6, 6);
                g2.fillOval(getWidth() - 8, dropLineY - 3, 6, 6);
                g2.dispose();
            }
        }
    };
    private final JLabel activeRouteLabel = new JLabel("No active route");
    private JButton addRouteBtn;
    private JButton exportBtn;
    private JButton addStepHeaderBtn;
    private JButton addSectionBtn;
    private String selectedSectionId = null;
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

        java.awt.Component targetRow = nearestDropRow(pInList.y);
        if (targetRow == null) return;

        com.routeplanner.model.RouteSection targetSection = rowSectionMap.get(targetRow);
        if (targetSection == null) return;

        // A row with a section but no step is a section header: drop there means "append".
        com.routeplanner.model.RouteStep targetStep = rowStepMap.get(targetRow);
        if (targetStep == draggedStep) return;

        java.util.List<com.routeplanner.model.RouteStep> src = draggedSection.getSteps();
        java.util.List<com.routeplanner.model.RouteStep> dst = targetSection.getSteps();

        int from = src.indexOf(draggedStep);
        if (from < 0) return;
        int to = (targetStep == null) ? dst.size() : dst.indexOf(targetStep);
        if (to < 0) return;
        if (src == dst && from == to) return;

        plugin.getRouteHistory().push(plugin.getActiveRoute(),
            "Move step \"" + draggedStep.getName() + "\"", plugin.getRouteGson());

        src.remove(from);
        if (to > dst.size()) to = dst.size();
        dst.add(to, draggedStep);

        // Otherwise the step would silently disappear into a collapsed section.
        if (targetSection.isCollapsed()) targetSection.setCollapsed(false);

        plugin.saveRoutesPublic();
        refresh();
    }

    private java.awt.Component nearestDropRow(int y) {
        java.awt.Component best = null;
        int bestDist = Integer.MAX_VALUE;
        for (java.awt.Component comp : stepListPanel.getComponents()) {
            // Step rows and section header rows are both valid targets; struts and other
            // filler have no section mapping at all. Headers let a step be dropped into an
            // empty or collapsed section, which has no step rows to aim at.
            if (rowSectionMap.get(comp) == null) continue;
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

    /** Flat, borderless glyph button used for the undo/redo controls in the header. */
    private javax.swing.JButton historyButton(boolean redo) {
        javax.swing.JButton b = new javax.swing.JButton(new ArrowIcon(redo));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setMargin(new java.awt.Insets(0, 4, 0, 4));
        b.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        return b;
    }

    /**
     * A drawn undo/redo icon: a curved arrow, mirrored for redo. Painted rather than set as a
     * glyph so it renders identically regardless of the font the client resolves for Swing --
     * the Unicode arrows we used before fell back to a missing-glyph box on the live client.
     * Colour tracks the button's foreground so enabled/disabled states still work.
     */
    private static final class ArrowIcon implements javax.swing.Icon {
        private final boolean redo;
        ArrowIcon(boolean redo) { this.redo = redo; }
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(java.awt.Component comp, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            if (redo) { g2.translate(16, 0); g2.scale(-1, 1); } // mirror horizontally
            g2.setColor(comp.getForeground());
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // arc: an open loop from upper-right sweeping down to the left
            java.awt.geom.Arc2D arc = new java.awt.geom.Arc2D.Float(3, 3, 10, 10, 30, 220, java.awt.geom.Arc2D.OPEN);
            g2.draw(arc);
            // arrowhead at the tail end of the arc (lower-left)
            int hx = 4, hy = 11;
            java.awt.Polygon head = new java.awt.Polygon(
                new int[]{ hx, hx + 4, hx - 1 },
                new int[]{ hy - 4, hy + 1, hy + 3 }, 3);
            g2.fill(head);
            g2.dispose();
        }
    }

    /**
     * Keep the header toggle in step with the config value. setSelected does not fire an
     * ActionListener, so this cannot loop back into setMode.
     */
    private void syncEditModeToggle(boolean dev) {
        if (editModeToggle == null) return;
        if (editModeToggle.isSelected() != dev) editModeToggle.setSelected(dev);
    }

    /** Sync the header undo/redo controls with the active route's history. Dev mode only. */
    private void updateHistoryButtons(boolean dev) {
        if (undoButton == null || redoButton == null) return;
        undoButton.setVisible(dev);
        redoButton.setVisible(dev);
        if (!dev) return;

        com.routeplanner.model.Route active = plugin.getActiveRoute();
        RouteHistory history = plugin.getRouteHistory();
        String undoLabel = history.peekUndoLabel(active);
        String redoLabel = history.peekRedoLabel(active);

        Color off = new Color(74, 74, 74);
        undoButton.setEnabled(undoLabel != null);
        redoButton.setEnabled(redoLabel != null);
        undoButton.setForeground(undoLabel != null ? ColorScheme.LIGHT_GRAY_COLOR : off);
        redoButton.setForeground(redoLabel != null ? ColorScheme.LIGHT_GRAY_COLOR : off);
        undoButton.setToolTipText(undoLabel != null ? "Undo: " + undoLabel : "Nothing to undo");
        redoButton.setToolTipText(redoLabel != null ? "Redo: " + redoLabel : "Nothing to redo");
    }

    public RoutePlannerPlugin getPlugin() { return plugin; }

    private final java.awt.CardLayout cardLayout = new java.awt.CardLayout();
    private JPanel mainView;
    private com.routeplanner.hub.RouteHubPanel hubPanel;

    /** Escapes HTML-significant characters before embedding user-authored text (a route name)
     *  inside an HTML-interpreting JLabel. Route names already pass the link scanner on creation,
     *  but that doesn't guard against markup characters affecting rendering. */
    private static String escapeRouteName(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public void init(RoutePlannerPlugin plugin) {
        this.plugin = plugin;
        this.spriteManager = plugin.getSpriteManager();

        // The panel root is a two-card swap: "main" (everything below, unchanged) and "hub"
        // (the Route Hub browse view). Only mainView's construction differs from before --
        // it gets what "this" used to get directly.
        setLayout(cardLayout);
        mainView = new JPanel(new BorderLayout(0, 8));
        mainView.setBorder(new EmptyBorder(8, 8, 8, 8));
        mainView.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Route Planner");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.add(title, BorderLayout.WEST);

        JPanel historyRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
        historyRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        undoButton = historyButton(false);
        redoButton = historyButton(true);
        undoButton.addActionListener(e -> plugin.undoActive());
        redoButton.addActionListener(e -> plugin.redoActive());
        historyRow.add(undoButton);
        historyRow.add(redoButton);
        titleRow.add(historyRow, BorderLayout.EAST);

        editModeToggle = new javax.swing.JCheckBox("Edit mode");
        editModeToggle.setBackground(ColorScheme.DARK_GRAY_COLOR);
        editModeToggle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        editModeToggle.setFocusPainted(false);
        editModeToggle.setFont(editModeToggle.getFont().deriveFont(11f));
        editModeToggle.setToolTipText("Add, edit, reorder and delete steps. Turn off to just follow the route.");
        editModeToggle.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        editModeToggle.addActionListener(e -> plugin.setMode(
            editModeToggle.isSelected() ? com.routeplanner.RouteMode.DEVELOPER
                                        : com.routeplanner.RouteMode.PLAYER));

        JPanel modeRow = new JPanel(new BorderLayout());
        modeRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        modeRow.add(editModeToggle, BorderLayout.WEST);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        northPanel.add(titleRow, BorderLayout.NORTH);
        northPanel.add(modeRow, BorderLayout.SOUTH);
        mainView.add(northPanel, BorderLayout.NORTH);

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
        JButton importBtn = new JButton("Import \u25be");
        importBtn.setForeground(new Color(100, 180, 255));
        importBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        importBtn.setBorderPainted(false);
        importBtn.setFocusPainted(false);
        importBtn.setFont(importBtn.getFont().deriveFont(Font.PLAIN, 15f));
        importBtn.setToolTipText("Import a route from a file, or browse the Route Hub");
        importBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        JPopupMenu importMenu = new JPopupMenu();
        JMenuItem importFromFile = new JMenuItem("Import from file");
        importFromFile.addActionListener(e -> importExport.importRoute());
        JMenuItem browseHub = new JMenuItem("Browse Route Hub");
        browseHub.addActionListener(e -> showHub());
        importMenu.add(importFromFile);
        importMenu.add(browseHub);
        importBtn.addActionListener(e -> importMenu.show(importBtn, 0, importBtn.getHeight()));

        routeBottomRow.add(importBtn);

        exportBtn = new JButton("Export \u25be");
        exportBtn.setForeground(new Color(220, 50, 50));
        exportBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        exportBtn.setBorderPainted(false);
        exportBtn.setFocusPainted(false);
        exportBtn.setFont(exportBtn.getFont().deriveFont(Font.PLAIN, 15f));
        exportBtn.setToolTipText("Export the active route, or share one to the Route Hub");
        exportBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        JPopupMenu exportMenu = new JPopupMenu();
        JMenuItem exportToFile = new JMenuItem("Export to file");
        exportToFile.addActionListener(e -> {
            if (plugin.getActiveRoute() != null) {
                importExport.exportRoute(plugin.getActiveRoute());
            } else {
                JOptionPane.showMessageDialog(null, "No active route to export.");
            }
        });
        JMenuItem shareToHub = new JMenuItem("Share a route to the Hub");
        shareToHub.setToolTipText("Opens the routes repo on GitHub \u2014 submitting is a pull "
            + "request, not an upload from the plugin. See the repo's README for the how-to.");
        shareToHub.addActionListener(e ->
            net.runelite.client.util.LinkBrowser.browse("https://github.com/BlackDeath-Osrs/route-planner-routes"));
        exportMenu.add(exportToFile);
        exportMenu.add(shareToHub);
        exportBtn.addActionListener(e -> exportMenu.show(exportBtn, 0, exportBtn.getHeight()));

        routeBottomRow.add(exportBtn);

        JPanel routeButtonsStack = new JPanel();
        routeButtonsStack.setLayout(new BoxLayout(routeButtonsStack, BoxLayout.Y_AXIS));
        routeButtonsStack.setOpaque(false);
        routeButtonsStack.add(routeBottomRow);
        routeHeader.add(routeButtonsStack, BorderLayout.SOUTH);

        JPanel routeContainer = new JPanel(new BorderLayout());
        routeContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        routeContainer.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        // Was capped at a fixed 150px, which didn't scale as routes accumulated or as route names
        // started wrapping to two lines -- more rows/taller rows just got squeezed into the same
        // small budget, making the list feel compressed and its scrollbar work harder than it
        // should. Raised to a roomier ceiling that comfortably fits several two-line-wrapped rows.
        routeContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        // A max/preferred size alone is negotiable -- BoxLayout can still compress a component
        // below both when the combined preferred heights of everything in centerPanel (route
        // list + step list, which has no cap of its own and always wants more room as steps grow)
        // exceed the panel's real available space. Without an explicit MINIMUM, the route list was
        // the one that gave, shrinking as the step count grew. This makes 230px a hard floor.
        routeContainer.setMinimumSize(new Dimension(0, 230));
        routeContainer.add(routeHeader, BorderLayout.NORTH);

        JScrollPane routeScroll = new JScrollPane(routeListPanel);
        routeScroll.setPreferredSize(new Dimension(0, 230));
        routeScroll.setMinimumSize(new Dimension(0, 230));
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
        JPanel stepsLabelRow = new JPanel(new BorderLayout());
        stepsLabelRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        stepsLabelRow.add(stepsLabel, BorderLayout.WEST);
        stepHeader.add(stepsLabelRow, BorderLayout.SOUTH);


        addStepHeaderBtn = new JButton("+ Add Step");
        addStepHeaderBtn.setForeground(new Color(0, 200, 0));
        addStepHeaderBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        addStepHeaderBtn.setBorderPainted(false);
        addStepHeaderBtn.setFocusPainted(false);
        addStepHeaderBtn.setFont(addStepHeaderBtn.getFont().deriveFont(Font.BOLD, 18f));
        addStepHeaderBtn.setToolTipText("Add Step");
        addStepHeaderBtn.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        addStepHeaderBtn.addActionListener(e -> {
            if (plugin.getActiveRoute() == null) {
                JOptionPane.showMessageDialog(this, "Select or create a route first.");
                return;
            }
            new StepEditorDialog(plugin, plugin.getActiveRoute(), null).setVisible(true);
        });
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

        mainView.add(centerPanel, BorderLayout.CENTER);

        add(mainView, "main");
        hubPanel = new com.routeplanner.hub.RouteHubPanel(plugin, plugin.getRouteHubCatalog(), this::showMain);
        add(hubPanel, "hub");
        cardLayout.show(this, "main");

        // importExport must be constructed here, unconditionally, during startup -- it was
        // previously only assigned inside showMain(), meaning a session that never visited the
        // Route Hub (and so never triggered showMain()) left this permanently null, causing a
        // NullPointerException the moment "Import from file" or "Export" was clicked.
        importExport = new RouteImportExport(plugin, this);

        // init() builds the route-list scaffolding above but never populates it -- refresh() is
        // the one method that actually draws rows from plugin.getRoutes(). Without this call, any
        // route that exists purely because loadRoutes() loaded it from saved config at startup
        // (with no subsequent in-session action like create/import/install to trigger a refresh)
        // silently never appears until something else happens to call refresh() later.
        refresh();
    }

    /** Switches the panel to the Route Hub browse view. */
    public void showHub() {
        cardLayout.show(this, "hub");
        hubPanel.onShown();
    }

    /** Switches back to the normal route-building view. */
    public void showMain() {
        cardLayout.show(this, "main");
        refresh();
    }

    private void applyMode() {
        boolean dev = plugin.getConfig() == null
            || plugin.getConfig().mode() == com.routeplanner.RouteMode.DEVELOPER;
        if (addRouteBtn != null) addRouteBtn.setVisible(dev);
        if (exportBtn != null) exportBtn.setVisible(dev);
        if (addStepHeaderBtn != null) addStepHeaderBtn.setVisible(dev);
        if (addSectionBtn != null) addSectionBtn.setVisible(dev);
        updateHistoryButtons(dev);
        syncEditModeToggle(dev);
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
                // Was a fixed 32px (one line only); a plain JLabel doesn't wrap on its own, so a
                // long route name either overflowed the row width (forcing a horizontal scroll on
                // the whole list) or got silently clipped. Wrapping the label in HTML lets it break
                // onto a second line, and the row's cap is raised to 48px so that second line has
                // somewhere to actually render instead of being cut off.
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

                JLabel name = new JLabel("<html><div style='width:150px;'>"
                    + escapeRouteName(route.getName()) + "</div></html>");
                name.setForeground(Color.WHITE);
                row.add(name, BorderLayout.CENTER);

                // Right-click context menu for route
                JPopupMenu routeMenu = new JPopupMenu();
                JMenuItem deleteRoute = new JMenuItem("Delete Route");
                deleteRoute.addActionListener(e -> {
                    int c = JOptionPane.showConfirmDialog(RoutePlannerPanel.this,
                        "Delete route \"" + route.getName() + "\" and its "
                            + route.getAllSteps().size() + " step(s)?\nThis cannot be undone.",
                        "Delete Route", JOptionPane.YES_NO_OPTION);
                    if (c == JOptionPane.YES_OPTION) plugin.deleteRoute(route);
                });
                JMenuItem renameRoute = new JMenuItem("Rename Route");
                renameRoute.addActionListener(e -> {
                    String newName = JOptionPane.showInputDialog(this,
                        "Route name:", route.getName());
                    if (newName != null && !newName.trim().isEmpty()
                            && !newName.trim().equals(route.getName())) {
                        route.setName(newName.trim());
                        plugin.saveRoutesPublic();
                        refresh();
                    }
                });
                JMenuItem resetRoute = new JMenuItem("Reset Progress");
                resetRoute.addActionListener(e -> { plugin.resetRoute(route); });
                routeMenu.add(renameRoute);
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
                    String arrow = section.isCollapsed() ? "+ " : "- ";
                    JLabel secLabel = new JLabel(arrow + section.getName() + "  (" + done + "/" + total + ")");
                    secLabel.setForeground(isSelected ? new Color(255, 180, 80) : Color.WHITE);
                    secLabel.setFont(secLabel.getFont().deriveFont(Font.BOLD, 13f));
                    secRow.add(secLabel, BorderLayout.CENTER);

                    JPopupMenu secMenu = new JPopupMenu();
                    JMenuItem renameSec = new JMenuItem("Rename Section");
                    renameSec.addActionListener(ev -> {
                        String nn = JOptionPane.showInputDialog(this, "Section name:", section.getName());
                        if (nn != null && !nn.trim().isEmpty()) {
                            String rnErr = RouteTextValidator.checkField("Section name", nn.trim());
                            if (rnErr != null) { JOptionPane.showMessageDialog(this, rnErr, "Links not allowed", JOptionPane.WARNING_MESSAGE); return; }
                            section.setName(nn.trim());
                            plugin.saveRoutesPublic();
                            refresh();
                        }
                    });
                    java.util.List<com.routeplanner.model.RouteSection> secList = active.getSections();
                    JMenuItem moveSecUp = new JMenuItem("Move Up");
                    moveSecUp.addActionListener(ev -> {
                        int i = secList.indexOf(section);
                        if (i > 0) {
                            java.util.Collections.swap(secList, i, i - 1);
                            plugin.saveRoutesPublic();
                            refresh();
                        }
                    });
                    JMenuItem moveSecDown = new JMenuItem("Move Down");
                    moveSecDown.addActionListener(ev -> {
                        int i = secList.indexOf(section);
                        if (i >= 0 && i < secList.size() - 1) {
                            java.util.Collections.swap(secList, i, i + 1);
                            plugin.saveRoutesPublic();
                            refresh();
                        }
                    });
                    long secDone = section.getSteps().stream().filter(RouteStep::isCompleted).count();
                    boolean allDone = !section.getSteps().isEmpty() && secDone == section.getSteps().size();
                    JMenuItem markSec = new JMenuItem(allDone ? "Mark Section Incomplete" : "Mark Section Complete");
                    markSec.addActionListener(ev -> {
                        for (RouteStep s : section.getSteps()) {
                            s.setCompleted(!allDone);
                            if (allDone) plugin.resetStepProgress(s); // un-completing -> reset
                        }
                        plugin.saveRoutesPublic();
                        refresh();
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
                    if (dev) { secMenu.add(moveSecUp); secMenu.add(moveSecDown); secMenu.add(markSec); secMenu.add(renameSec); secMenu.add(deleteSec); }
                    else { secMenu.add(markSec); }

                    final JPanel finalSecRow = secRow;
                    final com.routeplanner.model.RouteSection finalSection = section;
                    MouseAdapter secMa = new MouseAdapter() {
                        @Override public void mousePressed(MouseEvent e) {
                            if (e.isPopupTrigger()) { final int mx = e.getX(), my = e.getY(); SwingUtilities.invokeLater(() -> secMenu.show(finalSecRow, mx, my)); return; }
                            if (SwingUtilities.isLeftMouseButton(e)) {
                                selectedSectionId = finalSection.getId();
                                finalSection.setCollapsed(!finalSection.isCollapsed());
                                plugin.saveRoutesPublic();
                                refresh();
                            }
                        }
                        @Override public void mouseReleased(MouseEvent e) {
                            if (e.isPopupTrigger()) { final int mx = e.getX(), my = e.getY(); SwingUtilities.invokeLater(() -> secMenu.show(finalSecRow, mx, my)); }
                        }
                    };
                    secRow.addMouseListener(secMa);
                    secLabel.addMouseListener(secMa);

                    rowSectionMap.put(secRow, section);
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
                        editStep.addActionListener(ev -> new StepEditorDialog(plugin, plugin.getActiveRoute(), step).setVisible(true));
                        JMenuItem completeStep = new JMenuItem(step.isCompleted() ? "Mark Incomplete" : "Mark Complete");
                        completeStep.addActionListener(ev -> {
                            if (step.isCompleted()) {
                                step.setCompleted(false);
                                plugin.resetStepProgress(step);
                                step.setNpcKillProgress(0);
                                plugin.saveRoutesPublic();
                                refresh();
                            } else {
                                plugin.completeStep(step);
                            }
                        });
                        stepMenu.add(completeStep);
                        JMenu moveToSection = new JMenu("Move to Section");
                        {
                            java.util.List<com.routeplanner.model.RouteSection> allSecs = active.getSections();
                            int others = 0;
                            for (com.routeplanner.model.RouteSection ts : allSecs) {
                                if (ts == section) continue;
                                others++;
                                final com.routeplanner.model.RouteSection targetSec = ts;
                                JMenuItem mv = new JMenuItem(ts.getName());
                                mv.addActionListener(ev -> {
                                    active.removeStep(step);
                                    active.addStepToSection(step, targetSec.getId());
                                    plugin.saveRoutesPublic();
                                    refresh();
                                });
                                moveToSection.add(mv);
                            }
                            if (others == 0) {
                                JMenuItem none = new JMenuItem("(no other sections)");
                                none.setEnabled(false);
                                moveToSection.add(none);
                            }
                        }

                        if (dev) { stepMenu.add(moveToSection); stepMenu.add(editStep); stepMenu.add(deleteStep); }

                        final JPanel finalRow = row;
                        MouseAdapter ma = new MouseAdapter() {
                            @Override public void mousePressed(MouseEvent e) {
                                if (e.isPopupTrigger()) { final int mx = e.getX(), my = e.getY(); SwingUtilities.invokeLater(() -> stepMenu.show(finalRow, mx, my)); return; }
                                if (dev && SwingUtilities.isLeftMouseButton(e)) {
                                    draggedStep = step;
                                    draggedSection = finalSection;
                                    finalRow.setBackground(new Color(95, 95, 95));
                                    return;
                                }
                                if (!dev && SwingUtilities.isLeftMouseButton(e)) {
                                    if (step.isCompleted()) {
                                        step.setCompleted(false);
                                        plugin.resetStepProgress(step);
                                        step.setNpcKillProgress(0);
                                        plugin.saveRoutesPublic();
                                        refresh();
                                    } else {
                                        plugin.completeStep(step);
                                    }
                                }
                            }
                            @Override public void mouseDragged(MouseEvent e) {
                                if (!dev || draggedStep == null) return;
                                java.awt.Point pInList = SwingUtilities.convertPoint(
                                    (java.awt.Component) e.getSource(), e.getPoint(), stepListPanel);
                                java.awt.Component target = nearestDropRow(pInList.y);
                                int newLineY = -1;
                                if (target != null && rowStepMap.get(target) != draggedStep) {
                                    java.awt.Rectangle b = target.getBounds();
                                    // line above or below the target row depending on cursor position
                                    newLineY = (pInList.y < b.y + b.height / 2) ? b.y : b.y + b.height;
                                }
                                if (newLineY != dropLineY) {
                                    dropLineY = newLineY;
                                    stepListPanel.repaint();
                                }
                            }
                            @Override public void mouseReleased(MouseEvent e) {
                                if (e.isPopupTrigger()) { final int mx = e.getX(), my = e.getY(); SwingUtilities.invokeLater(() -> stepMenu.show(finalRow, mx, my)); return; }
                                if (dev && draggedStep != null) {
                                    java.awt.Point pInList = SwingUtilities.convertPoint(
                                        (java.awt.Component) e.getSource(), e.getPoint(), stepListPanel);
                                    dropLineY = -1;
                                    stepListPanel.repaint();
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


            routeListPanel.revalidate();
            routeListPanel.repaint();
            stepListPanel.revalidate();
            stepListPanel.repaint();
        });
    }

    
    private void onNewRoute() {
        String name = JOptionPane.showInputDialog(this, "Route name:", "New Route", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            String err = RouteTextValidator.checkField("Route name", name.trim());
            if (err != null) { JOptionPane.showMessageDialog(this, err, "Links not allowed", JOptionPane.WARNING_MESSAGE); return; }
            plugin.createRoute(name.trim());
        }
    }


    private void onAddSection() {

        if (plugin.getActiveRoute() == null) {
            JOptionPane.showMessageDialog(this, "Select or create a route first.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Section name:", "New Section", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        String secErr = RouteTextValidator.checkField("Section name", name.trim());
        if (secErr != null) { JOptionPane.showMessageDialog(this, secErr, "Links not allowed", JOptionPane.WARNING_MESSAGE); return; }
        com.routeplanner.model.RouteSection sec = new com.routeplanner.model.RouteSection(
            java.util.UUID.randomUUID().toString(), name.trim());
        plugin.getActiveRoute().getSections().add(sec);
        selectedSectionId = sec.getId();
        plugin.saveRoutesPublic();
        refresh();
    }


    













}
