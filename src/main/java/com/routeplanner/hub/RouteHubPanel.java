package com.routeplanner.hub;

import lombok.extern.slf4j.Slf4j;

import net.runelite.client.ui.ColorScheme;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * The Route Hub browse view. Skeleton for now (v1.4 step 4b): fetches the catalog and lists
 * routes as plain rows. No icons, search, tabs, or the warning banner yet -- those come once this
 * fetch-parse-render path is proven against the live repo. Network work runs off the EDT via
 * SwingWorker; nothing here ever blocks the UI thread.
 */
@Slf4j
public class RouteHubPanel extends JPanel {

    private final com.routeplanner.RoutePlannerPlugin plugin;
    private final RouteHubCatalog catalog;
    private final Runnable onBack;
    private final JPanel listPanel;
    private final JLabel statusLabel;
    private boolean loadedOnce = false;
    private JLabel footerLabel;

    public RouteHubPanel(com.routeplanner.RoutePlannerPlugin plugin, RouteHubCatalog catalog, Runnable onBack) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.onBack = onBack;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton back = new JButton("\u2039 Back");
        back.setForeground(new Color(255, 152, 31));
        back.setBorderPainted(false);
        back.setContentAreaFilled(false);
        back.setFocusPainted(false);
        back.setCursor(new Cursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> onBack.run());

        JLabel title = new JLabel("Route Hub");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.add(back, BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);

