package com.maxonaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import daot.TitanPowerType;
import daot.TitanPowerData; // Импортируем базу данных мода
import daot.network.PoweredVillagerSyncPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public class SpawnPoweredVillagerCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnshiftervillager")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("power_type", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                new String[]{"attack", "armored", "female", "beast", "warhammer", "colossal", "cart", "jaw", "founding"}, builder))
                        .executes(context -> {
                            String type = StringArgumentType.getString(context, "power_type");
                            return spawnVillager(context.getSource(), type);
                        })));
    }

    private static int spawnVillager(CommandSourceStack source, String type) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        // Получаем тип силы напрямую
        TitanPowerType powerToTransfer = getPowerType(type);
        if (powerToTransfer == null) {
            source.sendFailure(Component.literal("Неизвестная сила титана!").withStyle(ChatFormatting.RED));
            return 0;
        }

        // Спавним жителя
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) return 0;
        villager.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(villager);

        // ===============================================================
        // САМОЕ ГЛАВНОЕ: ОФИЦИАЛЬНАЯ ЗАПИСЬ ЖИТЕЛЯ В БАЗУ ДАННЫХ МОДА
        // ===============================================================
        TitanPowerData powerData = TitanPowerData.get(level);
        powerData.setPower(villager.getUUID(), powerToTransfer);

        // Отправляем пакеты свечения клиентам
        PoweredVillagerSyncPayload payload = PoweredVillagerSyncPayload.addPower(villager.getId(), powerToTransfer.ordinal());
        for (ServerPlayer trackingPlayer : PlayerLookup.tracking(villager)) {
            ServerPlayNetworking.send(trackingPlayer, payload);
        }
        if (source.getEntity() instanceof ServerPlayer sender) {
            ServerPlayNetworking.send(sender, payload);
        }

        // Теги для декора
        villager.addTag("shifter_villager");
        villager.addTag("titan_power_" + type.toLowerCase());
        villager.addTag("shifter_boss_vessel");

        source.sendSuccess(() -> Component.literal("Житель с силой " + powerToTransfer.getDisplayName() + " успешно призван!").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static TitanPowerType getPowerType(String requestedType) {
        for (TitanPowerType power : TitanPowerType.values()) {
            if (power.name().toLowerCase().startsWith(requestedType.toLowerCase())) {
                return power;
            }
        }
        return null;
    }
}