package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: открыть разговор с кадетом на сторожевой вышке (см. OutpostQuestScreen).
 * Решение о том, что диалог уместен, принимает сервер — клиент только показывает окно.
 * <p>
 * cooldownTicks > 0 означает, что нового задания сейчас не дадут: окно тогда показывает
 * благодарность за прошлую вылазку и время до следующей.
 */
public record OutpostGuiPayload(int cadetId, int titansRequired, String wallName,
                                int cooldownTicks) implements CustomPacketPayload {
    public static final Type<OutpostGuiPayload> TYPE = new Type<>(MaxonDaotAddon.id("outpost_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OutpostGuiPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OutpostGuiPayload::cadetId,
            ByteBufCodecs.INT, OutpostGuiPayload::titansRequired,
            ByteBufCodecs.STRING_UTF8, OutpostGuiPayload::wallName,
            ByteBufCodecs.INT, OutpostGuiPayload::cooldownTicks,
            OutpostGuiPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
