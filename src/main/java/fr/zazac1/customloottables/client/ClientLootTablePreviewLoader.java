package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.LootTablePreview;
import fr.zazac1.customloottables.LootTablePreviewParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarFile;

/** Same title-screen resource fallback used by Custom Recipe's vanilla browser. */
final class ClientLootTablePreviewLoader {
    private static final Map<String, Optional<String>> RESOURCE_CACHE = new HashMap<>();
    private static final Set<Path> MOD_ARCHIVES = new HashSet<>();
    private static boolean archivesCollected;

    static LootTablePreview load(String id) {
        return LootTablePreviewParser.parse(id, new LootTablePreviewParser.ResourceReader() {
            @Override public Optional<String> lootTable(String tableId) { return read(tableId, "loot_table"); }
            @Override public Optional<String> itemTag(String tagId) { return read(tagId, "tags/item"); }
        });
    }

    private static Optional<String> read(String id, String folder) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null) return Optional.empty();
        String resourcePath = folder + "/" + parsed.getPath() + ".json";
        String cacheKey = parsed.getNamespace() + ":" + resourcePath;
        return RESOURCE_CACHE.computeIfAbsent(cacheKey, ignored -> readUncached(parsed.getNamespace(), resourcePath));
    }

    private static Optional<String> readUncached(String namespace, String resourcePath) {
        MinecraftClient client = MinecraftClient.getInstance();
        Identifier resourceId = Identifier.of(namespace, resourcePath);
        try {
            Resource resource = client.getResourceManager().getResource(resourceId).orElse(null);
            if (resource != null) try (var input = resource.getInputStream()) {
                return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {}

        String jarPath = "data/" + namespace + "/" + resourcePath;
        try (JarFile jar = minecraftJar(client)) {
            Optional<String> found = readJar(jar, jarPath);
            if (found.isPresent()) return found;
        } catch (Exception ignored) {}

        collectModArchives();
        for (Path archive : MOD_ARCHIVES) try (JarFile jar = new JarFile(archive.toFile())) {
            Optional<String> found = readJar(jar, jarPath);
            if (found.isPresent()) return found;
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    private static Optional<String> readJar(JarFile jar, String path) throws Exception {
        if (jar == null) return Optional.empty();
        var entry = jar.getJarEntry(path);
        if (entry == null) return Optional.empty();
        try (var input = jar.getInputStream(entry)) {
            return Optional.of(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static JarFile minecraftJar(MinecraftClient client) throws Exception {
        var source = client.getClass().getProtectionDomain().getCodeSource();
        if (source == null) return null;
        Path path = Path.of(source.getLocation().toURI());
        return Files.isRegularFile(path) ? new JarFile(path.toFile()) : null;
    }

    private static void collectModArchives() {
        if (archivesCollected) return;
        archivesCollected = true;
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            List<Path> paths;
            try { paths = mod.getOrigin().getPaths(); }
            catch (RuntimeException ignored) { continue; }
            for (Path path : paths) {
                Path normalized = path.toAbsolutePath().normalize();
                if (Files.isRegularFile(normalized) && normalized.getFileName().toString().endsWith(".jar")) {
                    MOD_ARCHIVES.add(normalized);
                }
            }
        }
    }

    private ClientLootTablePreviewLoader() {}
}
