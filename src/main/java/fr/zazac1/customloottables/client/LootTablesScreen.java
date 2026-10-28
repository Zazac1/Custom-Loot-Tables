package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.LootEntryDefinition;
import fr.zazac1.customloottables.LootTableDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full-width target library, following Custom Recipes' library layout. */
final class LootTablesScreen extends StyledScreen {
    private static final int PAD = 8;
    private static final int SEARCH_Y = 30;
    private static final int SEARCH_H = 18;
    private static final int HEADER_Y = 52;
    private static final int HEADER_H = 16;
    private static final int ROW_H = 24;
    private static final int DETAIL_H = 86;

    private final ConfigScreen configScreen;
    private TextFieldWidget search;
    private ButtonWidget editButton;
    private ButtonWidget duplicateButton;
    private ButtonWidget toggleButton;
    private String query = "";
    private String selectedId;
    private int scroll;
    private boolean draggingScrollbar;

    LootTablesScreen(ConfigScreen configScreen) {
        super(Text.translatable("customloottables.tables.title"));
        this.configScreen = configScreen;
    }

    private int tableLeft() { return PAD; }
    private int tableRight() { return width - PAD; }
    private int iconRight() { return tableLeft() + 42; }
    private int statusRight() { return iconRight() + 52; }
    private int modeRight() { return statusRight() + 66; }
    private int deleteX() { return tableRight() - 22; }
    private int stateX() { return Math.max(modeRight() + 90, deleteX() - 112); }
    private int scrollbarX() { return deleteX() - RecipeStyleScrollBar.THUMB_WIDTH; }
    private int searchClearX() { return tableRight() - 20; }
    private int rowsTop() { return HEADER_Y + HEADER_H; }
    private int footerSaveY() { return height - 24; }
    private int footerAddY() { return footerSaveY() - 24; }
    private int detailY() { return footerAddY() - DETAIL_H - 4; }
    private int rowsBottom() { return selectedRow() == null ? footerAddY() - 4 : detailY() - 4; }
    private int visibleRows() { return Math.max(1, (rowsBottom() - rowsTop()) / ROW_H); }
    private int maxScroll() { return Math.max(0, rows().size() - visibleRows()); }

