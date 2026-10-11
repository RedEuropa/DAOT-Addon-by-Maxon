package com.maxonaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DashPayload() implements CustomPacketPayload {

    // В 1.21.1 ResourceLocation создается через fromNamespaceAndPath
    public static final Type<DashPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("dash_odm", "dash"));
    public static final StreamCodec<FriendlyByteBuf, DashPayload> CODEC = StreamCodec.unit(new DashPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}