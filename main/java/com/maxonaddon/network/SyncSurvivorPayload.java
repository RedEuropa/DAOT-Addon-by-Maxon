package com.maxonaddon.network;

import com.maxonaddon.MaxonDaotAddon;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C: маркер-локатор на раненого выжившего (тот же трек над XP-баром, что у доставки и
 * спасения — см. GuiMixin). Нужен, чтобы игрок не потерял жителя, если положил его на землю
 * и отошёл. carried=true — житель сейчас на спине игрока, стрелка не нужна (клиент прячет
 * трек и вместо него пишет подсказку "донесите его до командира").
 */
public record SyncSurvivorPayload(boolean active, String dimension, double x, double y, double z, boolean carried) implements CustomPacketPayload {
    public static final Type<SyncSurvivorPayload> TYPE = new Type<>(MaxonDaotAddon.id("sync_survivor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSurvivorPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncSurvivorPayload::active,
            ByteBufCodecs.STRING_UTF8, SyncSurvivorPayload::dimension,
            ByteBufCodecs.DOUBLE, SyncSurvivorPayload::x,
            ByteBufCodecs.DOUBLE, SyncSurvivorPayload::y,
            ByteBufCodecs.DOUBLE, SyncSurvivorPayload::z,
            ByteBufCodecs.BOOL, SyncSurvivorPayload::carried,
            SyncSurvivorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
