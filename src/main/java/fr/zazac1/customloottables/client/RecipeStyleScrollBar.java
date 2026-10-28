package fr.zazac1.customloottables.client;

import net.minecraft.client.gui.DrawContext;

/** Scrollbar geometry copied from Custom Recipes' list screens. */
final class RecipeStyleScrollBar {
    static final int TRACK_WIDTH = 11;
    static final int THUMB_WIDTH = 12;
    static final int THUMB_HEIGHT = 15;
    private static final int TRACK_COLOR = 0xFF5A5A5A;

    static void draw(DrawContext context, int x, int top, int height,
                     int scroll, int maxScroll, boolean dragging) {
        if (maxScroll <= 0 || height <= THUMB_HEIGHT) return;
        int thumbY = top + (height - THUMB_HEIGHT) * scroll / maxScroll;
        context.fill(x, top, x + TRACK_WIDTH, top + height, TRACK_COLOR);
        CustomLootTableSprites.draw(context,
                dragging ? CustomLootTableSprites.SCROLLER_ACTIVE : CustomLootTableSprites.SCROLLER_IDLE,
                x, thumbY, THUMB_WIDTH, THUMB_HEIGHT);
    }

    static boolean hit(double mouseX, double mouseY, int x, int top, int height, int maxScroll) {
        return maxScroll > 0 && mouseX >= x && mouseX < x + THUMB_WIDTH
                && mouseY >= top && mouseY < top + height;
    }

    static int valueFromMouse(double mouseY, int top, int height, int maxScroll) {
        if (maxScroll <= 0) return 0;
        double progress = Math.max(0.0, Math.min(1.0,
                (mouseY - top - THUMB_HEIGHT / 2.0) / Math.max(1, height - THUMB_HEIGHT)));
        return (int) Math.round(progress * maxScroll);
    }

    private RecipeStyleScrollBar() {}
}
