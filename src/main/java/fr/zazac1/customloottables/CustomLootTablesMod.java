package fr.zazac1.customloottables;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CustomLootTablesMod implements ModInitializer {
    public static final String MOD_ID = "customloottables";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ConfigLoader.get();
        WorldLootTableAssignments.initialize();
        LootTableInjector.initialize();
        ServerNetworking.initialize();
        LOGGER.info("[Custom Loot Tables] Initialized.");
    }
}
