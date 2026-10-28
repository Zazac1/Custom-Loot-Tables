package fr.zazac1.customloottables;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fast porting regressions. These run with Fabric Loader JUnit during every Gradle build and protect
 * gameplay rules that are easy to accidentally lose while adapting mappings or loader APIs.
 */
final class CustomLootTablesRegressionTest {
    @BeforeAll
    static void initializeMinecraftRegistries() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void configurationRoundTripNormalizesGameplayRanges() {
        ModConfig config = new ModConfig();
        LootTableDefinition table = table(" minecraft:chests/simple_dungeon ", "REPLACE", true,
                entry(" minecraft:diamond ", 0, 0, 150));
        table.type = "CHEST";
        table.rolls = 100;
        config.global_config.loot_tables.add(table);

        ModConfig restored = ConfigLoader.fromJson(ConfigLoader.toJson(config));

        assertNotNull(restored);
        LootTableDefinition normalized = restored.global_config.loot_tables.getFirst();
        assertEquals("minecraft:chests/simple_dungeon", normalized.id);
        assertEquals("replace", normalized.mode);
        assertEquals("chest", normalized.type);
        assertEquals(64, normalized.rolls);
        assertEquals(1, normalized.entries.getFirst().weight);
        assertEquals(1, normalized.entries.getFirst().min_count);
        assertEquals(99, normalized.entries.getFirst().max_count);
    }

    @Test
    void legacyConfigurationMigratesIntoTheGlobalLibrary() {
        String legacy = """
                {
                  "config_version": 1,
                  "loot_target_version": 0,
                  "loot_tables": [{
                    "id": "minecraft:chests/simple_dungeon",
                    "mode": "append",
                    "type": "chest",
                    "enabled": true,
                    "rolls": 1,
                    "entries": [{"item_id":"minecraft:diamond","weight":1,"min_count":1,"max_count":1}]
                  }]
                }
                """;

        ModConfig migrated = ConfigLoader.fromJson(legacy);

        assertNotNull(migrated);
        assertEquals(4, migrated.config_version);
        assertEquals(3, migrated.loot_target_version);
        assertTrue(migrated.loot_tables.isEmpty());
        assertEquals(1, migrated.global_config.loot_tables.size());
        assertEquals("minecraft:chests/simple_dungeon", migrated.global_config.loot_tables.getFirst().id);
        assertTrue(Boolean.TRUE.equals(migrated.global_config.loot_tables.getFirst().global_auto_add),
                "Tables from the old always-global model must keep applying after migration");
    }

    @Test
    void disabledLegacyGlobalTableBecomesLibraryOnly() {
        String legacy = """
                {
                  "config_version": 3,
                  "loot_target_version": 2,
                  "global_config": {"loot_tables": [{
                    "id": "minecraft:chests/simple_dungeon",
                    "mode": "append",
                    "type": "chest",
                    "enabled": false,
                    "rolls": 1,
                    "entries": [{"item_id":"minecraft:diamond","weight":1,"min_count":1,"max_count":1}]
                  }]}
                }
                """;

        ModConfig migrated = ConfigLoader.fromJson(legacy);

        assertNotNull(migrated);
        LootTableDefinition table = migrated.global_config.loot_tables.getFirst();
        assertTrue(table.enabled, "Global Library templates must remain usable");
        assertFalse(Boolean.TRUE.equals(table.global_auto_add),
                "A formerly disabled global table must not start affecting every world");
    }

    @Test
    void globalLibraryStaysSeparateAndAllWorldsCopiesAreManaged() {
        ModConfig config = new ModConfig();
        LootTableDefinition libraryOnly = table("minecraft:chests/simple_dungeon", "append", true,
                entry("minecraft:diamond", 1, 1, 1));
        LootTableDefinition allWorlds = table("minecraft:chests/village/village_plains_house", "append", true,
                entry("minecraft:bread", 1, 1, 2));
        allWorlds.global_auto_add = true;
        config.global_config.loot_tables.add(libraryOnly);
        config.global_config.loot_tables.add(allWorlds);

        LootTargetConfig world = config.getOrCreateWorldConfig("world-test", "Test World");
        LootTableDefinition worldOnly = table("minecraft:chests/shipwreck_treasure", "replace", true,
                entry("minecraft:emerald", 1, 2, 4));
        world.loot_tables.add(worldOnly);
        ConfigLoader.synchronizeAutomaticLibraryTables(config);

        var effective = ConfigLoader.effectiveTables(config, "world-test");

        assertEquals(2, effective.size());
        assertSame(worldOnly, effective.getFirst());
        LootTableDefinition automaticCopy = effective.get(1);
        assertEquals(allWorlds.id, automaticCopy.id);
        assertEquals(allWorlds.id, automaticCopy.global_library_source_id);
        assertTrue(effective.stream().noneMatch(table -> table.id.equals(libraryOnly.id)),
                "A library-only table must never affect gameplay by itself");

        assertTrue(ConfigLoader.importGlobalTableToWorld(config, "world-test", "Test World", libraryOnly));
        assertNull(world.loot_tables.get(2).global_library_source_id,
                "A manually imported table must be independent from All Worlds");

        allWorlds.global_auto_add = false;
        ConfigLoader.synchronizeAutomaticLibraryTables(config);
        assertEquals(2, ConfigLoader.effectiveTables(config, "world-test").size(),
                "Turning All Worlds off must remove only the managed copy");
        assertTrue(ConfigLoader.effectiveTables(config, "world-test").stream()
                .anyMatch(table -> table.id.equals(libraryOnly.id)),
                "Turning All Worlds off must keep independent manual imports");
    }

