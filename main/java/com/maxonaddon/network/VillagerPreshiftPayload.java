package com.maxonaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VillagerPreshiftPayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VillagerPreshiftPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("maxonaddon", "villager_preshift"));

    public static final StreamCodec<FriendlyByteBuf, VillagerPreshiftPayload> STREAM_CODEC = StreamCodec.composite(
            net.minecraft.network.codec.ByteBufCodecs.INT,
            VillagerPreshiftPayload::entityId,
            VillagerPreshiftPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}