        JPanel topStack = new JPanel();
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
        topStack.setOpaque(false);
        JTextField search = new JTextField();
        search.setBackground(ColorScheme.DARK_GRAY_COLOR.brighter());
        search.setForeground(Color.WHITE);
        search.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
            new EmptyBorder(5, 7, 5, 7)));
        search.putClientProperty("JTextField.placeholderText", "Search routes\u2026");
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(search.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(search.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(search.getText()); }
        });
        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setOpaque(false);
        searchWrap.setBorder(new EmptyBorder(6, 0, 0, 0));
        searchWrap.add(search, BorderLayout.CENTER);

        topStack.add(header);
        topStack.add(searchWrap);
        add(topStack, BorderLayout.NORTH);

        statusLabel = new JLabel("Loading\u2026");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setBorder(new EmptyBorder(10, 4, 10, 4));

        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        listPanel.add(statusLabel);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        add(scroll, BorderLayout.CENTER);

        footerLabel = new JLabel(" ");
        footerLabel.setForeground(Color.WHITE);
        footerLabel.setFont(footerLabel.getFont().deriveFont(9.5f));
        footerLabel.setBorder(new EmptyBorder(6, 2, 2, 2));
        footerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        footerLabel.setToolTipText("The date the Route Hub catalog was last changed \u2014 "
            + "not necessarily this specific route.");

        JPanel bottomStack = new JPanel();
        bottomStack.setLayout(new BoxLayout(bottomStack, BoxLayout.Y_AXIS));
        bottomStack.setOpaque(false);
        bottomStack.add(footerLabel);
        bottomStack.add(buildWarningBanner());
        add(bottomStack, BorderLayout.SOUTH);
    }

    /** Called each time the Hub view is switched to. Loads once per panel lifetime for now --
     *  a manual refresh / re-check-on-open comes with the Installed tab in a later step. */
    public void onShown() {
        if (loadedOnce) return;
        loadedOnce = true;
        load();
    }

    // Decoded icons keyed by entry id, so re-rendering the list (e.g. after a tab switch) does not
    // re-fetch or re-decode icons that already loaded successfully this session. Cleared never --
    // the panel's lifetime is one Hub-view visit, and 12-48px icons are cheap to hold in memory.
    private final java.util.Map<String, ImageIcon> iconCache = new java.util.HashMap<>();
    private java.util.List<HubRouteEntry> allEntries = java.util.Collections.emptyList();

    /** Fetches and decodes one entry's icon off the EDT, then swaps it into the given label. Never
     *  throws back to the caller and never leaves the label in a broken state -- on any failure the
     *  grey-blue placeholder simply stays as-is. */
    private void loadIconInto(JLabel target, HubRouteEntry entry) {
        ImageIcon cached = iconCache.get(entry.id);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }
        if (entry.icon == null || entry.icon.isEmpty()) return;

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                try {
                    byte[] bytes = catalog.fetchIconBytes(entry.icon);
                    java.awt.Image img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    if (img == null) return null; // not a decodable image
                    if (img.getWidth(null) <= 0 || img.getHeight(null) <= 0) return null;
                    java.awt.Image scaled = img.getScaledInstance(48, 48, java.awt.Image.SCALE_SMOOTH);
                    return new ImageIcon(scaled);
                } catch (Exception e) {
                    log.debug("Icon load failed for {}: {}", entry.id, e.toString());
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon == null) return; // leave the placeholder as-is
                    iconCache.put(entry.id, icon);
                    target.setIcon(icon);
                } catch (Exception e) {
                    // failed or cancelled -- placeholder remains, nothing to show the user for this
                }
            }
        }.execute();
    }

    private static final int MAX_STEPS = 1500;
    private static final int MAX_NOTE_LEN = 280;
    private static final int MAX_NAME_LEN = 100;

    /**
     * Re-validates every free-text field of every step in a fetched route, independently of
     * whatever the catalog entry or PR review already checked. A route JSON fetched over the
     * network must never be trusted just because it appeared in the curated index -- this is the
     * import-time re-validation layer from the security model. Returns null if the route is safe
     * to install, or a short user-facing reason if it should be refused.
     */
    private String validateFetchedRoute(com.routeplanner.model.Route route) {
        if (route == null || route.getName() == null) return "The route file is empty or malformed.";
        if (route.getName().length() > MAX_NAME_LEN) return "The route's name is too long.";

        String nameErr = com.routeplanner.util.RouteTextValidator.checkField("Route name", route.getName());
        if (nameErr != null) return nameErr;

        int totalSteps = 0;
        java.util.List<com.routeplanner.model.RouteSection> sections = route.getSections();
        if (sections == null) return null; // an empty route is unusual but not unsafe

        for (com.routeplanner.model.RouteSection section : sections) {
            if (section == null || section.getSteps() == null) continue;

            String secErr = com.routeplanner.util.RouteTextValidator.checkField("Section name", section.getName());
            if (secErr != null) return secErr;

            for (com.routeplanner.model.RouteStep step : section.getSteps()) {
                if (step == null) continue;
                totalSteps++;
                if (totalSteps > MAX_STEPS) return "This route has too many steps.";

                if (step.getName() != null && step.getName().length() > MAX_NAME_LEN) {
                    return "A step name is too long.";
                }
                if (step.getNoteText() != null && step.getNoteText().length() > MAX_NOTE_LEN) {
                    return "A step's note is too long.";
                }

                String err = com.routeplanner.util.RouteTextValidator.checkField("Step name", step.getName());
                if (err == null) err = com.routeplanner.util.RouteTextValidator.checkField("Note", step.getNoteText());
                if (err == null) err = com.routeplanner.util.RouteTextValidator.checkField("Highlight NPC", step.getNpcHighlight());
                if (err == null) err = com.routeplanner.util.RouteTextValidator.checkField("Dialogue options", step.getDialogOptions());
                if (err == null) err = com.routeplanner.util.RouteTextValidator.checkField("Items", step.getItemList());
                if (err != null) return err;
            }
        }
        return null;
    }

    private void load() {
        new SwingWorker<List<HubRouteEntry>, Void>() {
            private HubFetchException failure;

            @Override
            protected List<HubRouteEntry> doInBackground() {
                try {
                    return catalog.loadValidEntries();
                } catch (HubFetchException e) {
                    failure = e;
                    return null;
                }
            }

            @Override
            protected void done() {
                listPanel.remove(statusLabel);
                if (failure != null) {
                    showMessage(failure.getMessage());
                    return;
                }
                List<HubRouteEntry> entries;
                try {
                    entries = get();
                } catch (Exception e) {
                    showMessage("Something went wrong loading the Route Hub.");
                    return;
                }
                if (entries == null || entries.isEmpty()) {
                    showMessage("No routes are available yet.");
                    return;
                }
                allEntries = entries;
                applyFilter("");
                updateFooter(entries);
            }
        }.execute();
    }

    /** Re-renders the list from allEntries, keeping only rows whose name, description, author, or
     *  tags contain the query (case-insensitive). Rebuilding rows from scratch each time is simpler
     *  and less error-prone than trying to show/hide already-built components, and with a catalog
     *  this small the cost is negligible. */
    private void applyFilter(String query) {
        listPanel.removeAll();
        String q = query == null ? "" : query.trim().toLowerCase();

        java.util.List<HubRouteEntry> matches = new java.util.ArrayList<>();
        for (HubRouteEntry entry : allEntries) {
            if (q.isEmpty() || matchesQuery(entry, q)) matches.add(entry);
        }

        if (matches.isEmpty()) {
            showMessage(q.isEmpty() ? "No routes are available yet." : "No routes match \"" + query + "\".");
        } else {
            for (HubRouteEntry entry : matches) {
                listPanel.add(buildRow(entry));
                listPanel.add(Box.createVerticalStrut(6));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private boolean matchesQuery(HubRouteEntry entry, String lowerQuery) {
        if (entry.name != null && entry.name.toLowerCase().contains(lowerQuery)) return true;
        if (entry.description != null && entry.description.toLowerCase().contains(lowerQuery)) return true;
        if (entry.author != null && entry.author.toLowerCase().contains(lowerQuery)) return true;
        if (entry.tags != null) {
            for (String tag : entry.tags) {
                if (tag != null && tag.toLowerCase().contains(lowerQuery)) return true;
            }
        }
        return false;
    }

    /** Shows the most recent "updated" date across the catalog, in a small footer. A single line
     *  for the whole Hub, not per-row, since the ask was catalog freshness at a glance. */
    private void updateFooter(java.util.List<HubRouteEntry> entries) {
        String latest = null;
        for (HubRouteEntry entry : entries) {
            if (entry.updated == null || entry.updated.isEmpty()) continue;
            if (latest == null || entry.updated.compareTo(latest) > 0) latest = entry.updated;
        }
        footerLabel.setText(latest != null ? "Route Hub last updated " + latest : " ");
    }

    private void showMessage(String text) {
        JLabel msg = new JLabel(text);
        msg.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        msg.setBorder(new EmptyBorder(10, 4, 10, 4));
        listPanel.add(msg);
        listPanel.revalidate();
        listPanel.repaint();
    }

    /** A route row: a 48x48 icon placeholder on the left, name/description/author line in the
     *  middle, and a small green "+" install control anchored to the bottom-right of the sub-line.
     *  Real icon bytes and real install behaviour arrive with the install step (4e/4f); this gets
     *  the layout and sizing right first, since that is what is actually visible right now. */
    /** The persistent "Community content" disclosure: expanded by default, collapsible to a single
     *  line via the chevron, with the choice remembered across sessions. This replaces a per-install
     *  confirmation dialog -- the caution is visible the whole time the Hub is being browsed, rather
     *  than a click-through box shown once per install. */
    private JPanel buildWarningBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(42, 36, 22));
        banner.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(90, 74, 31), 1),
            new EmptyBorder(0, 0, 0, 0)));

        JLabel label = new JLabel("\u26a0 Community content");
        label.setForeground(new Color(255, 204, 102));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(new EmptyBorder(8, 10, 8, 4));

        JLabel chevron = new JLabel();
        chevron.setForeground(new Color(216, 201, 143));
        chevron.setFont(chevron.getFont().deriveFont(11f));
        chevron.setBorder(new EmptyBorder(8, 4, 8, 10));
        chevron.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(label, BorderLayout.WEST);
        head.add(chevron, BorderLayout.EAST);
        head.setCursor(new Cursor(Cursor.HAND_CURSOR));

        String warningText = "These routes are made by other players, not by Route Planner. "
            + "Submissions are reviewed and links are blocked, but always be cautious \u2014 never "
            + "follow a step that asks you to visit a website, share your account, or do anything "
            + "outside the game.";

        JLabel body = new JLabel("<html><div style=\'width:180px;\'>" + warningText + "</div></html>");
        body.setForeground(new Color(216, 201, 143));
        body.setFont(body.getFont().deriveFont(10.5f));
        body.setBorder(new EmptyBorder(0, 10, 10, 10));

        // The panel text is small to fit the row width; the tooltip shows the same words at normal
        // system tooltip size/font, which is easier to read on request.
        String tooltipHtml = "<html><div style=\'width:220px;\'>" + warningText + "</div></html>";
        body.setToolTipText(tooltipHtml);
        label.setToolTipText(tooltipHtml);

        banner.add(head, BorderLayout.NORTH);
        banner.add(body, BorderLayout.CENTER);

        Runnable applyState = () -> {
            boolean collapsed = plugin.isHubWarningCollapsed();
            body.setVisible(!collapsed);
            chevron.setText(collapsed ? "\u25b8" : "\u25be");
        };
        applyState.run();

        java.awt.event.MouseAdapter toggle = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                plugin.setHubWarningCollapsed(!plugin.isHubWarningCollapsed());
                applyState.run();
                banner.revalidate();
                banner.repaint();
            }
        };
        head.addMouseListener(toggle);
        chevron.addMouseListener(toggle);

        return banner;
    }

    private JPanel buildRow(HubRouteEntry entry) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        // Right padding trimmed (9 -> -6, i.e. 15px less) so the install "+" sits further right,
        // per direct feedback. Left/top/bottom padding are untouched.
        row.setBorder(new EmptyBorder(9, 9, 9, -6));

        // Fixed-size icon, wrapped in a panel so BorderLayout cannot stretch it to the row's height.
        JLabel iconLabel = new JLabel();
        iconLabel.setOpaque(true);
        iconLabel.setBackground(new Color(45, 55, 65));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setBorder(BorderFactory.createLineBorder(new Color(60, 70, 80), 1));
        loadIconInto(iconLabel, entry);
        JPanel iconBox = new JPanel();
        iconBox.setOpaque(false);
        iconBox.setLayout(new BoxLayout(iconBox, BoxLayout.Y_AXIS));
        iconLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        iconLabel.setMaximumSize(new Dimension(48, 48));
        iconLabel.setPreferredSize(new Dimension(48, 48));
        iconLabel.setMinimumSize(new Dimension(48, 48));
        iconBox.add(iconLabel);
        iconBox.add(Box.createVerticalStrut(6));
        // installBtn is built below and added here, under the icon, per direct feedback --
        // it no longer competes with the description for horizontal room on the text side.

        JLabel name = new JLabel(escape(entry.name));
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13.5f));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel desc = new JLabel(escape(entry.description));
        desc.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        desc.setFont(desc.getFont().deriveFont(12f));
        desc.setBorder(new EmptyBorder(2, 0, 2, 0));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        desc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        desc.setPreferredSize(new Dimension(0, 16));

        JLabel installBtn = buildInstallControl(entry);
        installBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        // "by X · N steps" no longer takes row width -- it's a tooltip on the name/description
        // instead, since the row is too narrow to fit that text alongside anything else without
        // clipping. The tooltip text is plain (not HTML), so no escaping is needed here beyond
        // what Swing already does for tooltip content.
        String subInfo = "by " + entry.author + " \u00b7 " + entry.steps + " steps";
        name.setToolTipText(subInfo);
        // Description's tooltip shows the description itself, not author/steps -- the div above it
        // wraps and can visually truncate, so hovering should reveal the full text rather than
        // duplicate what the name's tooltip already says.
        desc.setToolTipText("<html><div style=\'width:220px;\'>" + escape(entry.description) + "</div></html>");
        installBtn.setToolTipText("Install this route (" + subInfo + ")");
        iconBox.add(installBtn);

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);
        textCol.add(name);
        textCol.add(desc);

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 95));
        row.setPreferredSize(new Dimension(0, 95));
        row.add(iconBox, BorderLayout.WEST);
        row.add(textCol, BorderLayout.CENTER);
        return row;
    }

    /** Finds the installed route (if any) whose hubSourceId matches this catalog entry. Reading
     *  the real route list as the source of truth means there is no separate persisted "installed"
     *  set to drift out of sync -- e.g. if the user deletes the route manually from their list, the
     *  button correctly reverts to Install with no extra bookkeeping. */
    private com.routeplanner.model.Route findInstalled(HubRouteEntry entry) {
        for (com.routeplanner.model.Route r : plugin.getRoutes()) {
            if (entry.id.equals(r.getHubSourceId())) return r;
        }
        return null;
    }

    /** Install/Remove control. Green "+" when not installed; red "-" when it is, matching the
     *  route's real presence in plugin.getRoutes() rather than separately tracked state. */
    private JLabel buildInstallControl(HubRouteEntry entry) {
        JLabel btn = new JLabel();
        btn.setOpaque(true);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                com.routeplanner.model.Route existing = findInstalled(entry);
                if (existing != null) {
                    removeInstalled(entry, existing, btn);
                } else {
                    installEntry(entry, btn);
                }
            }
        });
        refreshInstallControl(btn, entry);
        return btn;
    }

    /** Sets the control's icon/colors/tooltip to match whether entry is currently installed. */
    private void refreshInstallControl(JLabel btn, HubRouteEntry entry) {
        boolean installed = findInstalled(entry) != null;
        if (installed) {
            btn.setText("\u2212"); // minus sign
            btn.setForeground(new Color(224, 159, 159));
            btn.setBackground(new Color(74, 46, 46));
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(107, 63, 63), 1),
                new EmptyBorder(1, 8, 1, 8)));
            btn.setToolTipText("Remove this route");
        } else {
            btn.setText("+");
            btn.setForeground(new Color(159, 224, 159));
            btn.setBackground(new Color(46, 74, 46));
            btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(63, 107, 63), 1),
                new EmptyBorder(1, 8, 1, 8)));
            btn.setToolTipText("Install this route");
        }
    }

    /** Fetches the route file, independently re-validates it, and on success installs it via the
     *  exact same add/save/activate sequence file-import uses -- so a hub-installed route behaves
     *  identically to a manually imported one from that point on. Runs off the EDT. */
    private void installEntry(HubRouteEntry entry, JLabel btn) {
        btn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            private String failureMessage;
            private com.routeplanner.model.Route parsedRoute;

            @Override
            protected Void doInBackground() {
                try {
                    String json = catalog.fetchRouteJson(entry.file);
                    com.routeplanner.model.Route route = plugin.getRouteGson().fromJson(json, com.routeplanner.model.Route.class);
                    if (route == null) {
                        failureMessage = "The route file is empty or malformed.";
                        return null;
                    }
                    route.migrateIfNeeded();
                    String err = validateFetchedRoute(route);
                    if (err != null) {
                        failureMessage = "This route couldn't be installed: " + err;
                        return null;
                    }
                    route.setHubSourceId(entry.id);
                    String name = route.getName();
                    boolean exists = plugin.getRoutes().stream().anyMatch(r -> r.getName().equalsIgnoreCase(name));
                    if (exists) route.setName(name + " (Hub)");
                    parsedRoute = route;
                } catch (HubFetchException e) {
                    failureMessage = e.getMessage();
                } catch (Exception e) {
                    log.warn("Failed to install hub route {}", entry.id, e);
                    failureMessage = "Something went wrong installing this route.";
                }
                return null;
            }

            @Override
            protected void done() {
                btn.setEnabled(true);
                if (failureMessage != null) {
                    JOptionPane.showMessageDialog(RouteHubPanel.this, failureMessage,
                        "Install failed", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                plugin.getRoutes().add(parsedRoute);
                plugin.saveRoutesPublic();
                plugin.setActiveRoute(parsedRoute);
                refreshInstallControl(btn, entry);
            }
        }.execute();
    }

    /** Removes a hub-installed route. Delegates to plugin.deleteRoute(), the same path the normal
     *  route-list "Delete Route" menu item uses -- that method also clears the active route if it
     *  matched, forgets its undo/redo history, and refreshes the main panel. A bare
     *  routes.remove(route) skips all of that, which is what originally left stale step data
     *  visible after a hub removal: the main panel was never told to refresh. */
    private void removeInstalled(HubRouteEntry entry, com.routeplanner.model.Route route, JLabel btn) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove \"" + route.getName() + "\" from your routes?",
            "Remove route", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        plugin.deleteRoute(route);
        refreshInstallControl(btn, entry);
    }

    /** Entry text is already link-scanned by HubRouteEntry.validate(), but it is still rendered
     *  inside an HTML-interpreting JLabel, so escape markup characters to prevent malformed or
     *  unexpected HTML from a catalog entry affecting the label\'s rendering. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
