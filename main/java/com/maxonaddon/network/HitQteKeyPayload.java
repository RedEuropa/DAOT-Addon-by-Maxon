package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record HitQteKeyPayload(boolean success) implements CustomPacketPayload {
    public static final Type<HitQteKeyPayload> TYPE = new Type<>(MaxonDaotAddon.id("hit_qte_key"));

    public static final StreamCodec<FriendlyByteBuf, HitQteKeyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, HitQteKeyPayload::success,
            HitQteKeyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}