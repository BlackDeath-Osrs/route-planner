package com.routeplanner.util;

import net.runelite.api.*;

/** Helpers for reading objects out of the scene. */
public final class SceneObjects {

    private SceneObjects() {}

    /**
     * Whether a tile's game object is a real object. The scene also lists the players and NPCs
     * standing on a tile among its game objects, and their ids can match an object's (a player
     * would light up as a "Crate").
     */
    public static boolean isObject(GameObject obj) {
        return obj != null && !(obj.getRenderable() instanceof Actor);
    }
}
