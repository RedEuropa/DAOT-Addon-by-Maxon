package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SilentJamPayload(int durationMs) implements CustomPacketPayload {
    public static final Type<SilentJamPayload> TYPE = new Type<>(MaxonDaotAddon.id("silent_jam"));

    public static final StreamCodec<FriendlyByteBuf, SilentJamPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SilentJamPayload::durationMs,
            SilentJamPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}