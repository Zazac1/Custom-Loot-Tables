# Custom Loot Tables test plan

## Automatic acceptance

Run from this version directory:

```powershell
$env:GRADLE_USER_HOME = Join-Path (Get-Location) '.gradle-user'
.\gradlew.bat clean build --no-daemon
```

Every build runs the Fabric Loader JUnit regression suite. It protects:

- configuration JSON round trips, normalization and legacy migration;
- separation of the Global Library and world libraries, All Worlds synchronization and safe imports;
- deep-copy isolation between editable drafts and saved configuration;
- rejection of duplicate, empty, invalid and oversized configurations;
- default-table previews, including items, tags, nested tables, conditions and count ranges;
- the single-drop guard for doors, beds and other two-block structures;
- stable, isolated world identifiers;
- compilation, resources and production JAR creation.

For a local client/server review, run `run-local-test.ps1`. It builds first, launches a dedicated
development server on port `25566` with `online-mode=false`, then launches the client. Development
accounts whose names match `Player` followed by digits are automatically promoted to operator by a
test-only source set. This helper is not present in the release JAR.

The workspace-level `test-all.ps1` runner adds metadata, translation, Mixin, security-contract,
website/issues-link and artifact checks for every port directory.

## Manual gameplay and interface review

Automatic tests cannot judge layout, readability or real player interaction. Before accepting a port,
perform every item below in both full-screen and a small bordered window.

### Menus and navigation

- Open from Mod Menu, the pause-menu logo and `/customloottables_solo`.
- Confirm the current-world editor cannot switch to another target when opened in a world.
- Check the target badge, world picker, Global Library, world library and default-table catalogue.
- From a world library, open Add from Global Library, add one table, then use Add all missing tables.
- In the Global Library, toggle a table between Library and All Worlds and verify the state label.
- Check every scrollbar, search field, clear button, tooltip and Back button.
- Verify blurred backgrounds are behind panels and text on every screen.
- Change GUI scale, open the editor, close it and confirm the exact previous scale is restored.
- Create an unsaved change and verify Save, Discard and Cancel behaviour.
- With Custom Recipe installed, confirm both pause-menu logos remain visible and clickable.
- Open Website and Issues from Mod Menu and verify the GitHub destinations.

### Loot gameplay

- Append a guaranteed diamond to a vanilla chest and confirm original loot is still present.
- Replace a chest table and confirm only configured drops can appear.
- Empty a table and confirm it produces no drops.
- Test rolls, weights and minimum/maximum quantities over several generated chests.
- Break a configured normal block and confirm its custom pool runs once.
- Break every half of a configured door, bed and tall plant; each structure must drop the custom loot once.
- Add a Global Library table in Library mode and confirm it affects no world by itself.
- Import it into one world and confirm no other world changes and no same-ID local table is overwritten.
- Mark another library table All Worlds and confirm every known local world receives a managed copy.
- Disable All Worlds and confirm managed copies disappear while manually imported copies remain.
- Save, leave the world, restart Minecraft and confirm the configuration persists.
- Import and export a configuration, then compare the restored tables and target assignments.

### Dedicated server

- Join with a non-operator and confirm `/customloottables_server` is unavailable.
- Join as an operator and confirm the server editor opens for the current server world only.
- Save a valid change and confirm it applies after the automatic reload.
- Cause a rejected save and confirm the editor stays open with the unsaved draft intact.
- Restart the server and confirm the saved loot behaviour remains active.

A port is accepted only when the automatic suite passes and this manual review is completed.
