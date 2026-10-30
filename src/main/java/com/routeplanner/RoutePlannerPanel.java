package com.routeplanner;

import com.routeplanner.bank.BankItemManager;
import com.routeplanner.hub.RouteHubPanel;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteSection;
import com.routeplanner.model.RouteStep;
import com.routeplanner.record.RecordReviewDialog;
import com.routeplanner.record.RouteRecorder;
import com.routeplanner.ui.StepIcon;
import com.routeplanner.ui.StepLive;
import com.routeplanner.ui.ThinBar;
import com.routeplanner.ui.WrapLabel;
import com.routeplanner.util.RouteTextValidator;
import net.runelite.api.Quest;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The side panel.
 *
 *  - No routes yet: a welcome screen pointing at the Route Hub, recording, building and importing.
 *  - Otherwise three tabs: Route (the active route), My routes (the list) and Hub (the Route Hub).
 *  - The Route tab has a route bar (switch routes, edit-mode pencil), then either a card for the
 *    current step with what's up next (following), or the whole sectioned list (editing).
 */
public class RoutePlannerPanel extends PluginPanel {

    private static final Color ORANGE = ColorScheme.BRAND_ORANGE;
    private static final Color GREEN = ColorScheme.PROGRESS_COMPLETE_COLOR;
    private static final Color AMBER = ColorScheme.PROGRESS_INPROGRESS_COLOR;
    private static final Color NOW_BG = new Color(58, 44, 18);
    private static final Color SECTION_BG = new Color(45, 45, 45);
    private static final Color DONE_FG = new Color(111, 143, 111);
    private static final Color DIM_FG = new Color(124, 124, 124);
    private static final Color REC_BG = new Color(59, 20, 20);
    private static final Color REC_RED = new Color(230, 30, 30);
    private static final Color TIP_BG = new Color(20, 20, 20);

    private RoutePlannerPlugin plugin;
    private RouteImportExport importExport;

    // Root: welcome screen or tabs
    private final CardLayout rootCards = new CardLayout();
    private boolean forceTabs; // browsing the Hub from the welcome screen before owning a route
    private boolean showingGuide; // the quick guide is open over the welcome screen or tabs
    private final CardLayout guidePages = new CardLayout();
    private JPanel guideDeck;
    private JLabel guideCount;
    private GuideDots guideDots;
    private JButton guideBack, guideNext;
    private int guidePage;
    private MaterialTabGroup tabGroup;
    private MaterialTab routeTab, mineTab, hubTab;
    private RouteHubPanel hubPanel;

    // Route tab
    private JLabel routeNameLabel, routeSubLabel;
    private ThinBar routeBar;
    private JButton undoButton, redoButton, pencilButton, pauseButton;
    /** The route last paused from this panel, so the pause button can resume it. */
    private Route pausedRoute;
    private JPanel editTools;
    private JPanel recBanner;
    private JLabel recStats, recLast;
    private Timer recTimer;
    private long recStartMillis;
    private final JPanel routeContent = new JPanel();
    private boolean showAllSteps;

    // Step card (live parts)
    private RouteStep cardStep;
    private JLabel distLabel;
    private JPanel liveBox;
    private StepLive live;
    private String liveSig;
    private final Map<Integer, AsyncBufferedImage> itemImages = new HashMap<>();

    // My routes tab
    private final JPanel mineList = new JPanel();
    private IconTextField mineSearch;

    // Full step list (drag to reorder in edit mode)
    private int dropLineY = -1;
    private final StepListView stepList = new StepListView();
    private String selectedSectionId = null;
    private RouteStep draggedStep = null;
    private RouteSection draggedSection = null;

    @Inject
    public RoutePlannerPanel() {
        super(false);
    }

    public String getSelectedSectionId() { return selectedSectionId; }

    public RoutePlannerPlugin getPlugin() { return plugin; }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private boolean devMode() {
        return plugin.getConfig() == null || plugin.getConfig().mode() == RouteMode.DEVELOPER;
    }

    // ------------------------------------------------------------------ setup

    public void init(RoutePlannerPlugin plugin) {
        this.plugin = plugin;
        setLayout(rootCards);
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildWelcome(), "welcome");
        add(buildTabs(), "tabs");
        add(buildGuide(), "guide");

        importExport = new RouteImportExport(plugin, this);
        recTimer = new Timer(1000, e -> updateRecBanner());

