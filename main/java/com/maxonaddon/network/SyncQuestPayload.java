package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: состояние активного задания. testQuestCompletions — сколько раз игрок уже сдал
 * ПОВТОРЯЕМОЕ тестовое задание: от этого числа клиент сам считает текущую планку
 * (см. IPlayerStats#testQuestTitansRequired/testQuestWheatRequired) и понимает, открыты ли
 * уже остальные задания у командиров. Раньше тут был boolean "тестовое сдано" — он
 * блокировал повторный приём, что для растущего задания больше не нужно.
 */
public record SyncQuestPayload(String questId, String target, int questTitansKilled, int testQuestCompletions,
                                long deliveryDeadlineTick, int testQuestCommanderMask) implements CustomPacketPayload {
    public static final Type<SyncQuestPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_quest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncQuestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncQuestPayload::questId,
            ByteBufCodecs.STRING_UTF8, SyncQuestPayload::target,
            ByteBufCodecs.INT, SyncQuestPayload::questTitansKilled,
            ByteBufCodecs.INT, SyncQuestPayload::testQuestCompletions,
            ByteBufCodecs.VAR_LONG, SyncQuestPayload::deliveryDeadlineTick,
            ByteBufCodecs.INT, SyncQuestPayload::testQuestCommanderMask,
            SyncQuestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
