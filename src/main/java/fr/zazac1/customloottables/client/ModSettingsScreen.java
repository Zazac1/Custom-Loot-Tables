package fr.zazac1.customloottables.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Same settings flow and reset controls as Custom Recipes. */
final class ModSettingsScreen extends StyledScreen {
    private final ConfigScreen parent;

    ModSettingsScreen(ConfigScreen parent) {
        super(Text.translatable("customloottables.settings.title"));
        this.parent = parent;
    }

    @Override
    protected boolean usesVanillaBackground() { return true; }

    @Override
    protected void init() {
        int x = width / 2 - 150;
        int y = height / 2 - 54;
        addDrawableChild(ButtonWidget.builder(autoScaleLabel(), button -> {
            parent.config().automatic_gui_scale = !parent.config().automatic_gui_scale;
            ClientInit.setEditorAutomaticGuiScale(parent.config().automatic_gui_scale);
            button.setMessage(autoScaleLabel());
        }).tooltip(Tooltip.of(Text.translatable("customloottables.settings.auto_scale.tooltip")))
                .dimensions(x, y, 276, 20).build());
        addResetButton(x + 280, y, () -> {
            parent.config().automatic_gui_scale = true;
            ClientInit.setEditorAutomaticGuiScale(true);
        });

        addDrawableChild(ButtonWidget.builder(defaultTargetLabel(), button -> client.setScreen(
                LootTargetSelectScreen.forSettings(this, parent, (id, name) -> {
                    parent.config().default_editor_target_id = id.isBlank() ? "global" : id;
                    parent.config().default_editor_target_name = name;
                    return new ModSettingsScreen(parent);
                }))).tooltip(Tooltip.of(Text.translatable("customloottables.settings.default_target.tooltip")))
                .dimensions(x, y + 28, 276, 20).build());
        addResetButton(x + 280, y + 28, () -> {
            parent.config().default_editor_target_id = "global";
            parent.config().default_editor_target_name = Text.translatable("customloottables.target.global").getString();
        });

        addDrawableChild(ButtonWidget.builder(preloadLabel(), button -> {
            parent.config().preload_loot_tables_on_startup = !parent.config().preload_loot_tables_on_startup;
            ClientInit.refreshPreloadedCatalog(parent.config().preload_loot_tables_on_startup);
            button.setMessage(preloadLabel());
        }).tooltip(Tooltip.of(Text.translatable("customloottables.settings.preload.tooltip")))
                .dimensions(x, y + 56, 276, 20).build());
        addResetButton(x + 280, y + 56, () -> {
            parent.config().preload_loot_tables_on_startup = true;
            ClientInit.refreshPreloadedCatalog(true);
        });

        int saveX = width / 2 - 100;
        int saveY = y + 96;
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.save"), button -> close())
                .dimensions(saveX, saveY, 200, 22).build());
        addDrawable((ctx, mouseX, mouseY, delta) ->
                CustomLootTableSprites.draw(ctx, CustomLootTableSprites.SAVE, saveX + 8, saveY + 3, 16, 16));
    }

    private void addResetButton(int x, int y, Runnable reset) {
        addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            reset.run();
            client.setScreen(new ModSettingsScreen(parent));
        }).tooltip(Tooltip.of(Text.translatable("customloottables.settings.reset")))
                .dimensions(x, y, 20, 20).build());
        addDrawable((ctx, mouseX, mouseY, delta) ->
                CustomLootTableSprites.draw(ctx, CustomLootTableSprites.RESET, x + 2, y + 2, 16, 16));
    }

    private Text autoScaleLabel() {
        return Text.translatable("customloottables.settings.auto_scale", yesNo(parent.config().automatic_gui_scale));
    }

    private Text defaultTargetLabel() {
        String name = "global".equals(parent.config().default_editor_target_id)
                ? Text.translatable("customloottables.target.global").getString()
                : parent.config().default_editor_target_name;
        return Text.translatable("customloottables.settings.default_target", name);
    }

    private Text preloadLabel() {
        return Text.translatable("customloottables.settings.preload",
                yesNo(parent.config().preload_loot_tables_on_startup));
    }

    private Text yesNo(boolean value) {
        return Text.translatable(value ? "customloottables.state.on" : "customloottables.state.off")
                .formatted(value ? Formatting.GREEN : Formatting.RED);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 86, ScreenStyle.TEXT);
    }

    @Override public void close() { client.setScreen(parent); }
}
