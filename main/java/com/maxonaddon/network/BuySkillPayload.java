package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BuySkillPayload(String skillId) implements CustomPacketPayload {
    public static final Type<BuySkillPayload> TYPE = new Type<>(MaxonDaotAddon.id("buy_skill"));

    public static final StreamCodec<FriendlyByteBuf, BuySkillPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BuySkillPayload::skillId,
            BuySkillPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}