package fr.zazac1.customloottables.client;

import com.google.gson.Gson;
import fr.zazac1.customloottables.LootEntryDefinition;
import fr.zazac1.customloottables.LootTableDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Visual loot-table builder based on Custom Recipe's two-panel recipe builder. */
final class LootTableEditorScreen extends StyledScreen {
    private static final Gson GSON = new Gson();
    private static final List<String> TYPES = List.of("chest", "block", "entity", "fishing", "gift", "generic");
    private static final int PAD = 12;
    private static final int PANEL_Y = 34;
    private static final int SLOT = 24;
    private final ConfigScreen configScreen;
    private final Screen returnScreen;
    private final LootTableDefinition original;
    private final LootTableDefinition draft;
    private final boolean addOnSave;
    private final String initialDraft;
    private TextFieldWidget idField;
    private TextFieldWidget rollsField;
    private TextFieldWidget searchField;
    private TextFieldWidget weightField;
    private TextFieldWidget minField;
    private TextFieldWidget maxField;
    private String query = "";
    private int selectedEntry = -1;
    private int entryScroll;
    private int suggestionScroll;
    private boolean draggingSuggestionScrollbar;
    private boolean draggingEntryScrollbar;
    private boolean restoreSearchFocus;
    private String error = "";

    LootTableEditorScreen(ConfigScreen configScreen, LootTableDefinition original) {
        this(configScreen, configScreen, original, original == null);
    }

    LootTableEditorScreen(ConfigScreen configScreen, Screen returnScreen, LootTableDefinition original) {
        this(configScreen, returnScreen, original, original == null);
    }

    LootTableEditorScreen(ConfigScreen configScreen, Screen returnScreen,
                          LootTableDefinition definition, boolean addOnSave) {
        super(Text.translatable(definition == null || addOnSave
                ? "customloottables.editor.create" : "customloottables.editor.edit"));
        this.configScreen = configScreen;
        this.returnScreen = returnScreen;
        this.original = addOnSave ? null : definition;
        this.draft = definition == null ? new LootTableDefinition() : definition.copy();
        this.addOnSave = addOnSave;
        this.initialDraft = GSON.toJson(this.draft);
        if (!this.draft.entries.isEmpty()) selectedEntry = 0;
    }

    private int leftX() { return PAD; }
    private boolean compactLayout() { return width < 520; }
    private int leftW() {
        return compactLayout()
                ? Math.max(128, width * 2 / 5)
                : Math.min(280, Math.max(190, width * 2 / 5));
    }
    private int rightX() { return leftX() + leftW() + 10; }
    private int rightW() { return width - rightX() - PAD; }
    private int bottom() { return height - 30; }
    private int panelH() { return bottom() - PANEL_Y - 4; }
    private int searchY() { return PANEL_Y + 24; }
    private int suggestionsY() { return searchY() + 22; }
    private int visibleSuggestions() { return Math.max(1, (bottom() - suggestionsY() - 4) / 20); }
    private int gridY() { return PANEL_Y + 126; }
    private int entryControlsY() { return bottom() - 52; }
    private int gridColumns() { return Math.max(1, (rightW() - 16) / SLOT); }
    private int gridRows() { return Math.max(1, (entryControlsY() - gridY() - 6) / SLOT); }
    private int visibleEntries() { return gridColumns() * gridRows(); }
    private int maxEntryScroll() { return Math.max(0, draft.entries.size() - visibleEntries()); }
    private int searchClearX() { return leftX() + leftW() - 22; }
    private int suggestionScrollbarX() { return leftX() + leftW() - RecipeStyleScrollBar.THUMB_WIDTH; }
    private int suggestionRowRight(int count) {
        return count > visibleSuggestions() ? suggestionScrollbarX() - 1 : leftX() + leftW() - 3;
    }
    private int suggestionScrollbarH() { return bottom() - suggestionsY() - 4; }
    private int entryScrollbarX() { return rightX() + rightW() - RecipeStyleScrollBar.THUMB_WIDTH; }
    private int entryScrollbarH() { return Math.max(16, entryControlsY() - gridY() - 4); }
    private int deleteEntryX() { return rightX() + rightW() - 25; }

