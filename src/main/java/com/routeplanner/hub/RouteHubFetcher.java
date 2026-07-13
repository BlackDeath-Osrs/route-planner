package com.routeplanner.hub;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

/**
 * Read-only GETs against the Route Hub's GitHub-hosted catalog. Every call is a plain HTTP GET to
 * raw.githubusercontent.com -- nothing is ever uploaded or posted. Bounds (payload size caps) are
 * enforced here so a malformed or oversized response can never wedge the panel; callers get a
 * {@link HubFetchException} instead of a crash, with a message safe to show the user directly.
 */
@Slf4j
@Singleton
public class RouteHubFetcher {

    /** Base raw-content URL for the curated routes repo, branch pinned to avoid surprise changes. */
    private static final String BASE_URL =
        "https://raw.githubusercontent.com/BlackDeath-Osrs/route-planner-routes/main/";

    // Sanity caps mirroring the v1.4 plan bounds. A response over these is rejected outright.
    private static final long MAX_INDEX_BYTES = 1_500_000;   // ~1 MB catalog, some headroom
    private static final long MAX_ROUTE_BYTES = 2_500_000;   // ~2 MB per-route cap, some headroom
    private static final long MAX_ICON_BYTES = 25_000;       // 20 KB icon cap, some headroom

    private final OkHttpClient okHttpClient;

    @Inject
    public RouteHubFetcher(OkHttpClient okHttpClient) {
        this.okHttpClient = okHttpClient;
    }

    /** Fetches index.json as raw text. Caller is responsible for JSON parsing + schema validation. */
    public String fetchIndex() throws HubFetchException {
        return fetchText("index.json", MAX_INDEX_BYTES, "route catalog");
    }

    /** Fetches a single route file (path as given in index.json's "file" field) as raw text. */
    public String fetchRoute(String relativePath) throws HubFetchException {
        requireSafeRelativePath(relativePath);
        return fetchText(relativePath, MAX_ROUTE_BYTES, "route");
    }

    /** Fetches an icon (path as given in index.json's "icon" field) as raw bytes. */
    public byte[] fetchIcon(String relativePath) throws HubFetchException {
        requireSafeRelativePath(relativePath);
        return fetchBytes(relativePath, MAX_ICON_BYTES, "icon");
    }

    // ---- internals ----

    /**
     * index.json and route "file"/"icon" entries are relative paths we build a URL from. Reject
     * anything that looks like it is trying to escape the repo (".." segments) or specify a whole
     * new host (a scheme or a leading "//") -- the catalog must only ever point within itself.
     */
    private void requireSafeRelativePath(String relativePath) throws HubFetchException {
        if (relativePath == null || relativePath.isEmpty()
            || relativePath.contains("..")
            || relativePath.startsWith("/")
            || relativePath.contains("://")) {
            throw new HubFetchException("The Route Hub catalog referenced an invalid file path.");
        }
    }

    private String fetchText(String relativePath, long maxBytes, String what) throws HubFetchException {
        byte[] bytes = fetchBytes(relativePath, maxBytes, what);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] fetchBytes(String relativePath, long maxBytes, String what) throws HubFetchException {
        Request request = new Request.Builder().url(BASE_URL + relativePath).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HubFetchException("Couldn't reach the Route Hub (the " + what + " wasn't found or the server had an error).");
            }
            if (response.body() == null) {
                throw new HubFetchException("The Route Hub sent back an empty response for the " + what + ".");
            }
            long declaredLength = response.body().contentLength();
            if (declaredLength > maxBytes) {
                throw new HubFetchException("The " + what + " from the Route Hub was too large to load safely.");
            }
            byte[] data = response.body().bytes();
            if (data.length > maxBytes) {
                throw new HubFetchException("The " + what + " from the Route Hub was too large to load safely.");
            }
            return data;
        } catch (IOException e) {
            log.warn("Route Hub fetch failed for {}", relativePath, e);
            throw new HubFetchException("Couldn't connect to the Route Hub. Check your connection and try again.");
        }
    }
}
