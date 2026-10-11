package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: живые счётчики квеста "Спасение застрявших" для журнала заданий (см.
 * QuestJournalScreen) — сколько кадетов уцелело из скольких, сколько ещё нужно убить
 * титанов (само число убитых уже идёт в общем SyncQuestPayload.questTitansKilled — оно
 * генерическое для любого активного квеста), добрался ли игрок до точки, и дедлайн на
 * дорогу (пока arrived == false).
 */
public record SyncRescueStatusPayload(boolean active, boolean arrived, int cadetsTotal, int cadetsAlive,
                                       int titansRequired, long deadlineTick) implements CustomPacketPayload {
    public static final Type<SyncRescueStatusPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_rescue_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncRescueStatusPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncRescueStatusPayload::active,
            ByteBufCodecs.BOOL, SyncRescueStatusPayload::arrived,
            ByteBufCodecs.INT, SyncRescueStatusPayload::cadetsTotal,
            ByteBufCodecs.INT, SyncRescueStatusPayload::cadetsAlive,
            ByteBufCodecs.INT, SyncRescueStatusPayload::titansRequired,
            ByteBufCodecs.VAR_LONG, SyncRescueStatusPayload::deadlineTick,
            SyncRescueStatusPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
