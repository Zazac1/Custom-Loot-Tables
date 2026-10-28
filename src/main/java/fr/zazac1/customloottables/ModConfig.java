package fr.zazac1.customloottables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModConfig {
    public int config_version = 4;
    public int loot_target_version = 3;
    public boolean automatic_gui_scale = true;
    public boolean preload_loot_tables_on_startup = true;
    public String default_editor_target_id = "global";
    public String default_editor_target_name = "Global Library";
    /** Reusable library. Only entries marked All Worlds are synchronized to local worlds. */
    public LootTargetConfig global_config = new LootTargetConfig();
    public Map<String, LootTargetConfig> world_configs = new LinkedHashMap<>();
    public Map<String, String> world_names = new LinkedHashMap<>();

    /** Legacy 1.0 field, migrated once into global_config. */
    public List<LootTableDefinition> loot_tables = new ArrayList<>();

    public LootTargetConfig getOrCreateWorldConfig(String worldId, String worldName) {
        if (worldId == null || worldId.isBlank()) return global_config;
        if (worldName != null && !worldName.isBlank()) world_names.put(worldId, worldName);
        return world_configs.computeIfAbsent(worldId, ignored -> new LootTargetConfig());
    }

    public LootTargetConfig findWorldConfig(String worldId) {
        return worldId == null ? null : world_configs.get(worldId);
    }
}
