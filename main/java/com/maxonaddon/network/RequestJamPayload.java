package com.maxonaddon.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestJamPayload() implements CustomPacketPayload {
    public static final Type<RequestJamPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "request_jam"));
    public static final StreamCodec<ByteBuf, RequestJamPayload> CODEC = StreamCodec.unit(new RequestJamPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}