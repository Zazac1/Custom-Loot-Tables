package fr.zazac1.customloottables;

import com.google.gson.Gson;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Lazily parses the authoritative server datapack resources after each reload. */
public final class LootTablePreviewService {
    private static final Gson GSON = new Gson();
    private static final Map<String, LootTablePreview> CACHE = new ConcurrentHashMap<>();
    private static volatile ResourceManager resources;

    public static void refresh(ResourceManager manager) {
        resources = manager;
        CACHE.clear();
    }

    public static LootTablePreview preview(String id) {
        if (id == null || id.length() > 512 || Identifier.tryParse(id) == null) return LootTablePreview.unavailable(id == null ? "" : id);
        return CACHE.computeIfAbsent(id, key -> LootTablePreviewParser.parse(key, new LootTablePreviewParser.ResourceReader() {
            @Override public Optional<String> lootTable(String tableId) { return read(tableId, "loot_table"); }
            @Override public Optional<String> itemTag(String tagId) { return read(tagId, "tags/item"); }
        }));
    }

    private static Optional<String> read(String id, String folder) {
        ResourceManager manager = resources;
        Identifier parsed = Identifier.tryParse(id);
        if (manager == null || parsed == null) return Optional.empty();
        Identifier resourceId = Identifier.of(parsed.getNamespace(), folder + "/" + parsed.getPath() + ".json");
        try {
            var resource = manager.getResource(resourceId).orElse(null);
            if (resource == null) return Optional.empty();
            try (var input = resource.getInputStream()) {
                return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { return Optional.empty(); }
    }

    public static String toJson(LootTablePreview preview) { return GSON.toJson(preview); }
    public static LootTablePreview fromJson(String json) {
        try { return GSON.fromJson(json, LootTablePreview.class); }
        catch (RuntimeException ignored) { return null; }
    }
    private LootTablePreviewService() {}
}
