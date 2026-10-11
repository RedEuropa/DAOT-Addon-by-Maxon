package com.maxonaddon.command;

import com.maxonaddon.util.SmugglerTradeHelper;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

public class SmugglerSpawnCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnsmuggler")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerLevel level = context.getSource().getLevel();
                    BlockPos pos = BlockPos.containing(context.getSource().getPosition());
                    spawnSmuggler(context.getSource(), level, pos);
                    return 1;
                }));
    }

    public static void spawnSmuggler(CommandSourceStack source, ServerLevel level, BlockPos pos) {
        Villager smuggler = EntityType.VILLAGER.create(level);
        if (smuggler == null) return;

        smuggler.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        SmugglerTradeHelper.markAsSmuggler(smuggler);
        level.addFreshEntity(smuggler);

        source.sendSuccess(() -> Component.translatable("command.maxonaddon.smuggler.spawn_success"), true);
    }
}
