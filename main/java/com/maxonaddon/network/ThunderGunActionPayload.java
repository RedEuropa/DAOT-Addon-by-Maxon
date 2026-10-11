package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ThunderGunActionPayload(int actionType, int hand) implements CustomPacketPayload {
    public static final Type<ThunderGunActionPayload> TYPE = new Type<>(MaxonDaotAddon.id("thunder_gun_action"));

    public static final StreamCodec<FriendlyByteBuf, ThunderGunActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.actionType());
                buf.writeInt(payload.hand());
            },
            buf -> new ThunderGunActionPayload(buf.readInt(), buf.readInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}