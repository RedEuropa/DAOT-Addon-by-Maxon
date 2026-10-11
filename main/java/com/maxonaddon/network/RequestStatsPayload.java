package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestStatsPayload() implements CustomPacketPayload {
    public static final Type<RequestStatsPayload> TYPE = new Type<>(MaxonDaotAddon.id("request_stats"));

    // Пустой кодек, так как нам нужно передать только сам факт нажатия (без переменных)
    public static final StreamCodec<FriendlyByteBuf, RequestStatsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new RequestStatsPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}