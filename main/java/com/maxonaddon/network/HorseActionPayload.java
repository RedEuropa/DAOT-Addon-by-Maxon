package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Клиент -> сервер: действие в лавке лошадей.
 * action: 0 = открыть/запросить предложение, 1 = купить лошадь под номером index,
 * 2 = переставить статы за изумруды, 3 = свистнуть своей лошади.
 */
public record HorseActionPayload(int action, int index) implements CustomPacketPayload {
    public static final Type<HorseActionPayload> TYPE = new Type<>(MaxonDaotAddon.id("horse_action"));

    public static final int OPEN = 0;
    public static final int BUY = 1;
    public static final int REROLL = 2;
    public static final int WHISTLE = 3;

    public static final StreamCodec<FriendlyByteBuf, HorseActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.action());
                buf.writeVarInt(payload.index());
            },
            buf -> new HorseActionPayload(buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
