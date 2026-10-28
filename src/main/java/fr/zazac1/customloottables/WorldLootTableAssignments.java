package fr.zazac1.customloottables;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.WorldSavePath;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

/** Tracks the running save so global settings can be combined with one isolated world profile. */
public final class WorldLootTableAssignments {
    private static volatile String activeWorldId = "";
    private static volatile String activeWorldName = "";

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            activeWorldId = worldId(server.getSavePath(WorldSavePath.ROOT));
            activeWorldName = server.getSaveProperties().getLevelName();
            if (activeWorldName == null || activeWorldName.isBlank()) activeWorldName = "Current world";
            ModConfig config = ConfigLoader.get();
            config.getOrCreateWorldConfig(activeWorldId, activeWorldName);
            ConfigLoader.saveAndInvalidate(config);
        });
        // The first loot reload happens before SERVER_STARTING. Reload once the
        // save identity is available so the per-world layer becomes authoritative.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> server.execute(() -> {
            ConfigLoader.invalidate();
            server.getCommandManager().executeWithPrefix(server.getCommandSource().withSilent(), "reload");
        }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            activeWorldId = "";
            activeWorldName = "";
        });
    }

    public static String activeWorldId() { return activeWorldId; }
    public static String activeWorldName() { return activeWorldName; }

    /** Persists the one-time local-world editor hint in the owning world profile. */
    public static boolean markEditorTipShown(String worldId, String worldName, String worldInstanceId) {
        if (worldId == null || worldId.isBlank()) return false;
        ModConfig config = ConfigLoader.get();
        LootTargetConfig worldConfig = config.getOrCreateWorldConfig(worldId, worldName);
        String instance = worldInstanceId == null ? "" : worldInstanceId;
        if (worldConfig.shown_editor_tip && instance.equals(worldConfig.editor_tip_world_instance)) return false;
        worldConfig.shown_editor_tip = true;
        worldConfig.editor_tip_world_instance = instance;
        return ConfigLoader.saveAndInvalidate(config);
    }

    public static String worldId(Path worldPath) {
        String normalized = worldPath.toAbsolutePath().normalize().toString();
        return "world-" + UUID.nameUUIDFromBytes(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private WorldLootTableAssignments() {}
}