    @Override
    protected void init() {
        // The shared outline starts at leftX + 3: keep four pixels of inner text padding,
        // and stop the field before the clear button instead of drawing beneath it.
        int searchTextX = leftX() + 7;
        searchField = new TextFieldWidget(textRenderer, searchTextX, searchY() + 6,
                searchClearX() - searchTextX, 18,
                Text.translatable("customloottables.builder.search"));
        searchField.setDrawsBackground(false);
        searchField.setPlaceholder(Text.translatable("customloottables.builder.search"));
        searchField.setText(query);
        searchField.setChangedListener(value -> {
            query = value;
            suggestionScroll = 0;
            restoreSearchFocus = true;
        });
        addDrawableChild(searchField);
        if (restoreSearchFocus) {
            setFocused(searchField);
            searchField.setFocused(true);
            restoreSearchFocus = false;
        }

        idField = new TextFieldWidget(textRenderer, rightX() + 4, PANEL_Y + 18, rightW() - 8, 20,
                Text.translatable("customloottables.editor.id"));
        idField.setMaxLength(256);
        idField.setText(draft.id);
        addDrawableChild(idField);

        int half = (rightW() - 12) / 2;
        int modeY = PANEL_Y + 42;
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.editor.mode", modeLabel()), button -> {
            syncFields();
            draft.mode = switch (draft.mode) { case "append" -> "replace"; case "replace" -> "empty"; default -> "append"; };
            clearAndInit();
        }).tooltip(Tooltip.of(Text.translatable("customloottables.editor." + draft.mode + "_help")))
                .dimensions(rightX() + 4, modeY, half, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.editor.type", typeLabel()), button -> {
            syncFields();
            int index = TYPES.indexOf(draft.type);
            draft.type = TYPES.get((Math.max(0, index) + 1) % TYPES.size());
            clearAndInit();
        }).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.editor.type")))
                .dimensions(rightX() + 8 + half, modeY, half, 20).build());

        int rollsLabelW = Math.min(58, Math.max(42, textRenderer.getWidth(
                Text.translatable("customloottables.editor.rolls")) + 8));
        rollsField = numberField(rightX() + 4 + rollsLabelW, PANEL_Y + 66,
                Math.max(34, half - rollsLabelW), draft.rolls, 2);
        addDrawableChild(ButtonWidget.builder(enabledLabel(), button -> {
                    syncFields();
                    draft.enabled = !draft.enabled;
                    clearAndInit();
                }).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.editor.enabled")))
                .dimensions(rightX() + 8 + half, PANEL_Y + 66, half, 20).build());

        int previewGap = 4;
        int previewW = Math.max(50, (rightW() - 12 - previewGap) / 2);
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.builder.add_item"), button -> {
            syncFields();
            selectedEntry = -1;
            query = "";
            restoreSearchFocus = true;
            clearAndInit();
        }).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.editor.add_drop")))
                .dimensions(rightX() + 4, PANEL_Y + 90, previewW, 18).build());
        int previewX = rightX() + 4 + previewW + previewGap;
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.preview.open"), button -> {
            syncFields();
            client.setScreen(new LootPreviewScreen(this, draft.copy()));
        }).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.editor.preview")))
                .dimensions(previewX, PANEL_Y + 90, rightW() - 8 - previewW - previewGap, 18).build());
        addDrawable((ctx, mouseX, mouseY, delta) -> {
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.ADD,
                    rightX() + 7, PANEL_Y + 91, 16, 16);
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.EYE,
                    previewX + 3, PANEL_Y + 91, 16, 16);
        });

        if (selectedEntry >= 0 && selectedEntry < draft.entries.size()) {
            LootEntryDefinition entry = draft.entries.get(selectedEntry);
            int y = entryControlsY();
            int fieldW = Math.max(30, (rightW() - 50) / 3);
            weightField = numberField(rightX() + 4, y + 18, fieldW, entry.weight, 5);
            minField = numberField(rightX() + 8 + fieldW, y + 18, fieldW, entry.min_count, 2);
            maxField = numberField(rightX() + 12 + fieldW * 2, y + 18, fieldW, entry.max_count, 2);
        } else {
            weightField = minField = maxField = null;
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.cancel"),
                button -> close()).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.cancel_unsaved")))
                .dimensions(width / 2 - 104, bottom(), 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.save"),
                button -> save()).tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.editor.save")))
                .dimensions(width / 2 + 4, bottom(), 100, 20).build());
        addDrawable((ctx, mouseX, mouseY, delta) -> {
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.REJECT,
                    width / 2 - 101, bottom() + 1, 18, 18);
            CustomLootTableSprites.draw(ctx, CustomLootTableSprites.ACCEPT,
                    width / 2 + 7, bottom() + 1, 18, 18);
        });
    }

    private TextFieldWidget numberField(int x, int y, int w, int value, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, w, 20, Text.empty());
        field.setMaxLength(maxLength);
        field.setText(Integer.toString(value));
        field.setTextPredicate(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        addDrawableChild(field);
        return field;
    }

    private List<Identifier> suggestions() {
        String lowered = query.strip().toLowerCase(Locale.ROOT);
        return Registries.ITEM.getIds().stream()
                .filter(id -> Registries.ITEM.get(id) != Items.AIR)
                .filter(id -> lowered.isEmpty() || id.toString().toLowerCase(Locale.ROOT).contains(lowered)
                        || new ItemStack(Registries.ITEM.get(id)).getName().getString().toLowerCase(Locale.ROOT).contains(lowered))
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
    }

    private void chooseItem(Identifier id) {
        syncFields();
        if (selectedEntry >= 0 && selectedEntry < draft.entries.size()) {
            draft.entries.get(selectedEntry).item_id = id.toString();
        } else {
            LootEntryDefinition entry = new LootEntryDefinition();
            entry.item_id = id.toString();
            draft.entries.add(entry);
            selectedEntry = draft.entries.size() - 1;
            entryScroll = Math.max(0, draft.entries.size() - visibleEntries());
        }
        query = "";
        restoreSearchFocus = true;
        clearAndInit();
    }

    private void syncFields() {
        if (idField != null) draft.id = idField.getText().strip();
        if (rollsField != null) draft.rolls = parse(rollsField, 1, 64);
        if (selectedEntry >= 0 && selectedEntry < draft.entries.size()) {
            LootEntryDefinition entry = draft.entries.get(selectedEntry);
            if (weightField != null) entry.weight = parse(weightField, 1, 10_000);
            if (minField != null) entry.min_count = parse(minField, 1, 99);
            if (maxField != null) entry.max_count = Math.max(entry.min_count, parse(maxField, 1, 99));
        }
    }

    private static int parse(TextFieldWidget field, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(field.getText()))); }
        catch (RuntimeException ignored) { return min; }
    }

    private String modeLabel() {
        return Text.translatable(switch (draft.mode) {
            case "replace" -> "customloottables.mode.replace";
            case "empty" -> "customloottables.mode.empty";
            default -> "customloottables.mode.append";
        }).getString();
    }

    private String typeLabel() { return Text.translatable("customloottables.type." + draft.type).getString(); }

    private Text enabledLabel() {
        if (compactLayout()) return Text.translatable(draft.enabled
                ? "customloottables.builder.enabled_short" : "customloottables.builder.disabled_short");
        return Text.translatable(draft.enabled
                ? "customloottables.builder.enabled" : "customloottables.builder.disabled");
    }

    private void save() {
        syncFields();
        if (Identifier.tryParse(draft.id) == null) {
            error = Text.translatable("customloottables.error.invalid_table").getString();
            return;
        }
        boolean duplicate = configScreen.tables().stream()
                .anyMatch(table -> table != original && table.id.equals(draft.id));
        if (duplicate) {
            error = Text.translatable("customloottables.error.duplicate_table").getString();
            return;
        }
        if (draft.enabled && draft.entries.isEmpty() && !"empty".equals(draft.mode)) {
            error = Text.translatable("customloottables.error.no_items").getString();
            return;
        }
        if (original == null || addOnSave) configScreen.addTable(draft);
        else configScreen.replaceTable(original, draft);
        client.setScreen(returnScreen);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        panel(context, leftX(), PANEL_Y, leftW(), panelH());
        panel(context, rightX(), PANEL_Y, rightW(), panelH());
        context.fill(leftX() + 3, searchY(), leftX() + leftW() - 3, searchY() + 20, 0xFF000000);
        context.drawBorder(leftX() + 3, searchY(), leftW() - 6, 20, 0xFFAAAAAA);
        if (mouseX >= searchClearX() && mouseX < leftX() + leftW() - 3
                && mouseY >= searchY() && mouseY < searchY() + 20) {
            context.fill(searchClearX(), searchY(), leftX() + leftW() - 3, searchY() + 20, 0x443355BB);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        LootTargetBadge.draw(context, client, configScreen, configScreen.targetCompactBadgeLabel());
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, ScreenStyle.TEXT);

        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.builder.item_palette"),
                leftX() + 4, PANEL_Y + 8, 0xFFEECC77);
        CustomLootTableSprites.draw(context, CustomLootTableSprites.REJECT,
                searchClearX() + 1, searchY() + 1, 18, 18);
        List<Identifier> suggestions = suggestions();
        int first = Math.min(suggestionScroll, Math.max(0, suggestions.size() - visibleSuggestions()));
        int end = Math.min(suggestions.size(), first + visibleSuggestions());
        int rowRight = suggestionRowRight(suggestions.size());
        for (int i = first; i < end; i++) {
            Identifier id = suggestions.get(i);
            int y = suggestionsY() + (i - first) * 20;
            boolean hovered = mouseX >= leftX() + 3 && mouseX < rowRight
                    && mouseY >= y && mouseY < y + 19;
            context.fill(leftX() + 3, y, rowRight, y + 19, hovered ? 0x66335588 : 0x44000000);
            context.drawItem(new ItemStack(Registries.ITEM.get(id)), leftX() + 5, y + 1);
            String name = new ItemStack(Registries.ITEM.get(id)).getName().getString();
            context.drawTextWithShadow(textRenderer, ellipsize(name, leftW() - 31), leftX() + 25, y + 5, ScreenStyle.TEXT);
        }
        drawSuggestionScrollbar(context, suggestions.size());

        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.editor.id"),
                rightX() + 4, PANEL_Y + 6, ScreenStyle.MUTED);
        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.editor.rolls"),
                rightX() + 8, PANEL_Y + 72, ScreenStyle.MUTED);
        context.drawTextWithShadow(textRenderer,
                Text.translatable("customloottables.builder.drops", draft.entries.size()), rightX() + 4,
                gridY() - 12, 0xFF8FC7E8);

        int firstEntry = Math.min(entryScroll, maxEntryScroll());
        int limit = Math.min(draft.entries.size(), firstEntry + visibleEntries());
        for (int index = firstEntry; index < limit; index++) {
            int visibleIndex = index - firstEntry;
            int x = rightX() + 8 + (visibleIndex % gridColumns()) * SLOT;
            int y = gridY() + (visibleIndex / gridColumns()) * SLOT;
            LootEntryDefinition entry = draft.entries.get(index);
            context.fill(x, y, x + 20, y + 20, index == selectedEntry ? 0xAA335588 : 0xAA303030);
            context.drawBorder(x, y, 20, 20, index == selectedEntry ? 0xFFFFFFFF : 0xFF666666);
            Identifier id = Identifier.tryParse(entry.item_id);
            ItemStack stack = id != null && Registries.ITEM.containsId(id)
                    ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
            if (!stack.isEmpty()) context.drawItem(stack, x + 2, y + 2);
            if (!stack.isEmpty() && entry.max_count > 1) {
                context.drawItemInSlot(textRenderer, stack, x + 2, y + 2, Integer.toString(entry.max_count));
            }
            if (mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20) {
                context.drawTooltip(textRenderer, List.of(Text.literal(entry.item_id),
                        Text.translatable("customloottables.preview.item_details", entry.min_count, entry.max_count, entry.weight)), mouseX, mouseY);
            }
        }
        if (draft.entries.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,
                ellipsize(Text.translatable("customloottables.builder.empty_hint").getString(), rightW() - 16),
                rightX() + rightW() / 2, gridY() + 12, ScreenStyle.MUTED);

        if (selectedEntry >= 0 && selectedEntry < draft.entries.size()) {
            LootEntryDefinition entry = draft.entries.get(selectedEntry);
            int y = entryControlsY();
            context.drawTextWithShadow(textRenderer, ellipsize(entry.item_id, rightW() - 42), rightX() + 4, y, ScreenStyle.TEXT);
            CustomLootTableSprites.draw(context, CustomLootTableSprites.REJECT,
                    deleteEntryX(), y - 3, 18, 18);
            int fieldW = Math.max(30, (rightW() - 50) / 3);
            context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.entry.weight"), rightX() + 4, y + 8, ScreenStyle.MUTED);
            context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.entry.min"), rightX() + 8 + fieldW, y + 8, ScreenStyle.MUTED);
            context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.entry.max"), rightX() + 12 + fieldW * 2, y + 8, ScreenStyle.MUTED);
        }
        if ("empty".equals(draft.mode)) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customloottables.builder.empty_mode"), rightX() + rightW() / 2,
                gridY() + gridRows() * SLOT - 10, ScreenStyle.RED);
        if (!error.isBlank()) context.drawCenteredTextWithShadow(textRenderer, error, width / 2, height - 43, ScreenStyle.RED);
        RecipeStyleScrollBar.draw(context, entryScrollbarX(), gridY(), entryScrollbarH(),
                entryScroll, maxEntryScroll(), draggingEntryScrollbar);
    }

    private void drawSuggestionScrollbar(DrawContext context, int count) {
        RecipeStyleScrollBar.draw(context, suggestionScrollbarX(), suggestionsY(), suggestionScrollbarH(),
                suggestionScroll, Math.max(0, count - visibleSuggestions()), draggingSuggestionScrollbar);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= searchClearX() && mouseX < leftX() + leftW() - 3
                && mouseY >= searchY() && mouseY < searchY() + 20) {
            query = "";
            suggestionScroll = 0;
            searchField.setText("");
            setFocused(searchField);
            return true;
        }
        int suggestionMax = Math.max(0, suggestions().size() - visibleSuggestions());
        if (button == 0 && RecipeStyleScrollBar.hit(mouseX, mouseY, suggestionScrollbarX(),
                suggestionsY(), suggestionScrollbarH(), suggestionMax)) {
            draggingSuggestionScrollbar = true;
            suggestionScroll = RecipeStyleScrollBar.valueFromMouse(mouseY, suggestionsY(),
                    suggestionScrollbarH(), suggestionMax);
            return true;
        }
        if (button == 0 && RecipeStyleScrollBar.hit(mouseX, mouseY, entryScrollbarX(),
                gridY(), entryScrollbarH(), maxEntryScroll())) {
            draggingEntryScrollbar = true;
            entryScroll = RecipeStyleScrollBar.valueFromMouse(mouseY, gridY(), entryScrollbarH(), maxEntryScroll());
            return true;
        }
        if (button == 0 && selectedEntry >= 0 && selectedEntry < draft.entries.size()
                && mouseX >= deleteEntryX() && mouseX < deleteEntryX() + 18
                && mouseY >= entryControlsY() - 3 && mouseY < entryControlsY() + 15) {
            draft.entries.remove(selectedEntry);
            selectedEntry = Math.min(selectedEntry, draft.entries.size() - 1);
            entryScroll = Math.min(entryScroll, maxEntryScroll());
            clearAndInit();
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        List<Identifier> suggestions = suggestions();
        int first = Math.min(suggestionScroll, Math.max(0, suggestions.size() - visibleSuggestions()));
        int row = ((int) mouseY - suggestionsY()) / 20;
        if (mouseX >= leftX() + 3 && mouseX < suggestionRowRight(suggestions.size()) && mouseY >= suggestionsY()
                && row >= 0 && row < visibleSuggestions() && first + row < suggestions.size()) {
            chooseItem(suggestions.get(first + row));
            return true;
        }
        int col = ((int) mouseX - rightX() - 8) / SLOT;
        int gridRow = ((int) mouseY - gridY()) / SLOT;
        if (mouseX >= rightX() + 8 && mouseX < rightX() + rightW() - 4 && mouseY >= gridY()
                && col >= 0 && col < gridColumns() && gridRow >= 0 && gridRow < gridRows()) {
            int index = entryScroll + gridRow * gridColumns() + col;
            if (index < draft.entries.size()) {
                syncFields();
                selectedEntry = index;
                clearAndInit();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSuggestionScrollbar) {
            int max = Math.max(0, suggestions().size() - visibleSuggestions());
            suggestionScroll = RecipeStyleScrollBar.valueFromMouse(mouseY, suggestionsY(), suggestionScrollbarH(), max);
            return true;
        }
        if (draggingEntryScrollbar) {
            entryScroll = RecipeStyleScrollBar.valueFromMouse(mouseY, gridY(), entryScrollbarH(), maxEntryScroll());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSuggestionScrollbar = false;
        draggingEntryScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int direction = -(int) Math.signum(verticalAmount);
        if (mouseX < rightX()) {
            int max = Math.max(0, suggestions().size() - visibleSuggestions());
            suggestionScroll = Math.max(0, Math.min(max, suggestionScroll + direction));
            return true;
        }
        entryScroll = Math.max(0, Math.min(maxEntryScroll(), entryScroll + direction * gridColumns()));
        return true;
    }

    private String ellipsize(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String suffix = "…";
        return textRenderer.trimToWidth(value, Math.max(0, maxWidth - textRenderer.getWidth(suffix))) + suffix;
    }

    private void panel(DrawContext context, int x, int y, int w, int h) {
        context.fill(x, y, x + w, y + h, 0x99141A20);
        context.drawBorder(x, y, w, h, 0xFF40506A);
    }

    @Override
    public void close() {
        syncFields();
        if (!GSON.toJson(draft).equals(initialDraft)) {
            client.setScreen(new SaveChangesScreen(this, this::save, () -> client.setScreen(returnScreen)));
        } else client.setScreen(returnScreen);
    }
}
