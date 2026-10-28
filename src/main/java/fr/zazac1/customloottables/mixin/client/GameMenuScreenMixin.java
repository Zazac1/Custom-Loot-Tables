package fr.zazac1.customloottables.mixin.client;

import fr.zazac1.customloottables.CustomLootTablesMod;
import fr.zazac1.customloottables.client.ConfigScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final Identifier CUSTOM_LOOT_TABLES_LOGO =
            Identifier.of(CustomLootTablesMod.MOD_ID, "icon.png");

    protected GameMenuScreenMixin(Text title) { super(title); }

    @Inject(method = "initWidgets", at = @At("TAIL"))
    private void customloottables$addEditorButton(CallbackInfo ci) {
        if (client == null || client.getServer() == null) return;
        int x = width - 28;
        // Custom Recipe owns the bottom-right slot. When both mods are loaded,
        // keep the two independent buttons and stack this one just above it.
        int y = height - (FabricLoader.getInstance().isModLoaded("customrecipe") ? 52 : 28);
        addDrawableChild(ButtonWidget.builder(Text.empty(),
                button -> client.setScreen(ConfigScreen.fromPauseMenu((Screen) (Object) this)))
                .tooltip(Tooltip.of(Text.translatable("customloottables.tooltip.open")))
                .dimensions(x, y, 20, 20).build());
        addDrawable((context, mouseX, mouseY, delta) -> context.drawTexture(
                CUSTOM_LOOT_TABLES_LOGO,
                x + 2, y + 2, 16, 16,
                0.0F, 0.0F, 512, 512, 512, 512));
    }
}
