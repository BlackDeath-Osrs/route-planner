package com.routeplanner.ui;

import net.runelite.client.game.SpriteManager;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The plugin's icon: the game's own world map button (its globe inside the bronze minimap ring)
 * with a red route pin. The globe and ring are read from the game at runtime, so no game art is
 * stored in the plugin; until they load, the drawn stand-in (route_icon.png) is used.
 */
public final class RouteIcon {

    /** The world map orb's globe and its bronze ring (gameval SpriteID.WorldmapIcon.PLANET, MINIMAP_ORB_WORLD_MAP_FRAME). */
    private static final int GLOBE_SPRITE = 1439;
    private static final int RING_SPRITE = 1438;

    private static final int RED = 0xFFD62626, DARK_RED = 0xFF8C1212, WHITE = 0xFFFFFFFF, OUTLINE = 0xFF280E0A;

    private RouteIcon() {}

    /** Builds the icon once the game's sprites are available, then hands it over (on the client thread). */
    public static void load(SpriteManager sprites, Consumer<BufferedImage> done) {
        sprites.getSpriteAsync(RING_SPRITE, 0, ring ->
            sprites.getSpriteAsync(GLOBE_SPRITE, 0, globe -> {
                if (ring == null || globe == null) return;
                done.accept(compose(ring, globe));
            }));
    }

    /** The ring, the globe centred in it, and the pin at the top right. */
    static BufferedImage compose(BufferedImage ring, BufferedImage globe) {
        BufferedImage out = new BufferedImage(ring.getWidth(), ring.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(ring, 0, 0, null);
        g.drawImage(globe, (ring.getWidth() - globe.getWidth()) / 2, (ring.getHeight() - globe.getHeight()) / 2, null);
        g.dispose();
        drawPin(out, out.getWidth() * 2 / 3, out.getHeight() / 10, Math.max(3, out.getWidth() / 7));
        return out;
    }

    /**
     * A map pin: a round red head of radius {@code r} with a white glint, tapering to a point,
     * shaded darker on the right, with a dark one-pixel outline.
     */
    static void drawPin(BufferedImage img, int cx, int top, int r) {
        double cy = top + r;
        double tipY = cy + 2 * r + 1;
        Map<Long, Integer> shape = new HashMap<>();
        for (int y = top; y <= (int) tipY; y++) {
            for (int x = cx - r - 1; x <= cx + r + 1; x++) {
                double dx = x - cx + 0.5, dy = y - cy + 0.5;
                boolean head = dx * dx + dy * dy <= (r + 0.3) * (r + 0.3);
                double half = y >= cy ? (tipY - y) / (tipY - cy) * (r + 0.3) : -1;
                boolean tail = y >= cy && Math.abs(dx) <= half;
                if (!head && !tail) continue;
                boolean shaded = dx > r * 0.35 || y > cy + r;
                shape.put(key(x, y), shaded ? DARK_RED : RED);
            }
        }
        for (Map.Entry<Long, Integer> e : shape.entrySet()) {
            int x = (int) (e.getKey() >> 32), y = (int) (long) e.getKey();
            double dx = x - cx + 0.5, dy = y - cy + 0.5 + 0.8;
            if (dx * dx + dy * dy <= 2.2) e.setValue(WHITE);
        }
        int[][] around = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (Long k : shape.keySet()) {
            int x = (int) (k >> 32), y = (int) (long) k;
            for (int[] d : around) {
                int ox = x + d[0], oy = y + d[1];
                if (!shape.containsKey(key(ox, oy))) set(img, ox, oy, OUTLINE);
            }
        }
        for (Map.Entry<Long, Integer> e : shape.entrySet()) {
            set(img, (int) (e.getKey() >> 32), (int) (long) e.getKey(), e.getValue());
        }
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static void set(BufferedImage img, int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, argb);
    }
}
