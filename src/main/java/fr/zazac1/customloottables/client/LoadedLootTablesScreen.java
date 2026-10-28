package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.DefaultLootTableCatalog;
import fr.zazac1.customloottables.LootEntryDefinition;
import fr.zazac1.customloottables.LootTableDefinition;
import fr.zazac1.customloottables.LootTablePreview;
import fr.zazac1.customloottables.LootTablePreviewService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Two-pane browser adapted from Custom Recipe's vanilla recipe browser. */
final class LoadedLootTablesScreen extends StyledScreen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 56;
    private static final int SCROLLBAR_WIDTH = RecipeStyleScrollBar.THUMB_WIDTH;
    private final ConfigScreen configScreen;
    private TextFieldWidget search;
    private String query = "";
    private String source = "all";
    private String category = "all";
    private String status = "all";
    private int scroll;
    private boolean restoreSearchFocus;
    private int pendingSearchTicks = -1;
    private String selectedId;
    private LootTablePreview selectedPreview;
    private boolean previewLoading;
    private boolean showResult;
    private boolean draggingScrollbar;

    LoadedLootTablesScreen(ConfigScreen configScreen) {
        super(Text.translatable("customloottables.defaults.title"));
        this.configScreen = configScreen;
    }

    private int listX() { return 6; }
    private int listW() { return Math.max(190, Math.min(390, width / 2 + 10)); }
    private int listRight() { return listX() + listW(); }
    private int previewX() { return listRight() + 8; }
    private int previewW() { return Math.max(100, width - previewX() - 6); }
    private int panelBottom() { return height - 34; }
    private int panelH() { return panelBottom() - TOP; }
    private int rowsY() { return TOP + 20; }
    private int visibleRows() { return Math.max(1, (panelBottom() - rowsY() - 3) / ROW_HEIGHT); }
    private int scrollbarX() { return listRight() - SCROLLBAR_WIDTH; }
    private int maxScroll() { return Math.max(0, filtered().size() - visibleRows()); }
    private int searchClearX() { return listX() + listW() - 22; }
    private int scrollbarH() { return panelBottom() - rowsY() - 3; }

    @Override
    protected void init() {
        search = new TextFieldWidget(textRenderer, listX() + 4, 36, listW() - 22, 18,
                Text.translatable("customloottables.defaults.search"));
        search.setDrawsBackground(false);
        search.setText(query);
        search.setPlaceholder(Text.translatable("customloottables.defaults.search"));
        search.setChangedListener(value -> {
            query = value;
            restoreSearchFocus = true;
            pendingSearchTicks = 3;
        });
        addDrawableChild(search);
        if (restoreSearchFocus) {
            setFocused(search);
            search.setFocused(true);
            restoreSearchFocus = false;
        }

        int filterGap = 3;
        int filterW = Math.max(38, (previewW() - filterGap * 2) / 3);
        addDrawableChild(ButtonWidget.builder(sourceLabel(), button -> {
            source = switch (source) { case "all" -> "vanilla"; case "vanilla" -> "modded"; default -> "all"; };
            resetList();
        }).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.defaults.source")))
                .dimensions(previewX(), 30, filterW, 20).build());
        addDrawableChild(ButtonWidget.builder(categoryLabel(), button -> {
            category = switch (category) {
                case "all" -> "chest"; case "chest" -> "block"; case "block" -> "entity";
                case "entity" -> "gameplay"; case "gameplay" -> "other"; default -> "all";
            };
            resetList();
        }).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.defaults.type")))
                .dimensions(previewX() + filterW + filterGap, 30, filterW, 20).build());
        addDrawableChild(ButtonWidget.builder(statusLabel(), button -> {
            status = switch (status) { case "all" -> "configured"; case "configured" -> "available"; default -> "all"; };
            resetList();
        }).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.defaults.status")))
                .dimensions(previewX() + (filterW + filterGap) * 2, 30, filterW, 20).build());

        List<String> ids = filtered();
        scroll = Math.min(scroll, Math.max(0, ids.size() - visibleRows()));
        int rowButtonW = listW() - SCROLLBAR_WIDTH - 4;
        for (int row = 0; row < visibleRows() && scroll + row < ids.size(); row++) {
            String id = ids.get(scroll + row);
            int y = rowsY() + row * ROW_HEIGHT;
            addDrawableChild(ButtonWidget.builder(Text.empty(), button -> select(id))
                    .dimensions(listX() + 2, y, rowButtonW, 20).build());
        }

        if (selectedId != null) addPreviewControls();
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.back"),
                button -> close()).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.back")))
                .dimensions(width / 2 - 50, height - 27, 100, 20).build());
    }

    private void addPreviewControls() {
        int x = previewX() + 6;
        int w = previewW() - 12;
        int actionY = panelBottom() - 48;
        ButtonWidget view = ButtonWidget.builder(Text.translatable(showResult
                        ? "customloottables.preview.view_result" : "customloottables.preview.view_original"), button -> {
                    showResult = !showResult;
                    clearAndInit();
                }).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.defaults.view")))
                .dimensions(x, TOP + 44, w, 18).build();
        view.active = selectedPreview != null;
        addDrawableChild(view);

        int gap = 4;
        int buttonW = Math.max(42, (w - gap) / 2);
        addDrawableChild(ButtonWidget.builder(Text.translatable(configScreen.hasEffectiveTable(selectedId)
                        ? "customloottables.preview.edit" : "customloottables.preview.customize"), button -> editSelected())
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.defaults.customize")))
                .dimensions(x, actionY, buttonW, 20).build());
        ButtonWidget copy = ButtonWidget.builder(Text.translatable("customloottables.preview.copy_original"),
                button -> copyOriginal()).tooltip(Tooltip.of(Text.translatable("customloottables.preview.copy_warning")))
                .dimensions(x + buttonW + gap, actionY, buttonW, 20).build();
        copy.active = selectedPreview != null && selectedPreview.available() && !selectedPreview.entries().isEmpty();
        addDrawableChild(copy);
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.preview.empty"),
                button -> emptySelected()).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.defaults.empty")))
                .dimensions(x, actionY + 22, buttonW, 20).build());
        ButtonWidget restore = ButtonWidget.builder(Text.translatable("customloottables.preview.restore"),
                button -> restoreOriginal()).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.defaults.restore")))
                .dimensions(x + buttonW + gap, actionY + 22, buttonW, 20).build();
        restore.active = configScreen.hasTargetTable(selectedId);
        addDrawableChild(restore);
        addDrawable((ctx, mouseX, mouseY, delta) -> {
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.EYE, x + 2, TOP + 45, 16, 16);
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.ADD, x + 2, actionY + 2, 16, 16);
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.ADD, x + buttonW + gap + 2, actionY + 2, 16, 16);
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.REJECT, x + 2, actionY + 24, 16, 16);
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.RESET,
                    x + buttonW + gap + 2, actionY + 24, 16, 16);
        });
    }

    private Text sourceLabel() { return Text.translatable("customloottables.defaults.source." + source); }
    private Text categoryLabel() { return Text.translatable("customloottables.defaults.category." + category); }
    private Text statusLabel() { return Text.translatable("customloottables.defaults.status." + status); }

    private void resetList() { scroll = 0; clearAndInit(); }

    private List<String> filtered() {
        String lowered = query.strip().toLowerCase(Locale.ROOT);
        return configScreen.catalogTableIds().stream().filter(id -> {
            String tableSource = DefaultLootTableCatalog.source(id);
            String tableCategory = DefaultLootTableCatalog.category(id);
            boolean configured = configScreen.hasEffectiveTable(id);
            boolean sourceMatch = "all".equals(source) || source.equals(tableSource);
            boolean categoryMatch = "all".equals(category) || category.equals(tableCategory)
                    || ("other".equals(category) && !List.of("chest", "block", "entity", "gameplay").contains(tableCategory));
            boolean statusMatch = "all".equals(status) || ("configured".equals(status) && configured)
                    || ("available".equals(status) && !configured);
            return sourceMatch && categoryMatch && statusMatch
                    && (lowered.isEmpty() || id.toLowerCase(Locale.ROOT).contains(lowered));
        }).toList();
    }

    private void select(String id) {
        selectedId = id;
        selectedPreview = null;
        previewLoading = true;
        showResult = false;
        if (configScreen.serverManaged()) {
            if (!ClientNetworking.requestPreview(id)) {
                previewLoading = false;
                selectedPreview = ClientLootTablePreviewLoader.load(id);
            }
        } else {
            LootTablePreview preview = client.getServer() == null ? null : LootTablePreviewService.preview(id);
            if (preview == null || !preview.available()) preview = ClientLootTablePreviewLoader.load(id);
            selectedPreview = preview;
            previewLoading = false;
        }
        clearAndInit();
    }

    void applyPreview(LootTablePreview preview) {
        if (preview == null || selectedId == null || !selectedId.equals(preview.tableId())) return;
        selectedPreview = preview;
        previewLoading = false;
        clearAndInit();
    }

    private void editSelected() {
        LootTableDefinition existing = configScreen.findTargetTable(selectedId);
        if (existing != null) {
            client.setScreen(new LootTableEditorScreen(configScreen, this, existing));
            return;
        }
        LootTableDefinition definition = new LootTableDefinition();
        definition.id = selectedId;
        definition.type = previewType();
        definition.entries.clear();
        client.setScreen(new LootTableEditorScreen(configScreen, this, definition, true));
    }

    private void copyOriginal() {
        if (selectedPreview == null || selectedPreview.entries().isEmpty()) return;
        LootTableDefinition definition = new LootTableDefinition();
        definition.id = selectedId;
        definition.type = previewType();
        definition.mode = "replace";
        definition.rolls = 1;
        definition.entries.clear();
        for (LootTablePreview.Entry entry : selectedPreview.entries()) {
            LootEntryDefinition copy = new LootEntryDefinition();
            copy.item_id = entry.itemId();
            copy.weight = Math.max(1, entry.weight());
            copy.min_count = Math.max(1, entry.minCount());
            copy.max_count = Math.max(copy.min_count, entry.maxCount());
            definition.entries.add(copy);
        }
        installDefinition(definition);
        client.setScreen(new LootTableEditorScreen(configScreen, this, definition));
    }

    private void emptySelected() {
        LootTableDefinition definition = new LootTableDefinition();
        definition.id = selectedId;
        definition.type = previewType();
        definition.mode = "empty";
        definition.entries.clear();
        installDefinition(definition);
        showResult = true;
        clearAndInit();
    }

    private void restoreOriginal() {
        configScreen.restoreOriginal(selectedId);
        showResult = false;
        clearAndInit();
    }

    private void installDefinition(LootTableDefinition definition) {
        LootTableDefinition local = configScreen.findTargetTable(selectedId);
        if (local != null) configScreen.replaceTable(local, definition);
        else configScreen.addTable(definition);
    }

    private String previewType() {
        String type = selectedPreview == null ? DefaultLootTableCatalog.category(selectedId) : selectedPreview.type();
        return List.of("chest", "block", "entity", "fishing", "gift").contains(type) ? type : "generic";
    }

    private List<LootTablePreview.Entry> shownEntries() {
        if (selectedPreview == null) return List.of();
        if (!showResult) return selectedPreview.entries();
        LootTableDefinition custom = configScreen.findEffectiveTable(selectedId);
        if (custom == null || !custom.enabled) return selectedPreview.entries();
        List<LootTablePreview.Entry> result = new ArrayList<>();
        if ("append".equals(custom.mode)) result.addAll(selectedPreview.entries());
        if (!"empty".equals(custom.mode)) {
            for (LootEntryDefinition entry : custom.entries) result.add(new LootTablePreview.Entry(
                    entry.item_id, entry.weight, entry.min_count, entry.max_count, -1, false));
        }
        return result;
    }

    @Override
    public void tick() {
        super.tick();
        if (pendingSearchTicks > 0 && --pendingSearchTicks == 0) resetList();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        drawPanel(context, listX(), TOP, listW(), panelH());
        drawPanel(context, previewX(), TOP, previewW(), panelH());
        context.fill(listX(), 30, listRight(), 50, 0xFF000000);
        context.drawBorder(listX(), 30, listW(), 20, 0xFFAAAAAA);
        if (mouseX >= searchClearX() && mouseX < listRight() && mouseY >= 30 && mouseY < 50) {
            context.fill(searchClearX(), 30, listRight(), 50, 0x443355BB);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        LootTargetBadge.draw(context, client, configScreen, configScreen.targetCompactBadgeLabel());
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, ScreenStyle.TEXT);
        CustomLootTableSprites.draw(context, CustomLootTableSprites.REJECT,
                searchClearX() + 1, 31, 18, 18);

        List<String> ids = filtered();
        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.defaults.results", ids.size()),
                listX() + 4, TOP + 6, 0xFFFFEE88);
        for (int row = 0; row < visibleRows() && scroll + row < ids.size(); row++) {
            String id = ids.get(scroll + row);
            int y = rowsY() + row * ROW_HEIGHT;
            boolean configured = configScreen.hasEffectiveTable(id);
            boolean selected = id.equals(selectedId);
            context.fill(listX() + 2, y, scrollbarX() - 2, y + 20,
                    selected ? 0x665577AA : configured ? 0x44337733 : 0x33000000);
            Item icon = representativeItem(id);
            if (icon != Items.AIR) context.drawItem(new ItemStack(icon), listX() + 4, y + 2);
            int textX = listX() + 23;
            int badgeW = configured ? 50 : 0;
            String label = ellipsize(id, Math.max(20, scrollbarX() - textX - badgeW - 6));
            context.drawTextWithShadow(textRenderer, label, textX, y + 6,
                    configured ? ScreenStyle.GREEN : ScreenStyle.TEXT);
            if (configured) context.drawTextWithShadow(textRenderer,
                    configScreen.isOriginalRestored(id) ? "ORIGINAL" : "EDIT",
                    scrollbarX() - 50, y + 6,
                    configScreen.isOriginalRestored(id) ? ScreenStyle.MUTED : ScreenStyle.GREEN);
        }
        renderScrollbar(context, ids.size());
        renderPreview(context, mouseX, mouseY);
    }

    private void renderPreview(DrawContext context, int mouseX, int mouseY) {
        int center = previewX() + previewW() / 2;
        if (selectedId == null) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.preview.select"),
                    center, TOP + 16, ScreenStyle.MUTED);
            return;
        }
        context.drawCenteredTextWithShadow(textRenderer, ellipsize(selectedId, previewW() - 12),
                center, TOP + 8, ScreenStyle.TEXT);
        LootTableDefinition configured = configScreen.findEffectiveTable(selectedId);
        String statusText = configured == null ? Text.translatable("customloottables.preview.unmodified").getString()
                : configScreen.isOriginalRestored(selectedId) ? Text.translatable("customloottables.preview.restored").getString()
                : configured.mode.toUpperCase(Locale.ROOT);
        context.drawCenteredTextWithShadow(textRenderer, statusText, center, TOP + 21,
                configured == null ? ScreenStyle.MUTED : ScreenStyle.GREEN);
        if (previewLoading) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.preview.loading"),
                    center, TOP + 78, ScreenStyle.MUTED);
            return;
        }
        if (selectedPreview == null || !selectedPreview.available()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.preview.unavailable"),
                    center, TOP + 78, ScreenStyle.RED);
            return;
        }
        String details = Text.translatable("customloottables.preview.details", selectedPreview.poolRolls().size(),
                selectedPreview.entries().size()).getString();
        if (selectedPreview.conditional()) details += " · " + Text.translatable("customloottables.preview.conditional").getString();
        context.drawCenteredTextWithShadow(textRenderer, ellipsize(details, previewW() - 12), center, TOP + 33, ScreenStyle.MUTED);

        List<LootTablePreview.Entry> entries = shownEntries();
        int columns = Math.max(1, (previewW() - 18) / 24);
        int gridX = center - Math.min(columns, Math.max(1, entries.size())) * 12 + 2;
        int gridY = TOP + 70;
        int maxRows = Math.max(1, (panelBottom() - 54 - gridY) / 24);
        int limit = Math.min(entries.size(), columns * maxRows);
        LootTablePreview.Entry hovered = null;
        for (int index = 0; index < limit; index++) {
            LootTablePreview.Entry entry = entries.get(index);
            int x = gridX + (index % columns) * 24;
            int y = gridY + (index / columns) * 24;
            context.fill(x, y, x + 20, y + 20, entry.conditional() ? 0xAA554400 : 0xAA303030);
            context.drawBorder(x, y, 20, 20, entry.conditional() ? 0xFFFFCC44 : 0xFF666666);
            Identifier itemId = Identifier.tryParse(entry.itemId());
            if (itemId != null && Registries.ITEM.containsId(itemId)) {
                context.drawItem(new ItemStack(Registries.ITEM.get(itemId)), x + 2, y + 2);
            }
            if (entry.maxCount() > 1) context.drawTextWithShadow(textRenderer,
                    Integer.toString(entry.maxCount()), x + 18 - textRenderer.getWidth(Integer.toString(entry.maxCount())), y + 11, 0xFFFFFFFF);
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) hovered = entry;
        }
        if (entries.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable(showResult ? "customloottables.preview.no_result" : "customloottables.preview.no_items"),
                center, gridY + 8, ScreenStyle.MUTED);
        else if (entries.size() > limit) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customloottables.preview.more", entries.size() - limit), center,
                gridY + maxRows * 24 + 2, ScreenStyle.MUTED);
        if (hovered != null) context.drawTooltip(textRenderer, tooltip(hovered), mouseX, mouseY);
    }

    private List<Text> tooltip(LootTablePreview.Entry entry) {
        Identifier id = Identifier.tryParse(entry.itemId());
        String name = id != null && Registries.ITEM.containsId(id)
                ? new ItemStack(Registries.ITEM.get(id)).getName().getString() : entry.itemId();
        List<Text> lines = new ArrayList<>();
        lines.add(Text.literal(name));
        lines.add(Text.literal(entry.itemId()).styled(style -> style.withColor(0xAAAAAA)));
        lines.add(Text.translatable("customloottables.preview.item_details", entry.minCount(), entry.maxCount(), entry.weight()));
        if (entry.conditional()) lines.add(Text.translatable("customloottables.preview.item_conditional"));
        return lines;
    }

    private Item representativeItem(String id) {
        LootTableDefinition configured = configScreen.findEffectiveTable(id);
        if (configured != null && !configured.entries.isEmpty()) {
            Identifier itemId = Identifier.tryParse(configured.entries.getFirst().item_id);
            if (itemId != null && Registries.ITEM.containsId(itemId)) return Registries.ITEM.get(itemId);
        }
        return switch (DefaultLootTableCatalog.category(id)) {
            case "chest" -> Items.CHEST; case "block" -> Items.GRASS_BLOCK; case "entity" -> Items.ZOMBIE_HEAD;
            case "fishing" -> Items.FISHING_ROD; default -> Items.BOOK;
        };
    }

    private String ellipsize(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String suffix = "…";
        return textRenderer.trimToWidth(value, Math.max(0, maxWidth - textRenderer.getWidth(suffix))) + suffix;
    }

    private void drawPanel(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0x99141A20);
        context.drawBorder(x, y, w, h, 0xFF40506A);
    }

    private void renderScrollbar(DrawContext context, int count) {
        RecipeStyleScrollBar.draw(context, scrollbarX(), rowsY(), scrollbarH(), scroll,
                Math.max(0, count - visibleRows()), draggingScrollbar);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int old = scroll;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
        if (scroll != old) clearAndInit();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= searchClearX() && mouseX < listRight()
                && mouseY >= 30 && mouseY < 50) {
            query = "";
            search.setText("");
            resetList();
            return true;
        }
        if (button == 0 && RecipeStyleScrollBar.hit(mouseX, mouseY, scrollbarX(), rowsY(), scrollbarH(), maxScroll())) {
            draggingScrollbar = true;
            updateScrollbar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) { updateScrollbar(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollbar(double mouseY) {
        int next = RecipeStyleScrollBar.valueFromMouse(mouseY, rowsY(), scrollbarH(), maxScroll());
        if (next != scroll) { scroll = next; clearAndInit(); }
    }

    @Override public void close() { client.setScreen(configScreen); }
}
