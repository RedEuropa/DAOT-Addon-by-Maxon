package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Сервер -> клиент: полный список ID перков, которые игрок разблокировал (получил). */
public record SyncUnlockedPerksPayload(List<String> unlockedPerks) implements CustomPacketPayload {
    public static final Type<SyncUnlockedPerksPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_unlocked_perks"));

    public static final StreamCodec<FriendlyByteBuf, SyncUnlockedPerksPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.unlockedPerks().size());
                for (String id : payload.unlockedPerks()) {
                    buf.writeUtf(id);
                }
            },
            buf -> {
                int size = buf.readVarInt();
                List<String> perks = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    perks.add(buf.readUtf());
                }
                return new SyncUnlockedPerksPayload(perks);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
