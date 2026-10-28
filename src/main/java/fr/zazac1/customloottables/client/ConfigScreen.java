package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.ConfigLoader;
import fr.zazac1.customloottables.DefaultLootTableCatalog;
import fr.zazac1.customloottables.LootTableDefinition;
import fr.zazac1.customloottables.LootTableInjector;
import fr.zazac1.customloottables.LootTargetConfig;
import fr.zazac1.customloottables.ModConfig;
import fr.zazac1.customloottables.WorldLootTableAssignments;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ConfigScreen extends StyledScreen {
    private final Screen parent;
    private ModConfig config;
    private final List<String> loadedTableIds;
    private final List<String> catalogTableIds;
    private final boolean serverManaged;
    /** Pause-menu and server-command editors must stay scoped to the running world. */
    private final boolean targetLocked;
    private final String activeWorldId;
    private final String activeWorldName;
    private String targetWorldId;
    private String targetWorldName;
    private String initialJson;
    private boolean savePending;

    public ConfigScreen(Screen parent, ModConfig config, List<String> loadedTableIds, boolean serverManaged,
                        String activeWorldId, String activeWorldName) {
        this(parent, config, loadedTableIds, serverManaged, activeWorldId, activeWorldName, serverManaged);
    }

    private ConfigScreen(Screen parent, ModConfig config, List<String> loadedTableIds, boolean serverManaged,
                         String activeWorldId, String activeWorldName, boolean targetLocked) {
        super(Text.translatable("customloottables.title"));
        this.parent = parent;
        this.config = ConfigLoader.copy(config);
        this.loadedTableIds = loadedTableIds == null ? new ArrayList<>() : new ArrayList<>(loadedTableIds);
        this.catalogTableIds = ClientInit.catalog(this.loadedTableIds, this.config.preload_loot_tables_on_startup);
        this.serverManaged = serverManaged;
        this.targetLocked = targetLocked;
        this.activeWorldId = activeWorldId == null ? "" : activeWorldId;
        this.activeWorldName = activeWorldName == null || activeWorldName.isBlank() ? "Current world" : activeWorldName;
        ClientInit.setEditorAutomaticGuiScale(this.config.automatic_gui_scale);
        chooseInitialTarget();
        this.initialJson = ConfigLoader.toJson(this.config);
    }

    public static Screen fromModMenu(Screen parent) {
        return local(parent);
    }

    public static Screen fromPauseMenu(Screen parent) { return local(parent, true); }

    private static ConfigScreen local(Screen parent) { return local(parent, false); }

    private static ConfigScreen local(Screen parent, boolean targetLocked) {
        return new ConfigScreen(parent, ConfigLoader.get(), LootTableInjector.loadedTableIds(), false,
                WorldLootTableAssignments.activeWorldId(), WorldLootTableAssignments.activeWorldName(), targetLocked);
    }

    private void chooseInitialTarget() {
        if (targetLocked && !activeWorldId.isBlank()) {
            selectTargetFields(activeWorldId, activeWorldName);
            return;
        }
        String preferred = config.default_editor_target_id;
        if ("global".equals(preferred)) {
            selectTargetFields("", Text.translatable("customloottables.target.global").getString());
        } else if ("current".equals(preferred) && !activeWorldId.isBlank()) {
            selectTargetFields(activeWorldId, activeWorldName);
        } else if (preferred != null && config.world_configs.containsKey(preferred)) {
            selectTargetFields(preferred, config.world_names.getOrDefault(preferred, config.default_editor_target_name));
        } else if (!activeWorldId.isBlank()) {
            selectTargetFields(activeWorldId, activeWorldName);
        } else {
            selectTargetFields("", Text.translatable("customloottables.target.global").getString());
        }
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(260, width - 32);
        int buttonHeight = 28;
        int gap = 8;
        int x = width / 2 - buttonWidth / 2;
        int top = Math.max(38, height / 2 - (buttonHeight * 5 + gap * 4) / 2 + 6);

        addDrawable((context, mouseX, mouseY, delta) -> context.drawCenteredTextWithShadow(
                textRenderer, title, width / 2, top - 27, ScreenStyle.TEXT));

        ButtonWidget selectWorld = homeButton(x, top, buttonWidth, buttonHeight,
                Text.translatable("customloottables.home.select_world"),
                Text.literal("(" + (isGlobalTarget()
                        ? Text.translatable("customloottables.target.global_short").getString()
                        : targetDisplayName()) + ")"),
                null, CustomLootTableSprites.WORLD_GLOBE,
                button -> client.setScreen(new LootTargetSelectScreen(this)));
        selectWorld.active = !serverManaged && !targetLocked;
        if (!selectWorld.active) selectWorld.setTooltip(Tooltip.of(
                Text.translatable("customloottables.home.current_world_locked")));
        addDrawableChild(selectWorld);
        int libraryY = top + buttonHeight + gap;
        addDrawableChild(homeButton(x, libraryY, buttonWidth, buttonHeight,
                Text.translatable("customloottables.home.library"), null, Items.BOOKSHELF, null,
                button -> client.setScreen(new LootTablesScreen(this))));
        int createY = libraryY + buttonHeight + gap;
        addDrawableChild(homeButton(x, createY, buttonWidth, buttonHeight,
                Text.translatable("customloottables.home.create"), null, Items.CRAFTING_TABLE, null,
                button -> client.setScreen(new LootTableEditorScreen(this, null))));
        int browseY = createY + buttonHeight + gap;
        addDrawableChild(homeButton(x, browseY, buttonWidth, buttonHeight,
                Text.translatable("customloottables.home.default_tables"), null, Items.GRASS_BLOCK, null,
                button -> client.setScreen(new LoadedLootTablesScreen(this))));
        int saveY = browseY + buttonHeight + gap;
        addDrawableChild(homeButton(x, saveY, buttonWidth, buttonHeight,
                Text.translatable("customloottables.button.save"), null, null, CustomLootTableSprites.SAVE,
                button -> save()));

        ButtonWidget settings = ButtonWidget.builder(Text.empty(), button -> client.setScreen(new ModSettingsScreen(this)))
                .tooltip(Tooltip.of(Text.translatable("customloottables.settings.open")))
                .dimensions(width - 28, height - 28, 20, 20).build();
        addDrawableChild(settings);
        addDrawable((context, mouseX, mouseY, delta) -> CustomLootTableSprites.draw(
                context, CustomLootTableSprites.SETTINGS, width - 26, height - 26, 16, 16));
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.export"),
                button -> exportAll()).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.export")))
                .dimensions(width / 2 - 104, saveY + 32, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.import"),
                button -> importAll()).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.import")))
                .dimensions(width / 2 + 4, saveY + 32, 100, 20).build());
        addDrawable((context, mouseX, mouseY, delta) -> {
            CustomLootTableSprites.drawWorldGlobe(context, x + 12,
                    top + Math.max(2, (buttonHeight - 16) / 2), 16, 16);
            CustomLootTableSprites.draw(context, CustomLootTableSprites.SAVE, x + 12,
                    saveY + Math.max(2, (buttonHeight - 16) / 2), 16, 16);
        });
    }

    private ButtonWidget homeButton(int x, int y, int width, int height, Text label, Text subtitle, Item icon,
                                    Identifier texture,
                                    ButtonWidget.PressAction action) {
        ButtonWidget button = ButtonWidget.builder(Text.empty(), action).dimensions(x, y, width, height).build();
        addDrawable((context, mouseX, mouseY, delta) -> {
            int maxTextWidth = Math.max(40, width - 68);
            String fittedLabel = ellipsize(label.getString(), maxTextWidth);
            int labelY = subtitle == null ? y + 10 : y + 4;
            context.drawCenteredTextWithShadow(textRenderer, fittedLabel, this.width / 2, labelY,
                    button.active ? ScreenStyle.TEXT : 0xFF777777);
            if (subtitle != null) context.drawCenteredTextWithShadow(textRenderer,
                    ellipsize(subtitle.getString(), maxTextWidth), this.width / 2, y + 15,
                    button.active ? ScreenStyle.MUTED : 0xFF666666);
            if (icon != null) context.drawItem(new ItemStack(icon), x + 12, y + Math.max(2, (height - 16) / 2));
            else if (texture != null) CustomLootTableSprites.draw(context, texture,
                    x + 12, y + Math.max(2, (height - 16) / 2), 16, 16);
        });
        return button;
    }

    private String ellipsize(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String suffix = "…";
        return textRenderer.trimToWidth(value, Math.max(0, maxWidth - textRenderer.getWidth(suffix))) + suffix;
    }

    ModConfig config() { return config; }
    List<String> loadedTableIds() { return loadedTableIds; }
    List<String> catalogTableIds() { return catalogTableIds; }
    boolean serverManaged() { return serverManaged; }
    String activeWorldId() { return activeWorldId; }
    String activeWorldName() { return activeWorldName; }
    String targetWorldId() { return targetWorldId; }
    String targetWorldName() { return targetWorldName; }
    boolean isGlobalTarget() { return targetWorldId == null || targetWorldId.isBlank(); }
    String targetDisplayName() { return isGlobalTarget()
            ? Text.translatable("customloottables.target.global").getString() : targetWorldName; }
    String targetBadgeLabel() { return isGlobalTarget()
            ? Text.translatable("customloottables.home.global_library").getString() : targetWorldName; }
    String targetCompactBadgeLabel() { return isGlobalTarget()
            ? Text.translatable("customloottables.target.global_short").getString() : targetWorldName; }
    LootTargetConfig targetConfig() { return ConfigLoader.target(config, targetWorldId, targetWorldName); }
    List<LootTableDefinition> tables() { return targetConfig().loot_tables; }

    void selectTarget(String worldId, String worldName) {
        if (targetLocked || serverManaged) return;
        selectTargetFields(worldId, worldName);
        client.setScreen(this);
    }

    void selectTargetFieldsOnly(String worldId, String worldName) {
        if (targetLocked || serverManaged) return;
        selectTargetFields(worldId, worldName);
    }

    private void selectTargetFields(String worldId, String worldName) {
        targetWorldId = worldId == null ? "" : worldId;
        targetWorldName = worldName == null || worldName.isBlank() ? "World" : worldName;
        if (!targetWorldId.isBlank()) config.getOrCreateWorldConfig(targetWorldId, targetWorldName);
    }

    private void openGlobalLibrary() {
        selectTargetFields("", Text.translatable("customloottables.target.global").getString());
        client.setScreen(new LootTablesScreen(this));
    }

    private void openWorldLibrary() {
        if (isGlobalTarget()) client.setScreen(new LootTargetSelectScreen(this));
        else client.setScreen(new LootTablesScreen(this));
    }

    LootTableDefinition findTargetTable(String id) {
        return tables().stream().filter(table -> table.id.equals(id)).findFirst().orElse(null);
    }

    LootTableDefinition findGlobalTable(String id) {
        return config.global_config.loot_tables.stream().filter(table -> table.id.equals(id)).findFirst().orElse(null);
    }

    LootTableDefinition findEffectiveTable(String id) {
        LootTableDefinition local = findTargetTable(id);
        return local != null ? local : findGlobalTable(id);
    }

    boolean isInherited(String id) {
        return !isGlobalTarget() && findTargetTable(id) == null && findGlobalTable(id) != null;
    }

    void addTable(LootTableDefinition definition) { if (definition != null) tables().add(definition); }

    void replaceTable(LootTableDefinition original, LootTableDefinition replacement) {
        int index = tables().indexOf(original);
        if (index >= 0) tables().set(index, replacement); else tables().add(replacement);
    }

    void deleteTable(LootTableDefinition definition) { tables().remove(definition); }
    boolean hasTargetTable(String id) { return findTargetTable(id) != null; }
    boolean hasEffectiveTable(String id) { return findEffectiveTable(id) != null; }
    boolean isOriginalRestored(String id) {
        LootTableDefinition local = findTargetTable(id);
        return local != null && !local.enabled;
    }

    void restoreOriginal(String id) {
        LootTableDefinition local = findTargetTable(id);
        if (isGlobalTarget()) {
            if (local != null) deleteTable(local);
            return;
        }
        LootTableDefinition global = findGlobalTable(id);
        if (global == null) {
            if (local != null) deleteTable(local);
            return;
        }
        LootTableDefinition blocker = local == null ? new LootTableDefinition() : local;
        blocker.id = id;
        blocker.type = global.type;
        blocker.mode = "append";
        blocker.enabled = false;
        blocker.entries.clear();
        if (local == null) addTable(blocker);
    }

    LootTableDefinition createWorldOverride(String id) {
        LootTableDefinition inherited = findGlobalTable(id);
        if (inherited == null || isGlobalTarget()) return null;
        LootTableDefinition copy = inherited.copy();
        tables().add(copy);
        return copy;
    }

    int importableGlobalCount() {
        if (isGlobalTarget()) return 0;
        return (int) config.global_config.loot_tables.stream().filter(table -> findTargetTable(table.id) == null).count();
    }

    void importAllFromGlobal() {
        if (isGlobalTarget()) return;
        for (LootTableDefinition table : config.global_config.loot_tables) {
            if (findTargetTable(table.id) == null) tables().add(table.copy());
        }
        client.setScreen(new LootTablesScreen(this));
    }

    void duplicateTable(LootTableDefinition source) {
        if (source == null) return;
        LootTableDefinition copy = source.copy();
        String base = copy.id + "_copy";
        copy.id = base;
        int suffix = 2;
        while (hasTargetTable(copy.id)) copy.id = base + suffix++;
        tables().add(copy);
    }

    private void exportAll() {
        WindowsFileDialogs.saveJson("customloottables-backup.json", path -> {
            try {
                ConfigLoader.exportTo(config, path);
                announce(Text.translatable("customloottables.chat.exported"));
            } catch (IOException error) { reportFileError(error); }
        }, detail -> announce(Text.literal("[Custom Loot Tables] " + detail)));
    }

    private void importAll() {
        WindowsFileDialogs.openJson(path -> {
            try {
                config = ConfigLoader.importFrom(path);
                chooseInitialTarget();
                clearAndInit();
                announce(Text.translatable("customloottables.chat.imported"));
            } catch (IOException error) { reportFileError(error); }
        }, detail -> announce(Text.literal("[Custom Loot Tables] " + detail)));
    }

    private void reportFileError(Throwable error) {
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        announce(Text.literal("[Custom Loot Tables] " + detail));
    }

    private void announce(Text message) { if (client.player != null) client.player.sendMessage(message, false); }

    void save() {
        if (savePending) return;
        List<String> problems = ConfigLoader.validate(config);
        if (!problems.isEmpty()) {
            announce(Text.literal("[Custom Loot Tables] " + problems.getFirst()));
            return;
        }
        if (serverManaged) {
            savePending = true;
            if (!ClientNetworking.save(config, (saved, message) -> {
                savePending = false;
                announce(Text.literal("[Custom Loot Tables] " + message));
                if (saved) {
                    initialJson = ConfigLoader.toJson(config);
                    client.setScreen(parent);
                }
            })) savePending = false;
            return;
        }
        if (!ConfigLoader.saveAndInvalidate(config)) {
            announce(Text.translatable("customloottables.chat.save_failed"));
            return;
        }
        initialJson = ConfigLoader.toJson(config);
        if (client.getServer() != null) client.getServer().execute(() ->
                client.getServer().getCommandManager().executeWithPrefix(
                        client.getServer().getCommandSource().withSilent(), "reload"));
        announce(Text.translatable("customloottables.chat.saved"));
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (!initialJson.equals(ConfigLoader.toJson(config))) {
            client.setScreen(new SaveChangesScreen(this, this::save, () -> client.setScreen(parent)));
        } else client.setScreen(parent);
    }

    @Override public boolean shouldPause() { return true; }
}
