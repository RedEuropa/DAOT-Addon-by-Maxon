package com.maxonaddon.util;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Отметка "игрок прямо сейчас проводит настоящий взмах клинком".
 * <p>
 * Нужна, чтобы отличить удар из механики dannys-aot (BladeAttackTracker.startAttack и
 * последующие попадания в его tick) от обычного ванильного клика по хитбоксу. Разделить их
 * по источнику урона нельзя: daot наносит урон тем же самым damageSources().playerAttack(),
 * что и ванильная атака мышью, — проверено байткодом BladeAttackTracker.
 * <p>
 * Без этого различения любой клик по шее титана с клинком в руке превращался нашим
 * TitanMeleeNapeMixin в полноценный удар с базой 30 и критом, а на скорости выше 3.0
 * (то есть в полёте на тросе) крит там гарантированный — отсюда и "просто зацепился
 * крюком, а титан умер".
 */
public final class BladeAttackWindow {

    /** Сколько тиков после начала взмаха попадания считаются частью этого взмаха. */
    private static final long WINDOW_TICKS = 60L;

    private static final Map<UUID, Long> lastAttackTick = new ConcurrentHashMap<>();

    private BladeAttackWindow() {}

    /** Вызывается, когда dannys-aot реально запустил атаку клинком. */
    public static void mark(Player player) {
        lastAttackTick.put(player.getUUID(), player.level().getGameTime());
    }

    /** Идёт ли у игрока настоящий взмах прямо сейчас. */
    public static boolean isSwinging(Player player) {
        Long started = lastAttackTick.get(player.getUUID());
        if (started == null) return false;

        long now = player.level().getGameTime();
        // now < started — время мира ушло назад (смена измерения, /time set): считаем отметку
        // протухшей, иначе она застряла бы навсегда.
        if (now - started > WINDOW_TICKS || now < started) {
            lastAttackTick.remove(player.getUUID());
            return false;
        }
        return true;
    }
}
