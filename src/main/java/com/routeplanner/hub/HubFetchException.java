package com.routeplanner.hub;

/**
 * Thrown by {@link RouteHubFetcher} on any failure -- network, HTTP, oversized payload, or an
 * unsafe path. The message is written to be shown directly to the user, never a stack trace.
 */
public class HubFetchException extends Exception {
    public HubFetchException(String message) {
        super(message);
    }
}
