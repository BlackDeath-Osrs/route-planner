package com.routeplanner.farming;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * The object ids of every farming patch (gameval FARMING_*_PATCH and the Troll Stronghold and Weiss
 * herb patches): the patch's own id, which stays the same whatever grows in it. Canopies, weeds
 * and decorations are left out.
 */
public final class FarmingPatches {

    private static final Set<Integer> IDS = new HashSet<>(Arrays.asList(
        7572, 7577, 7578, 7579, 7580, 7771, 7807, 7847, 7848, 7849, 7850, 7962, 7963, 7964,
        7965, 8150, 8151, 8152, 8153, 8173, 8174, 8175, 8176, 8337, 8338, 8382, 8383, 8388,
        8389, 8390, 8391, 8550, 8551, 8552, 8553, 8554, 8555, 8556, 8557, 8877, 9372, 18816,
        19147, 21950, 26579, 27111, 27113, 27114, 27115, 27116, 30480, 30481, 30482, 30500,
        30501, 33176, 33649, 33693, 33694, 33732, 33733, 33761, 33979, 33998, 34006, 34007,
        34051, 34052, 34053, 34054, 34055, 34056, 34057, 34058, 34059, 34629, 34632, 34633,
        34634, 34635, 34636, 34637, 34638, 34639, 34640, 34641, 34906, 34919, 34921, 34922,
        50692, 50693, 50695, 50696, 50697, 55341, 56953, 56954, 56955, 56959, 56960, 58675,
        58710, 58711, 58834));

    private FarmingPatches() {}

    public static boolean isPatch(int objectId) {
        return IDS.contains(objectId);
    }
}
