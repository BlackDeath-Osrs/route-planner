package com.routeplanner;

import com.routeplanner.util.RouteTextValidator;

import com.routeplanner.model.Route;
import com.routeplanner.model.RouteStep;
import com.routeplanner.model.StepType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Non-modal step editor window. The game stays fully interactive while this is open
 * (deliberate: a player in a live/dangerous situation must never be locked out).
 *
 * Phase 2.5a: shell only. Shows the component sections (Location expanded, others as
 * collapsed headers) + name + Save/Cancel. Save currently builds a Location step;
 * per-component editing arrives in 2.5b. One dialog serves both create and edit.
 */
public class StepEditorDialog extends JFrame {

    private final RoutePlannerPlugin plugin;
    private final Route route;
    private final RouteStep editing; // null = create mode
    private JCheckBox locationCheck;
    private static final String[] SKILLS = {
        "Agility", "Construction", "Herblore", "Thieving", "Crafting",
        "Fletching", "Mining", "Smithing", "Fishing",
        "Cooking", "Firemaking", "Woodcutting"
    };

    private final JTextField nameField = new JTextField();
    private final JTextField tileField = new JTextField();
    private String teleMethod = "WALK"; // WALK | SPELL | ITEM
    private com.routeplanner.teleport.TeleportSpell chosenSpell;
    private com.routeplanner.teleport.TeleportItem chosenItem;
    private final JLabel teleLabel = new JLabel("Walk only");
    private final java.util.Map<Integer, java.awt.image.BufferedImage> spriteCache = new java.util.HashMap<>();
    private JCheckBox itemsCheck;
    private JTextField itemsField;
    private JComboBox<String> itemsMode;
    private JCheckBox skillCheck;
    private JComboBox<String> skillSkill;
    private JComboBox<String> skillGoalType;
    private JTextField skillValue;
    private JComboBox<String> recipeCombo;
    private JCheckBox highlightCheck;
    private JCheckBox noteCheck;
    private JTextArea noteArea;
    private JTextField noteNpc;
    private JTextField noteKills;
    private JTextField noteDialog;

    public StepEditorDialog(RoutePlannerPlugin plugin, Route route, RouteStep editing) {
        this(plugin, route, editing, null);
    }

