package fr.zazac1.customloottables;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record SaveResultPayload(String result) implements CustomPayload {
    public static final Id<SaveResultPayload> ID = new Id<>(
            Identifier.of(CustomLootTablesMod.MOD_ID, "save_result"));
    public static final PacketCodec<RegistryByteBuf, SaveResultPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(2048), SaveResultPayload::result, SaveResultPayload::new);

    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
