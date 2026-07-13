package com.routeplanner;

import com.routeplanner.model.Route;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class RouteImportExport {

    private final RoutePlannerPlugin plugin;
    private final RoutePlannerPanel panel;

    public RouteImportExport(RoutePlannerPlugin plugin, RoutePlannerPanel panel) {
        this.plugin = plugin;
        this.panel = panel;
    }

    public void exportRoute(Route route) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Route");
        chooser.setSelectedFile(new File(route.getName().replaceAll("[^a-zA-Z0-9_-]", "_") + ".json"));
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));

        int result = chooser.showSaveDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().endsWith(".json")) {
            file = new File(file.getAbsolutePath() + ".json");
        }

        try {
            String json = plugin.getRouteGson().toJson(route);
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(null,
                "Route exported to:\n" + file.getAbsolutePath(),
                "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            log.info("Exported route {} to {}", route.getName(), file.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to export route", e);
            JOptionPane.showMessageDialog(null,
                "Export failed: " + e.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void exportAllRoutes() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export All Routes");
        chooser.setSelectedFile(new File("routes_export.json"));
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));

        int result = chooser.showSaveDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        if (!file.getName().endsWith(".json")) {
            file = new File(file.getAbsolutePath() + ".json");
        }

        try {
            String json = plugin.getRouteGson().toJson(plugin.getRoutes());
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
            JOptionPane.showMessageDialog(null,
                "All routes exported to:\n" + file.getAbsolutePath(),
                "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            log.error("Failed to export routes", e);
            JOptionPane.showMessageDialog(null,
                "Export failed: " + e.getMessage(),
                "Export Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * A JFileChooser using the platform's native look and feel (GTK on Linux, etc.) instead of
     * Swing's default cross-platform "Metal" theme, which several users found looked out of place
     * and confusing to navigate. Falls back silently to the default chooser if the system L&F
     * can't be set for any reason -- this is cosmetic, never worth failing an import/export over.
     */
    private JFileChooser newSystemFileChooser(String title) {
        try {
            String current = javax.swing.UIManager.getLookAndFeel().getClass().getName();
            String system = javax.swing.UIManager.getSystemLookAndFeelClassName();
            if (!current.equals(system)) {
                javax.swing.UIManager.setLookAndFeel(system);
            }
        } catch (Exception ignored) {
            // Best-effort only; the default cross-platform chooser still works fine.
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        return chooser;
    }

    public void importRoute() {
        JFileChooser chooser = newSystemFileChooser("Import Route");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));

        int result = chooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File file = chooser.getSelectedFile();
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

            // Try single route first
            try {
                Route route = plugin.getRouteGson().fromJson(json, Route.class);
                if (route != null) route.migrateIfNeeded();
                if (route != null && route.getName() != null) {
                    String name = route.getName();
                    boolean exists = plugin.getRoutes().stream()
                        .anyMatch(r -> r.getName().equalsIgnoreCase(name));
                    if (exists) {
                        route.setName(name + " (imported)");
                    }
                    plugin.getRoutes().add(route);
                    plugin.saveRoutesPublic();
                    plugin.setActiveRoute(route);
                    JOptionPane.showMessageDialog(null,
                        "Route \"" + route.getName() + "\" imported!",
                        "Import Successful", JOptionPane.INFORMATION_MESSAGE);
                    log.info("Imported route: {}", route.getName());
                    return;
                }
            } catch (Exception ignored) {}

            // Try list of routes
            Route[] routes = plugin.getRouteGson().fromJson(json, Route[].class);
            if (routes != null) for (Route r : routes) r.migrateIfNeeded();
            if (routes != null && routes.length > 0) {
                int imported = 0;
                for (Route route : routes) {
                    if (route.getName() == null) continue;
                    String name = route.getName();
                    boolean exists = plugin.getRoutes().stream()
                        .anyMatch(r -> r.getName().equalsIgnoreCase(name));
                    if (exists) route.setName(name + " (imported)");
                    plugin.getRoutes().add(route);
                    imported++;
                }
                plugin.saveRoutesPublic();
                panel.refresh();
                JOptionPane.showMessageDialog(null,
                    imported + " route(s) imported!",
                    "Import Successful", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception e) {
            log.error("Failed to import route", e);
            JOptionPane.showMessageDialog(null,
                "Import failed: " + e.getMessage(),
                "Import Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
