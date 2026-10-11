package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncFearStatsPayload(int titansKilled) implements CustomPacketPayload {
    public static final Type<SyncFearStatsPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_fear_stats"));

    public static final StreamCodec<ByteBuf, SyncFearStatsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SyncFearStatsPayload::titansKilled,
            SyncFearStatsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}