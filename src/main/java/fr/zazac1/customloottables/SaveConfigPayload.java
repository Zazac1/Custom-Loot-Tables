package fr.zazac1.customloottables;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SaveConfigPayload(String json) implements CustomPayload {
    public static final Id<SaveConfigPayload> ID = new Id<>(
            Identifier.of(CustomLootTablesMod.MOD_ID, "save_config"));
    public static final PacketCodec<RegistryByteBuf, SaveConfigPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(1_000_000), SaveConfigPayload::json, SaveConfigPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
