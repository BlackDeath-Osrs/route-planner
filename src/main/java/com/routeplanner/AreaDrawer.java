package com.routeplanner;

import com.routeplanner.model.TileArea;
import com.routeplanner.util.Strokes;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Marking a step's area in game. Dragging over the ground adds a box of tiles; holding Shift paints
 * tile by tile instead, and a Shift stroke that starts on a marked tile erases. Both add to the same
 * tiles, so a room can be boxed out and then its edges touched up. A stroke that closes a ring
 * (a circle painted round a room) fills in what it encloses. Enter finishes; right-click or Esc cancels.
 * While painting, left and right clicks are kept from the game so the player doesn't walk off; the
 * middle button, arrow keys and scroll wheel still move the camera.
 */
@Singleton
public class AreaDrawer extends Overlay implements MouseListener, KeyListener {

    private final Client client;
    private final ClientThread clientThread;
    private final MouseManager mouseManager;
    private final KeyManager keyManager;
    private final RoutePlannerConfig config;

    // Written by the mouse and key events, read when drawing
    private volatile boolean active;
    private volatile Point mouse;
    private volatile boolean pressed;
    private volatile boolean clicked;   // a press since the last frame, so a quick click still paints
    private volatile boolean brush;     // this stroke was started with Shift held: paint, not a box
    private volatile boolean enterPressed;
    private Consumer<Result> onDone;

    // Client thread only (render)
    private final Set<WorldPoint> painted = new LinkedHashSet<>();
    private int plane = -1;
    private boolean erasing;
    private boolean stroking;
    private boolean boxing;            // the stroke under way adds a box (rather than painting)
    private WorldPoint strokeStart;
    private WorldPoint lastStrokeTile;
    private final List<WorldPoint> strokeAdded = new ArrayList<>();
    private TileArea preview;          // painted, as an area to draw; null = rebuild it

    @Inject
    AreaDrawer(Client client, ClientThread clientThread, MouseManager mouseManager, KeyManager keyManager,
               RoutePlannerConfig config) {
        this.client = client;
        this.clientThread = clientThread;
        this.mouseManager = mouseManager;
        this.keyManager = keyManager;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Start painting, beginning from {@code initial} (null = nothing marked yet). {@code onDone} gets
     * the area on the Swing thread: null if cancelled, an empty result clears the area.
     */
    public synchronized void start(TileArea initial, Consumer<Result> onDone) {
        if (active) finish(null);
        this.onDone = onDone;
        mouse = null;
        pressed = false;
        clicked = false;
        enterPressed = false;
        clientThread.invokeLater(() -> {
            painted.clear();
            plane = initial != null ? initial.getPlane() : -1;
            if (initial != null) painted.addAll(initial.points());
            preview = null;
            stroking = false;
            lastStrokeTile = null;
            strokeAdded.clear();
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=00ff00>Route Planner:</col> drag over the ground to add a box of tiles; hold Shift to paint "
                    + "single tiles (start on a marked tile to erase). Painting a closed ring fills it in. "
                    + "Enter when done; right-click or Esc to cancel.", null);
        });
        active = true;
        mouseManager.registerMouseListener(this);
        keyManager.registerKeyListener(this);
    }

    /** What painting produced: {@code area} is null when every tile was erased. */
    public static final class Result {
        public final TileArea area;
        Result(TileArea area) { this.area = area; }
    }

    public void cancel() {
        finish(null);
    }

    /** Stop painting and hand the result back (null when cancelled). */
    private synchronized void finish(Result result) {
        if (!active) return;
        active = false;
        mouseManager.unregisterMouseListener(this);
        keyManager.unregisterKeyListener(this);
        Consumer<Result> done = onDone;
        onDone = null;
        if (done != null) SwingUtilities.invokeLater(() -> done.accept(result));
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!active) return null;
        if (enterPressed) {
            enterPressed = false;
            finish(new Result(plane < 0 ? null : TileArea.of(plane, painted)));
            return null;
        }
        net.runelite.api.Point canvas = client.getMouseCanvasPosition();
        Point m = mouse != null ? mouse : canvas == null ? null : new Point(canvas.getX(), canvas.getY());
        WorldPoint hover = m == null ? null : tileAtCached(m);
        paint(hover);

