package com.routeplanner.model;

/** How an items step's list is met. Saved by name, so these names must never change. */
public enum ItemMode {
    /** Fetch from the bank: done while the inventory holds them. */
    BANK,
    /** Buy from a shop: counts items gained since the step started. */
    SHOP,
    /** Sell to a shop: done once they've been carried and then gone. */
    SELL,
    /** Pick up or gather: counts items gained since the step started. */
    PICKUP,
    /** Deposit everything at any bank; no list. */
    DEPOSIT_ALL
}
