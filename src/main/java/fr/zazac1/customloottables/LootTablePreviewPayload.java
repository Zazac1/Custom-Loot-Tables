package fr.zazac1.customloottables;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LootTablePreviewPayload(String json) implements CustomPayload {
    public static final Id<LootTablePreviewPayload> ID = new Id<>(Identifier.of(CustomLootTablesMod.MOD_ID, "preview"));
    public static final PacketCodec<RegistryByteBuf, LootTablePreviewPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(1_000_000), LootTablePreviewPayload::json, LootTablePreviewPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
