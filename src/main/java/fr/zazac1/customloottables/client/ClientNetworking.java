package fr.zazac1.customloottables.client;

import com.google.gson.Gson;
import fr.zazac1.customloottables.ConfigLoader;
import fr.zazac1.customloottables.SaveConfigPayload;
import fr.zazac1.customloottables.LootTablePreviewRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.util.function.BiConsumer;

final class ClientNetworking {
    private static final int MAX_CONFIG_CHARS = 1_000_000;
    private static final Gson GSON = new Gson();
    private static BiConsumer<Boolean, String> saveCallback;

    static boolean canManageServer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return false;
        if (client.getServer() != null) return true;
        return client.player.hasPermissionLevel(2);
    }

    static boolean save(fr.zazac1.customloottables.ModConfig config, BiConsumer<Boolean, String> callback) {
        if (!canManageServer() || !ClientPlayNetworking.canSend(SaveConfigPayload.ID)) {
            callback.accept(false, "The server editor is not available for this connection.");
            return false;
        }
        if (saveCallback != null) {
            callback.accept(false, "A server save is already in progress.");
            return false;
        }
        String json = ConfigLoader.toJson(config);
        if (json.length() > MAX_CONFIG_CHARS) {
            callback.accept(false, "The configuration is too large to send to the server.");
            return false;
        }
        saveCallback = callback;
        ClientPlayNetworking.send(new SaveConfigPayload(json));
        return true;
    }

    static boolean requestPreview(String tableId) {
        if (!canManageServer() || tableId == null || tableId.length() > 512
                || Identifier.tryParse(tableId) == null
                || !ClientPlayNetworking.canSend(LootTablePreviewRequestPayload.ID)) return false;
        ClientPlayNetworking.send(new LootTablePreviewRequestPayload(tableId));
        return true;
    }

    static void completeSave(String result) {
        BiConsumer<Boolean, String> callback = saveCallback;
        saveCallback = null;
        if (callback == null) return;
        try {
            SaveResult parsed = GSON.fromJson(result, SaveResult.class);
            if (parsed == null) callback.accept(false, "The server returned an invalid save response.");
            else callback.accept(parsed.saved(), parsed.message() == null ? "" : parsed.message());
        } catch (RuntimeException error) {
            callback.accept(false, "The server returned an invalid save response.");
        }
    }

    private record SaveResult(boolean saved, String code, String message) {}

    private ClientNetworking() {}
}
