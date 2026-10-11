package com.maxonaddon.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "daot.TitanPowerHelper", remap = false)
public abstract class TitanEatingPowerMixin {

    /**
     * Защита от повторного "завершения поедания" одной и той же парой титан+жертва.
     *
     * В daot TitanEntity.tick() (оба места, где вызывается handleEatingCompletion) написано так:
     *
     *     TitanPowerHelper.handleEatingCompletion(this, victim, level);
     *     if (this.isDeadOrDying()) return;      // ранний выход
     *     this.setEating(false);                 // ...а вот эта очистка уже не выполняется
     *     this.setEatingTargetId(-1);
     *
     * То есть если титан сам мёртв/умирает в момент завершения поедания, состояние поедания
     * НЕ сбрасывается, а tick() у TitanEntity не имеет проверки на смерть в начале — и на
     * следующем тике всё повторяется. Каждый такой вызов внутри спавнит GibEntity.spawnGibs,
     * а это 45-69 сущностей ЗА ВЫЗОВ (проверено байткодом). Отсюда и "dannys-aot:gib в
     * сумасшедшем количестве" с просадкой TPS.
     *
     * Усугубляют это наши собственные UniversalTitanDeathMixin/BigTitanDeathMixin: они
     * перехватывают tickDeath и держат труп титана в мире до deathTime 200 вместо ванильных
     * ~20, то есть окно повтора длиннее примерно в 10 раз.
     */
    @Unique
    private static final long MAXON$DEDUPE_TICKS = 600L; // 30 секунд

    @Unique
    private static final Map<String, Long> maxon$completedEats = new ConcurrentHashMap<>();

    @Inject(method = "handleEatingCompletion", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maxon$onEatGrantStoredTag(Monster titan, LivingEntity victim, ServerLevel level, CallbackInfo ci) {
        long now = level.getGameTime();
        String key = titan.getUUID() + "|" + victim.getUUID();

        Long last = maxon$completedEats.get(key);
        if (last != null && now - last < MAXON$DEDUPE_TICKS) {
            // Это повтор того же самого поедания — гибы и передачу силы не дублируем.
            ci.cancel();
            return;
        }
        maxon$completedEats.put(key, now);

        // Чистим протухшие записи, чтобы карта не росла бесконечно на долгих сессиях.
        if (maxon$completedEats.size() > 128) {
            Iterator<Map.Entry<String, Long>> it = maxon$completedEats.entrySet().iterator();
            while (it.hasNext()) {
                if (now - it.next().getValue() >= MAXON$DEDUPE_TICKS) {
                    it.remove();
                }
            }
        }

        // Проверяем, что атакующим титаном управляет игрок
        if (titan.getFirstPassenger() instanceof ServerPlayer player) {

            // Сканируем все теги съеденной жертвы (жителя или игрока)
            for (String tag : victim.getTags()) {

                // Ищем теги силы мода DAOT
                if (tag.startsWith("dannys-aot:") && tag.endsWith("_titan")) {

                    // Очищаем строку, оставляя только суть.
                    // Например: "dannys-aot:attack_titan" -> "attack"
                    String powerName = tag.replace("dannys-aot:", "").replace("_titan", "");

                    // Собираем новый тег и выдаем игроку
                    player.addTag("stored_" + powerName);
                    break; // Прерываем цикл, так как силу уже нашли
                }
            }
        }
    }
}
