#!/usr/bin/env python3
"""Rebuild a shoal's route in shoals.properties from the plugin's bug report log.

With "Bug report log" on, the plugin logs every tile the nearest shoal moves to (SHOALPOS) and every
stop it makes (SHOALSTOP). Follow the shoal for at least one full lap, then:

    scripts/sailing/shoal_trace.py ~/.runelite/logs/client.log            # list shoals seen
    scripts/sailing/shoal_trace.py ~/.runelite/logs/client.log 14         # print the new lines
    scripts/sailing/shoal_trace.py ~/.runelite/logs/client.log 14 --write # replace them in the file

The game's world view id isn't always the route's key in shoals.properties (a marlin shoal seen as 15
swims route 14). Check where the lap was and pass the key to replace with --as, e.g. "15 --as 14 --write".

Only one lap is kept: the path is cut where the shoal comes back to the tile it was first seen on.
A shoal that never gets back there (route not a loop, or the lap wasn't finished) keeps everything.
"""
import re
import sys
from pathlib import Path

PROPS = Path(__file__).resolve().parents[2] / "src/main/resources/com/routeplanner/sailing/shoals.properties"
LINE = re.compile(r"\[RP-TRACE\] SHOAL(POS|STOP) worldview=(\d+) tick=(\d+) at=(\d+),(\d+),(\d+)")
# Keep a path point every this many tiles (the bundled data is about every 2-4 tiles)
SPACING = 3


def read(log):
    seen = {}
    for line in open(log, errors="replace"):
        m = LINE.search(line)
        if not m:
            continue
        kind, wv, tick = m.group(1), int(m.group(2)), int(m.group(3))
        pt = tuple(int(m.group(i)) for i in (4, 5, 6))
        seen.setdefault(wv, {"POS": [], "STOP": []})[kind].append((tick, pt))
    return seen


def dist(a, b):
    return max(abs(a[0] - b[0]), abs(a[1] - b[1]))


def one_lap(points):
    """Points from the first sighting until the shoal is back where it started (after going away)."""
    start, left = points[0], False
    for i, p in enumerate(points[1:], 1):
        if dist(p, start) > 20:
            left = True
        elif left and dist(p, start) <= 1:
            return points[:i], True
    return points, False


def thin(points):
    out = [points[0]]
    for p in points[1:]:
        if dist(p, out[-1]) >= SPACING:
            out.append(p)
    return out


def fmt(points):
    return "|".join(f"{x}, {y}, {z}" for x, y, z in points)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    seen = read(sys.argv[1])
    if len(sys.argv) < 3:
        for wv, d in sorted(seen.items()):
            print(f"worldview {wv}: {len(d['POS'])} tiles, {len(d['STOP'])} stops")
        return
    wv = int(sys.argv[2])
    if wv not in seen:
        sys.exit(f"no SHOALPOS lines for worldview {wv}")
    pos = [p for _, p in sorted(seen[wv]["POS"])]
    # Collapse repeats (re-logged after the shoal left and came back into view)
    pos = [p for i, p in enumerate(pos) if i == 0 or p != pos[i - 1]]
    lap, closed = one_lap(pos)
    path = thin(lap)
    lap_set = set(lap)
    stops = []
    for _, p in sorted(seen[wv]["STOP"]):
        if p in lap_set and p not in stops:
            stops.append(p)
    print(f"# {len(lap)} tiles -> {len(path)} points, {len(stops)} stops, "
          f"{'full lap' if closed else 'NOT a full lap (shoal never came back to its first tile)'}",
          file=sys.stderr)
    key = int(sys.argv[sys.argv.index("--as") + 1]) if "--as" in sys.argv else wv
    lines = {f"shoalpath.{key}": fmt(path), f"shoalstops.{key}": fmt(stops)}
    if "--write" not in sys.argv:
        for k, v in lines.items():
            print(f"{k}={v}")
        return
    if not closed:
        sys.exit("refusing to write an unfinished lap; follow the shoal all the way round")
    raw = PROPS.read_text()
    text = raw.splitlines()
    for k, v in lines.items():
        idx = [i for i, l in enumerate(text) if l.startswith(k + "=")]
        if not idx:
            sys.exit(f"{k} not in {PROPS}")
        text[idx[0]] = f"{k}={v}"
    PROPS.write_text("\n".join(text) + ("\n" if raw.endswith("\n") else ""))
    print(f"updated shoalpath.{key} and shoalstops.{key} in {PROPS}", file=sys.stderr)


if __name__ == "__main__":
    main()
