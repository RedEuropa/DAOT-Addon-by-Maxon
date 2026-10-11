package com.maxonaddon.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ForceDetachPayload(int targetEntityId) implements CustomPacketPayload {
    public static final Type<ForceDetachPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", "force_detach"));

    // Используем готовый StreamCodec для типа Integer (int)
    public static final StreamCodec<ByteBuf, ForceDetachPayload> CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeInt(payload.targetEntityId()),
            buf -> new ForceDetachPayload(buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}