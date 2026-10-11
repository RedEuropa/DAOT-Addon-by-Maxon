package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервер -> клиент: сетевые id контрабандистов, которых должен видеть подсвеченными
 * ЭТОТ игрок. Пакет уходит только владельцу перка "Торговец", поэтому подсветку видит
 * он один — ровно та же схема, по которой в dannys-aot работают очки Зика
 * (ZekesGlassesGlowTracker + клиентский миксин на Entity#isCurrentlyGlowing).
 */
public record SmugglerGlowPayload(List<Integer> entityIds) implements CustomPacketPayload {
    public static final Type<SmugglerGlowPayload> TYPE = new Type<>(MaxonDaotAddon.id("smuggler_glow"));

    public static final StreamCodec<FriendlyByteBuf, SmugglerGlowPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityIds().size());
                for (Integer id : payload.entityIds()) {
                    buf.writeVarInt(id);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<Integer> ids = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    ids.add(buf.readVarInt());
                }
                return new SmugglerGlowPayload(ids);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
