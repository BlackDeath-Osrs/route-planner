# Route Planner

Build your own step-by-step routes for Old School RuneScape and follow them in-game with on-screen guidance, pathfinding, and highlighting — like a quest helper, but for any goal you define yourself: skilling grinds, item runs, diary tasks, or full account progression plans.

Routes are organised into **sections** and **steps**, tracked with live progress, and can be shared with other players via import/export.

## Features

### Routes and steps
- Create named routes split into sections, each containing an ordered list of steps.
- Live progress tracking with completion marks; steps advance as you go.
- Drag steps to reorder them, or to move them between sections — a drop-line shows where they'll land. Drop onto a section header to send a step to the end of it.
- Undo and redo structural edits from the panel header. Each route keeps its own history.
- Deleting a route or a section asks for confirmation first.
- Import and export routes as text so you can share them or back them up.

### Edit mode
- Toggle **Edit mode** at the top of the panel to add, edit, reorder, and delete steps.
- With it off, the panel is a clean follow-along view: left-click a step to mark it complete, and the editing controls get out of your way.
- Following someone else's route? Flip Edit mode on to add your own notes or reorder a step you skipped, then turn it back off.

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

### Waypoint path
Draw the route to your next destination as a line, as highlighted tiles, or both.

- **Line** — solid, dashed, dotted, or dash-dot, with adjustable colour, transparency, thickness, and an optional glow. Dashes can take their own colour over a faint base line, and can flow toward your destination.
- **Tiles** — adjustable fill and border colour, border thickness, optional rounded corners and a gentle pulse, and fading for tiles further along the path.
- **Arrows** — none, spaced along the line, or one at the destination.
- The path is clipped to the client's draw distance, or to a limit you set.
- **Preview in game** draws a short demo path beside you so you can judge your settings without an active route.

### HUD customization
- Preset themes (OSRS Brown, Quest Helper style) or a fully **Custom** theme.
- Per-element colors for the body, outline, title, step text, and dividers, each with transparency control.
- Adjustable outline on/off and thickness, font family (RuneScape fonts or system fonts), and font size.
- **Match HUD to interface** — auto-generate a matching HUD palette from your active resource pack's overlay color.

## Getting started
1. Open the **Route Planner** side panel from the RuneLite toolbar.
2. Create a route, add a section, then start adding steps.
3. Turn on **Edit mode** to build the route, then turn it off to follow it.
4. Right-click a spot on the world map to set a waypoint destination for pathfinding.
5. Shift + right-click a tile in the game world and choose **Add Location Step** to open the step editor with that tile already filled in.

## Credits
Pathfinding relies on collision-map and transport data from the excellent [Shortest Path](https://github.com/Skretzo/shortest-path) plugin by Runemoro and Skretzo. See [`NOTICE`](NOTICE) for third-party attribution.

## License
Released under the BSD 2-Clause License. See [`LICENSE`](LICENSE). Created by BlackDeath_Osrs.
