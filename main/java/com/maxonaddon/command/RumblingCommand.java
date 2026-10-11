package com.maxonaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import daot.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.ChatFormatting;

public class RumblingCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rumbling")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("distance", IntegerArgumentType.integer(10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(context -> {
                                    int distance = IntegerArgumentType.getInteger(context, "distance");
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    return startRumbling(context.getSource(), distance, count);
                                })
                        )
                ));
    }

    private static int startRumbling(CommandSourceStack source, int distance, int count) {
        ServerLevel level = source.getLevel();

        for (int i = 0; i < count; i++) {
            // Рассчитываем позиции полукругом (от 0 до PI радиан)
            // Испольуем distance для радиуса
            double angle = (count > 1) ? Math.PI * ((double) i / (count - 1)) : Math.PI / 2;

            double spawnX = distance * Math.cos(angle);
            double spawnZ = distance * Math.sin(angle);

            // Находим высоту земли
            double spawnY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) spawnX, (int) spawnZ) + 1.0;

            ColossalTitanEntity titan = new ColossalTitanEntity(DannysAot.COLOSSAL_TITAN, level);

            // Настройка параметров
            titan.setPos(spawnX, spawnY, spawnZ);
            titan.addTag("rumbling_boss");
            titan.addTag("boss_mode");

            if (titan.getAttribute(Attributes.MAX_HEALTH) != null) {
                titan.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000.0D);
                titan.setHealth(5000.0F);
            }

            level.addFreshEntity(titan);

            // Визуальный эффект спавна (молния, чтобы показать, что это босс)
            net.minecraft.world.entity.LightningBolt lightning = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
            if (lightning != null) {
                lightning.setPos(spawnX, spawnY, spawnZ);
                level.addFreshEntity(lightning);
            }
        }

        source.sendSuccess(() -> Component.literal("The Rumbling has started! " + count + " Colossals spawned.")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);

        return 1;
    }
}