package com.maxonaddon;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class RedTitanCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("spawnredtitan")
                    .requires(source -> source.hasPermission(2)) // Только для операторов
                    .executes(context -> {
                        CommandSourceStack source = context.getSource();
                        ServerLevel level = source.getLevel();
                        // Берем координаты того, кто вызвал команду
                        BlockPos pos = BlockPos.containing(source.getPosition());

                        // ID обычного титана из мода dannys-aot
                        ResourceLocation titanId = ResourceLocation.tryParse("dannys-aot:titan_beard");
                        if (titanId == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(titanId)) {
                            source.sendFailure(Component.translatable("command.maxonaddon.redtitan.error_not_found").withStyle(ChatFormatting.RED));
                            return 0;
                        }

                        // Спавним сущность
                        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(titanId);
                        Entity entity = type.spawn(level, pos, MobSpawnType.COMMAND);

                        if (entity instanceof Mob mob) {
                            // Принудительно выдаем тег, чтобы миксин начал рисовать частицы редстоуна
                            mob.addTag("RedEyedTitan");

                            // Баффаем урон в 2 раза
                            if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
                                double currentDamage = mob.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
                                mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(currentDamage * 2.0);
                            }

                            // Баффаем скорость в 2 раза
                            if (mob.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
                                double currentSpeed = mob.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
                                mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(currentSpeed * 3.0);
                            }

                            source.sendSuccess(() -> Component.translatable("command.maxonaddon.redtitan.success").withStyle(ChatFormatting.RED), true);
                            return 1;
                        }

                        source.sendFailure(Component.translatable("command.maxonaddon.redtitan.error_spawn").withStyle(ChatFormatting.RED));
                        return 0;
                    }));
        });
    }
}