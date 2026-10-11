package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: сообщает клиенту, что игрок вошёл (active=true) или вышел (active=false) в состояние
 * "потери контроля" над титаном-берсерком. Управляет ambient-текстом на экране
 * (см. BerserkTextManager) — само QTE идёт отдельными пакетами (BerserkQteStatePayload).
 */
public record BerserkControlLossPayload(boolean active) implements CustomPacketPayload {
    public static final Type<BerserkControlLossPayload> TYPE = new Type<>(MaxonDaotAddon.id("berserk_control_loss"));

    public static final StreamCodec<FriendlyByteBuf, BerserkControlLossPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BerserkControlLossPayload::active,
            BerserkControlLossPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
