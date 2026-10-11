package com.maxonaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public class SpawnCarriageCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawn_carriage")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    Villager npc = EntityType.VILLAGER.create(source.getLevel());

                    if (npc != null) {
                        npc.setPos(source.getPosition().x, source.getPosition().y, source.getPosition().z);

                        // Делаем его бессмертным и невидимым для ванильного ИИ
                        npc.setInvulnerable(true);
                        npc.setNoAi(true);
                        npc.setPersistenceRequired(); // Метод без аргументов
                        // Одеваем плащ
                        ItemStack cloak = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("dannys-aot", "royal_cloak")));
                        npc.setItemSlot(EquipmentSlot.HEAD, cloak);

                        npc.addTag("carriage_driver");
                        source.getLevel().addFreshEntity(npc);

                        source.sendSuccess(() -> Component.literal("Перевозчик успешно установлен!"), false);
                    }
                    return 1;
                }));
    }
}