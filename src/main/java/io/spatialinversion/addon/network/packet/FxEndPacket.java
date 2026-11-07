package io.spatialinversion.addon.network.packet;

import io.spatialinversion.addon.client.ClientEffectManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FxEndPacket(long groupId) implements CustomPacketPayload {
    public static final Type<FxEndPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("spatialinversion", "fx_end"));
    public static final StreamCodec<FriendlyByteBuf, FxEndPacket> STREAM_CODEC = StreamCodec.of(
        FxEndPacket::encode,
        FxEndPacket::decode
    );

    public static void encode(FxEndPacket packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.groupId);
    }

    public static FxEndPacket decode(FriendlyByteBuf buf) {
        return new FxEndPacket(buf.readLong());
    }

    public static void handle(FxEndPacket packet, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> ClientEffectManager.endEffect(packet.groupId));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}