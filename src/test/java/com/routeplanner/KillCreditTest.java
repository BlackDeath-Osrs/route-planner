package com.routeplanner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KillCreditTest {
    @Test
    public void npcThePlayerNeverHitIsNotTheirs() {
        assertFalse(new KillCredit().isMine(new Object(), 100));
    }

    @Test
    public void npcHitRecentlyIsTheirs() {
        KillCredit credit = new KillCredit();
        Object goblin = new Object();
        credit.onHit(goblin, 100);
        assertTrue(credit.isMine(goblin, 100));
        assertTrue(credit.isMine(goblin, 100 + KillCredit.WINDOW_TICKS));
    }

    @Test
    public void npcAbandonedLongAgoIsNotTheirs() {
        KillCredit credit = new KillCredit();
        Object goblin = new Object();
        credit.onHit(goblin, 100);
        assertFalse(credit.isMine(goblin, 101 + KillCredit.WINDOW_TICKS));
    }

    @Test
    public void hittingOneNpcDoesNotClaimAnotherOfTheSameName() {
        KillCredit credit = new KillCredit();
        Object mine = new Object();
        Object theirs = new Object();
        credit.onHit(mine, 100);
        assertFalse(credit.isMine(theirs, 100));
    }
}
