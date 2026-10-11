package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: показывает (active=true) или прячет (active=false) на клиенте разовое QTE "одна кнопка
 * на 1.5 секунды" во время потери контроля над берсерком. Клиент сам выбирает случайную клавишу
 * (см. BerserkQteClient) — сервер только даёт сигнал старт/стоп и знает результат по
 * BerserkQteHitPayload.
 */
public record BerserkQteStatePayload(boolean active) implements CustomPacketPayload {
    public static final Type<BerserkQteStatePayload> TYPE = new Type<>(MaxonDaotAddon.id("berserk_qte_state"));

    public static final StreamCodec<FriendlyByteBuf, BerserkQteStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BerserkQteStatePayload::active,
            BerserkQteStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
