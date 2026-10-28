package fr.zazac1.customloottables;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigLoader {
    private static final int MAX_WORLD_CONFIGS = 4_096;
    private static final int MAX_TABLES_PER_TARGET = 4_096;
    private static final int MAX_TOTAL_TABLES = 16_384;
    private static final int MAX_ENTRIES_PER_TABLE = 1_024;
    private static final int MAX_TOTAL_ENTRIES = 65_536;
    private static final int MAX_IDENTIFIER_CHARS = 512;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("customloottables.json");
    private static ModConfig cached;

    public static synchronized ModConfig get() {
        if (cached == null) cached = load();
        return cached;
    }

    public static synchronized void invalidate() {
        cached = null;
    }

    public static synchronized boolean saveAndInvalidate(ModConfig config) {
        ModConfig normalized = normalize(config);
        try {
            Files.createDirectories(PATH.getParent());
            if (Files.exists(PATH)) {
                Files.copy(PATH, PATH.resolveSibling(PATH.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            Path temporary = PATH.resolveSibling(PATH.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(normalized), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
            }
            cached = normalized;
            return true;
        } catch (IOException error) {
            CustomLootTablesMod.LOGGER.error("Could not save {}", PATH, error);
            return false;
        }
    }

    public static String toJson(ModConfig config) {
        return GSON.toJson(normalize(copy(config)));
    }

    public static ModConfig fromJson(String json) {
        if (json == null || json.length() > 1_000_000) return null;
        try {
            ModConfig config = GSON.fromJson(json, ModConfig.class);
            return config == null ? null : normalize(config);
        } catch (RuntimeException error) {
            return null;
        }
    }

    public static ModConfig copy(ModConfig config) {
        ModConfig copy = GSON.fromJson(GSON.toJson(config), ModConfig.class);
        return normalize(copy == null ? new ModConfig() : copy);
    }

    public static List<String> validate(ModConfig config) {
        List<String> problems = new ArrayList<>();
        ModConfig normalized = normalize(copy(config));
        if (normalized.default_editor_target_id.length() > 256
                || normalized.default_editor_target_name.length() > 256) {
            problems.add("The default editor target is invalid.");
        }
        if (normalized.world_configs.size() > MAX_WORLD_CONFIGS) {
            problems.add("Too many world configurations (maximum " + MAX_WORLD_CONFIGS + ").");
        }
        normalized.world_configs.keySet().stream()
                .filter(id -> id.length() > 256)
                .findFirst()
                .ifPresent(id -> problems.add("A world identifier is too long."));
        normalized.world_names.values().stream()
                .filter(name -> name != null && name.length() > 256)
                .findFirst()
                .ifPresent(name -> problems.add("A world name is too long."));
        int[] totals = {0, 0};
        validateTarget("Global", normalized.global_config, problems, totals);
        normalized.world_configs.forEach((id, target) ->
                validateTarget(normalized.world_names.getOrDefault(id, id), target, problems, totals));
        if (totals[0] > MAX_TOTAL_TABLES) {
            problems.add("Too many configured loot tables (maximum " + MAX_TOTAL_TABLES + ").");
        }
        if (totals[1] > MAX_TOTAL_ENTRIES) {
            problems.add("Too many configured loot entries (maximum " + MAX_TOTAL_ENTRIES + ").");
        }
        return problems;
    }

    private static void validateTarget(String targetName, LootTargetConfig target, List<String> problems,
                                       int[] totals) {
        totals[0] += target.loot_tables.size();
        if (target.loot_tables.size() > MAX_TABLES_PER_TARGET) {
            problems.add(targetName + " — too many loot tables (maximum " + MAX_TABLES_PER_TARGET + ").");
        }
        Set<String> ids = new HashSet<>();
        int index = 0;
        for (LootTableDefinition table : target.loot_tables) {
            index++;
            totals[1] += table.entries.size();
            if (table.entries.size() > MAX_ENTRIES_PER_TABLE) {
                problems.add(targetName + " — " + table.id + ": too many entries (maximum "
                        + MAX_ENTRIES_PER_TABLE + ").");
            }
            Identifier tableId = table.id.length() <= MAX_IDENTIFIER_CHARS
                    ? Identifier.tryParse(table.id) : null;
            if (tableId == null) problems.add(targetName + " — loot table " + index + ": invalid identifier.");
            if (tableId != null && !ids.add(table.id)) problems.add(targetName + " — duplicate loot table: " + table.id);
            if (table.enabled && table.entries.isEmpty() && !"empty".equals(table.mode)) {
                problems.add(targetName + " — " + table.id + ": add at least one item.");
            }
            for (LootEntryDefinition entry : table.entries) {
                Identifier itemId = entry.item_id.length() <= MAX_IDENTIFIER_CHARS
                        ? Identifier.tryParse(entry.item_id) : null;
                if (itemId == null || !Registries.ITEM.containsId(itemId)) {
                    problems.add(targetName + " — " + table.id + ": unknown item " + entry.item_id);
                }
            }
        }
    }

    /** Runtime loot tables belong only to the active world. The global target is a reusable library. */
    public static List<LootTableDefinition> effectiveTables(ModConfig config, String worldId) {
        // Configurations returned by get() are normalized before publication. Do not
        // mutate that shared object here: loot tables may be built concurrently by
        // Minecraft's resource-reload workers.
        ModConfig normalized = config == null ? new ModConfig() : config;
        LootTargetConfig world = normalized.findWorldConfig(worldId);
        return world == null ? new ArrayList<>() : new ArrayList<>(world.loot_tables);
    }

    /** Counts Global Library tables that can be copied without replacing a world table. */
    public static LibraryImportResult previewGlobalLibraryImport(ModConfig config,
                                                                  List<LootTableDefinition> targetTables) {
        if (config == null || config.global_config == null) return new LibraryImportResult(0, 0);
        return countMissingTables(config.global_config.loot_tables, targetTables);
    }

    /** Copies every missing Global Library table into one isolated world target. */
    public static LibraryImportResult importGlobalLibraryToWorld(ModConfig config, String worldId,
                                                                  String worldName) {
        if (config == null || config.global_config == null || worldId == null || worldId.isBlank()) {
            return new LibraryImportResult(0, 0);
        }
        return importMissingTables(config.global_config.loot_tables,
                config.getOrCreateWorldConfig(worldId, worldName));
    }

    /** Copies one library table as an independent world table. */
    public static boolean importGlobalTableToWorld(ModConfig config, String worldId, String worldName,
                                                    LootTableDefinition source) {
        if (config == null || source == null || worldId == null || worldId.isBlank()) return false;
        LootTargetConfig target = config.getOrCreateWorldConfig(worldId, worldName);
        if (target.loot_tables.stream().anyMatch(existing -> existing.id.equals(source.id))) return false;
        LootTableDefinition copy = source.copy();
        copy.enabled = true;
        copy.global_auto_add = null;
        copy.global_library_source_id = null;
        target.loot_tables.add(copy);
        return true;
    }

    /** Ensures a newly opened local world receives every library table marked All Worlds. */
    public static void ensureAutomaticLibraryTablesForWorld(String worldId, String worldName) {
        if (worldId == null || worldId.isBlank()) return;
        ModConfig config = get();
        boolean created = config.findWorldConfig(worldId) == null;
        LootTargetConfig world = config.getOrCreateWorldConfig(worldId, worldName);
        boolean changed = synchronizeAutomaticLibraryTables(config, world);
        if (created || changed) saveAndInvalidate(config);
    }

    /** Synchronizes All Worlds copies for every local world already known by the editor. */
    public static void synchronizeAutomaticLibraryTables(ModConfig config) {
        if (config == null || config.world_configs == null) return;
        for (LootTargetConfig world : config.world_configs.values()) {
            synchronizeAutomaticLibraryTables(config, world);
        }
    }

    private static boolean synchronizeAutomaticLibraryTables(ModConfig config, LootTargetConfig target) {
        if (config == null || config.global_config == null || target == null) return false;
        if (target.loot_tables == null) target.loot_tables = new ArrayList<>();

        Map<String, LootTableDefinition> automatic = new LinkedHashMap<>();
        for (LootTableDefinition table : config.global_config.loot_tables) {
            if (table != null && table.id != null && !table.id.isBlank()
                    && Boolean.TRUE.equals(table.global_auto_add)) automatic.put(table.id, table);
        }

        int previousSize = target.loot_tables.size();
        target.loot_tables.removeIf(table -> table != null
                && table.global_library_source_id != null
                && !table.global_library_source_id.isBlank()
                && !automatic.containsKey(table.global_library_source_id));
        boolean changed = previousSize != target.loot_tables.size();

        for (LootTableDefinition source : automatic.values()) {
            boolean present = target.loot_tables.stream().anyMatch(existing -> existing != null
                    && (source.id.equals(existing.global_library_source_id) || source.id.equals(existing.id)));
            if (present) continue;
            LootTableDefinition copy = source.copy();
            copy.enabled = true;
            copy.global_auto_add = null;
            copy.global_library_source_id = source.id;
            target.loot_tables.add(copy);
            changed = true;
        }
        return changed;
    }

    private static LibraryImportResult countMissingTables(List<LootTableDefinition> source,
                                                           List<LootTableDefinition> target) {
        if (source == null || source.isEmpty()) return new LibraryImportResult(0, 0);
        List<LootTableDefinition> known = target == null ? List.of() : target;
        int added = 0;
        int alreadyPresent = 0;
        for (LootTableDefinition table : source) {
            if (table == null) continue;
            if (known.stream().anyMatch(existing -> existing.id.equals(table.id))) alreadyPresent++;
            else added++;
        }
        return new LibraryImportResult(added, alreadyPresent);
    }

    private static LibraryImportResult importMissingTables(List<LootTableDefinition> source,
                                                            LootTargetConfig target) {
        if (source == null || source.isEmpty() || target == null) return new LibraryImportResult(0, 0);
        int added = 0;
        int alreadyPresent = 0;
        for (LootTableDefinition table : source) {
            if (table == null) continue;
            if (target.loot_tables.stream().anyMatch(existing -> existing.id.equals(table.id))) {
                alreadyPresent++;
                continue;
            }
            LootTableDefinition copy = table.copy();
            copy.enabled = true;
            copy.global_auto_add = null;
            copy.global_library_source_id = null;
            target.loot_tables.add(copy);
            added++;
        }
        return new LibraryImportResult(added, alreadyPresent);
    }

    public static LootTargetConfig target(ModConfig config, String worldId, String worldName) {
        ModConfig normalized = normalize(config);
        return worldId == null || worldId.isBlank()
                ? normalized.global_config : normalized.getOrCreateWorldConfig(worldId, worldName);
    }

    public static void exportTo(ModConfig config, Path path) throws IOException {
        Path destination = path.toString().toLowerCase().endsWith(".json")
                ? path : path.resolveSibling(path.getFileName() + ".json");
        Files.writeString(destination, toJson(config), StandardCharsets.UTF_8);
    }

    public static ModConfig importFrom(Path path) throws IOException {
        if (Files.size(path) > 1_000_000L) throw new IOException("The selected configuration is too large.");
        ModConfig imported = fromJson(Files.readString(path, StandardCharsets.UTF_8));
        if (imported == null) throw new IOException("The selected JSON is not a valid Custom Loot Tables configuration.");
        return imported;
    }

    private static ModConfig load() {
        if (!Files.exists(PATH)) {
            ModConfig created = new ModConfig();
            saveAndInvalidate(created);
            return created;
        }
        try {
            ModConfig loaded = fromJson(Files.readString(PATH, StandardCharsets.UTF_8));
            if (loaded != null) return loaded;
        } catch (IOException error) {
            CustomLootTablesMod.LOGGER.error("Could not read {}", PATH, error);
        }
        CustomLootTablesMod.LOGGER.warn("Using a new configuration because the existing file is invalid.");
        return new ModConfig();
    }

    private static ModConfig normalize(ModConfig config) {
        if (config == null) config = new ModConfig();
        int previousVersion = config.config_version;
        config.config_version = 4;
        if (config.loot_tables == null) config.loot_tables = new ArrayList<>();
        if (config.global_config == null) config.global_config = new LootTargetConfig();
        if (config.world_configs == null) config.world_configs = new LinkedHashMap<>();
        if (config.world_names == null) config.world_names = new LinkedHashMap<>();
        if (config.loot_target_version < 2 || (!config.loot_tables.isEmpty()
                && config.global_config.loot_tables.isEmpty())) {
            config.global_config.loot_tables.addAll(config.loot_tables);
            config.loot_tables = new ArrayList<>();
            config.loot_target_version = 2;
        }
        // Version 2 treated global entries as live definitions in every world.
        // Preserve that behaviour during migration by marking them All Worlds;
        // users can later turn individual entries back into library-only templates.
        if (config.loot_target_version < 3) {
            for (LootTableDefinition table : config.global_config.loot_tables) {
                if (table != null && table.global_auto_add == null) {
                    table.global_auto_add = table.enabled ? Boolean.TRUE : Boolean.FALSE;
                }
            }
            config.loot_target_version = 3;
        }
        if (config.default_editor_target_id == null || config.default_editor_target_id.isBlank()) {
            config.default_editor_target_id = "global";
        }
        if (config.default_editor_target_name == null || config.default_editor_target_name.isBlank()) {
            config.default_editor_target_name = "Global Library";
        }
        if (previousVersion < 3 && "current".equals(config.default_editor_target_id)) {
            config.default_editor_target_id = "global";
            config.default_editor_target_name = "Global Library";
        }
        normalizeTarget(config.global_config);
        normalizeLibraryTables(config.global_config);
        config.world_configs.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank()
                || entry.getValue() == null);
        config.world_configs.values().forEach(ConfigLoader::normalizeTarget);
        config.world_names.keySet().retainAll(new LinkedHashSet<>(config.world_configs.keySet()));
        return config;
    }

    /** Global Library entries are templates, not directly enabled/disabled runtime entries. */
    private static void normalizeLibraryTables(LootTargetConfig library) {
        if (library == null || library.loot_tables == null) return;
        for (LootTableDefinition table : library.loot_tables) {
            if (table == null) continue;
            table.enabled = true;
            table.global_library_source_id = null;
        }
    }

    private static void normalizeTarget(LootTargetConfig target) {
        if (target.editor_tip_world_instance == null) target.editor_tip_world_instance = "";
        if (target.loot_tables == null) target.loot_tables = new ArrayList<>();
        target.loot_tables.removeIf(table -> table == null);
        for (LootTableDefinition table : target.loot_tables) {
            table.id = table.id == null ? "" : table.id.strip();
            table.mode = switch (table.mode == null ? "append" : table.mode.toLowerCase()) {
                case "replace" -> "replace";
                case "empty" -> "empty";
                default -> "append";
            };
            table.type = switch (table.type == null ? "generic" : table.type.toLowerCase()) {
                case "chest", "block", "entity", "fishing", "gift" -> table.type.toLowerCase();
                default -> "generic";
            };
            table.rolls = Math.max(1, Math.min(64, table.rolls));
            if (table.entries == null) table.entries = new ArrayList<>();
            table.entries.removeIf(entry -> entry == null);
            for (LootEntryDefinition entry : table.entries) {
                entry.item_id = entry.item_id == null ? "" : entry.item_id.strip();
                entry.weight = Math.max(1, Math.min(10_000, entry.weight));
                entry.min_count = Math.max(1, Math.min(99, entry.min_count));
                entry.max_count = Math.max(entry.min_count, Math.min(99, entry.max_count));
            }
        }
    }

    private ConfigLoader() {}

    public record LibraryImportResult(int added, int alreadyPresent) {}
}
