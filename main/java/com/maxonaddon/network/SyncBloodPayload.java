package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Стадия окровавленности игрока (0..3) — нужна клиенту, чтобы рисовать слои крови
 * на скине/плаще/клинке. Рассылается всем, кто видит игрока (не только ему самому),
 * поэтому передаём ещё и entityId владельца.
 */
public record SyncBloodPayload(int entityId, int bloodStage) implements CustomPacketPayload {
    public static final Type<SyncBloodPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_blood"));

    public static final StreamCodec<ByteBuf, SyncBloodPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncBloodPayload::entityId,
            ByteBufCodecs.VAR_INT, SyncBloodPayload::bloodStage,
            SyncBloodPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
