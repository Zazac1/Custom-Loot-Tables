package fr.zazac1.customloottables.client;

import com.google.gson.Gson;
import fr.zazac1.customloottables.ConfigLoader;
import fr.zazac1.customloottables.CustomLootTablesMod;
import fr.zazac1.customloottables.EditorData;
import fr.zazac1.customloottables.LootTargetConfig;
import fr.zazac1.customloottables.OpenEditorPayload;
import fr.zazac1.customloottables.SaveResultPayload;
import fr.zazac1.customloottables.LootTablePreview;
import fr.zazac1.customloottables.LootTablePreviewPayload;
import fr.zazac1.customloottables.LootTablePreviewService;
import fr.zazac1.customloottables.WorldLootTableAssignments;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import fr.zazac1.customloottables.DefaultLootTableCatalog;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

@Environment(EnvType.CLIENT)
public final class ClientInit implements ClientModInitializer {
    private static final Gson GSON = new Gson();
    private static Integer previousGuiScale;
    private static Boolean editorAutomaticGuiScale;
    private static List<String> preloadedCatalog = List.of();
    private static String activeClientWorldId = "";
    /** Opening must wait until the chat screen has completed its command-close cycle. */
    private static boolean pendingSoloEditorOpen;
    /** Server commands use the same one-tick delay as Custom Recipes. */
    private static Runnable deferredEditorOpen;
    private static int deferredEditorOpenTicks;

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("customloottables_solo")
                        .requires(source -> MinecraftClient.getInstance().getServer() != null)
                        .executes(context -> queueSoloEditor())
        ));
        ClientPlayNetworking.registerGlobalReceiver(OpenEditorPayload.ID, (payload, context) -> {
            if (!ClientNetworking.canManageServer()) return;
            EditorData data;
            try {
                data = GSON.fromJson(payload.json(), EditorData.class);
            } catch (RuntimeException error) {
                return;
            }
            if (data == null || data.config() == null) return;
            deferEditorOpen(() -> {
                Screen parent = context.client().currentScreen;
                context.client().setScreen(new ConfigScreen(parent, data.config(), data.loadedTableIds(), true,
                        data.activeWorldId(), data.activeWorldName()));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(SaveResultPayload.ID, (payload, context) -> {
            if (ClientNetworking.canManageServer()) ClientNetworking.completeSave(payload.result());
        });
        ClientPlayNetworking.registerGlobalReceiver(LootTablePreviewPayload.ID, (payload, context) -> {
            if (!ClientNetworking.canManageServer()) return;
            LootTablePreview preview = LootTablePreviewService.fromJson(payload.json());
            if (preview != null && context.client().currentScreen instanceof LoadedLootTablesScreen screen) {
                screen.applyPreview(preview);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            openDeferredEditorIfReady();
            if (pendingSoloEditorOpen) {
                pendingSoloEditorOpen = false;
                if (client.getServer() != null) {
                    client.setScreen(ConfigScreen.fromPauseMenu(new GameMenuScreen(true)));
                }
            }
            updateGuiScale(client);
            showLocalWorldEditorHint(client);
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            if (ConfigLoader.get().preload_loot_tables_on_startup) {
                preloadedCatalog = DefaultLootTableCatalog.collect(List.of());
            }
        });
    }

    private static int queueSoloEditor() {
        pendingSoloEditorOpen = true;
        return 1;
    }

    private static void deferEditorOpen(Runnable open) {
        deferredEditorOpen = open;
        deferredEditorOpenTicks = 1;
    }

    private static void openDeferredEditorIfReady() {
        if (deferredEditorOpen == null) return;
        if (deferredEditorOpenTicks > 0) {
            deferredEditorOpenTicks--;
            return;
        }
        Runnable open = deferredEditorOpen;
        deferredEditorOpen = null;
        open.run();
    }

    private static void showLocalWorldEditorHint(MinecraftClient client) {
        if (client.getServer() == null) {
            activeClientWorldId = "";
            return;
        }
        if (client.player == null) return;

        String worldId = currentLocalWorldId(client);
        if (worldId.equals(activeClientWorldId)) return;
        activeClientWorldId = worldId;

        String levelName = client.getServer().getSaveProperties().getLevelName();
        long day = client.getServer().getOverworld().getTimeOfDay() / 24_000L;
        String worldInstanceId = currentLocalWorldInstanceId(client);
        LootTargetConfig savedWorldConfig = ConfigLoader.get().findWorldConfig(worldId);
        boolean alreadyShown = savedWorldConfig != null && savedWorldConfig.shown_editor_tip
                && worldInstanceId.equals(savedWorldConfig.editor_tip_world_instance);
        CustomLootTablesMod.LOGGER.info(
                "[Custom Loot Tables] Editor hint check: world='{}', id='{}', instance='{}', day={}, alreadyShown={}",
                levelName, worldId, worldInstanceId, day, alreadyShown);

        if (day != 0L) return;
        if (!WorldLootTableAssignments.markEditorTipShown(worldId, levelName, worldInstanceId)) return;

        Text editorLink = Text.translatable("customloottables.chat.world_tip.link")
                .setStyle(Style.EMPTY.withColor(Formatting.AQUA).withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/customloottables_solo"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.translatable("customloottables.chat.world_tip.hover"))));
        client.player.sendMessage(Text.translatable("customloottables.chat.world_tip", editorLink), false);
    }

    private static String currentLocalWorldId(MinecraftClient client) {
        return WorldLootTableAssignments.worldId(currentLocalWorldDirectory(client));
    }

    private static String currentLocalWorldInstanceId(MinecraftClient client) {
        Path worldDirectory = currentLocalWorldDirectory(client);
        try {
            long created = Files.readAttributes(worldDirectory, BasicFileAttributes.class)
                    .creationTime().toMillis();
            return "created-" + created;
        } catch (Exception ignored) {
            return "seed-" + client.getServer().getOverworld().getSeed();
        }
    }

    private static Path currentLocalWorldDirectory(MinecraftClient client) {
        String levelName = client.getServer().getSaveProperties().getLevelName();
        if (levelName != null && !levelName.isBlank()) {
            Path saves = client.getLevelStorage().getSavesDirectory();
            Path worldDirectory = saves.resolve(levelName);
            if (Files.isDirectory(worldDirectory)) return worldDirectory;
        }
        return client.getServer().getSavePath(WorldSavePath.ROOT);
    }

    private static void updateGuiScale(MinecraftClient client) {
        Screen screen = client.currentScreen;
        boolean customScreen = screen != null
                && screen.getClass().getPackageName().startsWith("fr.zazac1.customloottables.client");
        boolean automaticScale = editorAutomaticGuiScale != null
                ? editorAutomaticGuiScale : ConfigLoader.get().automatic_gui_scale;
        if (customScreen && automaticScale) {
            if (previousGuiScale == null) previousGuiScale = client.options.getGuiScale().getValue();
            // Custom Recipes deliberately uses one stable editor scale.  Keeping the
            // same value here avoids the editor becoming tiny in a restored window
            // and also prevents a resize from changing every panel's proportions.
            int targetScale = 3;
            if (client.options.getGuiScale().getValue() != targetScale) {
                client.options.getGuiScale().setValue(targetScale);
                client.onResolutionChanged();
            }
        } else if (previousGuiScale != null) {
            int restore = previousGuiScale;
            previousGuiScale = null;
            if (client.options.getGuiScale().getValue() != restore) {
                client.options.getGuiScale().setValue(restore);
                client.onResolutionChanged();
            }
            if (!customScreen) editorAutomaticGuiScale = null;
        }
    }

    static void setEditorAutomaticGuiScale(boolean enabled) {
        editorAutomaticGuiScale = enabled;
    }

    static List<String> catalog(Collection<String> loadedIds, boolean preloadEnabled) {
        if (!preloadEnabled || preloadedCatalog.isEmpty()) return DefaultLootTableCatalog.collect(loadedIds);
        ArrayList<String> merged = new ArrayList<>(preloadedCatalog);
        if (loadedIds != null) merged.addAll(loadedIds);
        return merged.stream().distinct().sorted().toList();
    }

    static void refreshPreloadedCatalog(boolean enabled) {
        preloadedCatalog = enabled ? DefaultLootTableCatalog.collect(List.of()) : List.of();
    }
}
