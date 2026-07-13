package com.routeplanner.hub;

import java.util.List;

/**
 * Deserialized shape of index.json from the Route Hub repo. Field names must match the JSON
 * exactly since Gson binds by name. Anything the catalog doesn't recognize (a newer schemaVersion,
 * or fields added later) is simply ignored by Gson rather than causing a parse failure.
 */
public class HubIndex {
    public int schemaVersion;
    public List<HubRouteEntry> routes;
}
