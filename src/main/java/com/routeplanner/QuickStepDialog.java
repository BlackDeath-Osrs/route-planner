package com.routeplanner;

import com.routeplanner.model.ItemMode;
import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.SkillGoalType;
import com.routeplanner.model.StepType;
import com.routeplanner.model.TileArea;
import com.routeplanner.sailing.SailingDocks;
import com.routeplanner.skilling.RecipeLabels;
import com.routeplanner.teleport.TeleportItem;
import com.routeplanner.teleport.TeleportItems;
import com.routeplanner.teleport.TeleportPickers;
import com.routeplanner.teleport.TeleportSpell;
import com.routeplanner.teleport.TeleportSpells;
import com.routeplanner.ui.StepIcon;
import com.routeplanner.util.LocationNames;
import com.routeplanner.util.RouteTextValidator;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The step editor: pick what kind of step it is, and only that kind's fields show.
 *
 * Types: Go somewhere, Get items, Talk to, Kill, Train a skill, Quest, Note. "Add more" puts
 * extras (items, a skill goal, a quest, highlights, a teleport, stairs, a boat) on any type.
 *
 * On an existing step it only touches the fields it shows, so recorded, Hub and older steps edit safely.
 */
public class QuickStepDialog extends JFrame {

    enum Type {
        WALK("Go somewhere", StepIcon.Kind.WALK), ITEMS("Get items", StepIcon.Kind.BUY), TALK("Talk to", StepIcon.Kind.TALK),
        KILL("Kill", StepIcon.Kind.KILL), SKILL("Train a skill", StepIcon.Kind.SKILL), QUEST("Quest", StepIcon.Kind.QUEST),
        NOTE("Note", StepIcon.Kind.NOTE);

        final String label;
        final StepIcon.Kind icon;
        Type(String label, StepIcon.Kind icon) { this.label = label; this.icon = icon; }
    }

