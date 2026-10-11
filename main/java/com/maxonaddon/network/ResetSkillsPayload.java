package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ResetSkillsPayload() implements CustomPacketPayload {
    public static final Type<ResetSkillsPayload> TYPE = new Type<>(MaxonDaotAddon.id("reset_skills"));
    public static final StreamCodec<FriendlyByteBuf, ResetSkillsPayload> CODEC = StreamCodec.unit(new ResetSkillsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}