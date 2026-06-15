# Route Planner

Build your own step-by-step routes for Old School RuneScape and follow them in-game with on-screen guidance, pathfinding, and highlighting — like a quest helper, but for any goal you define yourself: skilling grinds, item runs, diary tasks, agility laps, or full account progression plans.

Routes are organised into **sections** and **steps**, tracked with live progress, and can be shared with other players via import/export.

## Features

### Routes and steps
- Create named routes split into sections, each containing an ordered list of steps.
- Live progress tracking with completion marks; steps advance as you go.
- Drag-to-reorder steps while editing, with wrapping step text in the side panel.
- Import and export routes as text so you can share them or back them up.

### Two modes
- **Developer mode** — build and edit: add, reorder, and configure every step.
- **Player mode** — a clean follow-along view; left-click a step to mark it complete. Editing controls are hidden so you can focus on the route.

### Step types
- **Location / waypoint** — pathfinds to a destination tile and draws the route on the world map, a minimap arrow, and the game scene. Uses RuneScape's collision data and known transports (fairy rings, spirit trees, gnome gliders, charter ships, teleports, and more) to find an efficient path.
- **Skilling goal** — pick a skill and a target (quantity, XP, or level). Per-skill flows add the right help automatically:
  - Object highlighting for trees, rocks, stalls, and the Wintertodt brazier.
  - Bank ingredient highlighting for Herblore (dynamic per potion tier), Cooking, Firemaking, Smithing, Crafting, and Fletching — missing items in orange, items you have in green.
  - Gear reminders (e.g. fishing equipment, the Construction toolkit, the Fletching knife).
  - Minigame prompts for Wintertodt, Giant's Foundry, and Vale Totems.
- **Item** — highlights the items a step needs in your bank.
- **Note** — free-form text for anything else, with optional waypoint and in-dialogue option highlighting.
- **Agility** — course presets with obstacle guidance and Mark of Grace highlighting.

### Highlighting
- Bank overlay shows exactly which items a step needs.
- Scene-object and NPC highlighting points you at the right tree, rock, stall, or target.
- Dialogue option highlighting nudges you through conversations.

## Getting started
1. Open the **Route Planner** side panel from the RuneLite toolbar.
2. Create a route, add a section, then start adding steps.
3. Use **Developer mode** to build the route, then switch to **Player mode** to follow it.
4. Right-click a spot on the world map to set a waypoint destination for pathfinding.

## Credits
Pathfinding relies on collision-map and transport data from the excellent
[Shortest Path](https://github.com/Skretzo/shortest-path) plugin by Runemoro and Skretzo.
See [`LICENSE`](LICENSE) for the full third-party notice.

## License
Released under the BSD 2-Clause License. See [`LICENSE`](LICENSE).

Created by BlackDeath_Osrs.
