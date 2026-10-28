package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.LootEntryDefinition;
import fr.zazac1.customloottables.LootTableDefinition;
import fr.zazac1.customloottables.LootTablePreview;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Visual drop preview; chest tables can be rolled repeatedly into a 27-slot chest. */
final class LootPreviewScreen extends StyledScreen {
    private final Screen parent;
    private final LootTableDefinition table;
    private final LootTablePreview originalPreview;
    private final Random random = new Random();
    private final List<ItemStack> rolled = new ArrayList<>();

    LootPreviewScreen(Screen parent, LootTableDefinition table) {
        super(Text.translatable("customloottables.preview.screen_title"));
        this.parent = parent;
        this.table = table;
        this.originalPreview = ClientLootTablePreviewLoader.load(table.id);
        rollChest();
    }

    @Override
    protected void init() {
        int y = height - 28;
        if ("chest".equals(table.type)) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.preview.reroll"), button -> rollChest())
                    .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.preview.reroll")))
                    .dimensions(width / 2 - 104, y, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.back"), button -> close())
                    .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.back")))
                    .dimensions(width / 2 + 4, y, 100, 20).build());
        } else {
            addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.back"), button -> close())
                    .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.back")))
                    .dimensions(width / 2 - 50, y, 100, 20).build());
        }
    }

    private void rollChest() {
        rolled.clear();
        for (int slot = 0; slot < 27; slot++) rolled.add(ItemStack.EMPTY);
        if (!"chest".equals(table.type)) return;

        List<ItemStack> generated = new ArrayList<>();
        if (!table.enabled || "append".equals(table.mode)) rollOriginal(generated);
        if (table.enabled && !"empty".equals(table.mode)) rollCustom(generated);
        distributeLikeChest(generated);
    }

    private void rollCustom(List<ItemStack> generated) {
        if (table.entries.isEmpty()) return;
        int totalWeight = table.entries.stream().mapToInt(entry -> Math.max(1, entry.weight)).sum();
        int rolls = Math.min(64, Math.max(1, table.rolls));
        for (int roll = 0; roll < rolls; roll++) {
            int value = random.nextInt(Math.max(1, totalWeight));
            LootEntryDefinition chosen = table.entries.getFirst();
            for (LootEntryDefinition entry : table.entries) {
                value -= Math.max(1, entry.weight);
                if (value < 0) { chosen = entry; break; }
            }
            ItemStack stack = stack(chosen);
            if (!stack.isEmpty()) {
                int count = chosen.min_count + random.nextInt(Math.max(1, chosen.max_count - chosen.min_count + 1));
                addGeneratedStack(generated, stack, count);
            }
        }
    }

    private void rollOriginal(List<ItemStack> generated) {
        if (originalPreview == null || !originalPreview.available() || originalPreview.entries().isEmpty()) return;
        int poolCount = originalPreview.poolRolls().size();
        for (LootTablePreview.Entry entry : originalPreview.entries()) poolCount = Math.max(poolCount, entry.pool() + 1);
        for (int pool = 0; pool < poolCount; pool++) {
            List<LootTablePreview.Entry> candidates = new ArrayList<>();
            for (LootTablePreview.Entry entry : originalPreview.entries()) if (entry.pool() == pool) candidates.add(entry);
            if (candidates.isEmpty()) continue;
            int rolls = pool < originalPreview.poolRolls().size()
                    ? randomRollCount(originalPreview.poolRolls().get(pool)) : 1;
            int totalWeight = candidates.stream().mapToInt(entry -> Math.max(1, entry.weight())).sum();
            for (int roll = 0; roll < rolls; roll++) {
                int value = random.nextInt(Math.max(1, totalWeight));
                LootTablePreview.Entry chosen = candidates.getFirst();
                for (LootTablePreview.Entry entry : candidates) {
                    value -= Math.max(1, entry.weight());
                    if (value < 0) { chosen = entry; break; }
                }
                ItemStack stack = stack(chosen);
                if (!stack.isEmpty()) {
                    int count = chosen.minCount() + random.nextInt(Math.max(1, chosen.maxCount() - chosen.minCount() + 1));
                    addGeneratedStack(generated, stack, count);
                }
            }
        }
    }

    private int randomRollCount(String description) {
        if (description == null || description.isBlank()) return 1;
        String normalized = description.trim().replace('–', '-');
        try {
            if (!normalized.contains("-")) return Math.max(0, (int) Math.floor(Double.parseDouble(normalized)));
            String[] range = normalized.split("-", 2);
            int min = Math.max(0, (int) Math.floor(Double.parseDouble(range[0].trim())));
            int max = Math.max(min, (int) Math.floor(Double.parseDouble(range[1].trim())));
            return min + random.nextInt(max - min + 1);
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    private void addGeneratedStack(List<ItemStack> generated, ItemStack template, int count) {
        int remaining = Math.max(0, count);
        while (remaining > 0) {
            ItemStack stack = template.copy();
            int part = Math.min(stack.getMaxCount(), remaining);
            stack.setCount(part);
            generated.add(stack);
            remaining -= part;
        }
    }

    /** Mirrors the visible part of LootTable#supplyInventory: split, shuffle, then fill random slots. */
    private void distributeLikeChest(List<ItemStack> generated) {
        int freeSlots = Math.max(0, 27 - generated.size());
        while (freeSlots > 0) {
            List<ItemStack> splittable = generated.stream().filter(stack -> stack.getCount() > 1).toList();
            if (splittable.isEmpty()) break;
            ItemStack source = splittable.get(random.nextInt(splittable.size()));
            int splitCount = 1 + random.nextInt(Math.max(1, source.getCount() / 2));
            generated.add(source.split(splitCount));
            freeSlots--;
        }
        Collections.shuffle(generated, random);
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 27; slot++) slots.add(slot);
        Collections.shuffle(slots, random);
        for (int index = 0; index < Math.min(27, generated.size()); index++) {
            rolled.set(slots.get(index), generated.get(index));
        }
    }

    private ItemStack stack(LootEntryDefinition entry) {
        Identifier id = Identifier.tryParse(entry.item_id);
        return id != null && Registries.ITEM.containsId(id)
                ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    private ItemStack stack(LootTablePreview.Entry entry) {
        Identifier id = Identifier.tryParse(entry.itemId());
        return id != null && Registries.ITEM.containsId(id)
                ? new ItemStack(Registries.ITEM.get(id)) : ItemStack.EMPTY;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, ScreenStyle.TEXT);
        context.drawCenteredTextWithShadow(textRenderer, fit(table.id, width - 36), width / 2, 28, ScreenStyle.MUTED);
        int left = Math.max(12, width / 2 - 180);
        int right = Math.min(width - 12, width / 2 + 180);
        ScreenStyle.panel(context, left, 48, right, height - 36);
        if ("chest".equals(table.type)) drawChest(context, mouseX, mouseY);
        else drawPossibleDrops(context, mouseX, mouseY, 66);
    }

    private void drawChest(DrawContext context, int mouseX, int mouseY) {
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("customloottables.preview.chest"),
                width / 2, 58, 0xFFFFCC55);
        int startX = width / 2 - 90;
        int startY = 76;
        for (int slot = 0; slot < 27; slot++) {
            int x = startX + (slot % 9) * 20;
            int y = startY + (slot / 9) * 20;
            CustomLootTableSprites.draw(context, CustomLootTableSprites.SLOT, x, y, 20, 20);
            if (!rolled.get(slot).isEmpty()) drawStack(context, rolled.get(slot), x + 2, y + 2, mouseX, mouseY);
        }
        drawPossibleDrops(context, mouseX, mouseY, startY + 72);
    }

    private void drawPossibleDrops(DrawContext context, int mouseX, int mouseY, int y) {
        List<ItemStack> possible = possibleDrops();
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customloottables.preview.possible_drops", possible.size()), width / 2, y,
                0xFF55CCFF);
        int columns = Math.max(1, Math.min(12, (width - 48) / 22));
        int shown = Math.min(possible.size(), columns * 4);
        int startX = width / 2 - Math.min(columns, Math.max(1, shown)) * 11;
        for (int i = 0; i < shown; i++) {
            int x = startX + (i % columns) * 22;
            int slotY = y + 14 + (i / columns) * 22;
            CustomLootTableSprites.draw(context, CustomLootTableSprites.SLOT, x, slotY, 20, 20);
            ItemStack stack = possible.get(i);
            if (!stack.isEmpty()) drawStack(context, stack, x + 2, slotY + 2, mouseX, mouseY);
        }
        if (possible.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customloottables.preview.no_result"), width / 2, y + 20, ScreenStyle.MUTED);
    }

    private List<ItemStack> possibleDrops() {
        List<ItemStack> result = new ArrayList<>();
        if ((!table.enabled || "append".equals(table.mode)) && originalPreview != null && originalPreview.available()) {
            for (LootTablePreview.Entry entry : originalPreview.entries()) {
                ItemStack stack = stack(entry);
                if (!stack.isEmpty()) result.add(stack);
            }
        }
        if (table.enabled && !"empty".equals(table.mode)) {
            for (LootEntryDefinition entry : table.entries) {
                ItemStack stack = stack(entry);
                if (!stack.isEmpty()) result.add(stack);
            }
        }
        return result;
    }

    private void drawStack(DrawContext context, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        context.drawItem(stack, x, y);
        if (stack.getCount() > 1) {
            String count = Integer.toString(stack.getCount());
            context.drawTextWithShadow(textRenderer, count, x + 16 - textRenderer.getWidth(count), y + 9, 0xFFFFFFFF);
        }
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            context.drawItemTooltip(textRenderer, stack, mouseX, mouseY);
        }
    }

    private String fit(String value, int maxWidth) {
        return textRenderer.getWidth(value) <= maxWidth ? value
                : textRenderer.trimToWidth(value, maxWidth - textRenderer.getWidth("…")) + "…";
    }

    @Override public void close() { client.setScreen(parent); }
}
