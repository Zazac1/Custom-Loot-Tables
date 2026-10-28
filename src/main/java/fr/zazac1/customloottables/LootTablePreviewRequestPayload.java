package fr.zazac1.customloottables;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record LootTablePreviewRequestPayload(String tableId) implements CustomPayload {
    public static final Id<LootTablePreviewRequestPayload> ID = new Id<>(Identifier.of(CustomLootTablesMod.MOD_ID, "preview_request"));
    public static final PacketCodec<RegistryByteBuf, LootTablePreviewRequestPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(512), LootTablePreviewRequestPayload::tableId, LootTablePreviewRequestPayload::new);
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
