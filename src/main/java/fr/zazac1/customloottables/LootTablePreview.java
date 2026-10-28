package fr.zazac1.customloottables;

import java.util.List;

/** Lightweight, display-only representation of a datapack loot table. */
public record LootTablePreview(
        String tableId,
        String type,
        List<String> poolRolls,
        List<Entry> entries,
        boolean conditional,
        boolean dynamic,
        boolean available
) {
    public record Entry(String itemId, int weight, int minCount, int maxCount,
                        int pool, boolean conditional) {}

    public static LootTablePreview unavailable(String id) {
        return new LootTablePreview(id, "generic", List.of(), List.of(), false, false, false);
    }
}
