package com.routeplanner.record;

import static com.routeplanner.ui.Ui.*;

import com.routeplanner.util.HighlightColors;
import com.routeplanner.*;
import com.routeplanner.model.*;
import net.runelite.client.ui.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * After re-recording a section: the section and the recording side by side as one list. New
 * steps are green, steps the recording fills in or changes are orange (one tick box per field),
 * steps the recording didn't see are grey, and everything else is plain. Apply writes the ticked
 * changes into the section; Discard leaves it as it was.
 */
public class ReRecordDialog extends JDialog {

    static final Color GREEN = new Color(80, 200, 120);
    static final Color ORANGE = new Color(255, 160, 50);
    static final Color GREY = new Color(130, 130, 130);

    private final RoutePlannerPlugin plugin;
    private final ReRecordSession session;
    private final List<SectionDiff.Row> rows;
    /** NEW rows: add the step. UNSEEN rows: keep the step. */
    private final Map<SectionDiff.Row, JCheckBox> rowBoxes = new IdentityHashMap<>();
    private final Map<SectionDiff.FieldChange, JCheckBox> fieldBoxes = new IdentityHashMap<>();

    public ReRecordDialog(RoutePlannerPlugin plugin, ReRecordSession session, List<RouteStep> drafts) {
        this.plugin = plugin;
        this.session = session;
        this.rows = SectionDiff.compare(session.getOriginalSteps(), drafts, session.getTickedAt());

        RouteSection section = session.getSection();
        setTitle("Re-recorded: " + section.getName());
        setAlwaysOnTop(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { onDiscard(); }
        });

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);

        int added = 0, changed = 0, unseen = 0;
        for (SectionDiff.Row r : rows) {
            if (r.kind == SectionDiff.Kind.NEW) added++;
            else if (r.kind == SectionDiff.Kind.CHANGED) changed++;
            else if (r.kind == SectionDiff.Kind.UNSEEN) unseen++;
        }
        JPanel head = bare(new JPanel());
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("<html><b>" + HighlightColors.escapeHtml(section.getName()) + "</b></html>");
        title.setForeground(Color.WHITE);
        head.add(title);
        JLabel legend = new JLabel("<html>"
            + swatch(GREEN, added + " new") + " &nbsp; "
            + swatch(ORANGE, changed + " changed") + " &nbsp; "
            + swatch(GREY, unseen + " not seen") + "<br>"
            + "<span style='color:#aaaaaa'>Untick anything you don't want. Double-click a new step to edit it.</span></html>");
        legend.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        head.add(Box.createVerticalStrut(4));
        head.add(legend);
        root.add(head, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        list.setBorder(new EmptyBorder(4, 4, 4, 4));
        int n = 1;
        for (SectionDiff.Row r : rows) list.add(rowPanel(r, n++));
        list.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        JPanel bottom = bare(new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0)));
        JButton discard = new JButton("Discard");
        discard.addActionListener(e -> onDiscard());
        JButton apply = new JButton("Apply to section");
        apply.setForeground(new Color(0, 200, 0));
        apply.addActionListener(e -> onApply());
        bottom.add(discard);
        bottom.add(apply);
        root.add(bottom, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(560, 520);
        setLocationRelativeTo(null);
    }

    private JPanel rowPanel(SectionDiff.Row r, int number) {
        JPanel p = bareLeft(new JPanel());
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        Color color = colorOf(r.kind);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 3, 0, 0, color == null ? ColorScheme.DARKER_GRAY_COLOR : color),
            new EmptyBorder(3, 6, 3, 2)));

        RouteStep shown = r.step != null ? r.step : r.recorded;
        String text = number + ". " + shown.getName() + tag(r.kind);
        Component top;
        if (r.kind == SectionDiff.Kind.NEW || r.kind == SectionDiff.Kind.UNSEEN) {
            JCheckBox box = new JCheckBox(text, true);
            box.setToolTipText(r.kind == SectionDiff.Kind.NEW ? "Add this recorded step to the section"
                : "Keep this step (the recording never reached it). Untick to delete it.");
            style(box, color);
            rowBoxes.put(r, box);
            if (r.kind == SectionDiff.Kind.NEW) {
                box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                box.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() != 2) return;
                        box.setSelected(true);
                        QuickStepDialog editor = new QuickStepDialog(plugin, session.getRoute(), r.recorded);
                        editor.setOnSaved(() -> box.setText(number + ". " + r.recorded.getName() + tag(r.kind)));
                        editor.setVisible(true);
                    }
                });
            }
            top = box;
        } else {
            JLabel l = smallText(new JLabel(text), color != null ? color : ColorScheme.LIGHT_GRAY_COLOR);
            l.setBorder(new EmptyBorder(2, 0, 2, 0));
            top = l;
        }
        ((javax.swing.JComponent) top).setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(top);

        for (SectionDiff.FieldChange c : r.changes) {
            String change = "<html><span style='color:#cccccc'>" + HighlightColors.escapeHtml(c.label) + ":</span> "
                + (c.fill ? "<i>none</i>" : HighlightColors.escapeHtml(clip(c.oldText))) + " &rarr; " + HighlightColors.escapeHtml(clip(c.newText)) + "</html>";
            JCheckBox box = new JCheckBox(change, c.fill);
            box.setToolTipText(c.fill ? "The step had nothing here; the recording fills it in."
                : "Different from what the step had. Tick to replace it with the recorded value.");
            style(box, ColorScheme.LIGHT_GRAY_COLOR);
            box.setBorder(new EmptyBorder(0, 16, 0, 0));
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            fieldBoxes.put(c, box);
            p.add(box);
        }
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private void onApply() {
        RouteSection section = session.getSection();
        if (!plugin.getRoutes().contains(session.getRoute()) || !session.getRoute().getSections().contains(section)) {
            JOptionPane.showMessageDialog(this, "The section \"" + section.getName()
                + "\" no longer exists, so the recording can't be applied to it.");
            return;
        }
        plugin.getRouteHistory().push(session.getRoute(), "Re-record section \"" + section.getName() + "\"",
            plugin.getRouteGson());
        List<RouteStep> merged = new ArrayList<>();
        for (SectionDiff.Row r : rows) {
            switch (r.kind) {
                case NEW:
                    if (!rowBoxes.get(r).isSelected()) break;
                    r.recorded.setSectionId(section.getId());
                    r.recorded.setCompleted(true); // just done while recording
                    merged.add(r.recorded);
                    break;
                case UNSEEN:
                    if (!rowBoxes.get(r).isSelected()) break;
                    r.step.setCompleted(session.doneBeforeOrDuring(r.step));
                    merged.add(r.step);
                    break;
                default: // SAME, CHANGED
                    for (SectionDiff.FieldChange c : r.changes) {
                        if (fieldBoxes.get(c).isSelected()) c.applyTo(r.step, r.recorded);
                    }
                    // Paired with a recorded step means it was done during the recording
                    r.step.setCompleted(r.recorded != null || session.doneBeforeOrDuring(r.step));
                    merged.add(r.step);
            }
        }
        // One swap, so the game never reads a half-built list
        section.setSteps(merged);
        plugin.saveRoutes();
        plugin.refreshHighlights();
        dispose();
    }

    private void onDiscard() {
        int c = JOptionPane.showConfirmDialog(this, "Discard the recording and leave the section as it was?",
            "Discard re-recording", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (c != JOptionPane.YES_OPTION) return;
        session.restoreTicks();
        plugin.saveRoutes();
        plugin.refreshHighlights();
        dispose();
    }

    private static void style(JCheckBox box, Color fg) {
        smallText(bare(box), fg);
        box.setFocusPainted(false);
        box.setHorizontalAlignment(SwingConstants.LEFT);
    }

    private static Color colorOf(SectionDiff.Kind k) {
        switch (k) {
            case NEW: return GREEN;
            case CHANGED: return ORANGE;
            case UNSEEN: return GREY;
            default: return null;
        }
    }

    private static String tag(SectionDiff.Kind k) {
        switch (k) {
            case NEW: return "  (new)";
            case UNSEEN: return "  (not seen)";
            default: return "";
        }
    }

    private static String swatch(Color c, String text) {
        return String.format("<span style='color:#%02x%02x%02x'>&#9632; %s</span>", c.getRed(), c.getGreen(), c.getBlue(), text);
    }

    private static String clip(String s) {
        return s.length() > 60 ? s.substring(0, 57) + "..." : s;
    }

}
