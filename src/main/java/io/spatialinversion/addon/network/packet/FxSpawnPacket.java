package io.spatialinversion.addon.network.packet;

import io.spatialinversion.addon.client.ClientEffectManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.UUID;
import net.minecraft.world.phys.Vec3;

public record FxSpawnPacket(
    int kind,
    long groupId,
    UUID owner,
    Vec3 posA,
    Vec3 normalA,
    Vec3 posB,
    Vec3 normalB,
    float radius,
    int durationTicks,
    int aux
) implements CustomPacketPayload {
    public static final Type<FxSpawnPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("spatialinversion", "fx_spawn"));
    public static final StreamCodec<FriendlyByteBuf, FxSpawnPacket> STREAM_CODEC = StreamCodec.of(
        FxSpawnPacket::encode,
        FxSpawnPacket::decode
    );

    public static void encode(FxSpawnPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.kind);
        buf.writeLong(packet.groupId);
        buf.writeUUID(packet.owner);
        buf.writeDouble(packet.posA.x);
        buf.writeDouble(packet.posA.y);
        buf.writeDouble(packet.posA.z);
        buf.writeDouble(packet.normalA.x);
        buf.writeDouble(packet.normalA.y);
        buf.writeDouble(packet.normalA.z);
        buf.writeDouble(packet.posB.x);
        buf.writeDouble(packet.posB.y);
        buf.writeDouble(packet.posB.z);
        buf.writeDouble(packet.normalB.x);
        buf.writeDouble(packet.normalB.y);
        buf.writeDouble(packet.normalB.z);
        buf.writeFloat(packet.radius);
        buf.writeInt(packet.durationTicks);
        buf.writeInt(packet.aux);
    }

    public static FxSpawnPacket decode(FriendlyByteBuf buf) {
        int kind = buf.readInt();
        long groupId = buf.readLong();
        UUID owner = buf.readUUID();
        Vec3 posA = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 normalA = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 posB = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 normalB = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        float radius = buf.readFloat();
        int durationTicks = buf.readInt();
        int aux = buf.readInt();
        return new FxSpawnPacket(kind, groupId, owner, posA, normalA, posB, normalB, radius, durationTicks, aux);
    }

    public static void handle(FxSpawnPacket packet, net.minecraftforge.network.NetworkEvent.Context context) {
        context.enqueueWork(() -> ClientEffectManager.spawnEffect(packet));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}