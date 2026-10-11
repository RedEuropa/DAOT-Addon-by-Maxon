package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Отправляется сервером клиенту (при входе, и заново при любом изменении),
 * чтобы сообщить, какие серверные системы сейчас включены: статистика и
 * система страха (геймрул allowFearSystem). Источник истины — сервер;
 * клиент только скрывает/показывает UI и визуальные эффекты страха, без
 * какой-либо собственной логики принятия решений.
 */
public record SyncModFlagsPayload(boolean statisticsEnabled, boolean fearSystemEnabled,
                                   boolean reputationEnabled) implements CustomPacketPayload {
    public static final Type<SyncModFlagsPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_mod_flags"));

    public static final StreamCodec<ByteBuf, SyncModFlagsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncModFlagsPayload::statisticsEnabled,
            ByteBufCodecs.BOOL, SyncModFlagsPayload::fearSystemEnabled,
            ByteBufCodecs.BOOL, SyncModFlagsPayload::reputationEnabled,
            SyncModFlagsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
