package com.maxonaddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VillagerHookSyncPayload(int entityId, boolean active, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<VillagerHookSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "villager_hook_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VillagerHookSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId());
                buf.writeBoolean(payload.active());
                buf.writeDouble(payload.x());
                buf.writeDouble(payload.y());
                buf.writeDouble(payload.z());
            },
            buf -> new VillagerHookSyncPayload(
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}