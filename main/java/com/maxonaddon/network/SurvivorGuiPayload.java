package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: открыть у игрока экран разговора с раненым выжившим (см. SurvivorQuestScreen) —
 * тот же приём, что и QuestGuiPayload для командиров: сервер решает, что диалог уместен
 * (это правда раненый, квест ещё не взят), клиент только показывает окно.
 */
public record SurvivorGuiPayload(int survivorId) implements CustomPacketPayload {
    public static final Type<SurvivorGuiPayload> TYPE = new Type<>(MaxonDaotAddon.id("survivor_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SurvivorGuiPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SurvivorGuiPayload::survivorId,
            SurvivorGuiPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