    @Override
    protected void init() {
        search = new TextFieldWidget(textRenderer, tableLeft() + 3, SEARCH_Y + 6,
                tableRight() - tableLeft() - 22, SEARCH_H,
                Text.translatable("customloottables.search"));
        search.setDrawsBackground(false);
        search.setPlaceholder(Text.translatable("customloottables.search"));
        search.setText(query);
        search.setChangedListener(value -> {
            query = value;
            scroll = 0;
            if (selectedRow() == null) selectedId = null;
        });
        addDrawableChild(search);

        int footerWidth = Math.min(220, width - 24);
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.add_table"), button ->
                        client.setScreen(new LootTableEditorScreen(configScreen, this, null)))
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.library.add")))
                .dimensions(width / 2 - footerWidth / 2, footerAddY(), footerWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.save"), button -> configScreen.save())
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.library.save")))
                .dimensions(width / 2 - footerWidth / 2, footerSaveY(), footerWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.back"), button -> close())
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.back")))
                .dimensions(PAD, footerSaveY(), Math.min(86, Math.max(54, width / 6)), 20).build());

        if (!configScreen.isGlobalTarget() && configScreen.importableGlobalCount() > 0) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.home.import_global_short"), button ->
                            configScreen.importAllFromGlobal())
                    .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.library.import_global")))
                    .dimensions(width - PAD - Math.min(130, width / 4), footerSaveY(), Math.min(130, width / 4), 20).build());
        }

        RowData selected = selectedRow();
        int actionY = detailY() + DETAIL_H - 24;
        int actionW = Math.max(54, Math.min(110, (tableRight() - tableLeft() - 16) / 3));
        int actionsW = actionW * 3 + 8;
        int actionX = width / 2 - actionsW / 2;
        editButton = addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.preview.edit"), button -> editSelected())
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.library.edit")))
                .dimensions(actionX, actionY, actionW, 20).build());
        duplicateButton = addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.duplicate"), button -> duplicateSelected())
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.library.duplicate")))
                .dimensions(actionX + actionW + 4, actionY, actionW, 20).build());
        toggleButton = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> toggleSelected())
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.library.toggle")))
                .dimensions(actionX + (actionW + 4) * 2, actionY, actionW, 20).build());
        boolean hasSelection = selected != null;
        editButton.visible = duplicateButton.visible = toggleButton.visible = hasSelection;
        editButton.active = duplicateButton.active = toggleButton.active = hasSelection;
        if (hasSelection) toggleButton.setMessage(Text.translatable(selected.table().enabled
                ? "customloottables.builder.disabled_short" : "customloottables.builder.enabled_short"));
    }

    private List<RowData> rows() {
        String lowered = query.strip().toLowerCase(Locale.ROOT);
        List<RowData> result = new ArrayList<>();
        // Like Custom Recipes, a world's library contains only definitions stored
        // in that world. Global definitions still apply at runtime, but stay in the
        // Global Library until the user explicitly imports/copies them here.
        for (LootTableDefinition table : configScreen.tables()) result.add(new RowData(table));
        return result.stream().filter(row -> lowered.isEmpty()
                || row.table().id.toLowerCase(Locale.ROOT).contains(lowered)
                || row.table().mode.toLowerCase(Locale.ROOT).contains(lowered)
                || row.table().type.toLowerCase(Locale.ROOT).contains(lowered)).toList();
    }

    private RowData selectedRow() {
        if (selectedId == null) return null;
        return rows().stream().filter(row -> row.table().id.equals(selectedId)).findFirst().orElse(null);
    }

    private LootTableDefinition editable(RowData row) {
        return row == null ? null : row.table();
    }

    private void editSelected() {
        LootTableDefinition table = editable(selectedRow());
        if (table != null) client.setScreen(new LootTableEditorScreen(configScreen, this, table));
    }

    private void duplicateSelected() {
        RowData row = selectedRow();
        if (row == null) return;
        configScreen.duplicateTable(row.table());
        selectedId = null;
        clearAndInit();
    }

    private void toggleSelected() {
        LootTableDefinition table = editable(selectedRow());
        if (table == null) return;
        table.enabled = !table.enabled;
        clearAndInit();
    }

    private void delete(RowData row) {
        if (row == null) return;
        configScreen.deleteTable(row.table());
        if (row.table().id.equals(selectedId)) selectedId = null;
        scroll = Math.min(scroll, maxScroll());
        clearAndInit();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fill(tableLeft(), SEARCH_Y, tableRight(), SEARCH_Y + SEARCH_H, 0xFF000000);
        context.drawBorder(tableLeft(), SEARCH_Y, tableRight() - tableLeft(), SEARCH_H, 0xFFAAAAAA);
        if (mouseX >= searchClearX() && mouseX < tableRight() && mouseY >= SEARCH_Y && mouseY < SEARCH_Y + SEARCH_H) {
            context.fill(searchClearX(), SEARCH_Y, tableRight(), SEARCH_Y + SEARCH_H, 0x443355BB);
        }
        context.fill(tableLeft(), HEADER_Y, tableRight(), rowsBottom(), 0x99101618);
        context.drawBorder(tableLeft(), HEADER_Y, tableRight() - tableLeft(), rowsBottom() - HEADER_Y, 0xFF414B4A);
        if (selectedRow() != null) {
            context.fill(tableLeft(), detailY(), tableRight(), detailY() + DETAIL_H, 0xCC10161C);
            context.drawBorder(tableLeft(), detailY(), tableRight() - tableLeft(), DETAIL_H, 0xFF40506A);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        LootTargetBadge.draw(context, client, configScreen, configScreen.targetBadgeLabel());
        Text found = Text.translatable("customloottables.library.found", rows().size());
        context.drawTextWithShadow(textRenderer, found,
                Math.max(tableLeft(), tableRight() - textRenderer.getWidth(found)), 18, 0xFFFFFF55);
        CustomLootTableSprites.draw(context, CustomLootTableSprites.REJECT, searchClearX() + 1, SEARCH_Y, 18, 18);
        drawHeaders(context);
        drawRows(context, mouseX, mouseY);
        drawDetails(context, selectedRow());
        RecipeStyleScrollBar.draw(context, scrollbarX(), rowsTop(), rowsBottom() - rowsTop(),
                scroll, maxScroll(), draggingScrollbar);
        CustomLootTableSprites.draw(context, CustomLootTableSprites.ADD,
                width / 2 - Math.min(220, width - 24) / 2 + 3, footerAddY() + 1, 18, 18);
        CustomLootTableSprites.draw(context, CustomLootTableSprites.SAVE,
                width / 2 - Math.min(220, width - 24) / 2 + 3, footerSaveY() + 1, 18, 18);
    }

    private void drawHeaders(DrawContext context) {
        int y = HEADER_Y + 4;
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.library.column.output"),
                (tableLeft() + iconRight()) / 2, y, ScreenStyle.MUTED);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.library.column.status"),
                (iconRight() + statusRight()) / 2, y, ScreenStyle.MUTED);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.library.column.mode"),
                (statusRight() + modeRight()) / 2, y, ScreenStyle.MUTED);
        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.library.column.table"),
                modeRight() + 6, y, ScreenStyle.MUTED);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.library.column.state"),
                (stateX() + scrollbarX()) / 2, y, ScreenStyle.MUTED);
        context.drawVerticalLine(iconRight(), HEADER_Y, rowsBottom() - 1, 0xFF414B4A);
        context.drawVerticalLine(statusRight(), HEADER_Y, rowsBottom() - 1, 0xFF414B4A);
        context.drawVerticalLine(modeRight(), HEADER_Y, rowsBottom() - 1, 0xFF414B4A);
        context.drawVerticalLine(stateX(), HEADER_Y, rowsBottom() - 1, 0xFF414B4A);
    }

    private void drawRows(DrawContext context, int mouseX, int mouseY) {
        List<RowData> data = rows();
        scroll = Math.min(scroll, Math.max(0, data.size() - visibleRows()));
        if (data.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.tables.empty"),
                    width / 2, rowsTop() + 16, ScreenStyle.MUTED);
            return;
        }
        int end = Math.min(data.size(), scroll + visibleRows());
        for (int index = scroll; index < end; index++) {
            RowData row = data.get(index);
            int y = rowsTop() + (index - scroll) * ROW_H;
            boolean selected = row.table().id.equals(selectedId);
            boolean hovered = mouseX >= tableLeft() && mouseX < scrollbarX()
                    && mouseY >= y && mouseY < y + ROW_H;
            int color = !row.table().enabled ? 0x664A2525 : 0x66304830;
            if (hovered) color |= 0x22000000;
            context.fill(tableLeft() + 1, y, scrollbarX(), y + ROW_H - 1, color);
            if (selected) context.drawBorder(tableLeft() + 1, y, scrollbarX() - tableLeft() - 1, ROW_H - 1, 0xFFFFFFFF);

            drawFirstItem(context, row.table(), tableLeft() + 12, y + 4);
            CustomLootTableSprites.draw(context, row.table().enabled ? CustomLootTableSprites.ACCEPT
                    : CustomLootTableSprites.REJECT, iconRight() + 16, y + 3, 18, 18);
            context.drawCenteredTextWithShadow(textRenderer, row.table().mode.toUpperCase(Locale.ROOT),
                    (statusRight() + modeRight()) / 2, y + 8, ScreenStyle.TEXT);
            context.drawTextWithShadow(textRenderer, trim(row.table().id, stateX() - modeRight() - 12),
                    modeRight() + 6, y + 8, ScreenStyle.TEXT);
            String state = Text.translatable(configScreen.isGlobalTarget()
                    ? "customloottables.library.state.global" : "customloottables.library.state.world").getString();
            context.drawCenteredTextWithShadow(textRenderer, trim(state, scrollbarX() - stateX() - 8),
                    (stateX() + scrollbarX()) / 2, y + 8, ScreenStyle.GREEN);
            CustomLootTableSprites.draw(context, CustomLootTableSprites.REJECT,
                    deleteX() + 1, y + 3, 18, 18);
        }
    }

    private void drawDetails(DrawContext context, RowData row) {
        if (row == null) return;
        LootTableDefinition table = row.table();
        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.library.detail"),
                tableLeft() + 8, detailY() + 7, 0xFFFFCC55);
        String info = Text.translatable("customloottables.library.detail_line", table.type,
                table.mode, table.rolls, table.entries.size()).getString();
        context.drawTextWithShadow(textRenderer, trim(table.id, tableRight() - tableLeft() - 16),
                tableLeft() + 8, detailY() + 21, ScreenStyle.TEXT);
        context.drawTextWithShadow(textRenderer, trim(info, tableRight() - tableLeft() - 16),
                tableLeft() + 8, detailY() + 34, ScreenStyle.MUTED);
        int x = tableRight() - 10;
        int shown = Math.min(8, table.entries.size());
        for (int i = shown - 1; i >= 0; i--) {
            x -= 22;
            CustomLootTableSprites.draw(context, CustomLootTableSprites.SLOT, x, detailY() + 9, 20, 20);
            drawItem(context, table.entries.get(i), x + 2, detailY() + 11);
        }
    }

    private void drawFirstItem(DrawContext context, LootTableDefinition table, int x, int y) {
        if (table.entries.isEmpty()) context.drawItem(new ItemStack(Items.CHEST), x, y);
        else drawItem(context, table.entries.getFirst(), x, y);
    }

    private void drawItem(DrawContext context, LootEntryDefinition entry, int x, int y) {
        Identifier id = Identifier.tryParse(entry.item_id);
        if (id != null && Registries.ITEM.containsId(id)) context.drawItem(new ItemStack(Registries.ITEM.get(id)), x, y);
    }

    private String trim(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String suffix = "…";
        return textRenderer.trimToWidth(value, Math.max(0, maxWidth - textRenderer.getWidth(suffix))) + suffix;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= searchClearX() && mouseX < tableRight()
                && mouseY >= SEARCH_Y && mouseY < SEARCH_Y + SEARCH_H) {
            query = "";
            search.setText("");
            scroll = 0;
            return true;
        }
        if (button == 0 && RecipeStyleScrollBar.hit(mouseX, mouseY, scrollbarX(), rowsTop(),
                rowsBottom() - rowsTop(), maxScroll())) {
            draggingScrollbar = true;
            updateScrollbar(mouseY);
            return true;
        }
        List<RowData> data = rows();
        int rowIndex = ((int) mouseY - rowsTop()) / ROW_H;
        if (button == 0 && mouseX >= tableLeft() && mouseX < scrollbarX()
                && mouseY >= rowsTop() && mouseY < rowsBottom()
                && rowIndex >= 0 && rowIndex < visibleRows() && scroll + rowIndex < data.size()) {
            RowData row = data.get(scroll + rowIndex);
            if (mouseX >= deleteX()) {
                delete(row);
                return true;
            }
            if (mouseX >= iconRight() && mouseX < statusRight()) {
                selectedId = row.table().id;
                toggleSelected();
                return true;
            }
            if (row.table().id.equals(selectedId)) {
                editSelected();
                return true;
            }
            selectedId = row.table().id;
            clearAndInit();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            updateScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollbar(double mouseY) {
        scroll = RecipeStyleScrollBar.valueFromMouse(mouseY, rowsTop(), rowsBottom() - rowsTop(), maxScroll());
    }

    @Override public void close() { client.setScreen(configScreen); }
    private record RowData(LootTableDefinition table) {}
}
