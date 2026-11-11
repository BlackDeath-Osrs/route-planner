package com.routeplanner.ui;

import com.routeplanner.bank.BankItemManager;
import com.routeplanner.model.RouteStep;
import com.routeplanner.teleport.FairyRings;
import com.routeplanner.teleport.Quetzals;
import com.routeplanner.teleport.TeleportItem;
import com.routeplanner.teleport.TeleportItems;
import com.routeplanner.teleport.TeleportSpell;
import com.routeplanner.teleport.TeleportSpells;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.SwingUtilities;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Game sprites for step icons: world map icons (bank, quest start, shop...), tab and spell icons,
 * skill icons and item images. All are read from the game at runtime, so no game art is stored in
 * the plugin. Lookups never block: a sprite that isn't loaded yet returns null (the caller draws
 * its glyph) and the panel is repainted once it arrives.
 */
public final class StepSprites {

    private static volatile SpriteManager sprites;
    private static volatile ItemManager items;
    private static volatile SkillIconManager skillIcons;
    private static volatile BankItemManager bank;
    private static volatile Runnable onLoaded = () -> {};

    private static final Map<String, BufferedImage> LOADED = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

    private StepSprites() {}

    public static void init(SpriteManager spriteManager, ItemManager itemManager, SkillIconManager skillIconManager,
                            BankItemManager bankItemManager, Runnable repaint) {
        sprites = spriteManager;
        items = itemManager;
        skillIcons = skillIconManager;
        bank = bankItemManager;
        onLoaded = repaint;
    }

    public static void shutDown() {
        sprites = null;
        items = null;
        skillIcons = null;
        bank = null;
        onLoaded = () -> {};
        LOADED.clear();
        REQUESTED.clear();
        ITEM_IDS.clear();
        ITEM_MISSES.clear();
    }

    /** The sprite for a step type on its own (the quick-step buttons), or null to draw the glyph. */
    public static BufferedImage forKind(StepIcon.Kind kind) {
        int id = spriteIdFor(kind);
        return id > 0 ? sprite(id) : null;
    }

    /** The sprite for a step, using what the step targets where that's known, or null to draw the glyph. */
    public static BufferedImage forStep(RouteStep s, StepIcon.Kind kind) {
        switch (kind) {
            case SKILL:
            case AGILITY: {
                BufferedImage img = skill(s.skill());
                if (img != null) return img;
                return kind == StepIcon.Kind.AGILITY ? sprite(SpriteID.Mapfunction.AGILITY_SHORT_CUT) : forKind(kind);
            }
            case GATHER: {
                if (s.hasFarmPatch()) return sprite(SpriteID.Mapfunction.FARMING_PATCH);
                Skill skill = s.skill();
                if (skill == Skill.FISHING) return sprite(SpriteID.Mapfunction.FISHING_SPOT);
                if (skill == Skill.MINING) return sprite(SpriteID.Mapfunction.MINING_SITE);
                return skill(skill);
            }
            case PICKUP:
                return item(s.getItemList());
            case TELEPORT:
                return teleport(s);
            default:
                return forKind(kind);
        }
    }

    private static int spriteIdFor(StepIcon.Kind kind) {
        switch (kind) {
            case QUEST: return SpriteID.Mapfunction.QUEST_START;
            case BANK: return SpriteID.Mapfunction.BANK;
            case BUY:
            case SELL: return SpriteID.Mapfunction.GENERAL_STORE;
            case KILL: return SpriteID.SideIcons.COMBAT;
            case TALK: return SpriteID.LunarMagicOn.NPC_CONTACT;
            case AGILITY: return SpriteID.Mapfunction.AGILITY_SHORT_CUT;
            case SKILL: return SpriteID.SideIcons.STATS;
            case TELEPORT: return SpriteID.Mapfunction.TRANSPORTATION;
            case WALK: return SpriteID.OrbIcon.RUN;
            default: return -1; // note, gather and pick up keep their drawn glyphs
        }
    }

    private static BufferedImage teleport(RouteStep s) {
        String method = s.getTeleportMethod();
        if ("SPELL".equals(method) && s.getTeleportSpell() != null) {
            TeleportSpell sp = TeleportSpells.getByName(s.getTeleportSpell());
            if (sp != null && sp.getSpriteId() > 0) return sprite(sp.getSpriteId());
        } else if ("ITEM".equals(method) && s.getTeleportItem() != null) {
            TeleportItem it = TeleportItems.getByDisplayName(s.getTeleportItem());
            BufferedImage img = it != null ? itemImage(it.getIconId()) : item(s.getTeleportItem());
            if (img != null) return img;
        } else if ("NEXUS".equals(method) || "PORTAL".equals(method)) {
            return sprite(SpriteID.Mapfunction.HOUSE_PORTAL);
        } else if (FairyRings.METHOD.equals(method)) {
            // No map icon for fairy rings: the staff that works them
            BufferedImage img = itemImage(ItemID.DRAMEN_STAFF);
            if (img != null) return img;
        } else if (Quetzals.METHOD_RENU.equals(method) || Quetzals.METHOD_WHISTLE.equals(method)) {
            BufferedImage img = itemImage(ItemID.HG_QUETZALWHISTLE_BASIC);
            if (img != null) return img;
        }
        return sprite(SpriteID.Mapfunction.TRANSPORTATION);
    }

    private static BufferedImage sprite(int id) {
        String key = "s" + id;
        BufferedImage img = LOADED.get(key);
        SpriteManager sm = sprites;
        if (img != null || sm == null || !REQUESTED.add(key)) return img;
        sm.getSpriteAsync(id, 0, loaded -> {
            if (loaded == null) return;
            LOADED.put(key, loaded);
            SwingUtilities.invokeLater(onLoaded);
        });
        return null;
    }

    private static BufferedImage skill(Skill skill) {
        SkillIconManager sim = skillIcons;
        if (skill == null || sim == null) return null;
        return LOADED.computeIfAbsent("k" + skill.name(), k -> sim.getSkillImage(skill, true));
    }

    /** The first item of an item list (or a single item name). */
    private static BufferedImage item(String itemList) {
        BankItemManager b = bank;
        if (itemList == null || itemList.trim().isEmpty() || b == null) return null;
        String key = itemList.trim().toLowerCase();
        Integer id = ITEM_IDS.get(key);
        if (id == null) {
            // Item names can't be looked up until the client's item list has loaded, so a miss is retried now and then.
            long now = System.currentTimeMillis();
            Long missed = ITEM_MISSES.get(key);
            if (missed != null && now - missed < MISS_RETRY_MS) return null;
            int found = b.firstItemId(itemList);
            if (found <= 0) {
                ITEM_MISSES.put(key, now);
                return null;
            }
            ITEM_MISSES.remove(key);
            ITEM_IDS.put(key, found);
            id = found;
        }
        return itemImage(id);
    }

    private static final long MISS_RETRY_MS = 30_000;
    private static final Map<String, Integer> ITEM_IDS = new ConcurrentHashMap<>();
    private static final Map<String, Long> ITEM_MISSES = new ConcurrentHashMap<>();

    private static BufferedImage itemImage(int id) {
        String key = "i" + id;
        BufferedImage img = LOADED.get(key);
        ItemManager im = items;
        if (img != null || im == null || !REQUESTED.add(key)) return img;
        AsyncBufferedImage async = im.getImage(id);
        async.onLoaded(() -> {
            LOADED.put(key, async);
            SwingUtilities.invokeLater(onLoaded);
        });
        return null;
    }
}
