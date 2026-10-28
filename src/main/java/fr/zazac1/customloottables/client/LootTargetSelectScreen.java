package fr.zazac1.customloottables.client;

import fr.zazac1.customloottables.WorldLootTableAssignments;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.stream.Stream;

/** Custom Recipes' world picker, adapted for loot-table targets. */
final class LootTargetSelectScreen extends StyledScreen {
    private static final int ROW = 42;
    private static final DateTimeFormatter LAST_PLAYED =
            DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US).withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final ConfigScreen source;
    private final BiFunction<String, String, Screen> selectedScreen;
    private final List<LocalWorld> worlds = new ArrayList<>();
    private TextFieldWidget search;
    private int scroll;
    private boolean draggingScrollbar;

    LootTargetSelectScreen(ConfigScreen parent) {
        this(parent, parent, (id, name) -> {
            parent.selectTargetFieldsOnly(id, name);
            return parent;
        });
    }

    @Override
    protected boolean usesVanillaBackground() { return true; }

    static LootTargetSelectScreen forSettings(Screen parent, ConfigScreen source,
                                               BiFunction<String, String, Screen> selectedScreen) {
        return new LootTargetSelectScreen(parent, source, selectedScreen);
    }

    private LootTargetSelectScreen(Screen parent, ConfigScreen source,
                                   BiFunction<String, String, Screen> selectedScreen) {
        super(Text.translatable("customloottables.target.select"));
        this.parent = parent;
        this.source = source;
        this.selectedScreen = selectedScreen;
    }

    @Override
    protected void init() {
        scanWorlds();
        int controlWidth = Math.min(240, width - 40);
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.target.global"),
                button -> select("", Text.translatable("customloottables.target.global").getString()))
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.target.global")))
                .dimensions(width / 2 - controlWidth / 2, 28, controlWidth, 20).build());
        search = new TextFieldWidget(textRenderer, width / 2 - controlWidth / 2, 54, controlWidth, 18,
                Text.translatable("customloottables.target.search"));
        search.setPlaceholder(Text.translatable("customloottables.target.search"));
        search.setChangedListener(value -> scroll = 0);
        addDrawableChild(search);
        addDrawableChild(ButtonWidget.builder(Text.translatable("customloottables.button.back"), button -> close())
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.back")))
                .dimensions(width / 2 - 55, height - 28, 110, 20).build());
    }

    private void select(String id, String name) {
        client.setScreen(selectedScreen.apply(id, name));
    }

    private void scanWorlds() {
        worlds.clear();
        if (source.serverManaged()) {
            if (!source.activeWorldId().isBlank()) worlds.add(new LocalWorld(source.activeWorldId(),
                    source.activeWorldName(), source.activeWorldName(),
                    Text.translatable("customloottables.target.current").getString(), null));
            return;
        }
        try {
            Path saves = client.getLevelStorage().getSavesDirectory();
            if (!Files.isDirectory(saves)) return;
            try (Stream<Path> entries = Files.list(saves)) {
                entries.filter(Files::isDirectory)
                        .filter(path -> Files.isRegularFile(path.resolve("level.dat")))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                        .forEach(path -> worlds.add(createWorld(path)));
            }
        } catch (Exception ignored) {}
    }

    private LocalWorld createWorld(Path directory) {
        String id = WorldLootTableAssignments.worldId(directory);
        String fallback = directory.getFileName().toString();
        WorldDetails details = readWorldDetails(directory, fallback);
        String name = id.equals(source.activeWorldId()) && !source.activeWorldName().isBlank()
                ? source.activeWorldName() : details.name();
        return new LocalWorld(id, name, details.lastPlayed(), details.description(), loadIcon(directory, id));
    }

    private WorldDetails readWorldDetails(Path directory, String fallback) {
        try {
            NbtCompound data = NbtIo.readCompressed(directory.resolve("level.dat"),
                    NbtSizeTracker.of(104857600L)).getCompound("Data");
            String name = data.contains("LevelName", 8) ? data.getString("LevelName") : fallback;
            long played = data.contains("LastPlayed", 4) ? data.getLong("LastPlayed") : 0L;
            int gameType = data.contains("GameType", 3) ? data.getInt("GameType") : 0;
            String mode = Text.translatable(switch (gameType) {
                case 1 -> "customloottables.world.creative";
                case 2 -> "customloottables.world.adventure";
                case 3 -> "customloottables.world.spectator";
                default -> "customloottables.world.survival";
            }).getString();
            String commands = data.contains("allowCommands", 1) && data.getBoolean("allowCommands")
                    ? Text.translatable("customloottables.world.commands").getString() : "";
            NbtCompound versionData = data.contains("Version", 10) ? data.getCompound("Version") : new NbtCompound();
            String version = versionData.contains("Name", 8) ? versionData.getString("Name") : "?";
            String lastPlayed = played > 0 ? name + " (" + LAST_PLAYED.format(Instant.ofEpochMilli(played)) + ")" : name;
            return new WorldDetails(name, lastPlayed, mode + commands
                    + Text.translatable("customloottables.world.version", version).getString());
        } catch (Exception ignored) {
            return new WorldDetails(fallback, fallback, Text.translatable("customloottables.world.local").getString());
        }
    }

    private WorldIcon loadIcon(Path directory, String id) {
        Path iconPath = directory.resolve("icon.png");
        if (!Files.isRegularFile(iconPath)) return null;
        try (InputStream input = Files.newInputStream(iconPath)) {
            NativeImage image = NativeImage.read(input);
            Identifier texture = Identifier.of("customloottables", "dynamic/target_worlds/"
                    + id.replaceAll("[^a-z0-9_./-]", "_"));
            client.getTextureManager().registerTexture(texture, new NativeImageBackedTexture(image));
            return new WorldIcon(texture, image.getWidth(), image.getHeight());
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<LocalWorld> visibleWorlds() {
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() ? worlds : worlds.stream()
                .filter(world -> world.name().toLowerCase(Locale.ROOT).contains(query)).toList();
    }

    private int rowsTop() { return 80; }
    private int rowsBottom() { return height - 34; }
    private int visibleRows() { return Math.max(1, (rowsBottom() - rowsTop()) / ROW); }
    private int listW() { return Math.min(340, width - 32); }
    private int listX() { return width / 2 - listW() / 2; }
    private int scrollbarX() { return listX() + listW() - 6; }
    private int maxScroll() { return Math.max(0, visibleWorlds().size() - visibleRows()); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll() > 0 && mouseX >= scrollbarX() - 2 && mouseX < scrollbarX() + 8
                && mouseY >= rowsTop() && mouseY < rowsBottom()) {
            draggingScrollbar = true;
            updateScrollbar(mouseY);
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        List<LocalWorld> shown = visibleWorlds();
        int row = ((int) mouseY - rowsTop()) / ROW;
        if (mouseX < listX() || mouseX >= listX() + listW() || mouseY < rowsTop()
                || row < 0 || row >= visibleRows() || scroll + row >= shown.size()) return false;
        LocalWorld world = shown.get(scroll + row);
        select(world.id(), world.name());
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            updateScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollbar(double mouseY) {
        double progress = Math.max(0.0, Math.min(1.0,
                (mouseY - rowsTop()) / Math.max(1, rowsBottom() - rowsTop() - 1)));
        scroll = (int) Math.round(progress * maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, ScreenStyle.TEXT);
        List<LocalWorld> shown = visibleWorlds();
        scroll = Math.min(scroll, maxScroll());
        int rows = Math.max(0, Math.min(visibleRows(), shown.size() - scroll));
        for (int row = 0; row < rows; row++) {
            LocalWorld world = shown.get(scroll + row);
            int y = rowsTop() + row * ROW;
            boolean hovered = mouseX >= listX() && mouseX < listX() + listW()
                    && mouseY >= y && mouseY < y + ROW;
            context.fill(listX(), y, listX() + listW(), y + ROW - 1, hovered ? 0x77335A42 : 0x66101010);
            context.drawBorder(listX(), y, listW(), ROW, hovered ? 0xFFFFFFFF : 0xFF505050);
            if (world.icon() != null) context.drawTexture(world.icon().id(), listX() + 4, y + 5, 0, 0,
                    32, 32, world.icon().width(), world.icon().height());
            else context.drawItem(new ItemStack(Items.GRASS_BLOCK), listX() + 12, y + 13);
            context.drawText(textRenderer, world.name(), listX() + 42, y + 4, ScreenStyle.TEXT, true);
            context.drawText(textRenderer, world.lastPlayed(), listX() + 42, y + 16, ScreenStyle.MUTED, false);
            context.drawText(textRenderer, world.description(), listX() + 42, y + 28, ScreenStyle.MUTED, false);
        }
        if (shown.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("customloottables.target.none"), width / 2, rowsTop() + 12, ScreenStyle.MUTED);
        if (maxScroll() > 0) drawScroll(context, shown.size());
    }

    private void drawScroll(DrawContext context, int size) {
        int h = rowsBottom() - rowsTop();
        int thumb = Math.max(12, h * visibleRows() / Math.max(visibleRows(), size));
        int y = rowsTop() + (h - thumb) * scroll / maxScroll();
        context.fill(scrollbarX(), rowsTop(), scrollbarX() + 6, rowsBottom(), 0xFF5A5A5A);
        context.fill(scrollbarX(), y, scrollbarX() + 6, y + thumb, 0xFFAAAAAA);
        context.drawBorder(scrollbarX(), y, 6, thumb, draggingScrollbar ? 0xFFFFFFFF : 0xFFEEEEEE);
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return true; }

    private record WorldIcon(Identifier id, int width, int height) {}
    private record WorldDetails(String name, String lastPlayed, String description) {}
    private record LocalWorld(String id, String name, String lastPlayed, String description, WorldIcon icon) {}
}
