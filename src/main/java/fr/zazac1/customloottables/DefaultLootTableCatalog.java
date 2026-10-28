package fr.zazac1.customloottables;

import net.minecraft.loot.LootTables;
import net.minecraft.registry.Registries;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Vanilla, registry-derived mod tables, and datapack tables merged into one catalogue. */
public final class DefaultLootTableCatalog {
    public static List<String> collect(Collection<String> loadedIds) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        LootTables.getAll().forEach(key -> ids.add(key.getValue().toString()));
        Registries.BLOCK.stream().forEach(block -> ids.add(block.getLootTableKey().getValue().toString()));
        Registries.ENTITY_TYPE.stream().forEach(type -> ids.add(type.getLootTableId().getValue().toString()));
        if (loadedIds != null) ids.addAll(loadedIds);
        return ids.stream().sorted().toList();
    }

    public static String category(String id) {
        if (id.contains(":chests/")) return "chest";
        if (id.contains(":blocks/")) return "block";
        if (id.contains(":entities/")) return "entity";
        if (id.contains(":gameplay/fishing")) return "fishing";
        if (id.contains(":gameplay/")) return "gameplay";
        if (id.contains(":archaeology/")) return "archaeology";
        if (id.contains(":equipment/")) return "equipment";
        return "other";
    }

    public static String source(String id) {
        return id.startsWith("minecraft:") ? "vanilla" : "modded";
    }

    private DefaultLootTableCatalog() {}
}
