package com.routeplanner.record;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

/** Always-visible indicator while Record mode is on, so recording is never silent. */
public class RecordingOverlay extends OverlayPanel {

    private final RouteRecorder recorder;

    @Inject
    public RecordingOverlay(RouteRecorder recorder) {
        this.recorder = recorder;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!recorder.isRecording()) return null;
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Recording route")
            .color(new Color(255, 60, 60))
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Steps")
            .right(String.valueOf(recorder.getDraftCount()))
            .build());
        return super.render(graphics);
    }
}
