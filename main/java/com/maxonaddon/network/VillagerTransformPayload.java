package com.maxonaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VillagerTransformPayload(int titanId, int titanType, double x, double y, double z) implements CustomPacketPayload {

    public static final Type<VillagerTransformPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("maxonaddon", "villager_transform"));

    public static final StreamCodec<FriendlyByteBuf, VillagerTransformPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, VillagerTransformPayload::titanId,
            ByteBufCodecs.INT, VillagerTransformPayload::titanType,
            ByteBufCodecs.DOUBLE, VillagerTransformPayload::x,
            ByteBufCodecs.DOUBLE, VillagerTransformPayload::y,
            ByteBufCodecs.DOUBLE, VillagerTransformPayload::z,
            VillagerTransformPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}