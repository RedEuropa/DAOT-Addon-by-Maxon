package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S: ответ игрока кадету. action: 0 = взять зачистку, 1 = отказаться.
 * cadetId сервер перепроверяет сам — что это кадет вышки и что он рядом.
 */
public record OutpostActionPayload(int action, int cadetId) implements CustomPacketPayload {
    public static final Type<OutpostActionPayload> TYPE = new Type<>(MaxonDaotAddon.id("outpost_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OutpostActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OutpostActionPayload::action,
            ByteBufCodecs.INT, OutpostActionPayload::cadetId,
            OutpostActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
