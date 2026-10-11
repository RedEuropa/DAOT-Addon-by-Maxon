package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * C2S: игрок тянет тросом зацепленную сущность.
 * <p>
 * Физику троса считает клиент — он же владеет крюками и знает нажатые клавиши. Серверу
 * достаётся только применить импульс, чтобы моб сдвинулся и для остальных игроков тоже.
 * Сервер при этом проверяет и дистанцию, и величину силы: пакет приходит от клиента, а
 * доверять ему нельзя.
 */
public record PullEntityPayload(int entityId, Vec3 force) implements CustomPacketPayload {
    public static final Type<PullEntityPayload> TYPE = new Type<>(MaxonDaotAddon.id("pull_entity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PullEntityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, PullEntityPayload::entityId,
            ByteBufCodecs.DOUBLE, p -> p.force().x,
            ByteBufCodecs.DOUBLE, p -> p.force().y,
            ByteBufCodecs.DOUBLE, p -> p.force().z,
            (id, x, y, z) -> new PullEntityPayload(id, new Vec3(x, y, z))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