    @Test
    void worldCanImportOneOrAllLibraryTablesWithoutOverwritingLocalTables() {
        ModConfig config = new ModConfig();
        LootTableDefinition libraryDungeon = table("minecraft:chests/simple_dungeon", "append", true,
                entry("minecraft:diamond", 1, 1, 1));
        LootTableDefinition libraryVillage = table("minecraft:chests/village/village_plains_house", "append", true,
                entry("minecraft:bread", 1, 1, 2));
        config.global_config.loot_tables.add(libraryDungeon);
        config.global_config.loot_tables.add(libraryVillage);

        LootTargetConfig world = config.getOrCreateWorldConfig("world-test", "Test World");
        LootTableDefinition localDungeon = table(libraryDungeon.id, "replace", true,
                entry("minecraft:emerald", 1, 3, 3));
        world.loot_tables.add(localDungeon);

        ConfigLoader.LibraryImportResult preview = ConfigLoader.previewGlobalLibraryImport(
                config, world.loot_tables);
        assertEquals(1, preview.added());
        assertEquals(1, preview.alreadyPresent());

        ConfigLoader.LibraryImportResult imported = ConfigLoader.importGlobalLibraryToWorld(
                config, "world-test", "Test World");
        assertEquals(1, imported.added());
        assertEquals(1, imported.alreadyPresent());
        assertSame(localDungeon, world.loot_tables.getFirst(), "The local table must not be overwritten");
        assertEquals(libraryVillage.id, world.loot_tables.get(1).id);
        assertNull(world.loot_tables.get(1).global_library_source_id,
                "A manual import must remain independent from All Worlds synchronization");
    }

    @Test
    void copiedConfigurationsAreDeepAndIndependent() {
        ModConfig original = new ModConfig();
        original.global_config.loot_tables.add(table("minecraft:chests/simple_dungeon", "append", true,
                entry("minecraft:diamond", 5, 1, 3)));
        original.getOrCreateWorldConfig("world-a", "World A").loot_tables.add(
                table("minecraft:blocks/stone", "replace", true, entry("minecraft:emerald", 1, 1, 1)));

        ModConfig copy = ConfigLoader.copy(original);
        copy.global_config.loot_tables.getFirst().entries.getFirst().max_count = 42;
        copy.world_names.put("world-a", "Changed");

        assertNotSame(original, copy);
        assertNotSame(original.global_config.loot_tables.getFirst(), copy.global_config.loot_tables.getFirst());
        assertEquals(3, original.global_config.loot_tables.getFirst().entries.getFirst().max_count);
        assertEquals("World A", original.world_names.get("world-a"));
    }

    @Test
    void validationRejectsDuplicateEmptyAndUnknownEntries() {
        ModConfig config = new ModConfig();
        config.global_config.loot_tables.add(table("minecraft:chests/simple_dungeon", "append", true,
                entry("minecraft:diamond", 1, 1, 1)));
        config.global_config.loot_tables.add(table("minecraft:chests/simple_dungeon", "append", true,
                entry("minecraft:emerald", 1, 1, 1)));
        config.global_config.loot_tables.add(table("minecraft:chests/abandoned_mineshaft", "append", true));
        config.global_config.loot_tables.add(table("minecraft:chests/desert_pyramid", "replace", true,
                entry("missingmod:not_an_item", 1, 1, 1)));

        String problems = String.join("\n", ConfigLoader.validate(config));

        assertTrue(problems.contains("duplicate loot table"));
        assertTrue(problems.contains("add at least one item"));
        assertTrue(problems.contains("unknown item missingmod:not_an_item"));
    }

    @Test
    void invalidAndOversizedJsonIsRejected() {
        assertNull(ConfigLoader.fromJson("{not-json"));
        assertNull(ConfigLoader.fromJson("x".repeat(1_000_001)));
    }