        refresh();
    }

    private JPanel buildTabs() {
        JPanel tabsView = new JPanel(new BorderLayout());
        tabsView.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel display = new JPanel(new BorderLayout());
        display.setBackground(ColorScheme.DARK_GRAY_COLOR);
        tabGroup = new MaterialTabGroup(display);
        tabGroup.setBorder(new EmptyBorder(4, 0, 0, 0));

        hubPanel = new RouteHubPanel(plugin, plugin.getRouteHubCatalog(), () -> tabGroup.select(routeTab));
        hubPanel.setBackVisible(false);

        routeTab = new MaterialTab("Route", tabGroup, buildRouteView());
        mineTab = new MaterialTab("My routes", tabGroup, buildMineView());
        hubTab = new MaterialTab("Hub", tabGroup, hubPanel);
        hubTab.setOnSelectEvent(() -> { hubPanel.onShown(); return true; });
        tabGroup.addTab(routeTab);
        tabGroup.addTab(mineTab);
        tabGroup.addTab(hubTab);
        tabGroup.select(routeTab);

        tabsView.add(tabGroup, BorderLayout.NORTH);
        tabsView.add(display, BorderLayout.CENTER);
        return tabsView;
    }

    /** Switches to the Route Hub tab (also used by "Browse Route Hub" elsewhere). */
    public void showHub() {
        forceTabs = true;
        rootCards.show(this, "tabs");
        tabGroup.select(hubTab);
    }

    /** Back to the active route. */
    public void showMain() {
        tabGroup.select(routeTab);
        refresh();
    }

    // ------------------------------------------------------------------ welcome

    private JPanel buildWelcome() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("Route Planner");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        root.add(title, BorderLayout.NORTH);

        JPanel center = vbox();
        center.setBorder(new EmptyBorder(30, 6, 10, 6));
        JLabel icon = new JLabel(new Glyph(Glyph.MAP, 40, ColorScheme.MEDIUM_GRAY_COLOR));
        center.add(centered(icon));
        center.add(Box.createVerticalStrut(10));
        JLabel none = new JLabel("No routes yet");
        none.setForeground(Color.WHITE);
        none.setFont(FontManager.getRunescapeBoldFont());
        center.add(centered(none));
        center.add(Box.createVerticalStrut(4));
        JLabel blurb = new JLabel("<html><div style='text-align:center;width:170px'>"
            + "Follow a route another player shared, or make your own.</div></html>");
        blurb.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        center.add(centered(blurb));
        center.add(Box.createVerticalStrut(12));

        JButton hub = button("Browse the Route Hub", new Glyph(Glyph.GLOBE, 14, null), true);
        hub.addActionListener(e -> showHub());
        JButton rec = button("Record a route", new Glyph(Glyph.REC, 14, null), false);
        rec.addActionListener(e -> startFirstRoute(true));
        JButton build = button("Build one step by step", new Glyph(Glyph.PLUS, 14, null), false);
        build.addActionListener(e -> startFirstRoute(false));
        JButton imp = button("Import a file", new Glyph(Glyph.FILE, 14, null), false);
        imp.addActionListener(e -> importExport.importRoute());
        for (JButton b : new JButton[]{hub, rec, build, imp}) {
            b.setAlignmentX(Component.CENTER_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
            center.add(b);
            center.add(Box.createVerticalStrut(5));
        }
        center.add(Box.createVerticalStrut(6));
        JButton guide = linkButton("New here? Take the quick guide");
        guide.addActionListener(e -> showGuide());
        center.add(centered(guide));
        root.add(center, BorderLayout.CENTER);

        root.add(tip("Tip:", "press Record and just play. Talking, buying, gathering, agility laps "
            + "and kills turn into steps on their own."), BorderLayout.SOUTH);
        return root;
    }

    // ------------------------------------------------------------------ quick guide

    /** One page of the quick guide: a glyph, a title and a short how-to. */
    private static final String[][] GUIDE = {
        {"Make a route",
            "Pick <b>Build one step by step</b> and give your route a name. It opens in edit mode, "
            + "with the pencil lit at the top right."},
        {"Add steps",
            "Press <b>+ Step</b> and choose what the player does: Walk, Items, Talk, Kill, Skill, Quest "
            + "or Note. Fill in only what you need.<br><br><b>+ Section</b> groups steps into chapters."},
        {"Mark places in game",
            "Hold <b>Shift</b> and right-click any tile, then pick <b>Add Location Step</b>. A path line "
            + "will lead players there, and stairs or ladders on the way are found for you."},
        {"Or just record it",
            "Press <b>Record</b> and play. Walking, talking, banking, buying, skilling and kills turn into "
            + "steps on their own.<br><br>Press <b>Note</b> to add a tip, then <b>Stop</b> to review and keep."},
        {"Follow and share",
            "Click the pencil to leave edit mode. Follow the line; steps tick off as you do them.<br><br>"
            + "To share it, open <b>My routes</b>, click the <b>...</b> on your route and pick "
            + "<b>Share to the Route Hub</b>."},
    };
    private static final int[] GUIDE_GLYPHS = {Glyph.MAP, Glyph.PLUS, Glyph.PIN, Glyph.REC, Glyph.CHECK};

    private JPanel buildGuide() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JButton close = iconButton(new Glyph(Glyph.BACK, 14, null), "Close the guide");
        close.addActionListener(e -> closeGuide());
        JLabel title = new JLabel("Quick guide");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        guideCount = new JLabel();
        guideCount.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        guideCount.setFont(FontManager.getRunescapeSmallFont());
        top.add(close, BorderLayout.WEST);
        top.add(title, BorderLayout.CENTER);
        top.add(guideCount, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);

        guideDeck = new JPanel(guidePages);
        guideDeck.setOpaque(false);
        for (int i = 0; i < GUIDE.length; i++) guideDeck.add(guidePage(i), String.valueOf(i));
        root.add(guideDeck, BorderLayout.CENTER);

        JPanel bottom = vbox();
        guideDots = new GuideDots(GUIDE.length);
        bottom.add(centered(guideDots));
        bottom.add(Box.createVerticalStrut(8));
        JPanel nav = new JPanel(new GridLayout(1, 2, 6, 0));
        nav.setOpaque(false);
        guideBack = button("Back", null, false);
        guideBack.addActionListener(e -> showGuidePage(guidePage - 1));
        guideNext = button("Next", null, true);
        guideNext.addActionListener(e -> {
            if (guidePage < GUIDE.length - 1) showGuidePage(guidePage + 1);
            else if (plugin.getRoutes().isEmpty()) { closeGuide(); startFirstRoute(false); }
            else closeGuide();
        });
        nav.add(guideBack);
        nav.add(guideNext);
        nav.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        bottom.add(nav);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private JPanel guidePage(int i) {
        JPanel page = vbox();
        page.setOpaque(false);
        page.setBorder(new EmptyBorder(24, 4, 8, 4));

        JPanel badge = new JPanel(new GridBagLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ColorScheme.DARKER_GRAY_COLOR);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(ORANGE);
                g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
            }
        };
        badge.setOpaque(false);
        badge.setPreferredSize(new Dimension(56, 56));
        badge.add(new JLabel(new Glyph(GUIDE_GLYPHS[i], 24, ORANGE)));
        page.add(centered(badge));
        page.add(Box.createVerticalStrut(12));

        JLabel step = new JLabel("STEP " + (i + 1));
        step.setForeground(ORANGE);
        step.setFont(FontManager.getRunescapeSmallFont());
        page.add(centered(step));
        JLabel head = new JLabel(GUIDE[i][0]);
        head.setForeground(Color.WHITE);
        head.setFont(FontManager.getRunescapeBoldFont());
        page.add(centered(head));
        page.add(Box.createVerticalStrut(8));

        JLabel body = new JLabel("<html><div style='text-align:center;width:170px'>" + GUIDE[i][1] + "</div></html>");
        body.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        page.add(centered(body));
        return page;
    }

    /** Opens the quick guide at its first page (from the welcome screen or the route menu). */
    public void showGuide() {
        showingGuide = true;
        showGuidePage(0);
        rootCards.show(this, "guide");
    }

    private void closeGuide() {
        showingGuide = false;
        rebuild();
    }

    private void showGuidePage(int i) {
        guidePage = Math.max(0, Math.min(GUIDE.length - 1, i));
        guidePages.show(guideDeck, String.valueOf(guidePage));
        guideCount.setText((guidePage + 1) + " / " + GUIDE.length);
        guideDots.setCurrent(guidePage);
        guideBack.setEnabled(guidePage > 0);
        boolean last = guidePage == GUIDE.length - 1;
        guideNext.setText(!last ? "Next" : plugin.getRoutes().isEmpty() ? "Start building" : "Done");
    }

    /** The guide's page dots: the current one is a wide orange pill. */
    private static final class GuideDots extends JComponent {
        private final int count;
        private int current;

        GuideDots(int count) {
            this.count = count;
            setPreferredSize(new Dimension(count * 12 + 8, 8));
        }

        void setCurrent(int current) {
            this.current = current;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = 0;
            for (int i = 0; i < count; i++) {
                int w = i == current ? 14 : 6;
                g2.setColor(i == current ? ORANGE : ColorScheme.MEDIUM_GRAY_COLOR);
                g2.fillRoundRect(x, 1, w, 6, 6, 6);
                x += w + 6;
            }
            g2.dispose();
        }
    }

    /** Welcome screen: name a first route, then record it or build it by hand. */
    private void startFirstRoute(boolean record) {
        String name = askRouteName("My route");
        if (name == null) return;
        plugin.createRoute(name);
        Route route = plugin.getRoutes().get(plugin.getRoutes().size() - 1);
        plugin.setActiveRoute(route);
        tabGroup.select(routeTab);
        if (record) startRecording(route);
        else plugin.setMode(RouteMode.DEVELOPER);
    }

    // ------------------------------------------------------------------ route tab

    private JPanel buildRouteView() {
        JPanel view = new JPanel(new BorderLayout());
        view.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = vbox();
        header.setBorder(new EmptyBorder(6, 8, 6, 8));

        // Route bar: the active route as a dropdown, plus undo/redo and the edit pencil.
        JPanel combo = new JPanel(new BorderLayout(6, 0));
        combo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        combo.setBorder(new CompoundBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(5, 7, 5, 6)));
        combo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        combo.setToolTipText("Switch route");
        JPanel names = vbox();
        names.setOpaque(false);
        routeNameLabel = new JLabel();
        routeNameLabel.setForeground(Color.WHITE);
        routeNameLabel.setFont(FontManager.getRunescapeBoldFont());
        routeSubLabel = new JLabel();
        routeSubLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        routeSubLabel.setFont(FontManager.getRunescapeSmallFont());
        names.add(routeNameLabel);
        names.add(routeSubLabel);
        combo.add(names, BorderLayout.CENTER);
        combo.add(new JLabel(new Glyph(Glyph.CHEV_DOWN, 12, ColorScheme.LIGHT_GRAY_COLOR)), BorderLayout.EAST);
        combo.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { showRouteMenu(combo); }
            @Override public void mouseEntered(MouseEvent e) { combo.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR); }
            @Override public void mouseExited(MouseEvent e) { combo.setBackground(ColorScheme.DARKER_GRAY_COLOR); }
        });

        undoButton = iconButton(new ArrowIcon(false), "Undo");
        redoButton = iconButton(new ArrowIcon(true), "Redo");
        undoButton.addActionListener(e -> plugin.undoActive());
        redoButton.addActionListener(e -> plugin.redoActive());
        pencilButton = iconButton(new Glyph(Glyph.PENCIL, 14, null), "Edit mode");
        pencilButton.addActionListener(e -> plugin.setMode(devMode() ? RouteMode.PLAYER : RouteMode.DEVELOPER));
        JPanel icons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        icons.setOpaque(false);
        icons.add(undoButton);
        icons.add(redoButton);
        icons.add(pencilButton);
        icons.setAlignmentX(Component.RIGHT_ALIGNMENT);
        pauseButton = iconButton(new Glyph(Glyph.PAUSE, 14, null), "Pause this route");
        pauseButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        pauseButton.setMaximumSize(new Dimension(24, 22));
        pauseButton.addActionListener(e -> togglePause());
        JPanel iconCol = vbox();
        iconCol.setOpaque(false);
        iconCol.add(icons);
        iconCol.add(pauseButton);

        JPanel barRow = new JPanel(new BorderLayout(3, 0));
        barRow.setOpaque(false);
        barRow.add(combo, BorderLayout.CENTER);
        barRow.add(iconCol, BorderLayout.EAST);
        barRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(barRow);
        header.add(Box.createVerticalStrut(5));
        routeBar = new ThinBar(5, GREEN);
        header.add(routeBar);

        // Edit-mode tools
        editTools = new JPanel(new GridLayout(1, 3, 4, 0));
        editTools.setOpaque(false);
        editTools.setBorder(new EmptyBorder(6, 0, 0, 0));
        editTools.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton addStep = button("Step", new Glyph(Glyph.PLUS, 12, null), false);
        addStep.setToolTipText("Add a step");
        addStep.addActionListener(e -> {
            if (plugin.getActiveRoute() == null) { JOptionPane.showMessageDialog(this, "Select or create a route first."); return; }
            new QuickStepDialog(plugin, plugin.getActiveRoute(), null).setVisible(true);
        });
        JButton addSection = button("Section", new Glyph(Glyph.PLUS, 12, null), false);
        addSection.setToolTipText("Add a section");
        addSection.addActionListener(e -> onAddSection());
        JButton record = button("Record", new Glyph(Glyph.REC, 12, null), false);
        record.setForeground(new Color(255, 107, 107));
        record.setToolTipText("Record steps into a new section of this route by playing normally");
        record.addActionListener(e -> {
            Route r = plugin.getActiveRoute();
            if (r == null) { JOptionPane.showMessageDialog(this, "Select or create a route first."); return; }
            startRecording(r);
        });
        for (JButton b : new JButton[]{addStep, addSection, record}) {
            b.setMargin(new Insets(4, 2, 4, 2));
            b.setIconTextGap(3);
            editTools.add(b);
        }
        header.add(editTools);

        recBanner = buildRecBanner();
        header.add(recBanner);

        view.add(header, BorderLayout.NORTH);

        routeContent.setLayout(new BoxLayout(routeContent, BoxLayout.Y_AXIS));
        routeContent.setBackground(ColorScheme.DARK_GRAY_COLOR);
        routeContent.setBorder(new EmptyBorder(2, 8, 8, 8));

        JPanel top = new WidthTracking();
        top.add(routeContent, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        view.add(scroll, BorderLayout.CENTER);
        return view;
    }

    private JPanel buildRecBanner() {
        JPanel b = vbox();
        b.setBackground(REC_BG);
        b.setOpaque(true);
        b.setBorder(new CompoundBorder(new EmptyBorder(6, 0, 0, 0),
            new CompoundBorder(BorderFactory.createLineBorder(new Color(107, 31, 31)), new EmptyBorder(6, 7, 6, 7))));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        JLabel title = new JLabel("Recording", new Glyph(Glyph.REC, 10, REC_RED), SwingConstants.LEFT);
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        recStats = new JLabel();
        recStats.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        recStats.setFont(FontManager.getRunescapeSmallFont());
        top.add(title, BorderLayout.WEST);
        top.add(recStats, BorderLayout.EAST);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.add(top);
        b.add(Box.createVerticalStrut(3));

        recLast = new JLabel();
        recLast.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        recLast.setFont(FontManager.getRunescapeSmallFont());
        recLast.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.add(recLast);
        b.add(Box.createVerticalStrut(5));

        JPanel btns = new JPanel(new GridLayout(1, 2, 4, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton note = button("Note", new Glyph(Glyph.NOTE, 12, null), false);
        note.setToolTipText("Add a note to the step you're recording (it goes on the latest step if you're still there)");
        note.addActionListener(e -> onRecordNote());
        JButton stop = button("Stop", new Glyph(Glyph.STOP, 12, null), false);
        stop.setForeground(new Color(255, 107, 107));
        stop.setToolTipText("Stop recording and review the recorded steps");
        stop.addActionListener(e -> stopRecording());
        btns.add(note);
        btns.add(stop);
        b.add(btns);
        return b;
    }

    private void updateRecBanner() {
        RouteRecorder rec = plugin.getRouteRecorder();
        if (!rec.isRecording()) return;
        long secs = Math.max(0, (System.currentTimeMillis() - recStartMillis) / 1000);
        int n = rec.getDraftCount();
        recStats.setText(n + (n == 1 ? " step · " : " steps · ") + String.format("%d:%02d", secs / 60, secs % 60));
        String last = rec.getLastDraftName();
        recLast.setText("<html><div style='width:170px'>" + (last == null ? "Play normally; steps appear as you go."
            : "Last: " + htmlEscape(last)) + "</div></html>");
    }

    /** Deselect the active route, remembering it so the pause button can bring it back. */
    private void pauseRoute() {
        Route active = plugin.getActiveRoute();
        if (active == null) return;
        plugin.setActiveRoute(null);
        pausedRoute = active;
        refresh();
    }

    private void togglePause() {
        if (plugin.getActiveRoute() != null) pauseRoute();
        else if (pausedRoute != null && plugin.getRoutes().contains(pausedRoute)) plugin.setActiveRoute(pausedRoute);
    }

    private void showRouteMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        Route active = plugin.getActiveRoute();
        for (Route r : plugin.getRoutes()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(r.getName(), r == active);
            item.addActionListener(e -> plugin.setActiveRoute(r));
            menu.add(item);
        }
        menu.addSeparator();
        if (active != null) {
            JMenuItem pause = new JMenuItem("Pause this route");
            pause.setToolTipText("Hide the route's path and HUD until you pick it again");
            pause.addActionListener(e -> pauseRoute());
            menu.add(pause);
        }
        JMenuItem manage = new JMenuItem("Manage routes...");
        manage.addActionListener(e -> tabGroup.select(mineTab));
        JMenuItem create = new JMenuItem("New route...");
        create.addActionListener(e -> onNewRoute());
        JMenuItem guide = new JMenuItem("Quick guide");
        guide.addActionListener(e -> showGuide());
        menu.add(manage);
        menu.add(create);
        menu.addSeparator();
        menu.add(guide);
        menu.show(anchor, 0, anchor.getHeight());
    }

    // ------------------------------------------------------------------ refresh

    public void refresh() {
        SwingUtilities.invokeLater(this::rebuild);
    }

    private void rebuild() {
        if (!plugin.getRoutes().isEmpty()) forceTabs = false;
        boolean welcome = plugin.getRoutes().isEmpty() && !forceTabs && !plugin.getRouteRecorder().isRecording();
        rootCards.show(this, showingGuide ? "guide" : welcome ? "welcome" : "tabs");

        boolean dev = devMode();
        boolean recording = plugin.getRouteRecorder().isRecording();
        Route active = plugin.getActiveRoute();

        // Route bar
        if (active != null) {
            List<RouteStep> all = active.getSteps();
            long done = all.stream().filter(RouteStep::isCompleted).count();
            RouteStep cur = active.getActiveStep();
            RouteSection sec = cur != null ? sectionOf(active, cur) : null;
            routeNameLabel.setText(clip(active.getName(), 24));
            routeSubLabel.setText(done + " of " + all.size() + " steps" + (sec != null ? " · " + clip(sec.getName(), 14) : ""));
            routeBar.setProgress(done, all.size());
        } else {
            routeNameLabel.setText(plugin.getRoutes().isEmpty() ? "No routes yet" : "No route selected");
            routeSubLabel.setText(plugin.getRoutes().isEmpty() ? "Make one in My routes" : "Click to choose one");
            routeBar.setProgress(0, 1);
        }
        if (active != null || !plugin.getRoutes().contains(pausedRoute)) pausedRoute = null;
        pauseButton.setVisible(active != null || pausedRoute != null);
        pauseButton.setIcon(new Glyph(active != null ? Glyph.PAUSE : Glyph.PLAY, 14, null));
        pauseButton.setToolTipText(active != null ? "Pause this route (hides its path and HUD)"
            : "Resume " + (pausedRoute != null ? pausedRoute.getName() : "the route"));
        pencilButton.setForeground(dev ? ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
        pencilButton.setToolTipText(dev ? "Edit mode is on (click to just follow the route)" : "Edit mode: add, edit and reorder steps");
        updateHistoryButtons(dev);
        editTools.setVisible(dev && !recording);
        recBanner.setVisible(recording);
        if (recording) {
            if (recStartMillis == 0) recStartMillis = System.currentTimeMillis();
            updateRecBanner();
            if (!recTimer.isRunning()) recTimer.start();
        } else {
            recStartMillis = 0;
        }
        if (!recording) recTimer.stop();

        // Content
        routeContent.removeAll();
        cardStep = null;
        if (active == null) {
            routeContent.add(emptyNote(plugin.getRoutes().isEmpty()
                ? "No routes yet. Make one in My routes, or grab one from the Hub."
                : "No route selected. Pick one from the menu above or in My routes."));
        } else if (dev) {
            buildStepList(active, true);
        } else {
            buildFollowView(active);
        }
        routeContent.revalidate();
        routeContent.repaint();

        rebuildMine();
    }

    private void updateHistoryButtons(boolean dev) {
        undoButton.setVisible(dev);
        redoButton.setVisible(dev);
        if (!dev) return;
        Route active = plugin.getActiveRoute();
        String undoLabel = plugin.getRouteHistory().peekUndoLabel(active);
        String redoLabel = plugin.getRouteHistory().peekRedoLabel(active);
        Color off = new Color(74, 74, 74);
        undoButton.setEnabled(undoLabel != null);
        redoButton.setEnabled(redoLabel != null);
        undoButton.setForeground(undoLabel != null ? ColorScheme.LIGHT_GRAY_COLOR : off);
        redoButton.setForeground(redoLabel != null ? ColorScheme.LIGHT_GRAY_COLOR : off);
        undoButton.setToolTipText(undoLabel != null ? "Undo: " + undoLabel : "Nothing to undo");
        redoButton.setToolTipText(redoLabel != null ? "Redo: " + redoLabel : "Nothing to redo");
    }

    // ------------------------------------------------------------------ follow view

    private void buildFollowView(Route route) {
        RouteStep cur = route.getActiveStep();
        if (cur == null) {
            routeContent.add(buildDoneCard(route));
        } else {
            routeContent.add(buildCard(route, cur));
            List<RouteStep> next = upcoming(route, cur, 3);
            if (!next.isEmpty() && !showAllSteps) {
                routeContent.add(Box.createVerticalStrut(10));
                JLabel up = new JLabel("UP NEXT");
                up.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                up.setFont(FontManager.getRunescapeSmallFont());
                up.setAlignmentX(Component.LEFT_ALIGNMENT);
                routeContent.add(up);
                routeContent.add(Box.createVerticalStrut(3));
                for (RouteStep s : next) {
                    routeContent.add(previewRow(s));
                    routeContent.add(Box.createVerticalStrut(2));
                }
            }
        }
        routeContent.add(Box.createVerticalStrut(8));
        int total = route.getSteps().size();
        JButton all = button(showAllSteps ? "Hide full list" : "All " + total + " steps", new Glyph(Glyph.LIST, 12, null), false);
        all.setAlignmentX(Component.LEFT_ALIGNMENT);
        all.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        all.addActionListener(e -> { showAllSteps = !showAllSteps; rebuild(); });
        routeContent.add(all);
        if (showAllSteps) {
            routeContent.add(Box.createVerticalStrut(6));
            buildStepList(route, false);
        }
    }

    private JPanel buildCard(Route route, RouteStep step) {
        cardStep = step;
        JPanel card = vbox();
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setOpaque(true);
        card.setBorder(new CompoundBorder(BorderFactory.createLineBorder(ORANGE), new EmptyBorder(7, 8, 8, 8)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        RouteSection sec = sectionOf(route, step);
        int idx = sec != null ? sec.getSteps().indexOf(step) + 1 : 0;
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel where = new JLabel(sec != null ? clip(sec.getName(), 16) + " · step " + idx + " of " + sec.getSteps().size() : "");
        where.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        where.setFont(FontManager.getRunescapeSmallFont());
        distLabel = new JLabel();
        distLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        distLabel.setFont(FontManager.getRunescapeSmallFont());
        top.add(where, BorderLayout.WEST);
        top.add(distLabel, BorderLayout.EAST);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(top);
        card.add(Box.createVerticalStrut(5));

        JPanel head = new JPanel(new BorderLayout(7, 0));
        head.setOpaque(false);
        JLabel icon = new JLabel(new StepIcon(StepIcon.kindOf(step), 26, false));
        icon.setVerticalAlignment(SwingConstants.TOP);
        JLabel title = new JLabel("<html><div style='width:138px'>" + htmlEscape(step.getName()) + "</div></html>");
        title.setForeground(Color.WHITE);
        title.setFont(FontManager.getRunescapeBoldFont());
        head.add(icon, BorderLayout.WEST);
        head.add(title, BorderLayout.CENTER);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(head);

        liveBox = vbox();
        liveBox.setOpaque(false);
        card.add(liveBox);
        liveSig = null;
        fillLive(step, live != null && live.step == step ? live : null);

        if (step.hasNote() && !step.getNoteText().trim().equals(step.getName().trim())) {
            card.add(Box.createVerticalStrut(7));
            card.add(tip(null, step.getNoteText()));
        }

        card.add(Box.createVerticalStrut(8));
        JPanel btns = new JPanel(new BorderLayout(4, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton back = button(null, new Glyph(Glyph.BACK, 12, null), false);
        back.setToolTipText("Go back to the previous step");
        RouteStep prev = previousStep(route, step);
        back.setEnabled(prev != null);
        back.addActionListener(e -> {
            if (prev == null) return;
            prev.setCompleted(false);
            prev.resetProgress();
            plugin.saveRoutes();
            plugin.setActiveRoute(route); // re-runs section collapsing for the restored step
        });
        JPanel right = new JPanel(new GridLayout(1, 2, 4, 0));
        right.setOpaque(false);
        JButton skip = button("Skip", null, false);
        skip.setToolTipText("Skip this step without doing it");
        skip.addActionListener(e -> plugin.completeStep(step));
        JButton done = button("Done", new Glyph(Glyph.CHECK, 12, null), true);
        done.setToolTipText("Mark this step done");
        done.addActionListener(e -> plugin.completeStep(step));
        right.add(skip);
        right.add(done);
        btns.add(back, BorderLayout.WEST);
        btns.add(right, BorderLayout.CENTER);
        card.add(btns);
        updateDistance();
        return card;
    }

    private JPanel buildDoneCard(Route route) {
        JPanel card = vbox();
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setOpaque(true);
        card.setBorder(new CompoundBorder(BorderFactory.createLineBorder(GREEN.darker()), new EmptyBorder(10, 8, 10, 8)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel t = new JLabel("Route complete", new Glyph(Glyph.CHECK, 16, GREEN), SwingConstants.LEFT);
        t.setForeground(Color.WHITE);
        t.setFont(FontManager.getRunescapeBoldFont());
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(t);
        card.add(Box.createVerticalStrut(4));
        JLabel s = new JLabel("<html><div style='width:170px'>Every step of " + htmlEscape(route.getName())
            + " is done.</div></html>");
        s.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(s);
        card.add(Box.createVerticalStrut(8));
        JButton again = button("Start over", null, false);
        again.setAlignmentX(Component.LEFT_ALIGNMENT);
        again.addActionListener(e -> plugin.resetRoute(route));
        card.add(again);
        return card;
    }

    /** New progress from the plugin (client thread). Repaints only what changed. */
    public void updateLive(StepLive next) {
        SwingUtilities.invokeLater(() -> {
            live = next;
            if (next == null || cardStep == null || next.step != cardStep || liveBox == null) return;
            updateDistance();
            String sig = next.signature();
            if (!sig.equals(liveSig)) {
                fillLive(cardStep, next);
                liveBox.revalidate();
                liveBox.repaint();
            }
        });
    }

    private void updateDistance() {
        if (distLabel == null) return;
        StepLive l = live != null && live.step == cardStep ? live : null;
        if (l == null || l.distance == StepLive.NO_TARGET) { distLabel.setText(""); distLabel.setIcon(null); return; }
        distLabel.setIcon(new Glyph(Glyph.PIN, 11, ColorScheme.LIGHT_GRAY_COLOR));
        distLabel.setText(l.distance == StepLive.OTHER_FLOOR ? "other floor"
            : l.distance <= 3 ? "here" : l.distance + " tiles");
    }

    /** The progress rows of the card: items, skill goal, kills, chat options, quest. */
    private void fillLive(RouteStep step, StepLive l) {
        liveSig = l != null ? l.signature() : null;
        liveBox.removeAll();

        if (l != null && l.items != null && !l.items.isEmpty()) {
            liveBox.add(Box.createVerticalStrut(8));
            liveBox.add(smallHeading(l.itemHeader));
            for (BankItemManager.ItemProgress ip : l.items) {
                liveBox.add(Box.createVerticalStrut(4));
                liveBox.add(itemRow(ip, "Sell".equals(l.itemHeader)));
            }
        }
        if (l != null && l.skillLabel != null && l.skillNeed > 0) {
            liveBox.add(Box.createVerticalStrut(8));
            liveBox.add(progressRow(l.skillLabel, l.skillHave, l.skillNeed, true));
        }
        if (step.getNpcKillCount() > 0) {
            liveBox.add(Box.createVerticalStrut(8));
            String who = step.getNpcHighlight() != null ? step.getNpcHighlight() : "Kills";
            liveBox.add(progressRow(who, step.getNpcKillProgress(), step.getNpcKillCount(), false));
        }
        String dlg = step.getDialogOptions();
        if (dlg != null && !dlg.trim().isEmpty()) {
            liveBox.add(Box.createVerticalStrut(8));
            liveBox.add(dialogChips(dlg, l != null ? l.dialogDone : 0));
        }
        if (step.hasQuest() && step.isQuestComplete()) {
            liveBox.add(Box.createVerticalStrut(8));
            String q = step.getQuestName();
            try { q = Quest.valueOf(q).getName(); } catch (IllegalArgumentException ignored) {}
            JLabel ql = new JLabel("<html><div style='width:170px'>Finish the quest <b>" + htmlEscape(q)
                + "</b></div></html>", new StepIcon(StepIcon.Kind.QUEST, 16, false), SwingConstants.LEFT);
            ql.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            ql.setAlignmentX(Component.LEFT_ALIGNMENT);
            liveBox.add(ql);
        }
    }

    private JPanel itemRow(BankItemManager.ItemProgress ip, boolean selling) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel slot = new JLabel();
        slot.setPreferredSize(new Dimension(28, 26));
        slot.setHorizontalAlignment(SwingConstants.CENTER);
        slot.setOpaque(true);
        slot.setBackground(new Color(62, 53, 41));
        slot.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        if (ip.itemId > 0) {
            AsyncBufferedImage img = itemImages.computeIfAbsent(ip.itemId,
                id -> plugin.getItemManager().getImage(id, (int) Math.max(1, Math.min(ip.need, Integer.MAX_VALUE)), ip.need > 1));
            img.addTo(slot);
        }
        row.add(slot, BorderLayout.WEST);

        boolean done = ip.have >= ip.need;
        JPanel text = vbox();
        text.setOpaque(false);
        JPanel line = new JPanel(new BorderLayout(4, 0));
        line.setOpaque(false);
        JLabel name = new JLabel(clip(ip.name, 18));
        name.setForeground(Color.WHITE);
        JLabel count = new JLabel(selling ? (done ? "sold" : "to sell")
            : ip.need == 1 ? (done ? "have" : "need") : ip.have + " / " + ip.need);
        count.setForeground(done ? GREEN : ColorScheme.LIGHT_GRAY_COLOR);
        line.add(name, BorderLayout.CENTER);
        line.add(count, BorderLayout.EAST);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(line);
        if (ip.need > 1) {
            text.add(Box.createVerticalStrut(3));
            ThinBar bar = new ThinBar(4, done ? GREEN : AMBER);
            bar.setProgress(ip.have, ip.need);
            text.add(bar);
        }
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    private JPanel progressRow(String label, long have, long need, boolean thousands) {
        JPanel p = vbox();
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel line = new JPanel(new BorderLayout(4, 0));
        line.setOpaque(false);
        JLabel l = new JLabel(clip(label, 20));
        l.setForeground(Color.WHITE);
        boolean done = have >= need;
        JLabel c = new JLabel(thousands ? String.format("%,d / %,d", have, need) : have + " / " + need);
        c.setForeground(done ? GREEN : ColorScheme.LIGHT_GRAY_COLOR);
        line.add(l, BorderLayout.CENTER);
        line.add(c, BorderLayout.EAST);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(line);
        p.add(Box.createVerticalStrut(3));
        ThinBar bar = new ThinBar(4, done ? GREEN : AMBER);
        bar.setProgress(have, need);
        p.add(bar);
        return p;
    }

    private JPanel dialogChips(String seq, int done) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel("Chat options");
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setBorder(new EmptyBorder(0, 0, 0, 4));
        p.add(l);
        String[] parts = seq.split(",");
        for (int i = 0; i < parts.length; i++) {
            String n = parts[i].trim();
            JLabel chip = new JLabel("0".equals(n) ? "?" : n);
            chip.setFont(FontManager.getRunescapeSmallFont());
            chip.setOpaque(true);
            chip.setBackground(TIP_BG);
            Color edge = i < done ? DONE_FG : i == done ? ORANGE : new Color(60, 60, 60);
            chip.setForeground(i < done ? DONE_FG : i == done ? ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
            chip.setBorder(new CompoundBorder(BorderFactory.createLineBorder(edge), new EmptyBorder(0, 4, 0, 4)));
            if ("0".equals(n)) chip.setToolTipText("This pick wasn't recorded; choose whichever option fits");
            p.add(chip);
        }
        return p;
    }

    // ------------------------------------------------------------------ step list

    /** The whole route as collapsible sections. Edit mode adds drag-to-reorder and section selection. */
    private void buildStepList(Route active, boolean dev) {
        if (selectedSectionId != null && active.findSection(selectedSectionId) == null) selectedSectionId = null;
        if (active.getSections().isEmpty()) {
            routeContent.add(emptyNote(dev ? "No steps yet. Add a step, or press Record and play."
                : "This route has no steps yet. Turn on edit mode (the pencil) to add some."));
            return;
        }
        stepList.show(active, dev);
        routeContent.add(stepList);
    }

    /** A plain "up next" line: number-less, with the step's icon and name. */
    private JPanel previewRow(RouteStep step) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 8, 4, 6));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.setOpaque(false);
        left.add(new JLabel(new StepIcon(StepIcon.kindOf(step), 18, step.isCompleted())));
        row.add(left, BorderLayout.WEST);
        WrapLabel name = new WrapLabel(step.getName(), 138);
        name.setForeground(step.isCompleted() ? DONE_FG : Color.WHITE);
        row.add(name, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private JPopupMenu stepMenu(Route active, RouteSection section, RouteStep step, boolean dev) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem complete = new JMenuItem(step.isCompleted() ? "Mark Incomplete" : "Mark Complete");
        complete.addActionListener(ev -> {
            if (step.isCompleted()) {
                step.setCompleted(false);
                step.resetProgress();
                plugin.saveRoutes();
                refresh();
            } else {
                plugin.completeStep(step);
            }
        });
        menu.add(complete);
        if (!dev) return menu;

        JMenu move = new JMenu("Move to Section");
        int others = 0;
        for (RouteSection ts : active.getSections()) {
            if (ts == section) continue;
            others++;
            JMenuItem mv = new JMenuItem(ts.getName());
            mv.addActionListener(ev -> {
                active.removeStep(step);
                active.addStepToSection(step, ts.getId());
                plugin.saveRoutes();
                refresh();
            });
            move.add(mv);
        }
        if (others == 0) {
            JMenuItem none = new JMenuItem("(no other sections)");
            none.setEnabled(false);
            move.add(none);
        }
        JMenuItem edit = new JMenuItem("Edit Step");
        edit.addActionListener(ev -> new QuickStepDialog(plugin, plugin.getActiveRoute(), step).setVisible(true));
        JMenuItem delete = new JMenuItem("Delete Step");
        delete.addActionListener(ev -> plugin.removeStep(active, step));
        menu.add(move);
        menu.add(edit);
        if (plugin.isDeveloperMode()) {
            boolean testing = step == active.getTestStep();
            JMenuItem test = new JMenuItem(testing ? "Resume Route" : "Test Step");
            test.addActionListener(ev -> {
                if (testing) plugin.resumeRoute();
                else plugin.testStep(step);
            });
            menu.add(test);
        }
        menu.add(delete);
        return menu;
    }

    private JPopupMenu sectionMenu(Route active, RouteSection section, boolean dev, boolean allDone) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem mark = new JMenuItem(allDone ? "Mark Section Incomplete" : "Mark Section Complete");
        mark.addActionListener(ev -> {
            for (RouteStep s : section.getSteps()) {
                s.setCompleted(!allDone);
                if (allDone) s.resetProgress();
            }
            plugin.saveRoutes();
            refresh();
        });
        if (!dev) { menu.add(mark); return menu; }

        List<RouteSection> secs = active.getSections();
        JMenuItem up = new JMenuItem("Move Up");
        up.addActionListener(ev -> {
            int i = secs.indexOf(section);
            if (i > 0) { Collections.swap(secs, i, i - 1); plugin.saveRoutes(); refresh(); }
        });
        JMenuItem down = new JMenuItem("Move Down");
        down.addActionListener(ev -> {
            int i = secs.indexOf(section);
            if (i >= 0 && i < secs.size() - 1) { Collections.swap(secs, i, i + 1); plugin.saveRoutes(); refresh(); }
        });
        JMenuItem rename = new JMenuItem("Rename Section");
        rename.addActionListener(ev -> {
            String nn = JOptionPane.showInputDialog(this, "Section name:", section.getName());
            if (nn == null || nn.trim().isEmpty()) return;
            String err = RouteTextValidator.checkField("Section name", nn.trim());
            if (err != null) { JOptionPane.showMessageDialog(this, err, "Links not allowed", JOptionPane.WARNING_MESSAGE); return; }
            section.setName(nn.trim());
            plugin.saveRoutes();
            refresh();
        });
        JMenuItem delete = new JMenuItem("Delete Section");
        delete.addActionListener(ev -> {
            int c = JOptionPane.showConfirmDialog(this, "Delete section \"" + section.getName() + "\" and its "
                + section.getSteps().size() + " step(s)?", "Delete Section", JOptionPane.YES_NO_OPTION);
            if (c != JOptionPane.YES_OPTION) return;
            active.getSections().remove(section);
            if (section.getId().equals(selectedSectionId)) selectedSectionId = null;
            plugin.saveRoutes();
            refresh();
        });
        menu.add(up);
        menu.add(down);
        menu.add(mark);
        menu.add(rename);
        menu.add(delete);
        return menu;
    }

    private void handleStepDrop(ListLine target) {
        if (draggedStep == null || draggedSection == null || target == null) return;
        RouteSection targetSection = target.section;
        // A section header has no step: dropping there means "append".
        RouteStep targetStep = target.step;
        if (targetStep == draggedStep) return;

        List<RouteStep> src = draggedSection.getSteps();
        List<RouteStep> dst = targetSection.getSteps();
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
        plugin.saveRoutes();
        refresh();
    }

    // ------------------------------------------------------------------ step list view

    /** One line of the step list: a section header (no step) or a step. */
    private static final class ListLine {
        final RouteSection section;
        final RouteStep step;
        final int num;
        final boolean now;
        final List<String> name;
        final int y;
        final int h;

        ListLine(RouteSection section, RouteStep step, int num, boolean now, List<String> name, int y, int h) {
            this.section = section;
            this.step = step;
            this.num = num;
            this.now = now;
            this.name = name;
            this.y = y;
            this.h = h;
        }

        boolean contains(int py) { return py >= y && py < y + h; }
    }

    /**
     * The whole step list drawn as one component. Next to the game's native canvas, AWT reworks the
     * canvas's shape for every Swing component added or removed, so hundreds of row components made
     * big routes crawl; one painted component keeps a rebuild cheap.
     */
    private final class StepListView extends JComponent {
        private static final int GAP = 2;
        private static final int NAME_X = 58;
        private static final String DRAG_TIP = "Drag to reorder, double-click to edit, right-click for more";

        private final List<ListLine> lines = new ArrayList<>();
        private Route route;
        private boolean dev;
        private ListLine hover;
        private int height;

        StepListView() {
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            ToolTipManager.sharedInstance().registerComponent(this);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    ListLine l = lineAt(e.getY());
                    if (l == null) return;
                    if (e.isPopupTrigger()) { showMenu(l, e); return; }
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    if (l.step == null) {
                        if (dev) selectedSectionId = l.section.getId();
                        l.section.setCollapsed(!l.section.isCollapsed());
                        plugin.saveRoutes();
                        rebuild();
                    } else if (dev) {
                        if (e.getClickCount() == 2) {
                            new QuickStepDialog(plugin, plugin.getActiveRoute(), l.step).setVisible(true);
                            return;
                        }
                        draggedStep = l.step;
                        draggedSection = l.section;
                        repaint(0, l.y, getWidth(), l.h);
                    }
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (!dev || draggedStep == null) return;
                    ListLine target = nearestLine(e.getY());
                    int newLineY = -1;
                    if (target != null && target.step != draggedStep) {
                        newLineY = e.getY() < target.y + target.h / 2 ? target.y : target.y + target.h;
                    }
                    if (newLineY != dropLineY) { dropLineY = newLineY; repaint(); }
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        ListLine l = lineAt(e.getY());
                        if (l != null) showMenu(l, e);
                        return;
                    }
                    if (dev && draggedStep != null) {
                        dropLineY = -1;
                        handleStepDrop(nearestLine(e.getY()));
                        draggedStep = null;
                        draggedSection = null;
                        rebuild();
                    }
                }
                @Override public void mouseMoved(MouseEvent e) { setHover(lineAt(e.getY())); }
                @Override public void mouseExited(MouseEvent e) { setHover(null); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        /** Lay the route out as lines; heights are fixed by the wrap widths, so no panel width is needed. */
        void show(Route route, boolean dev) {
            this.route = route;
            this.dev = dev;
            hover = null;
            lines.clear();
            RouteStep current = route.getActiveStep();
            FontMetrics fm = getFontMetrics(UIManager.getFont("Label.font"));
            FontMetrics bold = getFontMetrics(FontManager.getRunescapeBoldFont());
            int sectionH = Math.max(12, bold.getHeight()) + 10;
            int y = 0;
            for (RouteSection section : route.getSections()) {
                lines.add(new ListLine(section, null, 0, false, null, y, sectionH));
                y += sectionH + GAP;
                if (section.isCollapsed()) continue;
                int num = 0;
                for (RouteStep step : section.getSteps()) {
                    boolean now = step == current;
                    List<String> name = WrapLabel.wrap(step.getName(), fm, now ? 88 : 118);
                    int h = Math.max(18, name.size() * fm.getHeight()) + 8;
                    lines.add(new ListLine(section, step, ++num, now, name, y, h));
                    y += h + GAP;
                }
            }
            height = y;
            revalidate();
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(10, height); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, height); }

        @Override public String getToolTipText(MouseEvent e) {
            ListLine l = lineAt(e.getY());
            return dev && l != null && l.step != null ? DRAG_TIP : null;
        }

        private ListLine lineAt(int y) {
            for (ListLine l : lines) if (l.contains(y)) return l;
            return null;
        }

        private ListLine nearestLine(int y) {
            ListLine best = null;
            int bestDist = Integer.MAX_VALUE;
            for (ListLine l : lines) {
                int dist = Math.abs(l.y + l.h / 2 - y);
                if (dist < bestDist) { bestDist = dist; best = l; }
            }
            return best;
        }

        private void setHover(ListLine l) {
            if (l == hover) return;
            if (hover != null) repaint(0, hover.y, getWidth(), hover.h);
            hover = l;
            if (l != null) repaint(0, l.y, getWidth(), l.h);
            setCursor(Cursor.getPredefinedCursor(l != null && l.step == null ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        }

        private void showMenu(ListLine l, MouseEvent e) {
            JPopupMenu menu = l.step == null
                ? sectionMenu(route, l.section, dev, allDone(l.section))
                : stepMenu(route, l.section, l.step, dev);
            SwingUtilities.invokeLater(() -> menu.show(this, e.getX(), e.getY()));
        }

        private boolean allDone(RouteSection s) {
            return !s.getSteps().isEmpty() && s.getSteps().stream().allMatch(RouteStep::isCompleted);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            // Same text smoothing as a JLabel gets.
            Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (hints instanceof Map) g2.addRenderingHints((Map<?, ?>) hints);
            Rectangle clip = g2.getClipBounds();
            for (ListLine l : lines) {
                if (clip != null && (l.y + l.h < clip.y || l.y > clip.y + clip.height)) continue;
                if (l.step == null) paintSection(g2, l);
                else paintStep(g2, l);
            }
            if (dropLineY >= 0) {
                g2.setColor(new Color(255, 160, 40));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawLine(6, dropLineY, getWidth() - 6, dropLineY);
                g2.fillOval(2, dropLineY - 3, 6, 6);
                g2.fillOval(getWidth() - 8, dropLineY - 3, 6, 6);
            }
            g2.dispose();
        }

        private void paintSection(Graphics2D g, ListLine l) {
            RouteSection section = l.section;
            boolean selected = dev && section.getId().equals(selectedSectionId);
            g.setColor(selected ? new Color(70, 55, 25) : SECTION_BG);
            g.fillRect(0, l.y, getWidth(), l.h);
            int cy = l.y + l.h / 2;
            new Glyph(section.isCollapsed() ? Glyph.CHEV_RIGHT : Glyph.CHEV_DOWN, 12, ColorScheme.LIGHT_GRAY_COLOR)
                .paintIcon(this, g, 6, cy - 6);
            g.setFont(FontManager.getRunescapeBoldFont());
            g.setColor(selected ? new Color(255, 180, 80) : Color.WHITE);
            g.drawString(clip(section.getName(), 20), 22, baseline(g.getFontMetrics(), cy));

            int total = section.getSteps().size();
            long done = section.getSteps().stream().filter(RouteStep::isCompleted).count();
            int x = getWidth() - 6;
            if (total > 0 && done == total) {
                x -= 11;
                new Glyph(Glyph.CHECK, 11, GREEN).paintIcon(this, g, x, cy - 5);
                x -= 4;
            }
            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics fm = g.getFontMetrics();
            String count = done + "/" + total;
            g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
            g.drawString(count, x - fm.stringWidth(count), baseline(fm, cy));
        }

        private void paintStep(Graphics2D g, ListLine l) {
            RouteStep step = l.step;
            Color bg = l.now ? NOW_BG : ColorScheme.DARKER_GRAY_COLOR;
            if (step == draggedStep) bg = new Color(95, 95, 95);
            else if (l == hover && draggedStep == null && !l.now) bg = ColorScheme.DARKER_GRAY_HOVER_COLOR;
            g.setColor(bg);
            g.fillRect(0, l.y, getWidth(), l.h);
            if (l.now) {
                g.setColor(ORANGE);
                g.fillRect(0, l.y, 3, l.h);
            }
            int top = l.y + 4;

            g.setFont(FontManager.getRunescapeSmallFont());
            FontMetrics small = g.getFontMetrics();
            String num = String.valueOf(l.num);
            g.setColor(DIM_FG);
            g.drawString(num, 26 - small.stringWidth(num), baseline(small, top + 9));
            new StepIcon(StepIcon.kindOf(step), 18, step.isCompleted()).paintIcon(this, g, 30, top);

            g.setFont(UIManager.getFont("Label.font"));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(step.isCompleted() ? DONE_FG : Color.WHITE);
            int ty = l.y + (l.h - l.name.size() * fm.getHeight()) / 2 + fm.getAscent();
            for (String line : l.name) {
                g.drawString(line, NAME_X, ty);
                ty += fm.getHeight();
            }

            if (l.now) {
                boolean testing = step == route.getTestStep();
                boolean passed = testing && route.isTestPassed();
                String pill = !testing ? " NOW " : passed ? " PASS " : " TEST ";
                int w = small.stringWidth(pill), h = small.getHeight();
                int px = getWidth() - 6 - w, py = l.y + (l.h - h) / 2;
                g.setColor(passed ? GREEN : ORANGE);
                g.fillRect(px, py, w, h);
                g.setFont(FontManager.getRunescapeSmallFont());
                g.setColor(new Color(27, 18, 0));
                g.drawString(pill, px, py + small.getAscent());
            }
        }

        /** Baseline that centers a line of this font on {@code cy}. */
        private int baseline(FontMetrics fm, int cy) {
            return cy - fm.getHeight() / 2 + fm.getAscent();
        }
    }

    // ------------------------------------------------------------------ my routes tab

    private JPanel buildMineView() {
        JPanel view = new JPanel(new BorderLayout(0, 6));
        view.setBackground(ColorScheme.DARK_GRAY_COLOR);
        view.setBorder(new EmptyBorder(6, 8, 8, 8));

        mineSearch = new IconTextField();
        mineSearch.setIcon(IconTextField.Icon.SEARCH);
        mineSearch.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        mineSearch.setHoverBackgroundColor(ColorScheme.DARKER_GRAY_HOVER_COLOR);
        mineSearch.setPreferredSize(new Dimension(10, 30));
        mineSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { rebuildMine(); }
            public void removeUpdate(DocumentEvent e) { rebuildMine(); }
            public void changedUpdate(DocumentEvent e) { rebuildMine(); }
        });
        view.add(mineSearch, BorderLayout.NORTH);

        mineList.setLayout(new BoxLayout(mineList, BoxLayout.Y_AXIS));
        mineList.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JPanel top = new WidthTracking();
        top.add(mineList, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        view.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(1, 2, 4, 0));
        footer.setOpaque(false);
        JButton create = button("New", new Glyph(Glyph.PLUS, 12, null), false);
        create.addActionListener(e -> onNewRoute());
        JButton imp = button("Import", new Glyph(Glyph.FILE, 12, null), false);
        imp.setToolTipText("Import a route from a file");
        imp.addActionListener(e -> importExport.importRoute());
        footer.add(create);
        footer.add(imp);
        view.add(footer, BorderLayout.SOUTH);
        return view;
    }

    private void rebuildMine() {
        mineList.removeAll();
        String q = mineSearch != null && mineSearch.getText() != null ? mineSearch.getText().trim().toLowerCase() : "";
        int shown = 0;
        for (Route r : plugin.getRoutes()) {
            if (!q.isEmpty() && (r.getName() == null || !r.getName().toLowerCase().contains(q))) continue;
            mineList.add(routeCard(r));
            mineList.add(Box.createVerticalStrut(4));
            shown++;
        }
        if (shown == 0) {
            mineList.add(emptyNote(plugin.getRoutes().isEmpty() ? "No routes yet. Press New, or grab one from the Hub tab."
                : "No routes match \"" + q + "\"."));
        }
        mineList.revalidate();
        mineList.repaint();
    }

    private JPanel routeCard(Route route) {
        boolean active = route == plugin.getActiveRoute();
        JPanel card = vbox();
        card.setOpaque(true);
        Color bg = ColorScheme.DARKER_GRAY_COLOR;
        card.setBackground(bg);
        card.setBorder(active ? new CompoundBorder(new MatteBorder(0, 3, 0, 0, ORANGE), new EmptyBorder(6, 6, 7, 6))
            : new EmptyBorder(6, 9, 7, 6));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel top = new JPanel(new BorderLayout(4, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel name = new JLabel("<html><div style='width:" + (active ? 92 : 136) + "px'>" + htmlEscape(route.getName()) + "</div></html>");
        name.setForeground(Color.WHITE);
        name.setFont(FontManager.getRunescapeBoldFont());
        top.add(name, BorderLayout.CENTER);
        JPopupMenu menu = routeMenu(route);
        JButton more = iconButton(new Glyph(Glyph.DOTS, 14, null), "Rename, export, delete...");
        more.addActionListener(e -> menu.show(more, 0, more.getHeight()));
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        east.setOpaque(false);
        if (active) {
            JLabel pill = new JLabel(" ACTIVE ");
            pill.setOpaque(true);
            pill.setBackground(ORANGE);
            pill.setForeground(new Color(27, 18, 0));
            pill.setFont(FontManager.getRunescapeSmallFont());
            east.add(pill);
        }
        east.add(more);
        top.add(east, BorderLayout.EAST);
        card.add(top);
        card.add(Box.createVerticalStrut(4));

        List<RouteStep> all = route.getSteps();
        long done = all.stream().filter(RouteStep::isCompleted).count();
        ThinBar bar = new ThinBar(4, GREEN);
        bar.setProgress(done, Math.max(1, all.size()));
        card.add(bar);
        card.add(Box.createVerticalStrut(4));

        JPanel sub = new JPanel(new BorderLayout());
        sub.setOpaque(false);
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel progress = new JLabel(done + " of " + all.size() + " steps");
        progress.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        progress.setFont(FontManager.getRunescapeSmallFont());
        String tagText = route.getHubSourceId() != null ? "from Route Hub" : route.isRepeatable() ? "repeatable" : "";
        JLabel tag = new JLabel(tagText);
        tag.setForeground(DIM_FG);
        tag.setFont(FontManager.getRunescapeSmallFont());
        sub.add(progress, BorderLayout.WEST);
        sub.add(tag, BorderLayout.EAST);
        card.add(sub);

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) { popup(menu, card, e); return; }
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (!active) plugin.setActiveRoute(route);
                    tabGroup.select(routeTab);
                }
            }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) popup(menu, card, e); }
            @Override public void mouseEntered(MouseEvent e) { card.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR); }
            @Override public void mouseExited(MouseEvent e) { card.setBackground(bg); }
        };
        card.addMouseListener(ma);
        name.addMouseListener(ma);
        return card;
    }

    private JPopupMenu routeMenu(Route route) {
        JPopupMenu menu = new JPopupMenu();
        if (route == plugin.getActiveRoute()) {
            JMenuItem pause = new JMenuItem("Pause this route");
            pause.setToolTipText("Hide the route's path and HUD until you pick it again");
            pause.addActionListener(e -> pauseRoute());
            menu.add(pause);
            menu.addSeparator();
        } else if (route == pausedRoute) {
            JMenuItem resume = new JMenuItem("Resume this route");
            resume.addActionListener(e -> plugin.setActiveRoute(route));
            menu.add(resume);
            menu.addSeparator();
        }
        JMenuItem rename = new JMenuItem("Rename");
        rename.addActionListener(e -> {
            String newName = JOptionPane.showInputDialog(this, "Route name:", route.getName());
            if (newName == null || newName.trim().isEmpty() || newName.trim().equals(route.getName())) return;
            String err = RouteTextValidator.checkField("Route name", newName.trim());
            if (err != null) { JOptionPane.showMessageDialog(this, err, "Links not allowed", JOptionPane.WARNING_MESSAGE); return; }
            route.setName(newName.trim());
            plugin.saveRoutes();
            refresh();
        });
        JMenuItem repeat = new JMenuItem(route.isRepeatable() ? "Repeatable: on" : "Repeatable: off");
        repeat.setToolTipText("A repeatable route starts over by itself when you finish it");
        repeat.addActionListener(e -> { route.setRepeatable(!route.isRepeatable()); plugin.saveRoutes(); refresh(); });
        JMenuItem reset = new JMenuItem("Reset progress");
        reset.addActionListener(e -> plugin.resetRoute(route));
        JMenuItem export = new JMenuItem("Export to file");
        export.addActionListener(e -> importExport.exportRoute(route));
        JMenuItem share = new JMenuItem("Share to the Route Hub...");
        share.setToolTipText("Opens the routes repo on GitHub. Submitting is a pull request, not an upload "
            + "from the plugin. See the repo's README for the how-to.");
        share.addActionListener(e -> LinkBrowser.browse("https://github.com/BlackDeath-Osrs/route-planner-routes"));
        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Delete route \"" + route.getName() + "\" and its "
                + route.getAllSteps().size() + " step(s)?\nThis cannot be undone.", "Delete Route", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) plugin.deleteRoute(route);
        });
        menu.add(rename);
        menu.add(repeat);
        menu.add(reset);
        menu.addSeparator();
        menu.add(export);
        menu.add(share);
        menu.addSeparator();
        menu.add(delete);
        return menu;
    }

    // ------------------------------------------------------------------ actions

    private String askRouteName(String suggestion) {
        String name = (String) JOptionPane.showInputDialog(this, "Route name:", "New Route",
            JOptionPane.PLAIN_MESSAGE, null, null, suggestion);
        if (name == null || name.trim().isEmpty()) return null;
        String err = RouteTextValidator.checkField("Route name", name.trim());
        if (err != null) { JOptionPane.showMessageDialog(this, err, "Links not allowed", JOptionPane.WARNING_MESSAGE); return null; }
        return name.trim();
    }

    private void onNewRoute() {
        String name = askRouteName("");
        if (name == null) return;
        plugin.createRoute(name);
        plugin.setActiveRoute(plugin.getRoutes().get(plugin.getRoutes().size() - 1));
        tabGroup.select(routeTab);
    }

    private void onAddSection() {
        Route active = plugin.getActiveRoute();
        if (active == null) { JOptionPane.showMessageDialog(this, "Select or create a route first."); return; }
        String name = JOptionPane.showInputDialog(this, "Section name:", "New Section", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.trim().isEmpty()) return;
        String err = RouteTextValidator.checkField("Section name", name.trim());
        if (err != null) { JOptionPane.showMessageDialog(this, err, "Links not allowed", JOptionPane.WARNING_MESSAGE); return; }
        RouteSection sec = new RouteSection(UUID.randomUUID().toString(), name.trim());
        active.getSections().add(sec);
        selectedSectionId = sec.getId();
        plugin.saveRoutes();
        refresh();
    }

    private void startRecording(Route route) {
        RouteRecorder recorder = plugin.getRouteRecorder();
        if (recorder.isRecording()) return;
        plugin.getClientThread().invokeLater(() -> {
            recorder.start(route);
            refresh();
        });
    }

    private void stopRecording() {
        RouteRecorder recorder = plugin.getRouteRecorder();
        if (!recorder.isRecording()) return;
        plugin.getClientThread().invokeLater(() -> {
            List<RouteStep> drafts = recorder.stop();
            Route route = recorder.getTargetRoute();
            SwingUtilities.invokeLater(() -> {
                refresh();
                if (drafts.isEmpty()) JOptionPane.showMessageDialog(this, "Nothing was recorded.");
                else new RecordReviewDialog(plugin, route, drafts).setVisible(true);
            });
        });
    }

    private void onRecordNote() {
        String text = JOptionPane.showInputDialog(this, "Note for this step:", "Add note", JOptionPane.PLAIN_MESSAGE);
        if (text == null || text.trim().isEmpty()) return;
        String err = RouteTextValidator.checkField("Note", text.trim());
        if (err != null) { JOptionPane.showMessageDialog(this, err, "Links not allowed", JOptionPane.WARNING_MESSAGE); return; }
        RouteRecorder recorder = plugin.getRouteRecorder();
        plugin.getClientThread().invokeLater(() -> recorder.addNote(text.trim()));
    }

    // ------------------------------------------------------------------ route helpers

    private static RouteSection sectionOf(Route route, RouteStep step) {
        for (RouteSection s : route.getSections()) if (s.getSteps().contains(step)) return s;
        return null;
    }

    private static List<RouteStep> upcoming(Route route, RouteStep cur, int max) {
        List<RouteStep> out = new ArrayList<>();
        boolean after = false;
        for (RouteStep s : route.getSteps()) {
            if (s == cur) { after = true; continue; }
            if (after && !s.isCompleted()) out.add(s);
            if (out.size() >= max) break;
        }
        return out;
    }

    private static RouteStep previousStep(Route route, RouteStep cur) {
        RouteStep prev = null;
        for (RouteStep s : route.getSteps()) {
            if (s == cur) return prev;
            prev = s;
        }
        return null;
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1).trim() + "...";
    }

    // ------------------------------------------------------------------ widgets

    private static JPanel vbox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return p;
    }

    private static JPanel centered(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);
        p.add(c);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return p;
    }

    private static JLabel smallHeading(String text) {
        JLabel l = new JLabel(text == null ? "" : text);
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel emptyNote(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        p.setBorder(new EmptyBorder(10, 8, 10, 8));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel("<html><div style='width:170px'>" + htmlEscape(text) + "</div></html>");
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        p.add(l, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** A dark tip box with a note glyph; {@code lead} is shown bold before the text. */
    private static JPanel tip(String lead, String text) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setBackground(TIP_BG);
        p.setBorder(new EmptyBorder(6, 6, 6, 6));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel icon = new JLabel(new Glyph(Glyph.NOTE, 13, new Color(216, 196, 154)));
        icon.setVerticalAlignment(SwingConstants.TOP);
        String body = htmlEscape(text).replace("\n", "<br>");
        JLabel l = new JLabel("<html><div style='width:136px'>" + (lead != null ? "<b>" + htmlEscape(lead) + "</b> " : "")
            + body + "</div></html>");
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setFont(FontManager.getRunescapeSmallFont());
        p.add(icon, BorderLayout.WEST);
        p.add(l, BorderLayout.CENTER);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** The panel's one button style: flat, dark, bold; {@code primary} is the orange call to action. */
    private static JButton button(String text, Icon icon, boolean primary) {
        JButton b = new JButton(text, icon);
        Color bg = primary ? ORANGE : ColorScheme.DARKER_GRAY_COLOR;
        Color hover = primary ? new Color(240, 154, 16) : ColorScheme.DARKER_GRAY_HOVER_COLOR;
        b.setBackground(bg);
        b.setForeground(primary ? new Color(27, 18, 0) : ColorScheme.LIGHT_GRAY_COLOR);
        b.setFont(FontManager.getRunescapeBoldFont());
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setIconTextGap(5);
        b.setMargin(new Insets(4, 8, 4, 8));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { if (b.isEnabled()) b.setBackground(hover); }
            @Override public void mouseExited(MouseEvent e) { b.setBackground(bg); }
        });
        return b;
    }

    /** A plain underlined text link in the brand orange. */
    private static JButton linkButton(String text) {
        JButton b = new JButton("<html><u>" + htmlEscape(text) + "</u></html>");
        b.setForeground(ORANGE);
        b.setFont(FontManager.getRunescapeSmallFont());
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** A borderless square icon button (undo, redo, pencil, ⋯). */
    private static JButton iconButton(Icon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setMargin(new Insets(2, 3, 2, 3));
        b.setPreferredSize(new Dimension(24, 24));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** A scroll view that is always exactly as wide as the panel, so rows wrap instead of running off the edge. */
    private static final class WidthTracking extends JPanel implements Scrollable {
        WidthTracking() {
            super(new BorderLayout());
            setBackground(ColorScheme.DARK_GRAY_COLOR);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static void popup(JPopupMenu menu, Component on, MouseEvent e) {
        Point p = SwingUtilities.convertPoint((Component) e.getSource(), e.getPoint(), on);
        SwingUtilities.invokeLater(() -> menu.show(on, p.x, p.y));
    }

    /**
     * Small drawn UI glyphs. Drawn rather than typed because the client's font has no arrows,
     * chevrons or ticks (they come out as missing-glyph boxes). With no colour given they take
     * the component's foreground, so they follow enabled/disabled and hover states.
     */
    private static final class Glyph implements Icon {
        static final int PLUS = 0, REC = 1, STOP = 2, NOTE = 3, PENCIL = 4, BACK = 5, CHECK = 6, LIST = 7,
            GLOBE = 8, FILE = 9, MAP = 10, CHEV_DOWN = 11, CHEV_RIGHT = 12, DOTS = 13, PIN = 14,
            PAUSE = 15, PLAY = 16;

        private final int type;
        private final int size;
        private final Color color;

        Glyph(int type, int size, Color color) {
            this.type = type;
            this.size = size;
            this.color = color;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.scale(size / 16.0, size / 16.0);
            Color col = color != null ? color : c.getForeground();
            if (!c.isEnabled()) col = new Color(74, 74, 74);
            g2.setColor(col);
            g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Double();
            switch (type) {
                case PLUS: p.moveTo(8, 3); p.lineTo(8, 13); p.moveTo(3, 8); p.lineTo(13, 8); break;
                case REC: g2.fill(new Ellipse2D.Double(3.5, 3.5, 9, 9)); break;
                case STOP: g2.fillRect(4, 4, 8, 8); break;
                case NOTE:
                    p.moveTo(3, 2); p.lineTo(10, 2); p.lineTo(13, 5); p.lineTo(13, 14); p.lineTo(3, 14); p.closePath();
                    p.moveTo(5.5, 8); p.lineTo(10.5, 8); p.moveTo(5.5, 10.5); p.lineTo(9, 10.5);
                    break;
                case PENCIL:
                    p.moveTo(10.5, 2.5); p.lineTo(13.5, 5.5); p.lineTo(5.5, 13.5); p.lineTo(2.5, 13.5); p.lineTo(2.5, 10.5); p.closePath();
                    break;
                case BACK: p.moveTo(10, 3.5); p.lineTo(5.5, 8); p.lineTo(10, 12.5); break;
                case CHECK: p.moveTo(3, 8.5); p.lineTo(6.5, 12); p.lineTo(13, 4.5); break;
                case LIST:
                    for (int i = 0; i < 3; i++) {
                        double yy = 4 + i * 4;
                        p.moveTo(5.5, yy); p.lineTo(13.5, yy);
                        g2.fill(new Ellipse2D.Double(1.6, yy - 0.9, 1.8, 1.8));
                    }
                    break;
                case GLOBE:
                    g2.draw(new Ellipse2D.Double(2, 2, 12, 12));
                    p.moveTo(2, 8); p.lineTo(14, 8);
                    p.moveTo(8, 2); p.curveTo(10.8, 4.5, 10.8, 11.5, 8, 14);
                    p.moveTo(8, 2); p.curveTo(5.2, 4.5, 5.2, 11.5, 8, 14);
                    break;
                case FILE:
                    p.moveTo(4, 2); p.lineTo(9.5, 2); p.lineTo(12, 4.5); p.lineTo(12, 14); p.lineTo(4, 14); p.closePath();
                    p.moveTo(8, 6.5); p.lineTo(8, 11.5); p.moveTo(6, 9.5); p.lineTo(8, 11.5); p.lineTo(10, 9.5);
                    break;
                case MAP:
                    p.moveTo(1.5, 4); p.lineTo(5.5, 2.5); p.lineTo(10.5, 4); p.lineTo(14.5, 2.5); p.lineTo(14.5, 12);
                    p.lineTo(10.5, 13.5); p.lineTo(5.5, 12); p.lineTo(1.5, 13.5); p.closePath();
                    p.moveTo(5.5, 2.5); p.lineTo(5.5, 12); p.moveTo(10.5, 4); p.lineTo(10.5, 13.5);
                    break;
                case CHEV_DOWN: p.moveTo(4, 6); p.lineTo(8, 10); p.lineTo(12, 6); break;
                case CHEV_RIGHT: p.moveTo(6, 4); p.lineTo(10, 8); p.lineTo(6, 12); break;
                case DOTS:
                    for (int i = 0; i < 3; i++) g2.fill(new Ellipse2D.Double(2.2 + i * 4.6, 6.8, 2.4, 2.4));
                    break;
                case PAUSE:
                    g2.fill(new RoundRectangle2D.Double(3.5, 3, 3, 10, 1, 1));
                    g2.fill(new RoundRectangle2D.Double(9.5, 3, 3, 10, 1, 1));
                    break;
                case PLAY:
                    p.moveTo(5, 3); p.lineTo(13, 8); p.lineTo(5, 13); p.closePath();
                    g2.fill(p);
                    break;
                case PIN:
                default:
                    p.moveTo(8, 14.5); p.curveTo(8, 14.5, 3.8, 10, 3.8, 6.6); p.curveTo(3.8, 1.2, 12.2, 1.2, 12.2, 6.6);
                    p.curveTo(12.2, 10, 8, 14.5, 8, 14.5); p.closePath();
                    break;
            }
            g2.draw(p);
            g2.dispose();
        }
    }

    /**
     * A drawn undo/redo icon: a curved arrow, mirrored for redo. Painted rather than set as a
     * glyph so it renders identically regardless of the font the client resolves for Swing --
     * the Unicode arrows we used before fell back to a missing-glyph box on the live client.
     * Colour tracks the button's foreground so enabled/disabled states still work.
     */
    private static final class ArrowIcon implements Icon {
        private final boolean redo;
        ArrowIcon(boolean redo) { this.redo = redo; }
        public int getIconWidth() { return 16; }
        public int getIconHeight() { return 16; }
        public void paintIcon(Component comp, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            if (redo) { g2.translate(16, 0); g2.scale(-1, 1); }
            g2.setColor(comp.getForeground());
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Arc2D.Float(3, 3, 10, 10, 30, 220, Arc2D.OPEN));
            int hx = 4, hy = 11;
            g2.fill(new Polygon(new int[]{hx, hx + 4, hx - 1}, new int[]{hy - 4, hy + 1, hy + 3}, 3));
            g2.dispose();
        }
    }
}
