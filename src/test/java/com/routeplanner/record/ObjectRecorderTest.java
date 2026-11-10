package com.routeplanner.record;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ObjectRecorderTest {
    @Test
    public void questInteractionsCount() {
        assertTrue(ObjectRecorder.interesting("Search", "Crate"));
        assertTrue(ObjectRecorder.interesting("Open", "Chest"));
        assertTrue(ObjectRecorder.interesting("Inspect", "Painting"));
        assertTrue(ObjectRecorder.interesting("Use", "Glough's key -> Chest"));
        assertTrue(ObjectRecorder.interesting("Use", "Key -> Door")); // a key on a door is part of a quest
    }

    @Test
    public void travelAndBankingDont() {
        assertFalse(ObjectRecorder.interesting("Open", "Door"));
        assertFalse(ObjectRecorder.interesting("Open", "Large door"));
        assertFalse(ObjectRecorder.interesting("Climb-up", "Ladder"));
        assertFalse(ObjectRecorder.interesting("Climb-down", "Staircase"));
        assertFalse(ObjectRecorder.interesting("Open", "Trapdoor"));
        assertFalse(ObjectRecorder.interesting("Enter", "Portal"));
        assertFalse(ObjectRecorder.interesting("Board", "Gangplank"));
        assertFalse(ObjectRecorder.interesting("Bank", "Bank booth"));
        assertFalse(ObjectRecorder.interesting("Examine", "Crate"));
        assertFalse(ObjectRecorder.interesting("Walk here", ""));
    }

    @Test
    public void shortcutsAndEntrancesCount() {
        assertTrue(ObjectRecorder.interesting("Enter", "Cave"));
        assertTrue(ObjectRecorder.interesting("Climb-over", "Wall"));
        assertTrue(ObjectRecorder.interesting("Squeeze-through", "Hole"));
        assertTrue(ObjectRecorder.interesting("Cross", "Stepping stone"));
        assertTrue(ObjectRecorder.interesting("Climb", "Rocks"));
        assertTrue(ObjectRecorder.interesting("Jump-over", "Strange floor"));
        assertTrue(ObjectRecorder.interesting("Swing-on", "Ropeswing"));
    }

    @Test
    public void repeatGainsAddUp() {
        java.util.Map<String, Long> one = java.util.Collections.singletonMap("Banana", 1L);
        assertEquals("Banana", ObjectRecorder.addItems(null, one));
        assertEquals("2 Banana", ObjectRecorder.addItems("Banana", one));
        assertEquals("5 Banana", ObjectRecorder.addItems("4 Banana", one));
        assertEquals("Key, Banana", ObjectRecorder.addItems("Key", one));
    }

    @Test
    public void useOnSplitsItemAndObject() {
        assertEquals("Chest", ObjectRecorder.objectName("Glough's key -> Chest"));
        assertEquals("Glough's key", ObjectRecorder.usedItem("Glough's key -> Chest"));
        assertNull(ObjectRecorder.usedItem("Crate"));
    }
}
