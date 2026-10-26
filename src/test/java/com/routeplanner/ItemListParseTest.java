package com.routeplanner;

import com.routeplanner.bank.BankItemManager;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ItemListParseTest {

    private final BankItemManager items = new BankItemManager();

    @Test
    public void readsTheNameAfterAnAmount() {
        assertEquals("Coins", items.parseItemName("2.5k Coins"));
        assertEquals("Coins", items.parseItemName("1mil Coins"));
        assertEquals("Spade", items.parseItemName("Spade"));
        assertEquals("Spade", items.parseItemName("  Spade  "));
        assertEquals("Rune pouch", items.parseItemName("Rune pouch"));
    }

    @Test
    public void readsTheAmount() {
        assertEquals(2_500, items.parseQuantityValue("2.5k Coins"));
        assertEquals(1_000_000, items.parseQuantityValue("1mil Coins"));
        assertEquals(3_000_000, items.parseQuantityValue("3M Coins"));
        assertEquals(5, items.parseQuantityValue("5 Lobster"));
        assertEquals(1, items.parseQuantityValue("Spade"));
        assertEquals(1, items.parseQuantityValue("1.2.3k Coins")); // unreadable amount counts 1
    }

    @Test
    public void anyChargeMatchesEveryCharge() {
        assertTrue(BankItemManager.nameMatches("Amulet of glory(*)", "Amulet of glory(4)"));
        assertFalse(BankItemManager.nameMatches("Amulet of glory(*)", "Amulet of glory"));
        assertTrue(BankItemManager.nameMatches("spade", "Spade"));

        Map<String, Long> carried = new HashMap<>();
        carried.put("amulet of glory(4)", 1L);
        carried.put("amulet of glory(2)", 2L);
        assertEquals(3, BankItemManager.countOf(carried, "Amulet of glory(*)"));
    }
}
