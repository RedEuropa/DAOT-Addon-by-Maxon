package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Весь серверный maxon_addon.json целиком, одной строкой — сервер отправляет его каждому
 * входящему игроку.
 * <p>
 * Отдельными полями, как в SyncModFlagsPayload, тут было бы неудобно: настроек больше
 * полусотни, и каждая новая требовала бы правки пакета. JSON же переживает и добавление
 * полей, и разницу версий — GSON просто оставит незнакомому полю значение по умолчанию.
 * <p>
 * Геймрулы (страх и прочее) по-прежнему едут в SyncModFlagsPayload: они живут не в конфиге,
 * а в самом мире, и меняются по ходу игры.
 */
public record ConfigSyncPayload(String json) implements CustomPacketPayload {
    public static final Type<ConfigSyncPayload> TYPE = new Type<>(MaxonDaotAddon.id("config_sync"));

    public static final StreamCodec<ByteBuf, ConfigSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigSyncPayload::json,
            ConfigSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
