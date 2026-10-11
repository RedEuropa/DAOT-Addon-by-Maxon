package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuestActionPayload(int action, String questId, String target, int commanderId) implements CustomPacketPayload {
    public static final Type<QuestActionPayload> TYPE = new Type<>(MaxonDaotAddon.id("quest_action"));
    // action: 0 = Принять, 1 = Отказаться, 2 = Сдать
    // commanderId — сетевой ID НПС-командира, у которого игрок взял/сдаёт задание (для привязки
    // репутации к его стене, см. WallHelper.getWallForCommander); -1, если неизвестен/неважен
    // (например, "Отказаться" его не использует).

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, QuestActionPayload::action,
            ByteBufCodecs.STRING_UTF8, QuestActionPayload::questId,
            ByteBufCodecs.STRING_UTF8, QuestActionPayload::target,
            ByteBufCodecs.INT, QuestActionPayload::commanderId,
            QuestActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
