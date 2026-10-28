package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.WorldLootTableAssignments;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Persistent target marker, matching the one used by Custom Recipes. */
final class LootTargetBadge {
    private static final Map<String, WorldIcon> WORLD_ICONS = new HashMap<>();
    private static final Set<String> MISSING_WORLD_ICONS = new HashSet<>();

    static void draw(DrawContext context, MinecraftClient client, ConfigScreen screen, String label) {
        draw(context, client, screen, label, screen.isGlobalTarget());
    }

    static void draw(DrawContext context, MinecraftClient client, ConfigScreen screen, String label,
                     boolean globalTarget) {
        int x = 9;
        int y = 3;
        context.fill(x - 1, y - 1, x + 25, y + 25, 0xFF505050);
        if (globalTarget) {
            CustomLootTableSprites.drawWorldGlobe(context, x + 4, y + 4, 16, 16);
        } else {
            WorldIcon icon = worldIcon(client, screen.targetWorldId());
            if (icon != null) {
                context.drawTexture(icon.id(), x, y, 0, 0, 24, 24, icon.width(), icon.height());
            } else {
                context.drawItem(new ItemStack(Items.GRASS_BLOCK), x + 4, y + 4);
            }
        }
        context.drawText(client.textRenderer, label, x + 31, y + 8, 0xFFFFFFFF, true);
    }

    private static WorldIcon worldIcon(MinecraftClient client, String targetId) {
        if (client == null || targetId == null || MISSING_WORLD_ICONS.contains(targetId)) return null;
        WorldIcon cached = WORLD_ICONS.get(targetId);
        if (cached != null) return cached;
        try {
            Path saves = client.getLevelStorage().getSavesDirectory();
            Path directory;
            try (Stream<Path> paths = Files.list(saves)) {
                directory = paths.filter(Files::isDirectory)
                        .filter(path -> targetId.equals(WorldLootTableAssignments.worldId(path)))
                        .findFirst().orElse(null);
            }
            if (directory == null || !Files.isRegularFile(directory.resolve("icon.png"))) return missing(targetId);
            try (InputStream input = Files.newInputStream(directory.resolve("icon.png"))) {
                NativeImage image = NativeImage.read(input);
                Identifier id = Identifier.of("customloottables", "dynamic/world_icons/"
                        + targetId.replaceAll("[^a-z0-9_./-]", "_"));
                client.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                WorldIcon icon = new WorldIcon(id, image.getWidth(), image.getHeight());
                WORLD_ICONS.put(targetId, icon);
                return icon;
            }
        } catch (Exception ignored) {
            return missing(targetId);
        }
    }

    private static WorldIcon missing(String id) {
        MISSING_WORLD_ICONS.add(id);
        return null;
    }

    private record WorldIcon(Identifier id, int width, int height) {}
    private LootTargetBadge() {}
}
