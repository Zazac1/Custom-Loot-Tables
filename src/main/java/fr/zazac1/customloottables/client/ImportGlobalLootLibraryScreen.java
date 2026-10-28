package fr.zazac1.customloottables.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Confirmation for the non-destructive bulk import into one selected world. */
final class ImportGlobalLootLibraryScreen extends Screen {
    private final ConfigScreen config;
    private final LootTablesScreen returnTo;
    private final int tableCount;

    ImportGlobalLootLibraryScreen(ConfigScreen config, LootTablesScreen returnTo, int tableCount) {
        super(Text.translatable("customloottables.library.import.title"));
        this.config = config;
        this.returnTo = returnTo;
        this.tableCount = tableCount;
    }

    @Override
    protected void init() {
        int y = height / 2 + 22;
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.library.add"), button -> apply())
                .dimensions(width / 2 - 82, y, 76, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.cancel"),
                        button -> client.setScreen(returnTo))
                .dimensions(width / 2 + 6, y, 76, 20).build());
    }

    private void apply() {
        config.importAllFromGlobal();
        client.setScreen(new LootTablesScreen(config));
    }

    @Override
    public void close() {
        client.setScreen(returnTo);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        returnTo.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x98000000);

        int panelWidth = Math.min(340, width - 20);
        int panelHeight = 112;
        int panelX = width / 2 - panelWidth / 2;
        int panelY = height / 2 - panelHeight / 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xF0181B1E);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF6E7678);
        context.drawHorizontalLine(panelX + 8, panelX + panelWidth - 9, panelY + 62, 0xFF3E4749);
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelY + 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customloottables.library.import.confirm", tableCount,
                        config.targetDisplayName()), width / 2, panelY + 31, 0xFFE0E6E3);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customloottables.library.import.safe"),
                width / 2, panelY + 45, ScreenStyle.MUTED);
    }

    @Override public boolean shouldPause() { return true; }
}
