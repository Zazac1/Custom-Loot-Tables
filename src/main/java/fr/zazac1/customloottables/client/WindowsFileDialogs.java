package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.CustomLootTablesMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Native JSON chooser using Minecraft's bundled LWJGL runtime. */
final class WindowsFileDialogs {
    static void openJson(Consumer<Path> selected, Consumer<String> failed) {
        run(I18n.translate("customloottables.file_dialog.open_title"), initialDirectory(), false, selected, failed);
    }

    static void saveJson(String filename, Consumer<Path> selected, Consumer<String> failed) {
        run(I18n.translate("customloottables.file_dialog.save_title"),
                Path.of(initialDirectory(), filename).toString(), true, selected, failed);
    }

    private static void run(String title, String defaultPath, boolean save,
                            Consumer<Path> selected, Consumer<String> failed) {
        Thread thread = new Thread(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.pointers(stack.UTF8("*.json"));
                String result = save
                        ? TinyFileDialogs.tinyfd_saveFileDialog(title, defaultPath, filters,
                        I18n.translate("customloottables.file_dialog.filter"))
                        : TinyFileDialogs.tinyfd_openFileDialog(title, defaultPath, filters,
                        I18n.translate("customloottables.file_dialog.filter"), false);
                if (result == null || result.isBlank()) return;
                MinecraftClient.getInstance().execute(() -> selected.accept(Path.of(result)));
            } catch (Throwable error) {
                CustomLootTablesMod.LOGGER.error("Native file chooser failed", error);
                MinecraftClient.getInstance().execute(() -> failed.accept(
                        "Could not open the file chooser: " + error.getMessage()));
            }
        }, "CustomLootTables-NativeFileDialog");
        thread.setDaemon(true);
        thread.start();
    }

    private static String initialDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        Path documents = home.resolve("Documents");
        return Files.isDirectory(documents) ? documents.toString() : home.toString();
    }

    private WindowsFileDialogs() {}
}