    private static final Color ORANGE = ColorScheme.BRAND_ORANGE;
    private static final Color FIELD_BG = new Color(50, 50, 50);
    private static final Color SECTION_BG = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color DONE_BG = new Color(20, 33, 20), DONE_EDGE = new Color(44, 74, 44), DONE_FG = new Color(207, 233, 207);
    private static final Color MANUAL_BG = new Color(42, 36, 20), MANUAL_EDGE = new Color(74, 63, 34), MANUAL_FG = new Color(234, 220, 181);
    private static final Color ERROR = new Color(255, 120, 110);
    private static final String[] ITEM_MODES = {"Bring from the bank", "Buy at a shop", "Sell to a shop", "Pick up or gather", "Deposit everything"};
    private static final ItemMode[] ITEM_MODE_KEYS = {ItemMode.BANK, ItemMode.SHOP, ItemMode.SELL, ItemMode.PICKUP, ItemMode.DEPOSIT_ALL};
    private static final String[] GOALS = {"Gain XP", "Reach level", "Reach total XP"};
    private static final SkillGoalType[] GOAL_KEYS = {SkillGoalType.XP_GAIN, SkillGoalType.LEVEL, SkillGoalType.XP_TARGET};
    private static final Pattern QTY = Pattern.compile("^([\\d.]+\\s*(?:k|m|mil|b)?)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AREA_SPLIT = Pattern.compile("\\s*(?:\\bto\\b|->|\u2192)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTO_KILL_NOTE = Pattern.compile("^Kill \\d+ .+\\.$");

    private final RoutePlannerPlugin plugin;
    private final Route route;
    private final RouteStep editing;
    private final WorldPoint seedTile;
    /** Drawn areas are a beta feature: off, the Area row is hidden and a step's area is left as it is. */
    private final boolean areasOn;
    private Runnable onSaved;

    private Type type = Type.WALK;
    private boolean extraItems, extraSkill, extraQuest, extraHighlight, extraStairs, extraChat, extraTele, extraBoat;

    // Teleport: only written back when changed here, so steps with unusual teleports stay intact
    private String teleMethod = "WALK"; // WALK | SPELL | ITEM | NEXUS | PORTAL
    private TeleportSpell chosenSpell;
    private TeleportItem chosenItem;
    private String chosenNexus;
    private boolean teleTouched;

    private final Map<Type, JToggleButton> typeButtons = new EnumMap<>(Type.class);
    private final HintField nameField = new HintField();
    private final JTextField tileField = field();
    private final HintField areaField = new HintField();
    /** A painted (not rectangular) area, shown in areaField by its formatArea text. */
    private TileArea paintedArea;
    private final JLabel placeLabel = small("");
    private final HintField whoField = new HintField();
    private final JLabel whoLabel = label("Who");
    private final JTextField killCount = field();
    private final JCheckBox highlight = check("Highlight in game", true);
    private final List<Integer> chatPicks = new ArrayList<>();
    private final JPanel chatChips = flow();
    private final JComboBox<String> itemMode = combo(ITEM_MODES);
    private final JPanel itemRows = vbox();
    private final List<ItemRow> items = new ArrayList<>();
    private final JComboBox<String> skillBox = combo(skillNames());
    private final JComboBox<String> goalBox = combo(GOALS);
    private final JTextField goalValue = field();
    private final JComboBox<String> recipeBox = combo(new String[]{"(none)"});
    private final HintField questSearch = new HintField();
    private final JComboBox<String> questBox = combo(new String[0]);
    private final JCheckBox questComplete = check("Complete this step when the quest is finished", true);
    private final HintField hlNpc = new HintField();
    private final HintField hlObject = new HintField();
    private final HintField hlInventory = new HintField();
    private final JTextField stairsField = field();
    private final JComboBox<String> boatDock = combo(new String[0]);
    private final JTextArea noteArea = new JTextArea(3, 20);
    private final JCheckBox noteAuto = check("Complete on arrival", true);
    // On for a new area; a step that already has one keeps what it had
    private final JCheckBox areaHighlightOnly = check("Only highlight things inside the area", true);
    private final JTextArea doneLabel = new JTextArea();
    private final JPanel doneBox = new JPanel(new BorderLayout(7, 0));
    private final JLabel errorLabel = new JLabel(" ");

    private JPanel whereSec, teleSec, whoSec, chatSec, itemsSec, skillSec, questSec, highlightSec, stairsSec, boatSec, noteSec;
    private JPanel addMore;
    private JButton addItemsBtn, addSkillBtn, addQuestBtn, addHighlightBtn, addStairsBtn, addTeleBtn, addBoatBtn;
    private JPanel killRow;
    private final Map<JPanel, Component> gaps = new HashMap<>();
    private JPanel addMoreRow;
    private boolean loading = true; // listeners stay quiet until the form is built and filled

    public QuickStepDialog(RoutePlannerPlugin plugin, Route route, RouteStep editing) {
        this(plugin, route, editing, null);
    }

    /** @param seedTile for a new step, the tile to start from (shift+right-click "Add Location Step"). */
    public QuickStepDialog(RoutePlannerPlugin plugin, Route route, RouteStep editing, WorldPoint seedTile) {
        this.plugin = plugin;
        this.route = route;
        this.editing = editing;
        this.seedTile = seedTile;
        this.areasOn = plugin.getConfig().betaFeatures();

        setTitle(editing == null ? "Add step" : "Edit step");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        setSize(plugin.getStepEditorSize());
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                plugin.setStepEditorSize(getWidth(), getHeight());
            }
        });
        setLocationRelativeTo(null);

        JPanel root = vbox();
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        root.add(label("What kind of step?"));
        root.add(Box.createVerticalStrut(4));
        root.add(buildTypes());
        root.add(Box.createVerticalStrut(10));
        root.add(labeled("Step name", nameField));
        nameField.setToolTipText("Leave blank to use the suggested name");

        whereSec = buildWhere();
        teleSec = buildTeleport();
        whoSec = buildWho();
        chatSec = buildChat();
        itemsSec = buildItems();
        skillSec = buildSkill();
        questSec = buildQuest();
        highlightSec = buildHighlight();
        stairsSec = buildStairs();
        boatSec = buildBoat();
        noteSec = buildNote();
        for (JPanel p : new JPanel[]{whereSec, teleSec, whoSec, chatSec, itemsSec, skillSec, questSec, highlightSec, stairsSec, boatSec, noteSec}) {
            Component gap = Box.createVerticalStrut(10);
            gaps.put(p, gap); // hidden with its section, so hidden sections leave no hole
            root.add(gap);
            root.add(p);
        }
        root.add(Box.createVerticalStrut(10));
        root.add(highlightRow());
        root.add(Box.createVerticalStrut(10));
        addMore = buildAddMore();
        root.add(addMore);

        JPanel top = new ScrollTrack();
        top.add(root, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(top);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buildFooter(), BorderLayout.SOUTH);
        setContentPane(content);

        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);

        if (editing != null) load(editing);
        else {
            WorldPoint start = seedTile != null ? seedTile : plugin.getLastPlayerLocation();
            if (start != null) tileField.setText(formatTile(start));
            fillRecipes(null);
        }
        rebuildChips();
        syncTeleControls();
        loading = false;
        selectType(type);
        refresh();
    }

    /** Run after the user saves; used by callers that edit steps outside the route. */
    public void setOnSaved(Runnable onSaved) { this.onSaved = onSaved; }

    // ------------------------------------------------------------------ layout

    private JPanel buildTypes() {
        JPanel grid = new JPanel(new GridLayout(2, 4, 4, 4));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        ButtonGroup group = new ButtonGroup();
        for (Type t : Type.values()) {
            JToggleButton b = new JToggleButton(t.label, new StepIcon(t.icon, 20, false));
            b.setVerticalTextPosition(SwingConstants.BOTTOM);
            b.setHorizontalTextPosition(SwingConstants.CENTER);
            b.setIconTextGap(4);
            b.setFocusPainted(false);
            b.setFont(FontManager.getRunescapeSmallFont());
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(e -> selectType(t));
            group.add(b);
            typeButtons.put(t, b);
            grid.add(b);
        }
        return grid;
    }

    private JPanel buildWhere() {
        JPanel p = section();
        p.add(label("Where"));
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        tileField.setToolTipText("x, y (and plane if not the ground floor). Blank = anywhere.");
        row.add(tileField, BorderLayout.CENTER);
        JButton mine = button("My tile");
        mine.setToolTipText("Use the tile you're standing on");
        mine.addActionListener(e -> {
            WorldPoint wp = plugin.getLastPlayerLocation();
            if (wp != null) tileField.setText(formatTile(wp));
        });
        row.add(mine, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(row);
        p.add(Box.createVerticalStrut(5));

        JPanel areaRow = new JPanel(new BorderLayout(6, 0));
        areaRow.setOpaque(false);
        areaRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        areaField.setHint("Area (optional)");
        areaField.setToolTipText("<html>Arrive anywhere in this area instead of next to the tile.<br>"
            + "Paint it with Draw area, or type two corners, like 3200, 3200 to 3245, 3235. Blank = just the tile.</html>");
        areaRow.add(areaField, BorderLayout.CENTER);
        JButton draw = button("Draw area");
        draw.setToolTipText("Mark the area in game: drag to add boxes of tiles, hold Shift to paint or erase single tiles");
        draw.addActionListener(e -> drawArea());
        areaRow.add(draw, BorderLayout.EAST);
        areaRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, areaRow.getPreferredSize().height));
        areaRow.setVisible(areasOn);
        p.add(areaRow);
        areaHighlightOnly.setToolTipText("<html>NPCs, objects and ground items light up only inside the area.<br>"
            + "Off: anything in range lights up, as without an area.</html>");
        p.add(areaHighlightOnly);
        p.add(Box.createVerticalStrut(3));
        p.add(placeLabel);
        listen(tileField);
        listen(areaField);
        return p;
    }

    /** Hide while the area is painted in game (starting from the one in the field), then come back with it. */
    private void drawArea() {
        setVisible(false);
        plugin.getAreaDrawer().start(parseArea(areaField.getText()), result -> {
            if (result != null) setArea(result.area);
            TileArea area = result != null ? result.area : null;
            if (area != null) {
                // The route line heads for the tile, so keep it inside the area
                WorldPoint wp = parseTile(tileField.getText());
                if (wp == null || !area.contains(wp)) tileField.setText(formatTile(area.centre()));
            }
            setVisible(true);
            toFront();
        });
    }

    private JPanel buildWho() {
        JPanel p = section();
        p.add(whoLabel);
        whoField.setHint("NPC name, e.g. Hans");
        listen(whoField);
        killRow = new JPanel(new BorderLayout(6, 0));
        killRow.setOpaque(false);
        JLabel how = small("How many");
        killCount.setColumns(4);
        killRow.add(how, BorderLayout.WEST);
        killRow.add(killCount, BorderLayout.CENTER);
        listen(killCount);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(whoField, BorderLayout.CENTER);
        row.add(killRow, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(row);
        return p;
    }

    private JPanel buildChat() {
        JPanel p = section();
        p.add(label("Chat options, in order"));
        chatChips.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(chatChips);
        p.add(Box.createVerticalStrut(2));
        p.add(small("Click an option to remove it. Leave empty if you just talk."));
        return p;
    }

    private void rebuildChips() {
        chatChips.removeAll();
        for (int i = 0; i < chatPicks.size(); i++) {
            final int idx = i;
            int n = chatPicks.get(i);
            JLabel chip = new JLabel(n == 0 ? "?" : String.valueOf(n));
            chip.setOpaque(true);
            chip.setBackground(new Color(20, 20, 20));
            chip.setForeground(ORANGE);
            chip.setFont(FontManager.getRunescapeBoldFont());
            chip.setBorder(new CompoundBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)), new EmptyBorder(2, 8, 2, 8)));
            chip.setToolTipText(n == 0 ? "Recorded but not readable: any option. Click to remove." : "Option " + n + ". Click to remove.");
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chip.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { chatPicks.remove(idx); rebuildChips(); refresh(); }
            });
            chatChips.add(chip);
            if (i < chatPicks.size() - 1) chatChips.add(small("then"));
        }
        JButton add = button("+ Option");
        add.setToolTipText("The number of the line to pick in the chat menu (1 = top)");
        add.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            for (int n = 1; n <= 5; n++) {
                final int pick = n;
                JMenuItem mi = new JMenuItem("Option " + n);
                mi.addActionListener(ev -> { chatPicks.add(pick); rebuildChips(); refresh(); });
                menu.add(mi);
            }
            menu.show(add, 0, add.getHeight());
        });
        chatChips.add(add);
        chatChips.revalidate();
        chatChips.repaint();
    }

    private JPanel buildItems() {
        JPanel p = section();
        JPanel head = new JPanel(new BorderLayout(6, 0));
        head.setOpaque(false);
        head.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(label("Items"), BorderLayout.WEST);
        head.add(itemMode, BorderLayout.EAST);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        itemMode.addActionListener(e -> { itemRows.setVisible(itemModeKey() != ItemMode.DEPOSIT_ALL); refresh(); });
        p.add(head);
        p.add(Box.createVerticalStrut(5));
        itemRows.setOpaque(false);
        itemRows.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(itemRows);
        JButton add = button("+ Item");
        add.addActionListener(e -> { addItemRow("", "1"); refresh(); });
        p.add(Box.createVerticalStrut(4));
        p.add(add);
        p.add(Box.createVerticalStrut(2));
        p.add(small("Several names in one box, split by /, means any one of them."));
        return p;
    }

    private void addItemRow(String name, String qty) {
        ItemRow r = new ItemRow(name, qty);
        items.add(r);
        itemRows.add(r.panel);
        itemRows.revalidate();
    }

    private JPanel buildSkill() {
        JPanel p = section();
        JPanel row = new JPanel(new GridLayout(2, 3, 6, 2));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(small("Skill"));
        row.add(small("Goal"));
        row.add(small("Amount (e.g. 50, 10k, 1m)"));
        row.add(skillBox);
        row.add(goalBox);
        row.add(goalValue);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(label("Skill goal"));
        p.add(row);
        p.add(Box.createVerticalStrut(5));
        p.add(labeled("Method (optional: sets what gets highlighted)", recipeBox));
        skillBox.addActionListener(e -> { fillRecipes(null); refresh(); });
        goalBox.addActionListener(e -> refresh());
        listen(goalValue);
        return p;
    }

    private void fillRecipes(String select) {
        String skill = (String) skillBox.getSelectedItem();
        recipeBox.setModel(new DefaultComboBoxModel<>(RecipeLabels.labelsFor(skill)));
        if (select != null) recipeBox.setSelectedItem(select);
        recipeBox.setEnabled(recipeBox.getItemCount() > 1);
    }

    private JPanel buildQuest() {
        JPanel p = section();
        p.add(label("Quest"));
        questSearch.setHint("Type to search quests");
        questSearch.getDocument().addDocumentListener(new Doc(() -> filterQuests(questSearch.getText())));
        p.add(questSearch);
        p.add(Box.createVerticalStrut(4));
        filterQuests("");
        questBox.addActionListener(e -> refresh());
        p.add(questBox);
        p.add(Box.createVerticalStrut(4));
        questComplete.addActionListener(e -> refresh());
        p.add(questComplete);
        return p;
    }

    private void filterQuests(String q) {
        Object keep = questBox.getSelectedItem();
        String needle = q == null ? "" : q.trim().toLowerCase();
        List<String> names = new ArrayList<>();
        for (Quest quest : Quest.values()) {
            if (needle.isEmpty() || quest.getName().toLowerCase().contains(needle)) names.add(quest.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        questBox.setModel(new DefaultComboBoxModel<>(names.toArray(new String[0])));
        if (keep != null && names.contains(keep.toString())) questBox.setSelectedItem(keep);
        if (!loading) refresh();
    }

    private JPanel buildHighlight() {
        JPanel p = section();
        p.add(label("Highlight"));
        JPanel row = new JPanel(new GridLayout(2, 2, 6, 2));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(small("NPC"));
        row.add(small("Object"));
        hlNpc.setHint("e.g. Fishing spot");
        hlObject.setHint("e.g. Oak tree");
        row.add(hlNpc);
        row.add(hlObject);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(row);
        hlInventory.setHint("e.g. Cocktail shaker, Lemon");
        p.add(labeled("Inventory items", hlInventory));
        listen(hlNpc);
        listen(hlObject);
        listen(hlInventory);
        return p;
    }

    private static final String[] TELE_HOW = {"Spell", "Item", "Portal Nexus", "House portal"};
    private static final String[] TELE_HOW_KEYS = {"SPELL", "ITEM", "NEXUS", "PORTAL"};
    private static final String NEXUS_HINT = "Destination as named in your nexus, e.g. Varrock";
    private static final String PORTAL_HINT = "Portal's destination, e.g. Varrock for Varrock Portal";
    private final JComboBox<String> teleHow = combo(TELE_HOW);
    private final JComboBox<Object> spellBox = new JComboBox<>();
    private final JComboBox<Object> itemBox = new JComboBox<>();
    private final HintField nexusField = new HintField();
    private final JPanel teleChoice = new JPanel(new CardLayout());

    private JPanel buildTeleport() {
        JPanel p = section();
        p.add(label("Teleport"));

        JPanel top = new JPanel(new BorderLayout(6, 0));
        top.setOpaque(false);
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(small("Using"), BorderLayout.WEST);
        top.add(teleHow, BorderLayout.CENTER);
        JButton remove = button("Remove");
        remove.setToolTipText("Walk there instead");
        remove.addActionListener(e -> {
            teleMethod = "WALK"; chosenSpell = null; chosenItem = null; chosenNexus = null;
            teleTouched = true;
            extraTele = false;
            refresh();
        });
        top.add(remove, BorderLayout.EAST);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, top.getPreferredSize().height));
        p.add(top);
        p.add(Box.createVerticalStrut(6));

        spellBox.addItem("Choose a spell...");
        for (TeleportSpell sp : TeleportSpells.ALL) spellBox.addItem(sp);
        spellBox.setMaximumRowCount(14);
        spellBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                if (v instanceof TeleportSpell) {
                    TeleportSpell sp = (TeleportSpell) v;
                    setText(sp.getName());
                    setIcon(TeleportPickers.spellIcon(plugin, sp, () -> { spellBox.repaint(); l.repaint(); }));
                } else {
                    setIcon(null);
                }
                return this;
            }
        });
        spellBox.addActionListener(e -> {
            if (loading) return;
            Object v = spellBox.getSelectedItem();
            chosenSpell = v instanceof TeleportSpell ? (TeleportSpell) v : null;
            teleMethod = "SPELL";
            teleportChosen(chosenSpell != null ? chosenSpell.getDestination() : null);
        });

        itemBox.addItem("Choose an item...");
        for (TeleportItem it : TeleportItems.ALL) itemBox.addItem(it);
        itemBox.setMaximumRowCount(14);
        itemBox.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                if (v instanceof TeleportItem) {
                    TeleportItem it = (TeleportItem) v;
                    setText(it.getDisplayName());
                    setIcon(TeleportPickers.itemIcon(plugin, it));
                } else {
                    setIcon(null);
                }
                return this;
            }
        });
        itemBox.addActionListener(e -> {
            if (loading) return;
            Object v = itemBox.getSelectedItem();
            chosenItem = v instanceof TeleportItem ? (TeleportItem) v : null;
            teleMethod = "ITEM";
            teleportChosen(chosenItem != null ? chosenItem.getDestination() : null);
        });

        // The nexus and the house portals share this field: both are named by destination.
        nexusField.setHint(NEXUS_HINT);
        nexusField.getDocument().addDocumentListener(new Doc(() -> {
            if (loading) return;
            String t = nexusField.getText().trim();
            chosenNexus = t.isEmpty() ? null : t;
            teleMethod = TELE_HOW_KEYS[Math.max(0, teleHow.getSelectedIndex())];
            teleportChosen(null);
        }));

        for (JComponent c : new JComponent[]{spellBox, itemBox}) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        teleChoice.setOpaque(false);
        teleChoice.setAlignmentX(Component.LEFT_ALIGNMENT);
        teleChoice.add(spellBox, "SPELL");
        teleChoice.add(itemBox, "ITEM");
        teleChoice.add(nexusField, "NEXUS");
        teleChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        p.add(teleChoice);

        teleHow.addActionListener(e -> {
            String key = TELE_HOW_KEYS[Math.max(0, teleHow.getSelectedIndex())];
            showTeleCard(key);
            if (loading) return;
            teleMethod = key;
            teleTouched = true;
            refresh();
        });
        return p;
    }

    /** Point the teleport dropdowns at what was loaded from the step. */
    private void syncTeleControls() {
        int how = Math.max(0, Arrays.asList(TELE_HOW_KEYS).indexOf(teleMethod));
        teleHow.setSelectedIndex(how);
        showTeleCard(TELE_HOW_KEYS[how]);
        if (chosenSpell != null) spellBox.setSelectedItem(chosenSpell);
        if (chosenItem != null) itemBox.setSelectedItem(chosenItem);
        if (chosenNexus != null) nexusField.setText(chosenNexus);
    }

    private void showTeleCard(String key) {
        boolean portal = "PORTAL".equals(key);
        nexusField.setHint(portal ? PORTAL_HINT : NEXUS_HINT);
        ((CardLayout) teleChoice.getLayout()).show(teleChoice, portal ? "NEXUS" : key);
    }

    /** A teleport was picked: aim the step at where it lands (the house has no tile to aim at). */
    private void teleportChosen(WorldPoint dest) {
        teleTouched = true;
        if (dest != null) {
            tileField.setText(TeleportItems.isHouse(dest) ? "" : formatTile(dest));
        }
        refresh();
    }

    private boolean teleOn() { return extraTele; }

    private boolean hasTeleport() {
        return ("SPELL".equals(teleMethod) && chosenSpell != null) || ("ITEM".equals(teleMethod) && chosenItem != null)
            || (("NEXUS".equals(teleMethod) || "PORTAL".equals(teleMethod)) && chosenNexus != null);
    }

    private JPanel buildStairs() {
        JPanel p = section();
        p.add(label("Stairs or ladder"));
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(stairsField, BorderLayout.CENTER);
        JButton mine = button("My tile");
        mine.addActionListener(e -> {
            WorldPoint wp = plugin.getLastPlayerLocation();
            if (wp != null) stairsField.setText(formatTile(wp));
        });
        row.add(mine, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(row);
        p.add(Box.createVerticalStrut(3));
        p.add(small("<html>When the step is on another floor: the tile by the stairs. The route goes there first, then on.</html>"));
        listen(stairsField);
        return p;
    }

    private JPanel buildBoat() {
        JPanel p = section();
        p.add(label("Board a boat at"));
        List<String> names = new ArrayList<>();
        for (SailingDocks.Dock d : plugin.getSailingDocks().getDocks()) names.add(d.name);
        Collections.sort(names);
        for (String n : names) boatDock.addItem(n);
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(boatDock, BorderLayout.CENTER);
        JButton nearest = button("Nearest");
        nearest.addActionListener(e -> {
            WorldPoint wp = plugin.getLastPlayerLocation();
            SailingDocks.Dock d = wp == null ? null : plugin.getSailingDocks().nearest(wp, Integer.MAX_VALUE);
            if (d != null) boatDock.setSelectedItem(d.name);
        });
        JButton remove = button("Remove");
        remove.setToolTipText("No boat on this step");
        remove.addActionListener(e -> { extraBoat = false; refresh(); });
        JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
        buttons.setOpaque(false);
        buttons.add(nearest);
        buttons.add(remove);
        row.add(buttons, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        p.add(row);
        p.add(Box.createVerticalStrut(3));
        p.add(small("<html>The route goes to this dock's gangplank first and tells you to board. Once you're on the boat it"
            + " carries on to the tile over the sea. With no tile, boarding is the whole step.</html>"));
        return p;
    }

    /** The dock picked in the Boat section, or null. */
    private SailingDocks.Dock chosenDock() {
        Object name = boatDock.getSelectedItem();
        for (SailingDocks.Dock d : plugin.getSailingDocks().getDocks()) {
            if (d.name.equals(name)) return d;
        }
        return null;
    }

    private JPanel buildNote() {
        JPanel p = section();
        p.add(label("Note (optional)"));
        noteArea.setLineWrap(true);
        noteArea.setWrapStyleWord(true);
        noteArea.setBackground(FIELD_BG);
        noteArea.setForeground(Color.WHITE);
        noteArea.setCaretColor(Color.WHITE);
        noteArea.setBorder(new EmptyBorder(4, 6, 4, 6));
        JScrollPane sp = new JScrollPane(noteArea);
        sp.setBorder(BorderFactory.createLineBorder(new Color(17, 17, 17)));
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        p.add(sp);
        p.add(Box.createVerticalStrut(4));
        noteAuto.setToolTipText("Off: the step waits until you tick it off yourself");
        noteAuto.addActionListener(e -> refresh());
        p.add(noteAuto);
        noteArea.getDocument().addDocumentListener(new Doc(this::refresh));
        return p;
    }

    private JPanel highlightRow() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        highlight.setToolTipText("Light up this step's NPCs and objects in the game");
        p.add(highlight, BorderLayout.WEST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private JPanel buildAddMore() {
        JPanel p = vbox();
        p.setOpaque(false);
        p.add(label("Add more"));
        p.add(Box.createVerticalStrut(4));
        addMoreRow = new JPanel(new GridLayout(0, 3, 4, 4));
        addMoreRow.setOpaque(false);
        addMoreRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        addItemsBtn = button("+ Items");
        addItemsBtn.addActionListener(e -> { extraItems = true; if (items.isEmpty()) addItemRow("", "1"); refresh(); });
        addSkillBtn = button("+ Skill goal");
        addSkillBtn.addActionListener(e -> { extraSkill = true; refresh(); });
        addQuestBtn = button("+ Quest");
        addQuestBtn.addActionListener(e -> { extraQuest = true; refresh(); });
        addHighlightBtn = button("+ Highlight");
        addHighlightBtn.addActionListener(e -> { extraHighlight = true; refresh(); });
        addStairsBtn = button("+ Stairs");
        addStairsBtn.addActionListener(e -> { extraStairs = true; refresh(); });
        addBoatBtn = button("+ Boat");
        addBoatBtn.setToolTipText("Board a boat at a dock first");
        addBoatBtn.addActionListener(e -> { extraBoat = true; refresh(); });
        addTeleBtn = button("+ Teleport");
        addTeleBtn.setToolTipText("Get there with a spell, an item, the Portal Nexus or a house portal");
        addTeleBtn.addActionListener(e -> { extraTele = true; refresh(); });
        p.add(addMoreRow);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JPanel buildFooter() {
        JPanel f = vbox();
        f.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(26, 26, 26)), new EmptyBorder(8, 10, 8, 10)));
        f.setBackground(new Color(36, 36, 36));

        doneBox.setBorder(new EmptyBorder(7, 9, 7, 9));
        doneBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        doneLabel.setFont(FontManager.getRunescapeSmallFont());
        doneLabel.setEditable(false);
        doneLabel.setFocusable(false);
        doneLabel.setLineWrap(true);
        doneLabel.setWrapStyleWord(true);
        doneLabel.setOpaque(false);
        doneLabel.setBorder(null);
        doneBox.add(doneLabel, BorderLayout.CENTER);
        f.add(doneBox);

        errorLabel.setForeground(ERROR);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.add(Box.createVerticalStrut(4));
        f.add(errorLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton cancel = button("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = button("Save step");
        save.setBackground(ORANGE);
        save.setForeground(new Color(27, 18, 0));
        save.addActionListener(e -> onSave());
        buttons.add(cancel);
        buttons.add(save);
        f.add(buttons);
        getRootPane().setDefaultButton(save);
        return f;
    }

    // ------------------------------------------------------------------ state

    private void selectType(Type t) {
        type = t;
        JToggleButton b = typeButtons.get(t);
        if (b != null && !b.isSelected()) b.setSelected(true);
        if (t == Type.ITEMS && items.isEmpty()) addItemRow("", "1");
        if (t == Type.KILL && killCount.getText().trim().isEmpty()) killCount.setText("1");
        refresh();
    }

    private boolean itemsOn() { return type == Type.ITEMS || extraItems; }
    private boolean skillOn() { return type == Type.SKILL || extraSkill; }
    private boolean questOn() { return type == Type.QUEST || extraQuest; }
    private boolean chatOn() { return type == Type.TALK || extraChat; }

    /** Show the right sections, restyle the type tiles, and update the suggested name and summary. */
    private void refresh() {
        if (loading) return;
        for (Map.Entry<Type, JToggleButton> e : typeButtons.entrySet()) {
            boolean on = e.getKey() == type;
            JToggleButton b = e.getValue();
            b.setBackground(on ? new Color(58, 44, 18) : SECTION_BG);
            b.setForeground(on ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
            b.setBorder(BorderFactory.createLineBorder(on ? ORANGE : new Color(26, 26, 26)));
        }
        whoSec.setVisible(type == Type.TALK || type == Type.KILL);
        whoLabel.setText(type == Type.KILL ? "NPC to kill" : "Who");
        killRow.setVisible(type == Type.KILL);
        chatSec.setVisible(chatOn());
        itemsSec.setVisible(itemsOn());
        skillSec.setVisible(skillOn());
        questSec.setVisible(questOn());
        highlightSec.setVisible(extraHighlight);
        stairsSec.setVisible(extraStairs);
        boatSec.setVisible(extraBoat);
        teleSec.setVisible(teleOn());
        noteAuto.setVisible(type == Type.NOTE || type == Type.WALK || !noteArea.getText().trim().isEmpty());
        highlight.setVisible(type == Type.TALK || type == Type.KILL || extraHighlight || skillOn() || itemsOn());

        for (Map.Entry<JPanel, Component> g : gaps.entrySet()) g.getValue().setVisible(g.getKey().isVisible());
        // Only the extras not already on the step, packed into a grid (a flow row won't wrap here)
        addMoreRow.removeAll();
        if (!itemsOn()) addMoreRow.add(addItemsBtn);
        if (!skillOn()) addMoreRow.add(addSkillBtn);
        if (!questOn()) addMoreRow.add(addQuestBtn);
        if (!extraHighlight) addMoreRow.add(addHighlightBtn);
        if (!extraStairs) addMoreRow.add(addStairsBtn);
        if (!extraBoat) addMoreRow.add(addBoatBtn);
        if (!teleOn()) addMoreRow.add(addTeleBtn);
        addMoreRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, addMoreRow.getPreferredSize().height));

        WorldPoint wp = parseTile(tileField.getText());
        boolean tileBad = wp == null && !tileField.getText().trim().isEmpty();
        TileArea area = areasOn ? parseArea(areaField.getText()) : null;
        boolean areaBad = areasOn && area == null && !areaField.getText().trim().isEmpty();
        if (wp == null && area != null) wp = area.centre();
        areaHighlightOnly.setVisible(area != null);
        String place = wp != null ? LocationNames.describe(wp) : "Anywhere";
        if (area != null) place += area.isRectangle()
            ? " (area " + area.bounds().getWidth() + " x " + area.bounds().getHeight() + " tiles)"
            : " (area of " + area.size() + " tiles)";
        placeLabel.setText(tileBad ? "Use x, y or x, y, plane"
            : areaBad ? "Area: use two corners, like 3200, 3200 to 3245, 3235" : place);
        placeLabel.setForeground(tileBad || areaBad ? ERROR : ColorScheme.LIGHT_GRAY_COLOR);
        nameField.setHint(autoName(wp));
        updateDone(wp);
        errorLabel.setText(" ");
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    /** The "Completes when..." line, from the same rules playback uses. */
    private void updateDone(WorldPoint wp) {
        List<String> parts = new ArrayList<>();
        String place = wp != null ? LocationNames.describe(wp) : null;
        boolean hasItems = itemsOn() && (itemModeKey() == ItemMode.DEPOSIT_ALL || !itemListText().isEmpty());
        boolean hasSkill = skillOn() && parseAmount(goalValue.getText()) > 0;
        int kills = type == Type.KILL ? parseInt(killCount.getText()) : 0;
        boolean hasChat = chatOn() && !chatPicks.isEmpty();
        boolean hasQuest = questOn() && questComplete.isSelected() && questBox.getSelectedItem() != null;
        boolean hasNote = !noteArea.getText().trim().isEmpty();

        boolean hasArea = areasOn && parseArea(areaField.getText()) != null;
        if (place != null) parts.add(hasArea ? "you're anywhere in the area at " + place : "you're at " + place);
        if (hasItems) parts.add(itemsPhrase());
        if (hasSkill) parts.add(skillPhrase());
        if (kills > 0) parts.add("you've killed " + kills + " " + orDefault(whoField.getText(), "of the NPC"));
        if (hasChat) parts.add("you've picked chat options " + chatText(" then "));
        if (hasQuest) parts.add(questBox.getSelectedItem() + " is finished");

        boolean teleportArrival = teleOn() && hasTeleport() && wp == null;
        if (teleportArrival) parts.add("you've arrived by teleport");
        boolean triggers = hasItems || hasSkill || kills > 0 || hasChat || hasQuest || teleportArrival;
        boolean manual = !triggers && (wp == null || (hasNote && !noteAuto.isSelected()));
        String html;
        if (manual) {
            String why = wp != null ? " (Complete on arrival is off)" : "";
            html = "You'll tick this off yourself" + why + ": Done on the step card, or right-click, Mark Complete.";
        } else {
            html = "Completes when " + joinAnd(parts) + ".";
        }
        doneLabel.setText(html);
        doneBox.setBackground(manual ? MANUAL_BG : DONE_BG);
        doneLabel.setForeground(manual ? MANUAL_FG : DONE_FG);
        doneBox.setBorder(new CompoundBorder(BorderFactory.createLineBorder(manual ? MANUAL_EDGE : DONE_EDGE), new EmptyBorder(6, 8, 6, 8)));
    }

    private String itemsPhrase() {
        ItemMode mode = itemModeKey();
        if (mode == ItemMode.DEPOSIT_ALL) return "you've deposited everything in the bank";
        String list = itemsSummary(3);
        if (mode == ItemMode.SHOP) return "you've bought " + list;
        if (mode == ItemMode.SELL) return "you've sold " + list;
        if (mode == ItemMode.PICKUP) return "you've picked up " + list;
        return "you have " + list + " in your inventory";
    }

    private String skillPhrase() {
        String skill = (String) skillBox.getSelectedItem();
        long v = parseAmount(goalValue.getText());
        SkillGoalType goal = GOAL_KEYS[goalBox.getSelectedIndex()];
        if (goal == SkillGoalType.LEVEL) return "you reach " + skill + " level " + v;
        if (goal == SkillGoalType.XP_TARGET) return "you reach " + String.format("%,d", v) + " " + skill + " XP";
        return "you've gained " + String.format("%,d", v) + " " + skill + " XP";
    }

    private String autoName(WorldPoint wp) {
        String at = LocationNames.at(wp);
        // Plain ifs rather than an enum switch: a switch compiles to a numbered helper class that
        // breaks if the dev client loads a half-old build (NoSuchFieldError on $SwitchMap$).
        if (type == Type.ITEMS) {
            ItemMode mode = itemModeKey();
            if (mode == ItemMode.DEPOSIT_ALL) return "Deposit everything" + at;
            String list = itemsSummary(1);
            if (list.isEmpty()) return "Get items" + at;
            String verb = mode == ItemMode.SHOP ? "Buy " : mode == ItemMode.SELL ? "Sell " : mode == ItemMode.PICKUP ? "Pick up " : "Get ";
            return verb + list + at;
        }
        if (type == Type.TALK) return "Talk to " + orDefault(whoField.getText(), "someone") + at;
        if (type == Type.KILL) return "Kill " + Math.max(1, parseInt(killCount.getText())) + " " + orDefault(whoField.getText(), "NPC");
        if (type == Type.SKILL) return "Train " + skillBox.getSelectedItem() + at;
        if (type == Type.QUEST) return questBox.getSelectedItem() != null ? "Finish " + questBox.getSelectedItem() : "Quest";
        if (type == Type.NOTE) return "Note" + LocationNames.at(wp);
        return wp == null ? "Go somewhere" : LocationNames.nameOf(wp) != null ? "Go to " + LocationNames.nameOf(wp) : "Go to this spot";
    }

    // ------------------------------------------------------------------ load / save

    private void load(RouteStep s) {
        if (s.getName() != null) nameField.setText(s.getName());
        if (s.getWorldPoint() != null) tileField.setText(formatTile(s.getWorldPoint()));
        if (s.hasArea()) {
            setArea(s.area());
            areaHighlightOnly.setSelected(s.isHighlightInAreaOnly());
        }
        if (s.getTeleportMethod() != null) {
            extraTele = true;
            if (s.getTeleportSpell() != null && !s.getTeleportSpell().isEmpty()) {
                chosenSpell = TeleportSpells.getByName(s.getTeleportSpell());
                if (chosenSpell != null) teleMethod = "SPELL";
            } else if (s.getTeleportItem() != null && !s.getTeleportItem().isEmpty()) {
                chosenItem = TeleportItems.getByDisplayName(s.getTeleportItem());
                if (chosenItem != null) teleMethod = "ITEM";
            } else if (("NEXUS".equals(s.getTeleportMethod()) || "PORTAL".equals(s.getTeleportMethod()))
                    && s.getTeleportNexus() != null) {
                teleMethod = s.getTeleportMethod();
                chosenNexus = s.getTeleportNexus();
            }
        }

        StepIcon.Kind kind = StepIcon.kindOf(s);
        boolean talked = (s.getDialogOptions() != null && !s.getDialogOptions().trim().isEmpty())
            || (s.getName() != null && s.getName().startsWith("Talk to"));
        // The recorder puts a finished quest on the conversation it ended in: that's a Talk step.
        if (kind == StepIcon.Kind.QUEST && talked && s.getNpcKillCount() <= 0) kind = StepIcon.Kind.TALK;
        if (kind == StepIcon.Kind.QUEST) type = Type.QUEST;
        else if (kind == StepIcon.Kind.KILL) type = Type.KILL;
        else if (kind == StepIcon.Kind.TALK) type = Type.TALK;
        else if (kind == StepIcon.Kind.BUY || kind == StepIcon.Kind.SELL || kind == StepIcon.Kind.GATHER
            || kind == StepIcon.Kind.PICKUP || kind == StepIcon.Kind.BANK) type = Type.ITEMS;
        else if (kind == StepIcon.Kind.AGILITY || kind == StepIcon.Kind.SKILL) type = Type.SKILL;
        else if (kind == StepIcon.Kind.NOTE) type = Type.NOTE;
        else type = Type.WALK;

        if (type == Type.KILL) {
            whoField.setText(nz(s.getNpcHighlight()));
            killCount.setText(String.valueOf(s.getNpcKillCount()));
        } else if (type == Type.TALK) {
            whoField.setText(s.getSkillingTargetNpc() != null ? s.getSkillingTargetNpc() : nz(s.getNpcHighlight()));
        }
        if (s.getDialogOptions() != null && !s.getDialogOptions().trim().isEmpty()) {
            extraChat = type != Type.TALK;
            for (String part : s.getDialogOptions().split(",")) {
                try { chatPicks.add(Integer.parseInt(part.trim())); } catch (NumberFormatException ignored) {}
            }
        }
        highlight.setSelected(s.isHighlightEnabled());

        if (s.hasItems()) {
            extraItems = type != Type.ITEMS;
            ItemMode mode = s.getItemMode() != null ? s.getItemMode() : ItemMode.BANK;
            itemMode.setSelectedIndex(Math.max(0, Arrays.asList(ITEM_MODE_KEYS).indexOf(mode)));
            itemRows.setVisible(mode != ItemMode.DEPOSIT_ALL);
            if (s.getItemList() != null) {
                for (String group : s.getItemList().split(",")) {
                    String g = group.trim();
                    if (g.isEmpty()) continue;
                    Matcher m = QTY.matcher(g);
                    if (!g.contains("/") && m.matches()) addItemRow(m.group(2).trim(), m.group(1).trim());
                    else addItemRow(g, "1");
                }
            }
        }
        if (s.hasSkillGoal()) {
            extraSkill = type != Type.SKILL;
            skillBox.setSelectedItem(cap(s.getSkillingSkill()));
            int gi = Arrays.asList(GOAL_KEYS).indexOf(s.getSkillingGoalType());
            goalBox.setSelectedIndex(Math.max(0, gi));
            goalValue.setText(s.getSkillingGoalValue() > 0 ? String.valueOf(s.getSkillingGoalValue()) : "");
            fillRecipes(s.getRecipeLabel());
        } else {
            fillRecipes(null);
        }
        if (s.hasQuest()) {
            extraQuest = type != Type.QUEST;
            try { questBox.setSelectedItem(Quest.valueOf(s.getQuestName()).getName()); } catch (IllegalArgumentException ignored) {}
            questComplete.setSelected(s.isQuestComplete());
        }
        // Targets the Talk/Kill fields don't already cover (a fishing spot, a tree, a stall).
        String otherNpc = type == Type.TALK ? null : s.getSkillingTargetNpc();
        boolean recipeTargets = s.getRecipeLabel() != null;
        if ((otherNpc != null || s.getSkillingTargetObject() != null) && !recipeTargets) {
            extraHighlight = true;
            hlNpc.setText(nz(otherNpc));
            hlObject.setText(nz(s.getSkillingTargetObject()));
        }
        if (s.getInventoryHighlightItems() != null && !s.getInventoryHighlightItems().trim().isEmpty()) {
            extraHighlight = true;
            hlInventory.setText(s.getInventoryHighlightItems());
        }
        if (s.getTransitionPoint() != null) {
            extraStairs = true;
            stairsField.setText(formatTile(s.getTransitionPoint()));
        }
        if (s.hasBoarding()) {
            extraBoat = true;
            SailingDocks.Dock d = plugin.getSailingDocks().nearest(s.getBoardPoint(), Integer.MAX_VALUE);
            if (d != null) boatDock.setSelectedItem(d.name);
        }
        String note = s.getNoteText();
        // The recorder's "Kill 5 Chicken." note is generated; keep it out of the way.
        if (note != null && !(type == Type.KILL && AUTO_KILL_NOTE.matcher(note.trim()).matches())) noteArea.setText(note);
        noteAuto.setSelected(s.isNoteAutoComplete() || !s.hasNote());
        rebuildChips();
    }

    private void onSave() {
        String name = nameField.getText().trim();
        String err = check("Step name", name);
        if (err == null) err = check("Note", noteArea.getText());
        if (err == null) err = check("NPC", whoField.getText());
        if (err == null) err = check("Highlight NPC", hlNpc.getText());
        if (err == null) err = check("Highlight object", hlObject.getText());
        if (err == null) err = check("Inventory items", hlInventory.getText());
        if (err == null && itemsOn()) err = check("Items", itemListText());
        if (err == null && teleOn()) err = check("Teleport destination", nexusField.getText());
        if (err != null) { errorLabel.setText(err); return; }

        WorldPoint wp = parseTile(tileField.getText());
        if (wp == null && !tileField.getText().trim().isEmpty()) { fail(tileField, "The tile should look like 3222, 3218 (add , 1 for upstairs)."); return; }
        TileArea area = areasOn ? parseArea(areaField.getText()) : null;
        if (areasOn && area == null && !areaField.getText().trim().isEmpty()) { fail(areaField, "Paint the area with Draw area, or type two corners on one floor, like 3200, 3200 to 3245, 3235."); return; }
        if (wp == null && area != null) wp = area.centre();
        boolean teleports = teleOn() && (hasTeleport() || (!teleTouched && editing != null && editing.getTeleportMethod() != null));
        if (teleOn() && !teleports) { errorLabel.setText("Pick a teleport, or press Remove in the Teleport section."); return; }
        if (type == Type.WALK && wp == null && !teleports && !extraBoat) { fail(tileField, "Where should this step go? Enter a tile or press My tile."); return; }
        if ((type == Type.TALK || type == Type.KILL) && whoField.getText().trim().isEmpty()) { fail(whoField, "Enter the NPC's name."); return; }
        if (type == Type.KILL && parseInt(killCount.getText()) <= 0) { fail(killCount, "Enter how many to kill."); return; }
        if (itemsOn() && itemModeKey() != ItemMode.DEPOSIT_ALL && itemListText().isEmpty()) { errorLabel.setText("Add at least one item, or remove the items section by switching type."); return; }
        if (skillOn() && parseAmount(goalValue.getText()) <= 0) { fail(goalValue, "Enter the goal amount, like 50 or 10k."); return; }
        if (questOn() && questBox.getSelectedItem() == null) { fail(questSearch, "Pick a quest from the list."); return; }
        WorldPoint stairs = extraStairs ? parseTile(stairsField.getText()) : null;
        if (extraStairs && stairs == null && !stairsField.getText().trim().isEmpty()) { fail(stairsField, "The stairs tile should look like 3205, 3209."); return; }
        SailingDocks.Dock dock = extraBoat ? chosenDock() : null;
        if (extraBoat && dock == null) { errorLabel.setText("Pick the dock to board at, or press Remove in the Boat section."); return; }
        if (name.isEmpty()) name = wp == null && dock != null ? plugin.getSailingDocks().boardingHint(dock.land) : autoName(wp);

        RouteStep step = editing != null ? editing : new RouteStep(name, StepType.LOCATION, wp, -1);
        step.setName(name);
        step.setWorldPoint(wp);
        if (areasOn) {
            step.setArea(area);
            step.setHighlightInAreaOnly(area != null && areaHighlightOnly.isSelected());
        }
        apply(step, stairs);
        if (editing == null) plugin.addStep(route, step);
        else plugin.saveRoutes();
        plugin.refreshHighlights();
        if (onSaved != null) onSaved.run();
        dispose();
    }

    /** Write the fields this editor shows; leave the rest (teleport, gear reminders...) alone. */
    private void apply(RouteStep s, WorldPoint stairs) {
        // Items
        if (itemsOn()) {
            ItemMode mode = itemModeKey();
            String list = itemListText();
            s.setItemMode(mode);
            s.setItemList(mode == ItemMode.DEPOSIT_ALL || list.isEmpty() ? null : list);
        } else {
            s.setItemList(null);
            s.setItemMode(ItemMode.BANK);
        }

        // Skill goal (its method sets the highlight targets, so it goes before them)
        if (skillOn()) {
            String skill = (String) skillBox.getSelectedItem();
            SkillGoalType goal = GOAL_KEYS[goalBox.getSelectedIndex()];
            s.setSkillingSkill(skill.toUpperCase());
            s.setSkillingGoalType(goal);
            s.setSkillingGoalValue(parseAmount(goalValue.getText()));
            Object recipe = recipeBox.getSelectedItem();
            RecipeLabels.apply(s, skill, recipe == null ? null : recipe.toString());
        } else {
            s.setSkillingSkill(null);
            s.setSkillingGoalType(null);
            s.setRecipeLabel(null);
            s.setSkillingTargetNpc(null);
            s.setSkillingTargetObject(null);
            s.setBankHighlightItems(null);
            s.setHerblorePotion(null);
        }

        // Who, kills, chat
        String who = whoField.getText().trim();
        if (type == Type.TALK) {
            s.setSkillingTargetNpc(who);
            if (who.equalsIgnoreCase(nz(s.getNpcHighlight()))) s.setNpcHighlight(null);
            s.setNpcKillCount(0);
        } else if (type == Type.KILL) {
            s.setNpcHighlight(who);
            s.setNpcKillCount(parseInt(killCount.getText()));
        } else {
            if (s.getNpcKillCount() > 0) s.setNpcHighlight(null); // was a kill step
            s.setNpcKillCount(0);
        }
        s.setDialogOptions(chatOn() && !chatPicks.isEmpty() ? chatText(",") : null);

        // Extra highlights (typed targets win over a method's)
        if (extraHighlight) {
            String npc = hlNpc.getText().trim(), obj = hlObject.getText().trim();
            if (!npc.isEmpty() && type != Type.TALK) s.setSkillingTargetNpc(npc);
            if (!obj.isEmpty()) s.setSkillingTargetObject(obj);
            String inv = hlInventory.getText().trim();
            s.setInventoryHighlightItems(inv.isEmpty() ? null : inv);
        }
        s.setHighlightEnabled(highlight.isVisible() && highlight.isSelected() && s.hasHighlight());

        // Quest
        if (questOn() && questBox.getSelectedItem() != null) {
            String qName = questBox.getSelectedItem().toString();
            for (Quest q : Quest.values()) if (q.getName().equals(qName)) { s.setQuestName(q.name()); break; }
            s.setQuestComplete(questComplete.isSelected());
        } else {
            s.setQuestName(null);
            s.setQuestComplete(false);
        }

        // Teleport
        if (teleTouched || (editing == null && hasTeleport())) applyTeleport(s);

        // Stairs
        s.setTransitionPoint(stairs);
        if (stairs == null) s.setTransitionObjectId(-1);

        // Boat: the gangplank's object ID is only known from a recording, so keep it while the dock stays
        SailingDocks.Dock dock = extraBoat ? chosenDock() : null;
        boolean sameDock = dock != null && s.getBoardPoint() != null
            && plugin.getSailingDocks().nearest(s.getBoardPoint(), 15) == dock;
        if (!sameDock) {
            s.setBoardPoint(dock == null ? null : dock.land);
            s.setBoardObjectId(-1);
        }

        // Note (kill steps need one for the HUD's kill counter)
        String note = noteArea.getText().trim();
        if (type == Type.KILL && note.isEmpty()) note = "Kill " + s.getNpcKillCount() + " " + who + ".";
        s.setNoteText(note.isEmpty() ? null : note);
        s.setNoteAutoComplete(!note.isEmpty() && noteAuto.isSelected());
    }

    private void applyTeleport(RouteStep step) {
        boolean on = teleOn() && hasTeleport();
        boolean sameNamed = teleMethod.equals(step.getTeleportMethod());
        step.setTeleportMethod(null);
        step.setTeleportSpell(null);
        step.setTeleportItem(null);
        step.setTeleportNexus(null);
        if (!on) { step.setTeleportDestination(null); return; }
        if ("SPELL".equals(teleMethod)) {
            step.setTeleportMethod("SPELL");
            step.setTeleportSpell(chosenSpell.getName());
            step.setTeleportDestination(chosenSpell.getDestination());
        } else if ("ITEM".equals(teleMethod)) {
            step.setTeleportMethod("ITEM");
            step.setTeleportItem(chosenItem.getDisplayName());
            step.setTeleportDestination(chosenItem.getDestination());
        } else {
            step.setTeleportMethod(teleMethod); // NEXUS or PORTAL
            step.setTeleportNexus(chosenNexus);
            // Recorded nexus/portal steps carry the real landing tile; hand-made ones use the step's tile.
            if (!sameNamed || step.getTeleportDestination() == null) step.setTeleportDestination(step.getWorldPoint());
        }
    }

    // ------------------------------------------------------------------ helpers

    private ItemMode itemModeKey() { return ITEM_MODE_KEYS[Math.max(0, itemMode.getSelectedIndex())]; }

    /** The item list in the saved format: "28 Raw shrimps,Small fishing net". */
    private String itemListText() {
        List<String> out = new ArrayList<>();
        for (ItemRow r : items) {
            String n = r.name.getText().trim().replace(",", "");
            if (n.isEmpty()) continue;
            String q = r.qty.getText().trim();
            out.add(q.isEmpty() || q.equals("1") ? n : q + " " + n);
        }
        return String.join(",", out);
    }

    private String itemsSummary(int max) {
        List<String> names = new ArrayList<>();
        for (ItemRow r : items) {
            String n = r.name.getText().trim();
            if (n.isEmpty()) continue;
            String q = r.qty.getText().trim();
            names.add(q.isEmpty() || q.equals("1") ? n : q + " " + n);
        }
        if (names.isEmpty()) return "";
        if (names.size() <= max) return joinAnd(names);
        return String.join(", ", names.subList(0, max)) + " and " + (names.size() - max) + " more";
    }

    private String chatText(String sep) {
        List<String> s = new ArrayList<>();
        for (int n : chatPicks) s.add(String.valueOf(n));
        return String.join(sep, s);
    }

    private void fail(JComponent field, String msg) {
        errorLabel.setText(msg);
        field.requestFocusInWindow();
    }

    private static String check(String what, String text) {
        return text == null || text.trim().isEmpty() ? null : RouteTextValidator.checkField(what, text.trim());
    }

    private static String joinAnd(List<String> parts) {
        if (parts.isEmpty()) return "";
        if (parts.size() == 1) return parts.get(0);
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    private static long parseAmount(String s) {
        if (s == null) return 0;
        String t = s.trim().toLowerCase().replace(",", "");
        if (t.isEmpty()) return 0;
        double mult = 1;
        if (t.endsWith("k")) { mult = 1_000; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("m")) { mult = 1_000_000; t = t.substring(0, t.length() - 1); }
        try { return Math.round(Double.parseDouble(t.trim()) * mult); } catch (NumberFormatException e) { return 0; }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static String orDefault(String s, String dflt) {
        return s == null || s.trim().isEmpty() ? dflt : s.trim();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private static String[] skillNames() {
        List<String> out = new ArrayList<>();
        for (Skill s : Skill.values()) {
            if ("OVERALL".equals(s.name())) continue;
            out.add(s.getName());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out.toArray(new String[0]);
    }

    private static String formatTile(WorldPoint wp) {
        return wp.getPlane() == 0 ? wp.getX() + ", " + wp.getY() : wp.getX() + ", " + wp.getY() + ", " + wp.getPlane();
    }

    /** Put an area in the field: a rectangle as its corners, a painted shape as a short summary. */
    private void setArea(TileArea a) {
        paintedArea = a != null && !a.isRectangle() ? a : null;
        areaField.setText(a == null ? "" : formatArea(a));
    }

    private static String formatArea(TileArea t) {
        WorldArea a = t.bounds();
        if (!t.isRectangle()) {
            String floor = a.getPlane() == 0 ? "" : ", floor " + a.getPlane();
            return "Painted: " + t.size() + " tiles near " + (a.getX() + a.getWidth() / 2) + ", " + (a.getY() + a.getHeight() / 2) + floor;
        }
        return formatTile(new WorldPoint(a.getX(), a.getY(), a.getPlane())) + " to "
            + formatTile(new WorldPoint(a.getX() + a.getWidth() - 1, a.getY() + a.getHeight() - 1, a.getPlane()));
    }

    /**
     * The field's area: the painted shape while its summary is untouched, else two corner tiles on
     * one floor, like "3200, 3200 to 3245, 3235". Null when blank or unreadable.
     */
    private TileArea parseArea(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        if (paintedArea != null && s.trim().equals(formatArea(paintedArea))) return paintedArea;
        String[] corners = AREA_SPLIT.split(s.trim());
        if (corners.length != 2) return null;
        WorldPoint a = parseTile(corners[0]), b = parseTile(corners[1]);
        if (a == null || b == null || a.getPlane() != b.getPlane()) return null;
        return TileArea.of(AreaDrawer.between(a, b));
    }

    private static WorldPoint parseTile(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String[] p = s.trim().split("[,\\s]+");
        try {
            if (p.length == 2) return new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), 0);
            if (p.length == 3) return new WorldPoint(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private void listen(JTextField f) {
        f.getDocument().addDocumentListener(new Doc(this::refresh));
    }

    private static JPanel vbox() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        return p;
    }

    private static JPanel flow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.setOpaque(false);
        return p;
    }

    private static JPanel section() {
        JPanel p = vbox();
        p.setBackground(SECTION_BG);
        p.setBorder(new CompoundBorder(BorderFactory.createLineBorder(new Color(26, 26, 26)), new EmptyBorder(7, 9, 9, 9)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.WHITE);
        l.setFont(FontManager.getRunescapeBoldFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        return l;
    }

    private static JLabel small(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setFont(FontManager.getRunescapeSmallFont());
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JPanel labeled(String text, JComponent c) {
        JPanel p = vbox();
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(small(text));
        p.add(Box.createVerticalStrut(3));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(c);
        return p;
    }

    private static JTextField field() {
        JTextField f = new JTextField();
        styleField(f);
        return f;
    }

    private static void styleField(JTextField f) {
        f.setBackground(FIELD_BG);
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        f.setBorder(new CompoundBorder(BorderFactory.createLineBorder(new Color(17, 17, 17)), new EmptyBorder(4, 6, 4, 6)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static JCheckBox check(String text, boolean on) {
        JCheckBox c = new JCheckBox(text, on);
        c.setOpaque(false);
        c.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        c.setFocusPainted(false);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JComboBox<String> combo(String[] values) {
        JComboBox<String> c = new JComboBox<>(values);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JButton button(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(40, 40, 40));
        b.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        b.setFont(FontManager.getRunescapeBoldFont());
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setMargin(new Insets(4, 9, 4, 9));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        return b;
    }

    /** One item line: icon, name (with suggestions from the game's item list), amount, remove. */
    private final class ItemRow {
        final JPanel panel = new JPanel(new BorderLayout(6, 0));
        final HintField name = new HintField();
        final JTextField qty = field();
        final JLabel icon = new JLabel();
        private final JPopupMenu suggest = new JPopupMenu();
        private final Timer searchTimer;
        private boolean settingText;

        ItemRow(String n, String q) {
            panel.setOpaque(false);
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.setBorder(new EmptyBorder(0, 0, 4, 0));
            icon.setPreferredSize(new Dimension(30, 26));
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            icon.setOpaque(true);
            icon.setBackground(new Color(62, 53, 41));
            icon.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            name.setHint("Item name");
            name.setText(n);
            qty.setText(q);
            qty.setColumns(5);
            qty.setToolTipText("How many (e.g. 28, 1k)");
            JButton remove = button("x");
            remove.setToolTipText("Remove this item");
            remove.addActionListener(e -> {
                items.remove(this);
                itemRows.remove(panel);
                itemRows.revalidate();
                refresh();
            });
            JPanel right = new JPanel(new BorderLayout(4, 0));
            right.setOpaque(false);
            right.add(qty, BorderLayout.CENTER);
            right.add(remove, BorderLayout.EAST);
            panel.add(icon, BorderLayout.WEST);
            panel.add(name, BorderLayout.CENTER);
            panel.add(right, BorderLayout.EAST);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            suggest.setFocusable(false);
            searchTimer = new Timer(250, e -> showSuggestions());
            searchTimer.setRepeats(false);
            name.getDocument().addDocumentListener(new Doc(() -> {
                if (!settingText) searchTimer.restart();
                refresh();
            }));
            listen(qty);
            if (!n.isEmpty()) setIconFor(n);
        }

        private List<ItemPrice> search(String text) {
            try { return plugin.getItemManager().search(text); } catch (RuntimeException e) { return new ArrayList<>(); }
        }

        private void showSuggestions() {
            String text = name.getText().trim();
            suggest.setVisible(false);
            suggest.removeAll();
            if (text.length() < 2 || text.contains("/") || !name.isShowing()) return;
            List<ItemPrice> found = search(text);
            int shown = 0;
            for (ItemPrice p : found) {
                if (p.getName().equalsIgnoreCase(text)) { setIcon(p.getId()); continue; }
                JMenuItem mi = new JMenuItem(p.getName());
                mi.addActionListener(e -> {
                    settingText = true;
                    name.setText(p.getName());
                    settingText = false;
                    setIcon(p.getId());
                    refresh();
                });
                suggest.add(mi);
                if (++shown >= 8) break;
            }
            if (shown > 0) suggest.show(name, 0, name.getHeight());
        }

        private void setIconFor(String n) {
            for (ItemPrice p : search(n)) {
                if (p.getName().equalsIgnoreCase(n.trim())) { setIcon(p.getId()); return; }
            }
        }

        private void setIcon(int id) {
            AsyncBufferedImage img = plugin.getItemManager().getImage(id);
            img.addTo(icon);
        }
    }

    /** A text field that shows grey hint text while empty. */
    private static final class HintField extends JTextField {
        private String hint = "";

        HintField() { styleField(this); }

        void setHint(String hint) {
            this.hint = hint == null ? "" : hint;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!getText().isEmpty() || hint.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(124, 124, 124));
            g2.setFont(getFont());
            Insets in = getInsets();
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(hint, in.left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** The form scrolls up and down only, always as wide as the window. */
    private static final class ScrollTrack extends JPanel implements Scrollable {
        ScrollTrack() {
            super(new BorderLayout());
            setBackground(ColorScheme.DARK_GRAY_COLOR);
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static final class Doc implements DocumentListener {
        private final Runnable r;
        Doc(Runnable r) { this.r = r; }
        @Override public void insertUpdate(DocumentEvent e) { r.run(); }
        @Override public void removeUpdate(DocumentEvent e) { r.run(); }
        @Override public void changedUpdate(DocumentEvent e) { r.run(); }
    }
}
