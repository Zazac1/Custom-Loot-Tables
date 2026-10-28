package fr.zazac1.customloottables;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Reads vanilla/mod datapack JSON without trying to execute context-sensitive loot conditions. */
public final class LootTablePreviewParser {
    public interface ResourceReader {
        Optional<String> lootTable(String id);
        Optional<String> itemTag(String id);
    }

    public static LootTablePreview parse(String id, ResourceReader reader) {
        Optional<String> json = reader.lootTable(id);
        if (json.isEmpty()) return LootTablePreview.unavailable(id);
        try {
            JsonObject root = JsonParser.parseString(json.get()).getAsJsonObject();
            String type = string(root, "type", "generic").replace("minecraft:", "");
            List<String> rolls = new ArrayList<>();
            List<LootTablePreview.Entry> found = new ArrayList<>();
            Flags flags = new Flags();
            collectTable(root, reader, new HashSet<>(Set.of(id)), found, rolls, flags, false);
            return new LootTablePreview(id, type, List.copyOf(rolls), merge(found),
                    flags.conditional, flags.dynamic, true);
        } catch (RuntimeException error) {
            return LootTablePreview.unavailable(id);
        }
    }

    private static void collectTable(JsonObject root, ResourceReader reader, Set<String> visited,
                                     List<LootTablePreview.Entry> output, List<String> rolls,
                                     Flags flags, boolean inheritedConditional) {
        JsonArray pools = array(root, "pools");
        for (int poolIndex = 0; poolIndex < pools.size(); poolIndex++) {
            JsonElement poolElement = pools.get(poolIndex);
            if (!poolElement.isJsonObject()) continue;
            JsonObject pool = poolElement.getAsJsonObject();
            boolean conditional = inheritedConditional || hasArray(pool, "conditions");
            if (conditional) flags.conditional = true;
            rolls.add(numberDescription(pool.get("rolls")));
            JsonArray entries = array(pool, "entries");
            for (JsonElement entry : entries) if (entry.isJsonObject()) {
                collectEntry(entry.getAsJsonObject(), poolIndex, reader, visited, output, flags, conditional);
            }
        }
    }

    private static void collectEntry(JsonObject entry, int pool, ResourceReader reader, Set<String> visited,
                                     List<LootTablePreview.Entry> output, Flags flags,
                                     boolean inheritedConditional) {
        String type = string(entry, "type", "");
        boolean conditional = inheritedConditional || hasArray(entry, "conditions");
        if (conditional) flags.conditional = true;
        int weight = Math.max(1, integer(entry, "weight", 1));
        int[] count = countRange(entry);

        if (type.endsWith(":item")) {
            String item = string(entry, "name", "");
            if (!item.isBlank()) output.add(new LootTablePreview.Entry(item, weight, count[0], count[1], pool, conditional));
            return;
        }
        if (type.endsWith(":tag")) {
            String tag = string(entry, "name", "");
            Set<String> items = new TreeSet<>();
            collectTag(tag, reader, items, new HashSet<>());
            for (String item : items) output.add(new LootTablePreview.Entry(item, weight, count[0], count[1], pool, conditional));
            if (items.isEmpty()) flags.dynamic = true;
            return;
        }
        if (type.endsWith(":loot_table")) {
            String nestedId = string(entry, "value", string(entry, "name", ""));
            if (!nestedId.isBlank() && visited.add(nestedId)) {
                reader.lootTable(nestedId).ifPresent(json -> {
                    try {
                        JsonObject nested = JsonParser.parseString(json).getAsJsonObject();
                        collectTable(nested, reader, visited, output, new ArrayList<>(), flags, conditional);
                    } catch (RuntimeException ignored) { flags.dynamic = true; }
                });
                visited.remove(nestedId);
            } else flags.dynamic = true;
            return;
        }
        JsonArray children = array(entry, "children");
        if (!children.isEmpty()) {
            for (JsonElement child : children) if (child.isJsonObject()) {
                collectEntry(child.getAsJsonObject(), pool, reader, visited, output, flags, conditional);
            }
            return;
        }
        if (!type.endsWith(":empty")) flags.dynamic = true;
    }

