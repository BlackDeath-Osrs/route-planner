package com.routeplanner.dialog;

import lombok.RequiredArgsConstructor;
import com.routeplanner.*;
import com.routeplanner.model.*;
import net.runelite.api.Client;
import net.runelite.api.widgets.*;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import javax.inject.*;
import java.util.List;

/**
 * Colours the chat option to pick for a step's dialogue sequence (e.g. "3,1"), in the chatbox
 * itself, the way Quest Helper does: the option's text takes the highlight colour, and keeps it
 * when the mouse moves off it (the game would turn it back to black).
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DialogHighlighter {

    /** The game's colour for an option line. */
    private static final int OPTION_BLACK = 0x000000;

    private final Client client;
    private final ClientThread clientThread;
    private final RoutePlannerPlugin plugin;
    private final RoutePlannerConfig config;

    private Widget highlighted;

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded e) {
        // The options are filled in by a script after the menu loads, so colour them just after.
        if (e.getGroupId() == InterfaceID.CHATMENU) clientThread.invokeLater(this::apply);
    }

    /** Colours the option the step wants, and un-colours one it no longer wants. Call on the client thread. */
    public void apply() {
        List<Widget> options = DialogOptions.options(client);
        Widget target = target(options);
        if (highlighted != null && highlighted != target && options.contains(highlighted)) {
            Widget old = highlighted;
            old.setTextColor(OPTION_BLACK);
            old.setOnMouseLeaveListener((JavaScriptCallback) ev -> old.setTextColor(OPTION_BLACK));
        }
        highlighted = target;
        if (target == null) return;
        target.setTextColor(color());
        target.setOnMouseLeaveListener((JavaScriptCallback) ev -> target.setTextColor(color()));
    }

    /** Puts a coloured option back to black, for when the plugin stops. */
    public void clear() {
        Widget old = highlighted;
        highlighted = null;
        if (old != null && DialogOptions.options(client).contains(old)) {
            clientThread.invokeLater(() -> {
                old.setTextColor(OPTION_BLACK);
                old.setOnMouseLeaveListener((JavaScriptCallback) ev -> old.setTextColor(OPTION_BLACK));
            });
        }
    }

    private int color() {
        return config.dialogOptionColor().getRGB() & 0xFFFFFF;
    }

    /** The option line the active step wants picked next, or null. */
    private Widget target(List<Widget> options) {
        if (options.isEmpty()) return null;
        Route route = plugin.getActiveRoute();
        RouteStep step = route != null ? route.getActiveStep() : null;
        if (step == null || step.getDialogOptions() == null || step.getDialogOptions().trim().isEmpty()) return null;
        if (!plugin.isDialogPartnerOk()) return null;
        String[] parts = step.getDialogOptions().split(",");
        int progress = plugin.getDialogProgress();
        if (progress < 0 || progress >= parts.length) return null;
        int option;
        try {
            option = Integer.parseInt(parts[progress].trim());
        } catch (NumberFormatException ex) {
            return null;
        }
        return option >= 1 && option <= options.size() ? options.get(option - 1) : null;
    }
}
