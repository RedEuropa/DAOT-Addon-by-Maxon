package com.maxonaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import daot.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.ChatFormatting;

public class SpawnBossCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnboss")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("boss_type", StringArgumentType.word())
                        // ПОДСКАЗКИ НА КЛАВИШУ TAB
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                new String[]{"attack", "armored", "female", "beast", "warhammer", "colossal", "ogre", "ogre_shifter", "rumbling", "jaw"}, builder))
                        .executes(context -> {
                            String type = StringArgumentType.getString(context, "boss_type");
                            return spawnBoss(context.getSource(), type);
                        })));
    }

    private static int spawnBoss(CommandSourceStack source, String type) {
            ServerLevel level = source.getLevel();
            Vec3 pos = source.getPosition();

            Monster boss = null;
            String bossName = "";
            boolean isRumbling = false;

            switch (type.toLowerCase()) {
                case "attack": boss = new AttackTitanEntity(DannysAot.ATTACK_TITAN, level); bossName = "Attack Titan"; break;
                case "armored": boss = new ArmoredTitanEntity(DannysAot.ARMORED_TITAN, level); bossName = "Armored Titan"; break;
                case "female": boss = new FemaleTitanEntity(DannysAot.FEMALE_TITAN, level); bossName = "Female Titan"; break;
                case "beast": boss = new BeastTitanEntity(DannysAot.BEAST_TITAN, level); bossName = "Beast Titan"; break;
                case "warhammer": boss = new WarhammerTitanEntity(DannysAot.WARHAMMER_TITAN, level); bossName = "Warhammer Titan"; break;
                case "colossal": boss = new ColossalTitanEntity(DannysAot.COLOSSAL_TITAN, level); bossName = "Colossal Titan"; break;
                case "jaw": boss = new daot.TestShifterTitanEntity(DannysAot.TEST_SHIFTER_TITAN, level); bossName = "Jaw Titan"; break;
                case "rumbling":
                    boss = new ColossalTitanEntity(DannysAot.COLOSSAL_TITAN, level);
                    bossName = "Rumbling Titan";
                    isRumbling = true;
                    break;
                case "ogre_shifter": boss = new OgreShifterTitanEntity(DannysAot.OGRE_SHIFTER_TITAN, level); bossName = "Ogre Shifter"; break;
                case "ogre":
                    OgreTitanEntity ogre = new OgreTitanEntity(DannysAot.OGRE_TITAN, level);
                    ogre.setPos(pos.x, pos.y, pos.z);
                    level.addFreshEntity(ogre);
                    source.sendSuccess(() -> Component.translatable("command.maxonaddon.spawnboss.success.ogre").withStyle(ChatFormatting.GREEN), true);
                    return 1;
                default:
                    source.sendFailure(Component.translatable("command.maxonaddon.spawnboss.error.unknown_type").withStyle(ChatFormatting.RED));
                    return 0;
            }

            if (boss != null) {
                boss.setPos(pos.x, pos.y, pos.z);

                // Назначаем разные теги и ХП в зависимости от типа
                if (isRumbling) {
                    boss.addTag("rumbling_boss");
                    boss.addTag("boss_mode");
                    if (boss.getAttribute(Attributes.MAX_HEALTH) != null) {
                        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(5000.0D);
                        boss.setHealth(5000.0F);
                    }
                } else {
                    boss.addTag("berserk_boss");
                    boss.addTag("boss_mode");
                    if (boss.getAttribute(Attributes.MAX_HEALTH) != null) {
                        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1500.0D);
                        boss.setHealth(1500.0F);
                    }
                }

                level.addFreshEntity(boss);

                // Если это Молотобоец, при спавне командой ему тоже нужен кристалл,
                // но без жителя внутри (или с пустым/автоматическим созданием оболочки).
                if (boss instanceof daot.WarhammerTitanEntity warhammer) {
                    try {
                        java.lang.reflect.Method spawnCrystal = warhammer.getClass().getMethod(
                                "spawnCrystalShell", double.class, double.class, double.class, java.util.UUID.class
                        );
                        // Передаем фиктивный UUID, так как жителя нет
                        spawnCrystal.invoke(warhammer, pos.x, pos.y, pos.z, java.util.UUID.randomUUID());
                    } catch (Exception ignored) {}
                }

                final String finalName = bossName;
                source.sendSuccess(() -> Component.translatable("command.maxonaddon.spawnboss.success.berserk", finalName).withStyle(ChatFormatting.GREEN), true);
            }

            return 1;
        }
    }
