package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.LootEntryDefinition;
import fr.zazac1.customloottables.LootTableDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class LootEntriesScreen extends StyledScreen {
    private static final int ROW_HEIGHT = 28;
    private final Screen parent;
    private final LootTableDefinition table;
    private int scroll;

    LootEntriesScreen(Screen parent, LootTableDefinition table) {
        super(Text.translatable("customloottables.entries.title"));
        this.parent = parent;
        this.table = table;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.back"),
                button -> close()).dimensions(width / 2 - 104, height - 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.entries.add"),
                button -> client.setScreen(new LootEntryEditorScreen(this, table, null)))
                .dimensions(width / 2 + 4, height - 28, 100, 20).build());
    }

    private int visibleRows() { return Math.max(1, (height - 70) / ROW_HEIGHT); }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, ScreenStyle.TEXT);
        int left = Math.max(12, width / 2 - 180);
        int right = Math.min(width - 12, width / 2 + 180);
        scroll = Math.min(scroll, Math.max(0, table.entries.size() - visibleRows()));
        for (int row = 0; row < visibleRows() && scroll + row < table.entries.size(); row++) {
            LootEntryDefinition entry = table.entries.get(scroll + row);
            int y = 38 + row * ROW_HEIGHT;
            ScreenStyle.panel(context, left, y, right, y + 24);
            Identifier id = Identifier.tryParse(entry.item_id);
            if (id != null && Registries.ITEM.containsId(id)) {
                context.drawItem(new ItemStack(Registries.ITEM.get(id)), left + 4, y + 4);
            }
            context.drawTextWithShadow(textRenderer, entry.item_id, left + 25, y + 4, ScreenStyle.TEXT);
            context.drawTextWithShadow(textRenderer,
                    Text.translatable("customloottables.entries.summary", entry.weight, entry.min_count, entry.max_count),
                    left + 25, y + 14, ScreenStyle.MUTED);
            context.drawTextWithShadow(textRenderer, "✕", right - 16, y + 8, ScreenStyle.RED);
        }
        if (table.entries.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customloottables.entries.empty"), width / 2, height / 2, ScreenStyle.MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int left = Math.max(12, width / 2 - 180);
        int right = Math.min(width - 12, width / 2 + 180);
        int row = ((int) mouseY - 38) / ROW_HEIGHT;
        if (mouseX < left || mouseX >= right || mouseY < 38 || row < 0 || row >= visibleRows()
                || scroll + row >= table.entries.size()) return false;
        LootEntryDefinition entry = table.entries.get(scroll + row);
        if (mouseX >= right - 28) table.entries.remove(entry);
        else client.setScreen(new LootEntryEditorScreen(this, table, entry));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, table.entries.size() - visibleRows());
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override public void close() { client.setScreen(parent); }
}
