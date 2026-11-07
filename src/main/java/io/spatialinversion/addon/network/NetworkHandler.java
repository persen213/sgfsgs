package io.spatialinversion.addon.network;

import io.spatialinversion.addon.network.packet.FxEndPacket;
import io.spatialinversion.addon.network.packet.FxSpawnPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL;

    static {
        CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("spatialinversion", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
        );

        int index = 0;
        CHANNEL.messageBuilder(FxSpawnPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(FxSpawnPacket::encode)
            .decoder(FxSpawnPacket::decode)
            .consumerMainThread(FxSpawnPacket::handle)
            .add();
        CHANNEL.messageBuilder(FxEndPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(FxEndPacket::encode)
            .decoder(FxEndPacket::decode)
            .consumerMainThread(FxEndPacket::handle)
            .add();
    }

    public static void init() {}

    public static void sendToTracking(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), packet);
    }

    public static void sendToNearby(ServerLevel level, Vec3 pos, double range, Object packet) {
        CHANNEL.send(PacketDistributor.NEAR.with(() ->
            new PacketDistributor.TargetPoint(pos.x, pos.y, pos.z, range, level.dimension())), packet);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}