    /**
     * @param seedTile when creating a new step (editing == null), pre-fills the Location component
     *                 with this tile -- used by the shift+right-click "Add Location Step" menu.
     */
    public StepEditorDialog(RoutePlannerPlugin plugin, Route route, RouteStep editing, WorldPoint seedTile) {
        this.plugin = plugin;
        this.route = route;
        this.editing = editing;

        setTitle(editing == null ? "Add step" : "Edit step");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);            // float beside the game, but never modal
        setSize(320, 460);
        setLocationRelativeTo(null);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ColorScheme.DARK_GRAY_COLOR);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        root.add(labeled("Step name (auto if blank)", nameField));
        root.add(Box.createVerticalStrut(10));

        JLabel compHint = new JLabel("Components:");
        compHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        compHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(compHint);
        root.add(Box.createVerticalStrut(6));

        // Location component (expanded in the shell)
        locationCheck = new JCheckBox("Location");
        locationCheck.setSelected(true); // most steps have a location
        locationCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        locationCheck.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        locationCheck.setFocusPainted(false);
        JPanel locBody = new JPanel();
        locBody.setLayout(new BoxLayout(locBody, BoxLayout.Y_AXIS));
        locBody.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        locBody.setBorder(new EmptyBorder(6, 8, 8, 8));
        locBody.add(labeled("Tile (x, y, plane - plane optional, default 0)", tileField));
        JButton useTile = new JButton("Use my current tile");
        styleButton(useTile);
        useTile.addActionListener(e -> {
            WorldPoint wp = plugin.getLastPlayerLocation();
            if (wp != null) tileField.setText(formatTile(wp));
        });
        useTile.setAlignmentX(Component.LEFT_ALIGNMENT);
        locBody.add(Box.createVerticalStrut(6));
        locBody.add(useTile);

        // Teleport method row (Walk / Spell / Item)
        locBody.add(Box.createVerticalStrut(8));
        JLabel teleHdr = new JLabel("How to get there:");
        teleHdr.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        teleHdr.setAlignmentX(Component.LEFT_ALIGNMENT);
        locBody.add(teleHdr);
        locBody.add(Box.createVerticalStrut(4));
        JPanel teleBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        teleBtns.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        teleBtns.setAlignmentX(Component.LEFT_ALIGNMENT);
        teleBtns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JButton spellBtn = new JButton("Spell"); styleButton(spellBtn);
        JButton itemBtn = new JButton("Item"); styleButton(itemBtn);
        spellBtn.addActionListener(e -> pickSpell());
        itemBtn.addActionListener(e -> pickItem());
        JButton clearBtn = new JButton("Clear"); styleButton(clearBtn);
        clearBtn.addActionListener(e -> { teleMethod = "WALK"; chosenSpell = null; chosenItem = null; updateTeleLabel(); });
        teleBtns.add(spellBtn); teleBtns.add(itemBtn); teleBtns.add(clearBtn);
        locBody.add(teleBtns);
        teleLabel.setForeground(Color.WHITE);
        teleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        locBody.add(Box.createVerticalStrut(4));
        locBody.add(teleLabel);
        root.add(toggleableSection("Location", locationCheck, locBody));
        root.add(Box.createVerticalStrut(6));

        // Other components as collapsed placeholders (2.5b fills these in)
        // Items component (toggleable)
        itemsCheck = new JCheckBox("Items");
        itemsCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        itemsCheck.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        itemsCheck.setFocusPainted(false);
        JPanel itemsBody = new JPanel();
        itemsBody.setLayout(new BoxLayout(itemsBody, BoxLayout.Y_AXIS));
        itemsBody.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        itemsBody.setBorder(new EmptyBorder(6, 8, 8, 8));
        itemsField = new JTextField();
        itemsBody.add(labeled("Items (comma sep, / for alternatives)", itemsField));
        itemsBody.add(Box.createVerticalStrut(6));
        itemsMode = new JComboBox<>(new String[]{"Bank", "Shop (buy)", "Sell", "Pickup"});
        itemsMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        itemsBody.add(labeled("Mode", itemsMode));
        JPanel itemsSection = toggleableSection("Items", itemsCheck, itemsBody);
        root.add(itemsSection);
        root.add(Box.createVerticalStrut(6));
        // Skill goal component (toggleable)
        skillCheck = new JCheckBox("Skill goal");
        skillCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        skillCheck.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        skillCheck.setFocusPainted(false);
        JPanel skillBody = new JPanel();
        skillBody.setLayout(new BoxLayout(skillBody, BoxLayout.Y_AXIS));
        skillBody.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        skillBody.setBorder(new EmptyBorder(6, 8, 8, 8));
        skillSkill = new JComboBox<>(SKILLS);
        skillSkill.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        skillBody.add(labeled("Skill", skillSkill));
        skillBody.add(Box.createVerticalStrut(6));
        skillGoalType = new JComboBox<>(new String[]{"Reach Level", "Reach XP total", "Gain XP amount"});
        skillGoalType.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        skillBody.add(labeled("Goal type", skillGoalType));
        skillBody.add(Box.createVerticalStrut(6));
        skillValue = new JTextField();
        skillBody.add(labeled("Target value (e.g. 70, 100k, 1m)", skillValue));
        skillBody.add(Box.createVerticalStrut(6));
        recipeCombo = new JComboBox<>();
        recipeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        JPanel recipeWrap = labeled("Recipe / method (optional)", recipeCombo);
        skillBody.add(recipeWrap);
        Runnable refreshRecipes = () -> {
            String sk = (String) skillSkill.getSelectedItem();
            String[] labels = recipeLabelsFor(sk);
            recipeCombo.setModel(new DefaultComboBoxModel<>(labels));
            boolean has = labels.length > 1; // index 0 is the blank "(none)"
            recipeWrap.setVisible(has);
            skillBody.revalidate(); skillBody.repaint();
        };
        skillSkill.addItemListener(ev -> { if (ev.getStateChange() == java.awt.event.ItemEvent.SELECTED) refreshRecipes.run(); });
        refreshRecipes.run();
        root.add(toggleableSection("Skill goal", skillCheck, skillBody));
        root.add(Box.createVerticalStrut(6));
        // Highlight component: the section checkbox is the master toggle for this step's in-game highlights
        highlightCheck = new JCheckBox("Highlight");
        highlightCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        highlightCheck.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        highlightCheck.setFocusPainted(false);
        JPanel hlBody = new JPanel();
        hlBody.setLayout(new BoxLayout(hlBody, BoxLayout.Y_AXIS));
        hlBody.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        hlBody.setBorder(new EmptyBorder(6, 8, 8, 8));
        JLabel hlHint = new JLabel("<html>When on, lights up this step's NPC / object /<br>bank targets while the step is active.</html>");
        hlHint.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        hlHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hlBody.add(hlHint);
        root.add(toggleableSection("Highlight", highlightCheck, hlBody));
        root.add(Box.createVerticalStrut(6));
        // Note component (toggleable): text always displays; NPC highlight is gated by Highlight toggle
        noteCheck = new JCheckBox("Note");
        noteCheck.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        noteCheck.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        noteCheck.setFocusPainted(false);
        JPanel noteBody = new JPanel();
        noteBody.setLayout(new BoxLayout(noteBody, BoxLayout.Y_AXIS));
        noteBody.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        noteBody.setBorder(new EmptyBorder(6, 8, 8, 8));
        noteArea = new JTextArea(4, 20);
        noteArea.setLineWrap(true); noteArea.setWrapStyleWord(true);
        noteArea.setBackground(ColorScheme.DARK_GRAY_COLOR); noteArea.setForeground(Color.WHITE);
        JScrollPane noteScroll = new JScrollPane(noteArea);
        noteScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        noteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        JLabel noteLbl = new JLabel("Note text");
        noteLbl.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        noteLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        noteBody.add(noteLbl);
        noteBody.add(noteScroll);
        noteBody.add(Box.createVerticalStrut(6));
        noteNpc = new JTextField();
        noteBody.add(labeled("Highlight NPC (optional, exact name)", noteNpc));
        noteBody.add(Box.createVerticalStrut(6));
        noteKills = new JTextField();
        noteBody.add(labeled("Auto-complete after N kills (optional)", noteKills));
        noteBody.add(Box.createVerticalStrut(6));
        noteDialog = new JTextField();
        noteBody.add(labeled("Highlight dialogue options e.g. 3,1 (optional)", noteDialog));
        root.add(toggleableSection("Note", noteCheck, noteBody));
        root.add(Box.createVerticalStrut(12));

        // Save / Cancel
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton cancel = new JButton("Cancel"); styleButton(cancel);
        JButton save = new JButton("Save step"); styleButton(save);
        cancel.addActionListener(e -> dispose());
        save.addActionListener(e -> onSave());
        buttons.add(cancel); buttons.add(save);
        root.add(buttons);

        // seed the location from a shift+right-clicked tile when creating a new step
        if (editing == null && seedTile != null) {
            tileField.setText(formatTile(seedTile));
            locationCheck.setSelected(true);
        }

        // prefill in edit mode
        if (editing != null) {
            if (editing.getName() != null) nameField.setText(editing.getName());
            WorldPoint wp = editing.getWorldPoint();
            if (wp != null) tileField.setText(formatTile(wp));
            if (editing.getItemList() != null && !editing.getItemList().isEmpty()) {
                itemsCheck.setSelected(true);
                itemsField.setText(editing.getItemList());
                String m = editing.getItemMode();
                itemsMode.setSelectedIndex("SHOP".equals(m) ? 1 : "SELL".equals(m) ? 2 : "PICKUP".equals(m) ? 3 : 0);
            }
            if (editing.getSkillingSkill() != null) {
                skillCheck.setSelected(true);
                skillSkill.setSelectedItem(cap(editing.getSkillingSkill()));
                String gt = editing.getSkillingGoalType();
                skillGoalType.setSelectedItem("XP_GAIN".equals(gt) ? "Gain XP amount"
                    : "XP_TARGET".equals(gt) ? "Reach XP total" : "Reach Level");
                skillValue.setText(String.valueOf(editing.getSkillingGoalValue()));
            }
            // reverse-prefill the recipe dropdown from the stored label (exact match)
            if (editing.getRecipeLabel() != null && recipeCombo != null) {
                recipeCombo.setSelectedItem(editing.getRecipeLabel());
            }
            // teleport prefill: restore spell/item choice so save does not wipe it
            if (editing.getTeleportSpell() != null && !editing.getTeleportSpell().isEmpty()) {
                com.routeplanner.teleport.TeleportSpell sp = com.routeplanner.teleport.TeleportSpells.getByName(editing.getTeleportSpell());
                if (sp != null) { teleMethod = "SPELL"; chosenSpell = sp; updateTeleLabel(); }
            } else if (editing.getTeleportItem() != null && !editing.getTeleportItem().isEmpty()) {
                com.routeplanner.teleport.TeleportItem it = com.routeplanner.teleport.TeleportItems.getByDisplayName(editing.getTeleportItem());
                if (it != null) { teleMethod = "ITEM"; chosenItem = it; updateTeleLabel(); }
            }
            // location checkbox reflects whether the step actually has a location/teleport
            boolean hasLoc = editing.getWorldPoint() != null || editing.getTeleportMethod() != null;
            if (locationCheck != null) locationCheck.setSelected(hasLoc);
            if (editing.isHighlightEnabled()) highlightCheck.setSelected(true);
            boolean hasNote = (editing.getNoteText() != null && !editing.getNoteText().isEmpty())
                || (editing.getNpcHighlight() != null && !editing.getNpcHighlight().isEmpty())
                || (editing.getDialogOptions() != null && !editing.getDialogOptions().isEmpty());
            if (hasNote) {
                noteCheck.setSelected(true);
                if (editing.getNoteText() != null) noteArea.setText(editing.getNoteText());
                if (editing.getNpcHighlight() != null) noteNpc.setText(editing.getNpcHighlight());
                if (editing.getNpcKillCount() > 0) noteKills.setText(String.valueOf(editing.getNpcKillCount()));
                if (editing.getDialogOptions() != null) noteDialog.setText(editing.getDialogOptions());
            }
        }

        add(new JScrollPane(root));
    }

    private void onSave() {
        String name = nameField.getText() != null ? nameField.getText().trim() : "";

        // Block links in any free-text field before anything is persisted.
        String linkErr = RouteTextValidator.checkField("Step name", name);
        if (linkErr == null && noteArea != null) linkErr = RouteTextValidator.checkField("Note", noteArea.getText());
        if (linkErr == null && noteNpc != null) linkErr = RouteTextValidator.checkField("Highlight NPC", noteNpc.getText());
        if (linkErr == null && noteDialog != null) linkErr = RouteTextValidator.checkField("Dialogue options", noteDialog.getText());
        if (linkErr == null && itemsField != null) linkErr = RouteTextValidator.checkField("Items", itemsField.getText());
        if (linkErr != null) {
            JOptionPane.showMessageDialog(this, linkErr, "Links not allowed", JOptionPane.WARNING_MESSAGE);
            return;
        }
        WorldPoint wp = (locationCheck == null || locationCheck.isSelected()) ? parseTile(tileField.getText()) : null;
        if (name.isEmpty()) name = wp != null ? ("Go to " + wp.getX() + ", " + wp.getY()) : "Location";

        if (editing == null) {
            WorldPoint dest = wp;
            if ("SPELL".equals(teleMethod) && chosenSpell != null) dest = chosenSpell.getDestination();
            if ("ITEM".equals(teleMethod) && chosenItem != null) dest = chosenItem.getDestination();
            RouteStep step = new RouteStep(name, StepType.LOCATION, dest, -1);
            applyTeleport(step);
            applyItems(step);
            applySkillGoal(step);
            applyHighlight(step);
            applyNote(step);
            plugin.addStep(route, step);
            plugin.refreshHighlights();
        } else {
            editing.setName(name);
            editing.setWorldPoint(wp);
            applyTeleport(editing);
            applyItems(editing);
            applySkillGoal(editing);
            applyHighlight(editing);
            applyNote(editing);
            plugin.saveRoutes();
            plugin.refreshHighlights();
        }
        dispose();
    }

    private String[] recipeLabelsFor(String skill) {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("(none)");
        if (skill == null) return out.toArray(new String[0]);
        switch (skill) {
            case "Cooking": for (com.routeplanner.skilling.CookingFoods.Food f : com.routeplanner.skilling.CookingFoods.FOODS) out.add(f.toString()); break;
            case "Smithing": for (com.routeplanner.skilling.SmithingBars.Bar b : com.routeplanner.skilling.SmithingBars.BARS) out.add(b.toString()); break;
            case "Firemaking": for (com.routeplanner.skilling.FiremakingLogs.Log l : com.routeplanner.skilling.FiremakingLogs.LOGS) out.add(l.toString()); break;
            case "Mining": for (com.routeplanner.skilling.MiningRocks.Rock r : com.routeplanner.skilling.MiningRocks.ROCKS) out.add(r.toString()); break;
            case "Woodcutting": for (com.routeplanner.skilling.WoodcuttingTrees.Tree t : com.routeplanner.skilling.WoodcuttingTrees.TREES) out.add(t.toString()); break;
            case "Herblore": for (com.routeplanner.skilling.PotionRecipes.Potion p : com.routeplanner.skilling.PotionRecipes.POTIONS) out.add(p.toString()); break;
            case "Fishing": for (com.routeplanner.skilling.FishingMethods.Method m : com.routeplanner.skilling.FishingMethods.METHODS) out.add(m.toString()); break;
            case "Construction": for (com.routeplanner.skilling.ConstructionMethods.Method m : com.routeplanner.skilling.ConstructionMethods.METHODS) out.add(m.toString()); break;
            case "Crafting":
                for (com.routeplanner.skilling.CraftingData.Item i : com.routeplanner.skilling.CraftingData.JEWELRY) out.add(i.toString());
                for (com.routeplanner.skilling.CraftingData.Item i : com.routeplanner.skilling.CraftingData.GLASS) out.add(i.toString());
                for (com.routeplanner.skilling.CraftingData.Item i : com.routeplanner.skilling.CraftingData.LEATHER) out.add(i.toString());
                break;
            case "Fletching":
                for (com.routeplanner.skilling.FletchingData.Item i : com.routeplanner.skilling.FletchingData.AMMO) out.add(i.toString());
                for (com.routeplanner.skilling.FletchingData.Item i : com.routeplanner.skilling.FletchingData.BOWS) out.add(i.toString());
                break;
            case "Agility":
                for (com.routeplanner.agility.AgilityCourse ac : com.routeplanner.agility.AgilityCoursePresets.ALL) out.add(ac.getName());
                break;
            case "Thieving":
                for (com.routeplanner.skilling.SkillingNpc n : com.routeplanner.skilling.ThievingNpcs.PICKPOCKET_NPCS) out.add(n.toString());
                for (com.routeplanner.skilling.SkillingNpc s : com.routeplanner.skilling.ThievingStalls.STALLS) out.add(s.toString());
                break;
            default: break;
        }
        return out.toArray(new String[0]);
    }

    private void clearSkillingTargets(RouteStep step) {
        step.setSkillingTargetNpc(null);
        step.setSkillingTargetObject(null);
        step.setBankHighlightItems(null);
        step.setHerblorePotion(null);
    }

    private void applyRecipe(RouteStep step, String skill) {
        if (recipeCombo == null) return;
        clearSkillingTargets(step); // switching skills must not leave stale targets
        Object sel = recipeCombo.getSelectedItem();
        boolean none = (sel == null || "(none)".equals(sel));
        step.setRecipeLabel(none ? null : sel.toString());
        if (none) return;
        String label = sel.toString();
        if (skill == null) return;
        switch (skill) {
            case "Cooking": { com.routeplanner.skilling.CookingFoods.Food f = com.routeplanner.skilling.CookingFoods.getByLabel(label); if (f != null) step.setBankHighlightItems(f.rawItem); break; }
            case "Smithing": { com.routeplanner.skilling.SmithingBars.Bar b = com.routeplanner.skilling.SmithingBars.getByLabel(label); if (b != null) step.setBankHighlightItems(b.ores); break; }
            case "Firemaking": { com.routeplanner.skilling.FiremakingLogs.Log l = com.routeplanner.skilling.FiremakingLogs.getByLabel(label); if (l != null) step.setBankHighlightItems(l.item); break; }
            case "Mining": { com.routeplanner.skilling.MiningRocks.Rock r = com.routeplanner.skilling.MiningRocks.getByLabel(label); if (r != null) step.setSkillingTargetObject(r.objectName); break; }
            case "Woodcutting": { com.routeplanner.skilling.WoodcuttingTrees.Tree t = com.routeplanner.skilling.WoodcuttingTrees.getByLabel(label); if (t != null) step.setSkillingTargetObject(t.objectName); break; }
            case "Herblore": { step.setHerblorePotion(labelName(label)); break; }
            case "Crafting": { String mats = craftingMaterials(label); if (mats != null) step.setBankHighlightItems(mats); break; }
            case "Fletching": { String mats = fletchingMaterials(label); if (mats != null) step.setBankHighlightItems(mats); break; }
            case "Agility": {
                net.runelite.api.coords.WorldPoint loc = com.routeplanner.agility.AgilityCoursePresets.startLocation(label);
                if (loc != null) step.setWorldPoint(loc); // course start -> Location tile
                break;
            }
            case "Thieving": {
                boolean isNpc = false;
                for (com.routeplanner.skilling.SkillingNpc n : com.routeplanner.skilling.ThievingNpcs.PICKPOCKET_NPCS) {
                    if (n.toString().equals(label)) { step.setSkillingTargetNpc(n.getName()); isNpc = true; break; }
                }
                if (!isNpc) {
                    for (com.routeplanner.skilling.SkillingNpc s : com.routeplanner.skilling.ThievingStalls.STALLS) {
                        if (s.toString().equals(label)) { step.setSkillingTargetObject(s.getName()); break; }
                    }
                }
                break;
            }
            default: break; // Fishing/Construction: informational only (use Items component to bring gear)
        }
    }

    private String labelName(String label) {
        int i = label.indexOf(" (Lv");
        return i > 0 ? label.substring(0, i) : label;
    }

    private String craftingMaterials(String label) {
        for (com.routeplanner.skilling.CraftingData.Item i : com.routeplanner.skilling.CraftingData.JEWELRY) if (i.toString().equals(label)) return i.materials;
        for (com.routeplanner.skilling.CraftingData.Item i : com.routeplanner.skilling.CraftingData.GLASS) if (i.toString().equals(label)) return i.materials;
        for (com.routeplanner.skilling.CraftingData.Item i : com.routeplanner.skilling.CraftingData.LEATHER) if (i.toString().equals(label)) return i.materials;
        return null;
    }

    private String fletchingMaterials(String label) {
        for (com.routeplanner.skilling.FletchingData.Item i : com.routeplanner.skilling.FletchingData.AMMO) if (i.toString().equals(label)) return i.materials;
        for (com.routeplanner.skilling.FletchingData.Item i : com.routeplanner.skilling.FletchingData.BOWS) if (i.toString().equals(label)) return i.materials;
        return null;
    }

    private void applyNote(RouteStep step) {
        if (noteCheck != null && noteCheck.isSelected()) {
            String text = noteArea.getText() != null ? noteArea.getText().trim() : "";
            step.setNoteText(text.isEmpty() ? null : text);
            String npc = noteNpc.getText() != null ? noteNpc.getText().trim() : "";
            step.setNpcHighlight(npc.isEmpty() ? null : npc);
            String kills = noteKills.getText() != null ? noteKills.getText().trim() : "";
            if (!kills.isEmpty()) {
                try { int k = Integer.parseInt(kills); if (k > 0) step.setNpcKillCount(k); } catch (NumberFormatException ignored) {}
            } else { step.setNpcKillCount(0); }
            String dlg = noteDialog.getText() != null ? noteDialog.getText().trim() : "";
            step.setDialogOptions(dlg.isEmpty() ? null : dlg);
        } else {
            step.setNoteText(null);
            step.setNpcHighlight(null);
            step.setNpcKillCount(0);
            step.setDialogOptions(null);
        }
    }

    private void applyHighlight(RouteStep step) {
        step.setHighlightEnabled(highlightCheck != null && highlightCheck.isSelected());
    }

    private void applySkillGoal(RouteStep step) {
        if (skillCheck != null && skillCheck.isSelected()) {
            String val = skillValue.getText() != null ? skillValue.getText().trim() : "";
            if (val.isEmpty()) return;
            try {
                long goalValue = parseXpValue(val);
                String gt = (String) skillGoalType.getSelectedItem();
                String goalKey = "Gain XP amount".equals(gt) ? "XP_GAIN"
                    : "Reach XP total".equals(gt) ? "XP_TARGET" : "LEVEL";
                String skill = (String) skillSkill.getSelectedItem();
                step.setSkillingSkill(skill.toUpperCase());
                step.setSkillingGoalType(goalKey);
                step.setSkillingGoalValue(goalValue);
                applyRecipe(step, skill);
                if ("XP_GAIN".equals(goalKey)) {
                    net.runelite.api.Skill api = getApiSkill(skill);
                    long startXp = api != null ? plugin.getClient().getSkillExperience(api) : 0;
                    step.setSkillingStartXp(startXp);
                }
            } catch (NumberFormatException ignored) {}
        } else {
            step.setSkillingSkill(null);
            step.setSkillingGoalType(null);
        }
    }

    private String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }

    private long parseXpValue(String input) throws NumberFormatException {
        String s = input.trim().toLowerCase().replace(",", "");
        double mult = 1;
        if (s.endsWith("k")) { mult = 1_000; s = s.substring(0, s.length() - 1); }
        else if (s.endsWith("m")) { mult = 1_000_000; s = s.substring(0, s.length() - 1); }
        return Math.round(Double.parseDouble(s.trim()) * mult);
    }

    private net.runelite.api.Skill getApiSkill(String name) {
        try { return net.runelite.api.Skill.valueOf(name.toUpperCase()); }
        catch (Exception e) { return null; }
    }

    private void applyItems(RouteStep step) {
        if (itemsCheck != null && itemsCheck.isSelected()) {
            String items = itemsField.getText() != null ? itemsField.getText().trim() : "";
            if (!items.isEmpty()) {
                step.setItemList(items);
                int mi = itemsMode.getSelectedIndex();
                String mode = mi == 1 ? "SHOP" : mi == 2 ? "SELL" : mi == 3 ? "PICKUP" : "BANK";
                step.setItemMode(mode);
            }
        } else {
            step.setItemList(null);
        }
    }

    private void applyTeleport(RouteStep step) {
        if ("SPELL".equals(teleMethod) && chosenSpell != null) {
            step.setTeleportMethod("SPELL");
            step.setTeleportSpell(chosenSpell.getName());
            step.setTeleportDestination(chosenSpell.getDestination());
        } else if ("ITEM".equals(teleMethod) && chosenItem != null) {
            step.setTeleportMethod("ITEM");
            step.setTeleportItem(chosenItem.getDisplayName());
            step.setTeleportDestination(chosenItem.getDestination());
        } else {
            step.setTeleportMethod(null);
            step.setTeleportSpell(null);
            step.setTeleportItem(null);
        }
    }

    private void updateTeleLabel() {
        if ("SPELL".equals(teleMethod) && chosenSpell != null) {
            teleLabel.setText("Teleport (spell): " + chosenSpell.getName());
        } else if ("ITEM".equals(teleMethod) && chosenItem != null) {
            teleLabel.setText("Teleport (item): " + chosenItem.getDisplayName());
        } else {
            teleLabel.setText("Walk only");
        }
    }

    private void pickSpell() {
        java.util.List<com.routeplanner.teleport.TeleportSpell> all = com.routeplanner.teleport.TeleportSpells.ALL;
        DefaultListModel<com.routeplanner.teleport.TeleportSpell> model = new DefaultListModel<>();
        for (com.routeplanner.teleport.TeleportSpell s : all) model.addElement(s);
        JList<com.routeplanner.teleport.TeleportSpell> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(15);
        final JList<com.routeplanner.teleport.TeleportSpell> spellListRef = list;
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                com.routeplanner.teleport.TeleportSpell sp = (com.routeplanner.teleport.TeleportSpell) v;
                setText(sp.getName());
                Integer key = sp.getSpriteId();
                java.awt.image.BufferedImage cached = spriteCache.get(key);
                if (cached != null) {
                    setIcon(new ImageIcon(cached.getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH)));
                } else {
                    setIcon(null);
                    plugin.getSpriteManager().getSpriteAsync(sp.getSpriteId(), 0, img -> {
                        if (img != null) {
                            spriteCache.put(key, img);
                            SwingUtilities.invokeLater(spellListRef::repaint);
                        }
                    });
                }
                return this;
            }
        });
        JScrollPane sc = new JScrollPane(list);
        sc.setPreferredSize(new Dimension(240, 400));
        int r = JOptionPane.showConfirmDialog(this, sc, "Select teleport spell", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        com.routeplanner.teleport.TeleportSpell s = list.getSelectedValue();
        if (s == null) return;
        teleMethod = "SPELL"; chosenSpell = s; chosenItem = null; updateTeleLabel();
    }

    private void pickItem() {
        java.util.List<com.routeplanner.teleport.TeleportItem> all = com.routeplanner.teleport.TeleportItems.ALL;
        DefaultListModel<com.routeplanner.teleport.TeleportItem> model = new DefaultListModel<>();
        for (com.routeplanner.teleport.TeleportItem it : all) model.addElement(it);
        JList<com.routeplanner.teleport.TeleportItem> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(15);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, i, sel, foc);
                com.routeplanner.teleport.TeleportItem it = (com.routeplanner.teleport.TeleportItem) v;
                setText(it.getDisplayName());
                try {
                    java.awt.image.BufferedImage img = plugin.getItemManager().getImage(it.getIconId());
                    if (img != null) setIcon(new ImageIcon(img.getScaledInstance(20, 20, java.awt.Image.SCALE_SMOOTH)));
                    else setIcon(null);
                } catch (Exception ex) { setIcon(null); }
                return this;
            }
        });
        JScrollPane sc = new JScrollPane(list);
        sc.setPreferredSize(new Dimension(240, 400));
        int r = JOptionPane.showConfirmDialog(this, sc, "Select item teleport", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        com.routeplanner.teleport.TeleportItem it = list.getSelectedValue();
        if (it == null) return;
        teleMethod = "ITEM"; chosenItem = it; chosenSpell = null; updateTeleLabel();
    }

    /** Formats a tile for the text field. Plane is included unless it's 0, so ground-floor tiles keep
     *  looking like plain "x, y" while upper floors round-trip correctly through parseTile. */
    private static String formatTile(WorldPoint wp) {
        return wp.getPlane() == 0 ? (wp.getX() + ", " + wp.getY())
                                   : (wp.getX() + ", " + wp.getY() + ", " + wp.getPlane());
    }

    private WorldPoint parseTile(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            String[] parts = s.split(",");
            int x = Integer.parseInt(parts[0].trim());
            int y = Integer.parseInt(parts[1].trim());
            int plane = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 0;
            return new WorldPoint(x, y, plane);
        } catch (Exception ex) {
            return null;
        }
    }

    // --- small UI helpers matching the plugin's dark styling ---

    private JPanel labeled(String label, JComponent field) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel l = new JLabel(label);
        l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        if (field instanceof JTextField) {
            field.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            field.setForeground(Color.WHITE);
        }
        p.add(l);
        p.add(Box.createVerticalStrut(2));
        p.add(field);
        return p;
    }

    private JPanel toggleableSection(String title, JCheckBox check, JPanel body) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(2, 6, 2, 8));
        header.add(check, BorderLayout.WEST);
        body.setVisible(check.isSelected()); // start state matches checkbox
        JButton collapseChevron = new JButton(body.isVisible() ? "-" : "+");
        collapseChevron.setForeground(Color.WHITE);
        collapseChevron.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        collapseChevron.setBorderPainted(false);
        collapseChevron.setFocusPainted(false);
        collapseChevron.setToolTipText("Collapse / expand");
        collapseChevron.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        header.add(collapseChevron, BorderLayout.EAST);
        p.add(header);
        p.add(body);
        collapseChevron.addActionListener(e -> {
            body.setVisible(!body.isVisible());
            collapseChevron.setText(body.isVisible() ? "-" : "+");
            p.revalidate(); p.repaint();
        });
        // Checking the box auto-expands; unchecking collapses.
        check.addActionListener(e -> {
            body.setVisible(check.isSelected());
            collapseChevron.setText(body.isVisible() ? "-" : "+");
            p.revalidate(); p.repaint();
        });
        return p;
    }


    private JPanel section(String title, boolean on) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(ColorScheme.DARK_GRAY_COLOR);
        p.setBorder(BorderFactory.createLineBorder(
            on ? ColorScheme.BRAND_ORANGE : ColorScheme.MEDIUM_GRAY_COLOR));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(new EmptyBorder(6, 8, 6, 8));
        JLabel t = new JLabel(title);
        t.setForeground(on ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
        header.add(t, BorderLayout.WEST);
        JLabel state = new JLabel(on ? "on" : "off");
        state.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        header.add(state, BorderLayout.EAST);
        p.add(header);
        return p;
    }

    private JPanel collapsedSection(String title) {
        return section(title, false);
    }

    private void styleButton(JButton b) {
        b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
    }
}
