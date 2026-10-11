package com.maxonaddon.command;

import com.maxonaddon.util.PoliceGearHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

public class PoliceSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnpolice")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                    spawnPolice(context.getSource(), level, pos);
                    return 1;
                }));
    }

    public static void spawnPolice(CommandSourceStack source, ServerLevel level, BlockPos pos) {
        Villager officer = EntityType.VILLAGER.create(level);
        if (officer == null) return;

        officer.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        PoliceGearHelper.equipAsPolice(officer);
        level.addFreshEntity(officer);

        source.sendSuccess(() -> Component.translatable("command.maxonaddon.police.spawn_success"), true);
    }
}
