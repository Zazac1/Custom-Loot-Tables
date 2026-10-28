# Custom Loot Tables — Fabric 1.21.1

Custom Loot Tables adds an in-game editor for managing Minecraft, mod and datapack loot tables.

## First version

- configuration screen available from Mod Menu;
- chest button in the single-player pause menu;
- `/customloottables` server command for operators;
- searchable catalogue of the default Minecraft tables and every table loaded by mods or datapacks;
- two-panel catalogue with an original/final-result preview of every possible visible drop;
- filters by source, loot-table type and configuration status;
- an explicit global library shared by every world, plus separate per-world libraries and overrides;
- append, replace and empty modes;
- one-click customization, item import, emptying, and restoration of the original loot table;
- configurable rolls, item weights, and minimum/maximum stack counts;
- visual drop builder with an item palette, in-game icons, search, and editable drop slots;
- duplicate, enable/disable, import and export actions;
- settings screen with automatic GUI scaling and a preferred editor target;
- responsive layouts for both full-screen and small-window play;
- explicit save, unsaved-change confirmation, validation, and automatic `/reload`;
- server-authoritative configuration on multiplayer servers.

World-specific definitions override global definitions with the same loot-table identifier. Disabling a
world override suppresses its inherited global definition for that world. The empty mode deliberately
removes every drop from the selected table.

The configuration is stored in `config/customloottables.json`. The previous file is retained as
`customloottables.json.bak` whenever a new version is saved.

## Build

Requires Java 21.

```powershell
./gradlew.bat build
```
