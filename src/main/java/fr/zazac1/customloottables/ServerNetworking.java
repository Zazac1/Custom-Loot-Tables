package fr.zazac1.customloottables;

import com.google.gson.Gson;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

import static net.minecraft.server.command.CommandManager.literal;

public final class ServerNetworking {
    private static final int MAX_JSON_CHARS = 1_000_000;
    private static final Gson GSON = new Gson();

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(OpenEditorPayload.ID, OpenEditorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveConfigPayload.ID, SaveConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SaveResultPayload.ID, SaveResultPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LootTablePreviewRequestPayload.ID, LootTablePreviewRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LootTablePreviewPayload.ID, LootTablePreviewPayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            if (environment.dedicated) {
                dispatcher.register(literal("customloottables_server")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> openEditor(context.getSource())));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveConfigPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (!player.hasPermissionLevel(2)) {
                sendResult(player, false, "permission_denied",
                        "You do not have permission to edit server loot tables.");
                return;
            }
            if (payload.json() == null || payload.json().length() > MAX_JSON_CHARS) {
                sendResult(player, false, "too_large", "The submitted configuration is too large.");
                return;
            }
            ModConfig config = ConfigLoader.fromJson(payload.json());
            if (config == null) {
                sendResult(player, false, "invalid_config", "The submitted configuration is invalid.");
                return;
            }
            List<String> problems;
            try {
                problems = ConfigLoader.validate(config);
            } catch (RuntimeException error) {
                CustomLootTablesMod.LOGGER.error("Could not validate a proposed server configuration", error);
                sendResult(player, false, "validation_failed",
                        "The server could not validate this configuration.");
                return;
            }
            if (!problems.isEmpty()) {
                sendResult(player, false, "invalid_config", problems.getFirst());
                return;
            }
            if (!ConfigLoader.saveAndInvalidate(config)) {
                sendResult(player, false, "write_failed",
                        "The server could not write its configuration to disk.");
                return;
            }
            try {
                context.server().getCommandManager().executeWithPrefix(
                        player.getCommandSource().withSilent(), "reload");
            } catch (RuntimeException error) {
                CustomLootTablesMod.LOGGER.error("Could not reload loot tables", error);
                sendResult(player, false, "reload_failed",
                        "The configuration was saved, but loot tables could not be reloaded.");
                return;
            }
            sendResult(player, true, "ok", "Server loot tables were saved and the reload was started.");
        });

        ServerPlayNetworking.registerGlobalReceiver(LootTablePreviewRequestPayload.ID, (payload, context) -> {
            if (!context.player().hasPermissionLevel(2)
                    || !ServerPlayNetworking.canSend(context.player(), LootTablePreviewPayload.ID)) return;
            try {
                LootTablePreview preview = LootTablePreviewService.preview(payload.tableId());
                String json = LootTablePreviewService.toJson(preview);
                if (json.length() <= MAX_JSON_CHARS) {
                    ServerPlayNetworking.send(context.player(), new LootTablePreviewPayload(json));
                }
            } catch (RuntimeException error) {
                CustomLootTablesMod.LOGGER.warn("Could not create loot-table preview for {}",
                        payload.tableId(), error);
            }
        });
    }

    private static int openEditor(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (!ServerPlayNetworking.canSend(player, OpenEditorPayload.ID)) {
            source.sendError(Text.translatable("customloottables.chat.client_required"));
            return 0;
        }
        source.getServer().execute(() -> sendEditor(player));
        source.sendFeedback(() -> Text.translatable("customloottables.chat.opening_editor"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendEditor(ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(2) || !ServerPlayNetworking.canSend(player, OpenEditorPayload.ID)) return;
        ConfigLoader.invalidate();
        EditorData data = new EditorData(ConfigLoader.copy(ConfigLoader.get()), LootTableInjector.loadedTableIds(),
                WorldLootTableAssignments.activeWorldId(), WorldLootTableAssignments.activeWorldName());
        String json = GSON.toJson(data);
        if (json.length() > MAX_JSON_CHARS) {
            player.sendMessage(Text.translatable("customloottables.chat.server_send_too_large"), false);
        } else {
            ServerPlayNetworking.send(player, new OpenEditorPayload(json));
        }
    }

    private static void sendResult(ServerPlayerEntity player, boolean saved, String code, String message) {
        if (!ServerPlayNetworking.canSend(player, SaveResultPayload.ID)) return;
        String safeMessage = message == null ? "" : message;
        if (safeMessage.length() > 1_500) safeMessage = safeMessage.substring(0, 1_500) + "…";
        ServerPlayNetworking.send(player,
                new SaveResultPayload(GSON.toJson(new SaveResult(saved, code, safeMessage))));
    }

    private record SaveResult(boolean saved, String code, String message) {}

    private ServerNetworking() {}
}
