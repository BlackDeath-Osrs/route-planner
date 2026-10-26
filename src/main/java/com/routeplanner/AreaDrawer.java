package com.routeplanner;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
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
import java.util.function.Consumer;

/**
 * Drawing a step's area in game: press on the ground, drag, let go. While drawing, left and right
 * clicks are kept from the game so the player doesn't walk off; the middle button, arrow keys and
 * scroll wheel still move the camera. Right-click or Esc cancels.
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
    private volatile boolean released;
    private volatile WorldPoint hover;
    private volatile WorldPoint start;
    private Consumer<WorldArea> onDone;

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

    /** Start drawing; {@code onDone} gets the area on the Swing thread, or null if cancelled. */
    public synchronized void start(Consumer<WorldArea> onDone) {
        if (active) finish(null);
        this.onDone = onDone;
        mouse = null;
        pressed = false;
        released = false;
        hover = null;
        start = null;
        clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
            "<col=00ff00>Route Planner:</col> drag over the ground to mark the area. Right-click or Esc to cancel.", null));
        active = true;
        mouseManager.registerMouseListener(this);
        keyManager.registerKeyListener(this);
    }

    public void cancel() {
        finish(null);
    }

    /** Stop drawing and hand the area (null when cancelled) back. */
    private synchronized void finish(WorldArea area) {
        if (!active) return;
        active = false;
        mouseManager.unregisterMouseListener(this);
        keyManager.unregisterKeyListener(this);
        Consumer<WorldArea> done = onDone;
        onDone = null;
        if (done != null) SwingUtilities.invokeLater(() -> done.accept(area));
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (!active) return null;
        net.runelite.api.Point canvas = client.getMouseCanvasPosition();
        Point m = mouse != null ? mouse : canvas == null ? null : new Point(canvas.getX(), canvas.getY());
        WorldPoint t = m == null ? null : tileAt(m);
        if (t != null) hover = t;
        if (pressed && start == null) start = hover;
        if (hover == null) return null;

        WorldArea area = between(start != null ? start : hover, hover);
        if (released) {
            released = false;
            pressed = false;
            if (start != null) {
                finish(area);
                return null;
            }
        }

        Color color = config.tileHighlightColor();
        AreaGraphics.drawGround(g, client, area, color);
        if (m != null) {
            String size = area.getWidth() + " x " + area.getHeight();
            g.setColor(Color.BLACK);
            g.drawString(size, m.x + 13, m.y + 21);
            g.setColor(Color.WHITE);
            g.drawString(size, m.x + 12, m.y + 20);
        }
        return null;
    }

    /** The rectangle with these two tiles as opposite corners, on the first one's floor. */
    static WorldArea between(WorldPoint a, WorldPoint b) {
        return new WorldArea(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
            Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, a.getPlane());
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
            pressed = true;
            e.consume();
        } else if (SwingUtilities.isRightMouseButton(e)) {
            e.consume();
            cancel();
        }
        return e;
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent e) {
        if (!active) return e;
        if (SwingUtilities.isLeftMouseButton(e) && pressed) {
            mouse = e.getPoint();
            released = true;
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
        if (active && e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            e.consume();
            cancel();
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }
}
