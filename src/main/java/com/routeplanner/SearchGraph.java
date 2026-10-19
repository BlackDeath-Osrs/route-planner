package com.routeplanner;

import java.util.Arrays;

/**
 * The open and closed sets of one A* search in flat arrays: an open-addressing table from a packed
 * tile key to a node index, per-node cost and parent arrays, and a binary heap of node indices.
 * A route over the sea can explore a million tiles, which as HashMaps of WorldPoints takes several
 * times the memory and time.
 */
final class SearchGraph {

    static final int NONE = -1;

    private long[] tableKeys;
    private int[] tableNodes;
    private int tableMask;

    private long[] keys;
    private int[] g;
    private int[] f;
    private int[] parent;
    private int size;

    /** Queued entries as (f << 32 | node), so a node queued again at a lower cost leaves its old entry stale, not misplaced. */
    private long[] heap;
    private int heapSize;

    SearchGraph(int expected) {
        int cap = Integer.highestOneBit(Math.max(16, expected * 2) - 1) << 1;
        tableKeys = new long[cap];
        tableNodes = new int[cap];
        Arrays.fill(tableNodes, NONE);
        tableMask = cap - 1;
        keys = new long[expected];
        g = new int[expected];
        f = new int[expected];
        parent = new int[expected];
        heap = new long[expected];
    }

    static long key(int x, int y, int plane) {
        return ((long) plane << 32) | ((long) (x & 0xFFFF) << 16) | (y & 0xFFFF);
    }

    static int x(long key) { return (int) ((key >>> 16) & 0xFFFF); }
    static int y(long key) { return (int) (key & 0xFFFF); }
    static int plane(long key) { return (int) (key >>> 32); }

    int size() { return size; }
    long key(int node) { return keys[node]; }
    int g(int node) { return g[node]; }
    int parent(int node) { return parent[node]; }

    /** The node for {@code key}, or NONE if it hasn't been reached. */
    int find(long key) {
        int slot = slot(key);
        while (tableNodes[slot] != NONE) {
            if (tableKeys[slot] == key) return tableNodes[slot];
            slot = (slot + 1) & tableMask;
        }
        return NONE;
    }

    /**
     * Records reaching {@code key} at cost {@code cost} from {@code from} and queues it, unless it was
     * already reached at least as cheaply. Returns whether it was queued.
     */
    boolean offer(long key, int cost, int estimate, int from) {
        int node = find(key);
        if (node == NONE) {
            if (size == keys.length) grow();
            node = size++;
            keys[node] = key;
            insert(key, node);
        } else if (cost >= g[node]) {
            return false;
        }
        g[node] = cost;
        f[node] = cost + estimate;
        parent[node] = from;
        push(((long) f[node] << 32) | node);
        return true;
    }

    boolean isEmpty() { return heapSize == 0; }

    /**
     * Removes the queued node with the lowest estimate, skipping entries left behind when a node was
     * queued again more cheaply. Returns NONE once nothing is left.
     */
    int poll() {
        while (heapSize > 0) {
            long top = heap[0];
            heap[0] = heap[--heapSize];
            siftDown(0);
            int node = (int) top;
            if ((int) (top >>> 32) == f[node]) return node;
        }
        return NONE;
    }

    private int slot(long key) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 40) & tableMask;
    }

    private void insert(long key, int node) {
        if (size * 2 > tableKeys.length) rehash();
        int slot = slot(key);
        while (tableNodes[slot] != NONE) slot = (slot + 1) & tableMask;
        tableKeys[slot] = key;
        tableNodes[slot] = node;
    }

    private void rehash() {
        long[] oldKeys = tableKeys;
        int[] oldNodes = tableNodes;
        tableKeys = new long[oldKeys.length * 2];
        tableNodes = new int[oldNodes.length * 2];
        Arrays.fill(tableNodes, NONE);
        tableMask = tableKeys.length - 1;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldNodes[i] == NONE) continue;
            int slot = slot(oldKeys[i]);
            while (tableNodes[slot] != NONE) slot = (slot + 1) & tableMask;
            tableKeys[slot] = oldKeys[i];
            tableNodes[slot] = oldNodes[i];
        }
    }

    private void grow() {
        int cap = keys.length * 2;
        keys = Arrays.copyOf(keys, cap);
        g = Arrays.copyOf(g, cap);
        f = Arrays.copyOf(f, cap);
        parent = Arrays.copyOf(parent, cap);
    }

    // Entries compare as plain longs: f is the high half, so the lowest f comes out first.
    private void push(long entry) {
        if (heapSize == heap.length) heap = Arrays.copyOf(heap, heap.length * 2);
        int i = heapSize++;
        while (i > 0) {
            int up = (i - 1) >> 1;
            if (heap[up] <= entry) break;
            heap[i] = heap[up];
            i = up;
        }
        heap[i] = entry;
    }

    private void siftDown(int i) {
        if (heapSize == 0) return;
        long entry = heap[i];
        while (true) {
            int child = 2 * i + 1;
            if (child >= heapSize) break;
            if (child + 1 < heapSize && heap[child + 1] < heap[child]) child++;
            if (heap[child] >= entry) break;
            heap[i] = heap[child];
            i = child;
        }
        heap[i] = entry;
    }
}
