package com.routeplanner.hub;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches and parses the Route Hub catalog. Entries that fail {@link HubRouteEntry#validate()} are
 * dropped rather than shown or installed -- a bad entry should never reach the browse panel or the
 * install path, even if it somehow got past PR review.
 */
@Slf4j
@Singleton
public class RouteHubCatalog {

    private final RouteHubFetcher fetcher;
    private final Gson gson;

    @Inject
    public RouteHubCatalog(RouteHubFetcher fetcher, Gson gson) {
        this.fetcher = fetcher;
        this.gson = gson;
    }

    /** Fetches, parses, and validates the catalog. Never returns null; returns an empty list on
     *  total failure so the panel can show "couldn't load" rather than crash. */
    /** Passthrough to the fetcher for icon bytes, keeping RouteHubPanel from needing to know about
     *  RouteHubFetcher directly -- it only ever talks to the catalog. */
    public byte[] fetchIconBytes(String relativeIconPath) throws HubFetchException {
        return fetcher.fetchIcon(relativeIconPath);
    }

    /** Passthrough to the fetcher for a route file's raw JSON. Deserialization and validation are
     *  the caller's responsibility (RouteHubPanel uses plugin.getRouteGson() and
     *  validateFetchedRoute()), since RouteHubCatalog only knows about catalog-level shape. */
    public String fetchRouteJson(String relativeRoutePath) throws HubFetchException {
        return fetcher.fetchRoute(relativeRoutePath);
    }

    public List<HubRouteEntry> loadValidEntries() throws HubFetchException {
        String json = fetcher.fetchIndex();
        HubIndex index;
        try {
            index = gson.fromJson(json, HubIndex.class);
        } catch (JsonSyntaxException e) {
            log.warn("Route Hub index.json failed to parse", e);
            throw new HubFetchException("The Route Hub catalog is malformed and couldn't be read.");
        }
        if (index == null || index.routes == null) {
            throw new HubFetchException("The Route Hub catalog is empty or malformed.");
        }

        List<HubRouteEntry> valid = new ArrayList<>();
        for (HubRouteEntry entry : index.routes) {
            if (entry == null) continue;
            String err = entry.validate();
            if (err != null) {
                log.warn("Skipping invalid Route Hub entry (id={}): {}", entry.id, err);
                continue;
            }
            valid.add(entry);
        }
        return valid;
    }
}
