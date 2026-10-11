package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record JawQteStatePayload(boolean active, int progress, int requiredKey) implements CustomPacketPayload {
    public static final Type<JawQteStatePayload> TYPE = new Type<>(MaxonDaotAddon.id("jaw_qte_state"));

    public static final StreamCodec<FriendlyByteBuf, JawQteStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, JawQteStatePayload::active,
            ByteBufCodecs.INT, JawQteStatePayload::progress,
            ByteBufCodecs.INT, JawQteStatePayload::requiredKey,
            JawQteStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}