    @Test
    void previewParserFindsItemsTagsNestedTablesAndConditions() {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("test:root", """
                {
                  "type":"minecraft:chest",
                  "pools":[{
                    "rolls":{"min":1,"max":3},
                    "entries":[
                      {"type":"minecraft:item","name":"minecraft:diamond","weight":3,
                       "functions":[{"function":"minecraft:set_count","count":{"min":2,"max":4}}]},
                      {"type":"minecraft:tag","name":"test:gems"},
                      {"type":"minecraft:loot_table","value":"test:nested",
                       "conditions":[{"condition":"minecraft:random_chance","chance":0.5}]}
                    ]
                  }]
                }
                """);
        tables.put("test:nested", """
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:apple"}]}]}
                """);
        Map<String, String> tags = Map.of("test:gems", """
                {"values":["minecraft:emerald","minecraft:diamond"]}
                """);

        LootTablePreview preview = LootTablePreviewParser.parse("test:root", reader(tables, tags));

        assertTrue(preview.available());
        assertEquals("chest", preview.type());
        assertEquals("1–3", preview.poolRolls().getFirst());
        assertTrue(preview.conditional());
        assertFalse(preview.dynamic());
        assertEquals(3, preview.entries().size());
        LootTablePreview.Entry diamond = preview.entries().stream()
                .filter(entry -> entry.itemId().equals("minecraft:diamond")).findFirst().orElseThrow();
        assertEquals(3, diamond.weight());
        assertEquals(1, diamond.minCount());
        assertEquals(4, diamond.maxCount());
        assertTrue(preview.entries().stream().anyMatch(entry -> entry.itemId().equals("minecraft:emerald")));
        assertTrue(preview.entries().stream().anyMatch(entry -> entry.itemId().equals("minecraft:apple")
                && entry.conditional()));
    }

    @Test
    void previewParserReportsUnavailableAndDynamicTablesSafely() {
        LootTablePreview missing = LootTablePreviewParser.parse("test:missing", reader(Map.of(), Map.of()));
        assertFalse(missing.available());

        Map<String, String> tables = Map.of("test:dynamic", """
                {"pools":[{"rolls":1,"entries":[{"type":"minecraft:dynamic","name":"minecraft:contents"}]}]}
                """);
        LootTablePreview dynamic = LootTablePreviewParser.parse("test:dynamic", reader(tables, Map.of()));
        assertTrue(dynamic.available());
        assertTrue(dynamic.dynamic());
    }

    @Test
    void doorsBedsAndTallBlocksUseOnlyOneLootProducingHalf() {
        assertEquals(LootTableInjector.MultiBlockGuard.LOWER_HALF,
                LootTableInjector.multiBlockGuard(Identifier.of("minecraft", "blocks/oak_door")));
        assertEquals(LootTableInjector.MultiBlockGuard.BED_HEAD,
                LootTableInjector.multiBlockGuard(Identifier.of("minecraft", "blocks/red_bed")));
        assertEquals(LootTableInjector.MultiBlockGuard.LOWER_HALF,
                LootTableInjector.multiBlockGuard(Identifier.of("minecraft", "blocks/sunflower")));
        assertEquals(LootTableInjector.MultiBlockGuard.NONE,
                LootTableInjector.multiBlockGuard(Identifier.of("minecraft", "blocks/stone")));
        assertEquals(LootTableInjector.MultiBlockGuard.NONE,
                LootTableInjector.multiBlockGuard(Identifier.of("minecraft", "chests/simple_dungeon")));
    }

    @Test
    void worldIdsAreStableButIsolatedBySavePath() {
        String first = WorldLootTableAssignments.worldId(Path.of("test-saves", "world-a"));
        String same = WorldLootTableAssignments.worldId(Path.of("test-saves", ".", "world-a"));
        String other = WorldLootTableAssignments.worldId(Path.of("test-saves", "world-b"));

        assertEquals(first, same);
        assertTrue(first.startsWith("world-"));
        assertFalse(first.equals(other));
    }

    private static LootTableDefinition table(String id, String mode, boolean enabled,
                                             LootEntryDefinition... entries) {
        LootTableDefinition table = new LootTableDefinition();
        table.id = id;
        table.mode = mode;
        table.enabled = enabled;
        table.entries.clear();
        table.entries.addAll(java.util.List.of(entries));
        return table;
    }

    private static LootEntryDefinition entry(String id, int weight, int min, int max) {
        LootEntryDefinition entry = new LootEntryDefinition();
        entry.item_id = id;
        entry.weight = weight;
        entry.min_count = min;
        entry.max_count = max;
        return entry;
    }

    private static LootTablePreviewParser.ResourceReader reader(Map<String, String> tables,
                                                                Map<String, String> tags) {
        return new LootTablePreviewParser.ResourceReader() {
            @Override public Optional<String> lootTable(String id) { return Optional.ofNullable(tables.get(id)); }
            @Override public Optional<String> itemTag(String id) { return Optional.ofNullable(tags.get(id)); }
        };
    }
}
