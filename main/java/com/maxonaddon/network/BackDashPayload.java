package com.maxonaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BackDashPayload() implements CustomPacketPayload {
    // Указываем уникальное имя для пакета (back_dash)
    public static final Type<BackDashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("dash_odm", "back_dash"));
    public static final StreamCodec<FriendlyByteBuf, BackDashPayload> CODEC = StreamCodec.unit(new BackDashPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}