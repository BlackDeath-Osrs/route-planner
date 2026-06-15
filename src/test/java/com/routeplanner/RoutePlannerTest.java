package com.routeplanner;

import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.RuneLite;

public class RoutePlannerTest {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(RoutePlannerPlugin.class);
        RuneLite.main(args);
    }
}
