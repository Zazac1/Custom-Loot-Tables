package fr.zazac1.customloottables.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Shared sprites copied from Custom Recipes so every editor uses the same visual language. */
final class CustomLootTableSprites {
    static final Identifier ACCEPT = icon("accept");
    static final Identifier REJECT = icon("reject");
    static final Identifier ADD = icon("add");
    static final Identifier EYE = icon("eye");
    static final Identifier SAVE = icon("save");
    static final Identifier RESET = icon("reset");
    static final Identifier SETTINGS = icon("settings");
    static final Identifier SCROLLER_IDLE = icon("scroller_idle");
    static final Identifier SCROLLER_ACTIVE = icon("scroller_active");
    static final Identifier SLOT = texture("gui/container/slot");
    static final Identifier SLOT_SELECTED = texture("gui/container/slot_selected");
    static final Identifier OUTPUT = texture("gui/container/output");
    static final Identifier OUTPUT_SELECTED = texture("gui/container/output_selected");
    static final Identifier OUTPUT_ARROW = texture("gui/container/output_arrow");
    static final Identifier WORLD_GLOBE = texture("gui/world_globe");

    private static Identifier icon(String name) {
        return Identifier.of("customloottables", "textures/gui/icons/" + name + ".png");
    }

    private static Identifier texture(String path) {
        return Identifier.of("customloottables", "textures/" + path + ".png");
    }

    static void draw(DrawContext context, Identifier sprite, int x, int y, int width, int height) {
        context.drawTexture(sprite, x, y, 0, 0, width, height, width, height);
    }

    static void drawWorldGlobe(DrawContext context, int x, int y, int width, int height) {
        context.drawTexture(WORLD_GLOBE, x, y, width, height,
                0.0F, 0.0F, 251, 269, 251, 269);
    }

    private CustomLootTableSprites() {}
}
