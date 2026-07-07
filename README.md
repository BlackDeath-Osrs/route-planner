# Route Planner

Build your own step-by-step routes for Old School RuneScape and follow them in-game with on-screen guidance, pathfinding, and highlighting — like a quest helper, but for any goal you define yourself: skilling grinds, item runs, diary tasks, or full account progression plans.

Routes are organised into **sections** and **steps**, tracked with live progress, and can be shared with other players via import/export.

## Features

### Routes and steps
- Create named routes split into sections, each containing an ordered list of steps.
- Live progress tracking with completion marks; steps advance as you go.
- Drag steps to reorder them within a section, with a drop-line showing where they'll land.
- Import and export routes as text so you can share them or back them up.

### Two modes
- **Developer mode** — build and edit: add, reorder, and configure every step.
- **Player mode** — a clean follow-along view; left-click a step to mark it complete. Editing controls are hidden so you can focus on the route.

### Component-based steps
Each step is built from optional **components** in a single editor, and any combination can be attached to one step:

- **Location** — pathfinds to a destination tile and draws the route on the world map, a minimap arrow, and the game scene. Uses RuneScape's collision data and known transports (fairy rings, spirit trees, gnome gliders, charter ships, teleports, and more) to find an efficient path. A location can optionally use a **teleport method** — a spellbook teleport or a teleport item — with the spell or item highlighted in your spellbook or inventory.
- **Items** — the items a step needs, handled as a bank fetch, a shop buy, a sell, or a ground pickup. Missing items are shown in orange, items you already have in green.
- **Skill goal** — pick a skill and a target (level, XP total, or XP gained). An optional per-skill recipe/method picker records what to train (foods, bars, logs, potions, rocks, trees, pickpocket targets, agility courses, and more). Choosing an agility course also sets the walk-to start tile automatically.
- **Highlight** — an opt-in toggle that lights up the step's in-game targets (skilling NPCs, objects, and bank items) while the step is active.
- **Note** — free-form text shown on the step, with an optional NPC to highlight, an auto-complete-after-N-kills counter, and in-dialogue option highlighting.

Steps complete automatically when every tracked component on them is satisfied — for example, a step that both fetches items and sets a skill goal completes only once you have the items **and** reach the goal.

### Highlighting
- Bank overlay shows exactly which items a step needs.
- Scene-object and NPC highlighting points you at the right tree, rock, stall, or target.
- Dialogue option highlighting nudges you through conversations.
- All in-game highlighting is opt-in per step via the Highlight component.

### HUD customization
- Preset themes (OSRS Brown, Quest Helper style) or a fully **Custom** theme.
- Per-element colors for the body, outline, title, step text, and dividers, each with transparency control.
- Adjustable outline on/off and thickness, font family (RuneScape fonts or system fonts), and font size.
- **Match HUD to interface** — auto-generate a matching HUD palette from your active resource pack's overlay color.

## Getting started
1. Open the **Route Planner** side panel from the RuneLite toolbar.
2. Create a route, add a section, then start adding steps.
3. Use **Developer mode** to build the route, then switch to **Player mode** to follow it.
4. Right-click a spot on the world map to set a waypoint destination for pathfinding.

## Credits
Pathfinding relies on collision-map and transport data from the excellent [Shortest Path](https://github.com/Skretzo/shortest-path) plugin by Runemoro and Skretzo. See [`LICENSE`](LICENSE) for the full third-party notice.

## License
Released under the BSD 2-Clause License. See [`LICENSE`](LICENSE). Created by BlackDeath_Osrs.