    private static List<LootTablePreview.Entry> merge(List<LootTablePreview.Entry> entries) {
        Map<String, LootTablePreview.Entry> merged = new LinkedHashMap<>();
        for (LootTablePreview.Entry entry : entries) {
            LootTablePreview.Entry previous = merged.get(entry.itemId());
            if (previous == null) merged.put(entry.itemId(), entry);
            else merged.put(entry.itemId(), new LootTablePreview.Entry(entry.itemId(),
                    Math.max(previous.weight(), entry.weight()),
                    Math.min(previous.minCount(), entry.minCount()),
                    Math.max(previous.maxCount(), entry.maxCount()),
                    Math.min(previous.pool(), entry.pool()),
                    previous.conditional() || entry.conditional()));
        }
        return new ArrayList<>(merged.values());
    }

    private static void collectTag(String id, ResourceReader reader, Set<String> output, Set<String> visited) {
        if (id == null || id.isBlank() || !visited.add(id)) return;
        Identifier tagId = Identifier.tryParse(id);
        if (tagId == null) return;
        try {
            for (var entry : Registries.ITEM.iterateEntries(TagKey.of(RegistryKeys.ITEM, tagId))) {
                output.add(Registries.ITEM.getId(entry.value()).toString());
            }
            if (!output.isEmpty()) return;
        } catch (RuntimeException ignored) {}
        reader.itemTag(id).ifPresent(json -> {
            try {
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                for (JsonElement value : array(root, "values")) {
                    String raw = value.isJsonPrimitive() ? value.getAsString()
                            : value.isJsonObject() ? string(value.getAsJsonObject(), "id", "") : "";
                    if (raw.startsWith("#")) collectTag(raw.substring(1), reader, output, visited);
                    else if (!raw.isBlank()) output.add(raw);
                }
            } catch (RuntimeException ignored) {}
        });
    }

    private static int[] countRange(JsonObject entry) {
        int[] result = {1, 1};
        for (JsonElement functionElement : array(entry, "functions")) {
            if (!functionElement.isJsonObject()) continue;
            JsonObject function = functionElement.getAsJsonObject();
            if (!string(function, "function", "").endsWith(":set_count")) continue;
            JsonElement count = function.get("count");
            if (count == null) continue;
            if (count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber()) {
                int value = Math.max(0, (int) Math.floor(count.getAsDouble()));
                result[0] = value;
                result[1] = value;
            } else if (count.isJsonObject()) {
                result[0] = Math.max(0, numberValue(count.getAsJsonObject().get("min"), 1));
                result[1] = Math.max(result[0], numberValue(count.getAsJsonObject().get("max"), result[0]));
            }
        }
        return result;
    }

    private static String numberDescription(JsonElement element) {
        if (element == null) return "1";
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) return cleanNumber(element.getAsDouble());
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("min") || object.has("max")) return cleanNumber(numberDouble(object.get("min"), 1))
                    + "–" + cleanNumber(numberDouble(object.get("max"), 1));
            return string(object, "type", "dynamic").replace("minecraft:", "");
        }
        return "dynamic";
    }

    private static String cleanNumber(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.2f", value);
    }
    private static int numberValue(JsonElement element, int fallback) { return (int) Math.floor(numberDouble(element, fallback)); }
    private static double numberDouble(JsonElement element, double fallback) {
        try { return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
                ? element.getAsDouble() : fallback; } catch (RuntimeException ignored) { return fallback; }
    }
    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; } catch (RuntimeException ignored) { return fallback; }
    }
    private static String string(JsonObject object, String key, String fallback) {
        try { return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean hasArray(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonArray() && !object.getAsJsonArray(key).isEmpty(); }
    private static JsonArray array(JsonObject object, String key) { return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray(); }
    private static final class Flags { boolean conditional; boolean dynamic; }
    private LootTablePreviewParser() {}
}
