package fr.zazac1.customloottables.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

final class ScreenStyle {
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xFFAAAAAA;
    static final int GREEN = 0xFF55FF55;
    static final int RED = 0xFFFF5555;

    static void background(Screen screen, DrawContext context) {
        context.fillGradient(0, 0, screen.width, screen.height, 0xC0101010, 0xD0101010);
    }

    static void panel(DrawContext context, int left, int top, int right, int bottom) {
        context.fill(left, top, right, bottom, 0xAA000000);
        context.drawBorder(left, top, right - left, bottom - top, 0xFF555555);
    }

    private ScreenStyle() {}
}
