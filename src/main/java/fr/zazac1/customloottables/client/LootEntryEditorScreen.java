package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.LootEntryDefinition;
import fr.zazac1.customloottables.LootTableDefinition;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

final class LootEntryEditorScreen extends StyledScreen {
    private final Screen parent;
    private final LootTableDefinition table;
    private final LootEntryDefinition original;
    private final LootEntryDefinition draft;
    private TextFieldWidget itemField;
    private TextFieldWidget weightField;
    private TextFieldWidget minField;
    private TextFieldWidget maxField;
    private String error = "";

    LootEntryEditorScreen(Screen parent, LootTableDefinition table, LootEntryDefinition original) {
        super(Text.translatable(original == null
                ? "customloottables.entry.create" : "customloottables.entry.edit"));
        this.parent = parent;
        this.table = table;
        this.original = original;
        this.draft = original == null ? new LootEntryDefinition() : original.copy();
    }

    @Override
    protected void init() {
        int left = width / 2 - 130;
        itemField = field(left, 44, 260, draft.item_id, 256);
        itemField.setChangedListener(value -> error = "");
        weightField = numberField(left, 88, 78, draft.weight);
        minField = numberField(left + 91, 88, 78, draft.min_count);
        maxField = numberField(left + 182, 88, 78, draft.max_count);
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.cancel"),
                button -> close()).dimensions(width / 2 - 104, height - 28, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.save"),
                button -> save()).dimensions(width / 2 + 4, height - 28, 100, 20).build());
    }

    private TextFieldWidget field(int x, int y, int width, String value, int maxLength) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 20, Text.empty());
        field.setMaxLength(maxLength);
        field.setText(value);
        addDrawableChild(field);
        return field;
    }

    private TextFieldWidget numberField(int x, int y, int width, int value) {
        TextFieldWidget field = field(x, y, width, Integer.toString(value), 5);
        field.setTextPredicate(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        return field;
    }

    private List<Identifier> suggestions() {
        String query = itemField == null ? "" : itemField.getText().strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return List.of();
        return Registries.ITEM.getIds().stream()
                .filter(id -> id.toString().toLowerCase(Locale.ROOT).contains(query))
                .sorted()
                .limit(6)
                .toList();
    }

    private void save() {
        Identifier itemId = Identifier.tryParse(itemField.getText().strip());
        if (itemId == null || !Registries.ITEM.containsId(itemId)) {
            error = Text.translatable("customloottables.error.unknown_item").getString();
            return;
        }
        draft.item_id = itemId.toString();
        draft.weight = parse(weightField, 1, 10_000);
        draft.min_count = parse(minField, 1, 99);
        draft.max_count = Math.max(draft.min_count, parse(maxField, 1, 99));
        if (original == null) table.entries.add(draft);
        else {
            int index = table.entries.indexOf(original);
            if (index >= 0) table.entries.set(index, draft);
        }
        client.setScreen(parent);
    }

    private static int parse(TextFieldWidget field, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(field.getText()))); }
        catch (NumberFormatException ignored) { return min; }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, ScreenStyle.TEXT);
        int left = width / 2 - 130;
        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.entry.item"), left, 32, ScreenStyle.MUTED);
        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.entry.weight"), left, 76, ScreenStyle.MUTED);
        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.entry.min"), left + 91, 76, ScreenStyle.MUTED);
        context.drawTextWithShadow(textRenderer, Text.translatable("customloottables.entry.max"), left + 182, 76, ScreenStyle.MUTED);
        List<Identifier> suggestions = suggestions();
        for (int i = 0; i < suggestions.size(); i++) {
            Identifier id = suggestions.get(i);
            int y = 116 + i * 22;
            context.fill(left, y, left + 260, y + 20, 0x77000000);
            context.drawItem(new ItemStack(Registries.ITEM.get(id)), left + 2, y + 2);
            context.drawTextWithShadow(textRenderer, id.toString(), left + 23, y + 6, ScreenStyle.TEXT);
        }
        if (!error.isBlank()) context.drawCenteredTextWithShadow(textRenderer, error, width / 2, height - 44, ScreenStyle.RED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int left = width / 2 - 130;
        int row = ((int) mouseY - 116) / 22;
        List<Identifier> suggestions = suggestions();
        if (mouseX >= left && mouseX < left + 260 && mouseY >= 116 && row >= 0 && row < suggestions.size()) {
            itemField.setText(suggestions.get(row).toString());
            return true;
        }
        return false;
    }

    @Override public void close() { client.setScreen(parent); }
}
