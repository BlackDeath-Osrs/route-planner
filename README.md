# Route Planner

Build your own step-by-step routes for Old School RuneScape and follow them in-game with on-screen guidance, pathfinding, and highlighting, like a quest helper, but for any goal you define yourself: skilling grinds, item runs, diary tasks, or full account progression plans.

Make a route by adding steps one at a time, or just press **Record** and play: what you do turns into steps on its own. Then follow it, or share it with other players through the Route Hub.

## Getting started
1. Open the **Route Planner** side panel from the RuneLite toolbar.
2. On the welcome screen, pick one:
   - **Build one step by step**: name your route and start adding steps.
   - **Record a route**: play normally and let the plugin write the steps.
   - **Import a file**: load a route someone sent you.
   - **Browse the Route Hub**: install a community route.
3. Click the pencil to leave edit mode, then follow the line. Steps tick off as you do them.

New to the plugin? The **Quick guide** on the welcome screen (also in the route menu) walks through it in five short pages.

## Features

### The panel
- **Route** tab: the active route, with a card for the current step showing what to do, plus **Back**, **Skip**, and **Done** buttons. Switch routes from the dropdown at the top.
- **My routes** tab: all your routes. Use the **...** menu on a route to rename, reset progress, export, share, or delete it, or to make it **repeatable** so it starts over by itself when you finish.
- **Hub** tab: browse and install community routes (see [Route Hub](#route-hub)).
- **Pause this route** hides its path and HUD until you pick it again.

### Building routes
- Click the pencil to enter edit mode, then use **+ Step** and **+ Section**. Sections group steps into chapters.
- **+ Step** opens a quick editor: choose what the player does (**Walk, Items, Talk, Kill, Skill, Quest,** or **Note**) and fill in only what you need. **Full editor** has every setting at once, including teleports.
- Hold **Shift** and right-click any tile in game, then pick **Add Location Step** to make a step with that tile already filled in.
- Drag steps to reorder them or move them between sections. Double-click to edit; right-click for more.
- Undo and redo structural edits. Each route keeps its own history.
- Import and export routes as files to share or back them up.

### Record mode
Press **Record** and play normally. Walking, teleports, stairs and ladders, talking to NPCs, banking, buying, selling, picking things up, skilling, agility courses, kills, and quests all turn into draft steps as you go.

- Press **Note** while recording to add a tip to the current step.
- Press **Stop** to review what was recorded (keep it, fix it, or discard it) before it joins your route.
- Recording goes into a new section of the route, so you can record a route in parts.
- Settings under **Record mode** control how long you stand still before a new step starts, and whether recorded NPCs and objects are highlighted.

### Step types
- **Walk**: pathfinds to a tile and draws the way on the world map, the minimap, and the game scene. Stairs and ladders on the way are found for you, along with fairy rings, spirit trees, gnome gliders, charter ships, and more. A step can also say how to get there: a spellbook teleport, a teleport item, or the Portal Nexus, with the spell or item highlighted.
- **Items**: the items to bring from the bank, buy at a shop, sell, or pick up. Missing items show in orange and items you have in green. A step can also be "deposit everything".
- **Talk**: an NPC to talk to, with the chat options to pick highlighted in order.
- **Kill**: an NPC to kill, completing after a set number of kills.
- **Skill**: a skill and a target (level, XP total, or XP gained), with an optional method such as foods, bars, logs, potions, rocks, trees, pickpocket targets, or agility courses.
- **Quest**: completes when the quest is finished.
- **Note**: free-form text shown on the step.

A step completes on its own once everything it tracks is done. For example, a step that fetches items and has a skill goal completes only when you have the items **and** reach the goal.

### Highlighting
- A **Route Planner bank tab** filters your bank down to just what the current step needs.
- Shop, ground item, NPC, and object highlights point you at the right target.
- Dialogue options are highlighted as you talk.
- In-game highlighting is opt-in per step.

### Waypoint path
Draw the way to your next destination as a line, as highlighted tiles, or both.

- **Line**: solid, dashed, dotted, or dash-dot (animated dashes by default), with adjustable colour, transparency, thickness, an optional glow, and an optional separate dash colour.
- **Tiles**: adjustable fill and border colour, border thickness, rounded corners, a gentle pulse, and fading for tiles further along the path.
- The path is clipped to the client's draw distance, or to a limit you set.
- **Preview in game** draws a short demo path beside you so you can try your settings without an active route.
- **Pathfinder**: use the built-in pathfinder, or the [Shortest Path](https://runelite.net/plugin-hub/show/shortest-path) plugin if you have it installed. If Shortest Path is off or doesn't answer, the built-in one is used.

### Route Hub
Browse and install community-made routes from the **Hub** tab.

- The plugin fetches route data (read-only) from a GitHub-hosted repository. No player information,
  account data, or anything else about you is ever sent: only plain GET requests for the catalog,
  route files, and icons.
- Submissions go the other way: sharing a route means opening a pull request on GitHub yourself
  (**My routes → ... → Share to the Route Hub**). The plugin never uploads anything on your behalf.
- Every submission is reviewed before it's added to the catalog, and route text is scanned for
  links on both creation and installation. Even so, routes are community content: never follow a
  step that asks you to visit a website, share account details, or do anything outside the game.

### HUD
- Shows the current step, its goal, and what you still need. It hides itself while the bank is open.
- Preset themes (OSRS Brown, Quest Helper style) or a fully **Custom** theme.
- Per-element colours for the body, outline, title, step text, and dividers, each with transparency control.
- Outline on/off and thickness, font family (RuneScape fonts or system fonts), and font size.
- **Match HUD to interface**: build a matching HUD palette from your resource pack's overlay colour.

## Credits
Pathfinding relies on collision-map and transport data from the excellent [Shortest Path](https://github.com/Skretzo/shortest-path) plugin by Runemoro and Skretzo. See [`NOTICE`](NOTICE) for third-party attribution.

## License
Released under the BSD 2-Clause License. See [`LICENSE`](LICENSE). Created by BlackDeath_Osrs.
