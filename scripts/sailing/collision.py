"""Reads Route Planner's collision-map.zip (Shortest Path's SplitFlagMap format) for the Sailing scripts.

Each zip entry "rx_ry" is a region's BitSet: per tile on each plane, flags 0 (can move north) and
1 (can move east), then 2 and 3 (wall edges, unused). South and west are the north/east of the
neighbour. A region missing from the zip is treated as blocked here.
"""
import zipfile
from collections import deque

FLAG_COUNT = 4


class CollisionMap:
    def __init__(self, path):
        z = zipfile.ZipFile(path)
        self.regions = {tuple(map(int, n.split('_')[:2])): z.read(n) for n in z.namelist()}

    def flag(self, x, y, plane, d):
        r = self.regions.get((x >> 6, y >> 6))
        if r is None:
            return False
        i = ((plane * 4096) + ((y & 63) << 6) + (x & 63)) * FLAG_COUNT + d
        return (i >> 3) < len(r) and (r[i >> 3] >> (i & 7)) & 1 == 1

    def n(self, x, y, p=0): return self.flag(x, y, p, 0)
    def s(self, x, y, p=0): return self.flag(x, y - 1, p, 0)
    def e(self, x, y, p=0): return self.flag(x, y, p, 1)
    def w(self, x, y, p=0): return self.flag(x - 1, y, p, 1)

    def neighbours(self, x, y, p=0):
        if self.n(x, y, p): yield x, y + 1
        if self.s(x, y, p): yield x, y - 1
        if self.e(x, y, p): yield x + 1, y
        if self.w(x, y, p): yield x - 1, y

    def walkable(self, x, y, p=0):
        return self.n(x, y, p) or self.s(x, y, p) or self.e(x, y, p) or self.w(x, y, p)

    def flood(self, seed, limit=None):
        """The tiles reachable from seed on plane 0, as a set."""
        seen = {seed}
        q = deque([seed])
        while q and (limit is None or len(seen) < limit):
            p = q.popleft()
            for t in self.neighbours(*p):
                if t not in seen:
                    seen.add(t)
                    q.append(t)
        return seen
