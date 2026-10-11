package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: игрок успел нажать правильную клавишу разового QTE берсерка в отведённое окно. */
public record BerserkQteHitPayload() implements CustomPacketPayload {
    public static final Type<BerserkQteHitPayload> TYPE = new Type<>(MaxonDaotAddon.id("berserk_qte_hit"));
    public static final StreamCodec<FriendlyByteBuf, BerserkQteHitPayload> CODEC = StreamCodec.unit(new BerserkQteHitPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
