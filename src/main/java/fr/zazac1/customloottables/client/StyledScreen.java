package fr.zazac1.customloottables.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Matches Custom Recipes' background policy for editor and modal screens. */
abstract class StyledScreen extends Screen {
    protected StyledScreen(Text title) {
        super(title);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (usesVanillaBackground()) super.renderBackground(context, mouseX, mouseY, delta);
        else ScreenStyle.background(this, context);
    }

    /** World/settings screens keep Minecraft's blur; editors draw one dark layer. */
    protected boolean usesVanillaBackground() { return false; }
}
