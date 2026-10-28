package fr.zazac1.customloottables;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.block.Block;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.BlockStatePropertyLootCondition;
import net.minecraft.loot.context.LootContextType;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.predicate.StatePredicate;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LootTableInjector {
    private static volatile List<String> loadedTableIds = List.of();

    public static void initialize() {
        LootTableEvents.REPLACE.register((key, original, source, registries) -> {
            LootTableDefinition definition = findReplacement(key.getValue());
            if (definition == null) return null;
            LootTable.Builder builder = LootTable.builder().type(contextType(definition.type));
            if (!"empty".equals(definition.mode)) addPool(builder, definition, key.getValue());
            return builder.build();
        });

        LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
            LootTableDefinition definition = find(key.getValue(), "append");
            if (definition != null) addPool(builder, definition, key.getValue());
        });

        LootTableEvents.ALL_LOADED.register((resourceManager, registry) -> {
            LootTablePreviewService.refresh(resourceManager);
            List<String> ids = registry.getKeys().stream()
                    .map(key -> key.getValue().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
            loadedTableIds = List.copyOf(ids);
            CustomLootTablesMod.LOGGER.info("[Custom Loot Tables] Indexed {} loaded loot tables.", ids.size());
        });
    }

    public static List<String> loadedTableIds() {
        return new ArrayList<>(loadedTableIds);
    }

    private static LootTableDefinition find(Identifier id, String mode) {
        for (LootTableDefinition definition : ConfigLoader.effectiveTables(
                ConfigLoader.get(), WorldLootTableAssignments.activeWorldId())) {
            if (definition.enabled && mode.equals(definition.mode) && id.toString().equals(definition.id)) {
                return definition;
            }
        }
        return null;
    }

    private static LootTableDefinition findReplacement(Identifier id) {
        for (LootTableDefinition definition : ConfigLoader.effectiveTables(
                ConfigLoader.get(), WorldLootTableAssignments.activeWorldId())) {
            if (definition.enabled && ("replace".equals(definition.mode) || "empty".equals(definition.mode))
                    && id.toString().equals(definition.id)) return definition;
        }
        return null;
    }

    private static void addPool(LootTable.Builder table, LootTableDefinition definition, Identifier tableId) {
        LootPool.Builder pool = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(definition.rolls));
        addMultiBlockCondition(pool, definition, tableId);
        for (LootEntryDefinition entry : definition.entries) {
            Identifier id = Identifier.tryParse(entry.item_id);
            if (id == null || !Registries.ITEM.containsId(id)) continue;
            Item item = Registries.ITEM.get(id);
            LeafEntry.Builder<?> itemBuilder = ItemEntry.builder(item).weight(entry.weight);
            if (entry.min_count != 1 || entry.max_count != 1) {
                itemBuilder.apply(SetCountLootFunction.builder(
                        UniformLootNumberProvider.create(entry.min_count, entry.max_count)));
            }
            pool.with(itemBuilder);
        }
        table.pool(pool);
    }

    /**
     * Vanilla only produces block loot from one half of a two-block structure. Custom pools need the
     * same guard, otherwise breaking a door, bed or tall block evaluates the custom pool once for
     * each half and duplicates every configured drop.
     */
    private static void addMultiBlockCondition(LootPool.Builder pool, LootTableDefinition definition,
                                               Identifier tableId) {
        if (!"block".equals(definition.type)) return;

        Block block = blockForLootTable(tableId);
        if (block == null) return;

        if (block.getDefaultState().contains(Properties.DOUBLE_BLOCK_HALF)) {
            pool.conditionally(BlockStatePropertyLootCondition.builder(block).properties(
                    StatePredicate.Builder.create()
                            .exactMatch(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)));
        } else if (block.getDefaultState().contains(Properties.BED_PART)) {
            pool.conditionally(BlockStatePropertyLootCondition.builder(block).properties(
                    StatePredicate.Builder.create()
                            .exactMatch(Properties.BED_PART, BedPart.HEAD)));
        }
    }

    private static Block blockForLootTable(Identifier tableId) {
        String path = tableId.getPath();
        if (!path.startsWith("blocks/") || path.length() == "blocks/".length()) return null;

        Identifier blockId = Identifier.of(tableId.getNamespace(), path.substring("blocks/".length()));
        return Registries.BLOCK.containsId(blockId) ? Registries.BLOCK.get(blockId) : null;
    }

    private static LootContextType contextType(String type) {
        return switch (type) {
            case "chest" -> LootContextTypes.CHEST;
            case "block" -> LootContextTypes.BLOCK;
            case "entity" -> LootContextTypes.ENTITY;
            case "fishing" -> LootContextTypes.FISHING;
            case "gift" -> LootContextTypes.GIFT;
            default -> LootContextTypes.GENERIC;
        };
    }

    private LootTableInjector() {}
}
