package com.routeplanner.hub;

import com.routeplanner.RoutePlannerPlugin;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteSection;
import com.routeplanner.model.RouteStep;
import com.routeplanner.util.RouteTextValidator;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Route Hub browse view: fetches the catalog of shared routes and lists them with their icons,
 * a search box and install/remove buttons. Network work runs off the EDT via SwingWorker.
 */
@Slf4j
public class RouteHubPanel extends JPanel {

    private final RoutePlannerPlugin plugin;
    private final RouteHubCatalog catalog;
    private final Runnable onBack;
    private final JPanel listPanel;
    private final JLabel statusLabel;
    private boolean loadedOnce = false;
    private JLabel footerLabel;
    private JButton back;

    /** Hidden when the Hub lives in its own tab, where there's nothing to go back to. */
    public void setBackVisible(boolean visible) {
        back.setVisible(visible);
    }

    public RouteHubPanel(RoutePlannerPlugin plugin, RouteHubCatalog catalog, Runnable onBack) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.onBack = onBack;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        back = new JButton("\u2039 Back");
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
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { applyFilter(search.getText()); }
            public void removeUpdate(DocumentEvent e) { applyFilter(search.getText()); }
            public void changedUpdate(DocumentEvent e) { applyFilter(search.getText()); }
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

    /** Called each time the Hub view is switched to; the catalog is loaded on the first visit only. */
    public void onShown() {
        if (loadedOnce) return;
        loadedOnce = true;
        load();
    }

    // Decoded icons by entry id, so redrawing the list doesn't fetch them again. Small enough to keep.
    private final Map<String, ImageIcon> iconCache = new HashMap<>();
    private List<HubRouteEntry> allEntries = Collections.emptyList();

    /** Fetches and decodes one entry's icon off the EDT, then swaps it into the label. On any
     *  failure the placeholder stays. */
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
                    Image img = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (img == null) return null; // not a decodable image
                    if (img.getWidth(null) <= 0 || img.getHeight(null) <= 0) return null;
                    Image scaled = img.getScaledInstance(48, 48, Image.SCALE_SMOOTH);
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
                    // failed or cancelled: the placeholder stays
                }
            }
        }.execute();
    }

    private static final int MAX_STEPS = 1500;
    private static final int MAX_NOTE_LEN = 280;
    private static final int MAX_NAME_LEN = 100;

    /**
     * Checks every free-text field of every step in a fetched route, whatever the catalog already
     * checked: a route from the network is never trusted as is. Returns null if it's safe to
     * install, or a short reason to show the user if not.
     */
    private String validateFetchedRoute(Route route) {
        if (route == null || route.getName() == null) return "The route file is empty or malformed.";
        if (route.getName().length() > MAX_NAME_LEN) return "The route's name is too long.";

        String nameErr = RouteTextValidator.checkField("Route name", route.getName());
        if (nameErr != null) return nameErr;

        int totalSteps = 0;
        List<RouteSection> sections = route.getSections();
        if (sections == null) return null; // an empty route is unusual but not unsafe

        for (RouteSection section : sections) {
            if (section == null || section.getSteps() == null) continue;

            String secErr = RouteTextValidator.checkField("Section name", section.getName());
            if (secErr != null) return secErr;

            for (RouteStep step : section.getSteps()) {
                if (step == null) continue;
                totalSteps++;
                if (totalSteps > MAX_STEPS) return "This route has too many steps.";

                if (step.getName() != null && step.getName().length() > MAX_NAME_LEN) {
                    return "A step name is too long.";
                }
                if (step.getNoteText() != null && step.getNoteText().length() > MAX_NOTE_LEN) {
                    return "A step's note is too long.";
                }

                String err = RouteTextValidator.checkField("Step name", step.getName());
                if (err == null) err = RouteTextValidator.checkField("Note", step.getNoteText());
                if (err == null) err = RouteTextValidator.checkField("Highlight NPC", step.getNpcHighlight());
                if (err == null) err = RouteTextValidator.checkField("Dialogue options", step.getDialogOptions());
                if (err == null) err = RouteTextValidator.checkField("Items", step.getItemList());
                if (err == null) err = RouteTextValidator.checkField("Inventory items", step.getInventoryHighlightItems());
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
                    log.warn("Route Planner: loading the Route Hub failed", e);
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

    /** Rebuilds the list from allEntries, keeping rows whose name, description, author or tags
     *  contain the query (ignoring case). */
    private void applyFilter(String query) {
        listPanel.removeAll();
        String q = query == null ? "" : query.trim().toLowerCase();

        List<HubRouteEntry> matches = new ArrayList<>();
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

    /** A small footer with the catalog's most recent "updated" date. */
    private void updateFooter(List<HubRouteEntry> entries) {
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

    /** The "Community content" notice: open by default, folds to one line with the chevron, and
     *  remembers which. Shown the whole time the Hub is browsed, so installs need no confirm box. */
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

        // Small text to fit the width; the tooltip repeats it at a readable size.
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

        MouseAdapter toggle = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
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
        // Little right padding, so the install "+" sits near the edge.
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
        // The install button goes under the icon (added below), leaving the text side its full width.

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
        // "by X · N steps" is the name's tooltip: the row is too narrow to show it. Plain text, no escaping.
        String subInfo = "by " + entry.author + " \u00b7 " + entry.steps + " steps";
        name.setToolTipText(subInfo);
        // The description can be cut off, so its tooltip shows all of it.
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

    /** The installed route (if any) whose hubSourceId matches this catalog entry. Read from the
     *  route list itself, so deleting the route by hand turns the button back to Install. */
    private Route findInstalled(HubRouteEntry entry) {
        for (Route r : plugin.getRoutes()) {
            if (entry.id.equals(r.getHubSourceId())) return r;
        }
        return null;
    }

    /** Install/Remove control: green "+" when the route isn't in plugin.getRoutes(), red "-" when it is. */
    private JLabel buildInstallControl(HubRouteEntry entry) {
        JLabel btn = new JLabel();
        btn.setOpaque(true);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Route existing = findInstalled(entry);
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

    /** Fetches the route file, checks it, and installs it the same way a file import does (add,
     *  save, activate). Runs off the EDT. */
    private void installEntry(HubRouteEntry entry, JLabel btn) {
        btn.setEnabled(false);
        new SwingWorker<Void, Void>() {
            private String failureMessage;
            private Route parsedRoute;

            @Override
            protected Void doInBackground() {
                try {
                    String json = catalog.fetchRouteJson(entry.file);
                    Route route = plugin.getRouteGson().fromJson(json, Route.class);
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
                plugin.saveRoutes();
                plugin.setActiveRoute(parsedRoute);
                refreshInstallControl(btn, entry);
            }
        }.execute();
    }

    /** Removes a hub-installed route through plugin.deleteRoute(), like "Delete Route" in the
     *  route list: it also clears the active route, its undo history, and refreshes the panel. */
    private void removeInstalled(HubRouteEntry entry, Route route, JLabel btn) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove \"" + route.getName() + "\" from your routes?",
            "Remove route", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        plugin.deleteRoute(route);
        refreshInstallControl(btn, entry);
    }

    /** Escapes markup in catalog text, which is shown in HTML-rendering JLabels. */
    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