        Color color = config.tileHighlightColor();
        drawTiles(g, color);
        if (stroking && boxing && strokeStart != null && lastStrokeTile != null) {
            AreaGraphics.drawGround(g, client, TileArea.of(between(strokeStart, lastStrokeTile)), color);
        }
        if (hover != null && (plane < 0 || hover.getPlane() == plane)) {
            outlineTile(g, hover, AreaGraphics.outline(erasing && stroking ? Color.RED : color));
        }
        if (m != null) {
            String label = painted.size() + (painted.size() == 1 ? " tile" : " tiles") + "  (Shift = paint, Enter = done)";
            g.setColor(Color.BLACK);
            g.drawString(label, m.x + 13, m.y + 21);
            g.setColor(Color.WHITE);
            g.drawString(label, m.x + 12, m.y + 20);
        }
        return null;
    }

    /**
     * Apply this frame of the stroke. A box stroke follows the mouse and adds its tiles on letting go;
     * a brush stroke paints (or erases) every tile from the last one to {@code hover}, so fast drags
     * leave no gaps.
     */
    private void paint(WorldPoint hover) {
        boolean down = pressed || clicked;
        clicked = false;
        if (!down) {
            if (stroking && boxing && strokeStart != null && lastStrokeTile != null) {
                for (WorldPoint t : TileArea.of(between(strokeStart, lastStrokeTile)).points()) {
                    if (painted.add(t)) strokeAdded.add(t);
                }
            }
            if (stroking && !strokeAdded.isEmpty()) {
                painted.addAll(enclosedBy(painted, strokeAdded, plane));
                preview = null;
            }
            strokeAdded.clear();
            stroking = false;
            strokeStart = null;
            lastStrokeTile = null;
            return;
        }
        if (hover == null) return;
        if (plane < 0) plane = hover.getPlane();
        if (hover.getPlane() != plane) return;
        if (!stroking) {
            stroking = true;
            boxing = !brush;
            erasing = !boxing && painted.contains(hover);
            strokeStart = hover;
            lastStrokeTile = hover;
        }
        if (!boxing) {
            for (WorldPoint t : line(lastStrokeTile, hover)) {
                if (erasing) {
                    if (painted.remove(t)) preview = null;
                } else if (painted.add(t)) {
                    strokeAdded.add(t);
                    preview = null;
                }
            }
        }
        lastStrokeTile = hover;
    }

    /**
     * Fill the empty pockets the tiles just added closed off: empty tiles beside them with no way out
     * to the open ground by straight (not diagonal) steps. Pockets elsewhere, like a hole erased on
     * purpose, are left alone.
     */
    static Set<WorldPoint> enclosedBy(Set<WorldPoint> painted, List<WorldPoint> added, int plane) {
        Set<WorldPoint> fill = new HashSet<>();
        if (painted.isEmpty()) return fill;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (WorldPoint t : painted) {
            x0 = Math.min(x0, t.getX()); y0 = Math.min(y0, t.getY());
            x1 = Math.max(x1, t.getX()); y1 = Math.max(y1, t.getY());
        }
        // One tile of margin all round: that ring is outside, and anything that reaches it is too
        x0--; y0--; x1++; y1++;
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        boolean[][] marked = new boolean[w][h];
        for (WorldPoint t : painted) marked[t.getX() - x0][t.getY() - y0] = true;
        boolean[][] outside = new boolean[w][h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{0, 0});
        outside[0][0] = true;
        flood(queue, marked, outside, w, h);

        for (WorldPoint t : added) {
            for (int[] d : STEPS) {
                int x = t.getX() - x0 + d[0], y = t.getY() - y0 + d[1];
                if (x < 0 || y < 0 || x >= w || y >= h || marked[x][y] || outside[x][y]) continue;
                // An enclosed pocket: fill all of it
                boolean[][] pocket = new boolean[w][h];
                queue.add(new int[]{x, y});
                pocket[x][y] = true;
                flood(queue, marked, pocket, w, h);
                for (int px = 0; px < w; px++) {
                    for (int py = 0; py < h; py++) {
                        if (!pocket[px][py]) continue;
                        marked[px][py] = true;
                        fill.add(new WorldPoint(px + x0, py + y0, plane));
                    }
                }
            }
        }
        return fill;
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Spread {@code seen} over unmarked tiles from the queued ones, by straight steps. */
    private static void flood(ArrayDeque<int[]> queue, boolean[][] marked, boolean[][] seen, int w, int h) {
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : STEPS) {
                int x = c[0] + d[0], y = c[1] + d[1];
                if (x < 0 || y < 0 || x >= w || y >= h || marked[x][y] || seen[x][y]) continue;
                seen[x][y] = true;
                queue.add(new int[]{x, y});
            }
        }
    }

    /** The tiles on a straight line from a to b, both ends included. */
    private static List<WorldPoint> line(WorldPoint a, WorldPoint b) {
        int dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) return Collections.singletonList(b);
        List<WorldPoint> out = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            out.add(new WorldPoint(a.getX() + Math.round((float) dx * i / steps),
                a.getY() + Math.round((float) dy * i / steps), a.getPlane()));
        }
        return out;
    }

    private void drawTiles(Graphics2D g, Color color) {
        if (painted.isEmpty()) return;
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null || wv.getPlane() != plane) return;
        if (preview == null) preview = TileArea.of(plane, painted);
        if (preview != null) AreaGraphics.drawGround(g, client, preview, color);
    }

    private void outlineTile(Graphics2D g, WorldPoint t, Color color) {
        WorldView wv = client.getTopLevelWorldView();
        LocalPoint lp = wv == null ? null : LocalPoint.fromWorld(wv, t);
        Polygon poly = lp == null ? null : Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) return;
        g.setColor(color);
        g.setStroke(Strokes.round(2));
        g.drawPolygon(poly);
    }

    /** The rectangle with these two tiles as opposite corners, on the first one's floor. */
    static WorldArea between(WorldPoint a, WorldPoint b) {
        return new WorldArea(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
            Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, a.getPlane());
    }

    // tileAt projects every tile in the scene, so it's redone only when the mouse or camera moves
    private Point lastMouse;
    private long lastCamera;
    private WorldPoint lastTile;

    private WorldPoint tileAtCached(Point p) {
        long camera = ((long) client.getCameraX() * 31 + client.getCameraY()) * 31 + client.getCameraZ();
        camera = (camera * 31 + client.getCameraYawTarget()) * 31 + client.getCameraPitchTarget();
        camera = camera * 31 + client.getPlane();
        if (!p.equals(lastMouse) || camera != lastCamera) {
            lastTile = tileAt(p);
            lastMouse = new Point(p);
            lastCamera = camera;
        }
        return lastTile;
    }

    /** The ground tile under a point on the screen, on the floor being shown. */
    private WorldPoint tileAt(Point p) {
        WorldView wv = client.getTopLevelWorldView();
        if (wv == null) return null;
        Tile[][] tiles = wv.getScene().getTiles()[wv.getPlane()];
        for (Tile[] column : tiles) {
            for (Tile tile : column) {
                if (tile == null) continue;
                Polygon poly = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());
                if (poly != null && poly.contains(p)) return tile.getWorldLocation();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public MouseEvent mousePressed(MouseEvent e) {
        if (!active) return e;
        if (SwingUtilities.isLeftMouseButton(e)) {
            mouse = e.getPoint();
            brush = e.isShiftDown();
            pressed = true;
            clicked = true;
            e.consume();
        } else if (SwingUtilities.isRightMouseButton(e)) {
            e.consume();
            finish(null);
        }
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e) {
        if (!active) return e;
        if (SwingUtilities.isLeftMouseButton(e)) {
            mouse = e.getPoint();
            pressed = false;
        }
        if (!SwingUtilities.isMiddleMouseButton(e)) e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent e) {
        if (active && !SwingUtilities.isMiddleMouseButton(e)) e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent e) {
        if (!active) return e;
        mouse = e.getPoint();
        if (SwingUtilities.isLeftMouseButton(e)) e.consume();
        return e;
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent e) {
        if (active) mouse = e.getPoint();
        return e;
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent e) {
        return e;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent e) {
        return e;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (!active) return;
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            e.consume();
            finish(null);
        } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            e.consume();
            enterPressed = true;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }
}
