package com.routeplanner.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class ProgressCheckTest {

    @Test
    public void questStages() {
        ProgressCheck c = ProgressCheck.parse("varbit 3185 >= 4");
        assertNotNull(c);
        assertEquals(ProgressCheck.Kind.VARBIT, c.kind);
        assertEquals(3185, c.id);
        assertFalse(c.isMet(3));
        assertTrue(c.isMet(4));
        assertTrue(c.isMet(100));

        c = ProgressCheck.parse("  VARP 107   >=5 ");
        assertNotNull(c);
        assertEquals(ProgressCheck.Kind.VARP, c.kind);
        assertEquals("varp 107 >= 5", c.toString());
    }

    @Test
    public void diaryBits() {
        ProgressCheck c = ProgressCheck.parse("varp 1194 bit 6");
        assertNotNull(c);
        assertEquals(6, c.bit);
        assertFalse(c.isMet(0b0111111));
        assertTrue(c.isMet(0b1000000));
        assertTrue(ProgressCheck.parse("varp 1 bit 31").isMet(Integer.MIN_VALUE));
        assertEquals("varp 1194 bit 6", c.toString());
    }

    @Test
    public void rejectsOthers() {
        assertNull(ProgressCheck.parse(null));
        assertNull(ProgressCheck.parse(""));
        assertNull(ProgressCheck.parse("varbit 12 bit 3"));   // bits are for varps
        assertNull(ProgressCheck.parse("varp 12 bit 32"));
        assertNull(ProgressCheck.parse("varp 99999999999 >= 1"));
        assertNull(ProgressCheck.parse("quest 12 >= 1"));
    }
}
