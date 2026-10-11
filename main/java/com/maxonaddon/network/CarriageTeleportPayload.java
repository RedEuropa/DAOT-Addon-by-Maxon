package com.maxonaddon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CarriageTeleportPayload(String cityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CarriageTeleportPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("maxonaddon", "carriage_teleport"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CarriageTeleportPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CarriageTeleportPayload::cityId,
            CarriageTeleportPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}