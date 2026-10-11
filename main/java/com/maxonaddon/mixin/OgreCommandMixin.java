package com.maxonaddon.mixin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import daot.ModCommands;
import daot.network.ModNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Mixin(value = ModCommands.class, remap = false)
public class OgreCommandMixin {

    // Получаем доступ к приватным спискам мода и делаем их изменяемыми
    @Shadow @Mutable @Final private static Set<String> VALID_TYPES;
    @Shadow @Mutable @Final private static Set<String> ALL_SHIFTER_TAGS;

    // 1. Внедряемся в момент загрузки класса (clinit) и добавляем Огра в валидные титаны
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void addOgreToSets(CallbackInfo ci) {
        Set<String> newValid = new HashSet<>(VALID_TYPES);
        newValid.add("ogre_shifter");
        VALID_TYPES = Set.copyOf(newValid);

        Set<String> newAll = new HashSet<>(ALL_SHIFTER_TAGS);
        newAll.add("ogre_shifter");
        ALL_SHIFTER_TAGS = Set.copyOf(newAll);
    }

    // 2. Добавляем автодополнение (Tab) в чате
    @Inject(method = "suggestTitanTypes", at = @At("HEAD"), cancellable = true)
    private static void onSuggestTitanTypes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder, CallbackInfoReturnable<CompletableFuture<Suggestions>> cir) {
        for (String type : VALID_TYPES) {
            builder.suggest(type);
        }
        cir.setReturnValue(builder.buildFuture());
    }

    // 3. Задаем красивое отображение имени в системных сообщениях
    @Inject(method = "getTitanDisplayName", at = @At("HEAD"), cancellable = true)
    private static void onGetTitanDisplayName(String type, CallbackInfoReturnable<String> cir) {
        if ("ogre_shifter".equals(type)) {
            cir.setReturnValue("Ogre Shifter");
        }
    }

    // 4. Ловим УСПЕШНОЕ завершение команды /daot shifter set и выдаем здоровье Огра
    @Inject(method = "setShifter", at = @At("RETURN"))
    private static void onSetShifterReturn(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        try {
            String type = StringArgumentType.getString(context, "type").toLowerCase();
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");

            for (ServerPlayer player : targets) {
                if ("ogre_shifter".equals(type)) {
                    ModNetworking.applyOgreShifterAttributes(player);
                } else if (!player.getTags().contains("ogre_shifter")) {
                    // Если выдали другого титана, снимаем бонус хп Огра на всякий случай
                    ModNetworking.removeOgreShifterAttributes(player);
                }
            }
        } catch (Exception ignored) {}
    }

    // 5. Ловим УСПЕШНОЕ завершение команды /daot shifter remove и забираем здоровье Огра
    @Inject(method = "removeShifter", at = @At("RETURN"))
    private static void onRemoveShifterReturn(CommandContext<CommandSourceStack> context, CallbackInfoReturnable<Integer> cir) {
        try {
            String type = StringArgumentType.getString(context, "type").toLowerCase();
            if ("ogre_shifter".equals(type)) {
                Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                for (ServerPlayer player : targets) {
                    ModNetworking.removeOgreShifterAttributes(player);
                }
            }
        } catch (Exception ignored) {}
    }
}