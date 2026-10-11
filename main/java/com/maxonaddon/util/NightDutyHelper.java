package com.maxonaddon.util;

import com.maxonaddon.MaxonConfig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

/**
 * Общая логика "дежурства" для гарнизона, скаутов и полиции: каждую игровую ночь
 * заново решает, останется ли конкретный житель патрулировать (тег "night_duty")
 * или проведёт эту ночь как обычный житель ("спит"/не гоняется ни за кем).
 * Контрабандистов ("smuggler") это НЕ касается — они активны каждую ночь без исключений.
 */
public final class NightDutyHelper {

    public static final String NIGHT_DUTY_TAG = "night_duty";
    private static final String LAST_ROLL_DAY_TAG_PREFIX = "maxon$nightRollDay_";

    private NightDutyHelper() {}

    public static boolean isNight(Level level) {
        return !level.isDay();
    }

    /**
     * Если наступила новая ночь — один раз перебрасывает "останется ли на дежурство"
     * для этого жителя и запоминает игровой день, чтобы не перебрасывать повторно
     * каждый тик. Безопасно вызывать каждый тик — сам следит за частотой.
     */
    public static void rollIfNewNight(Villager villager, double dutyChancePercent) {
        Level level = villager.level();
        if (level.isClientSide() || !isNight(level)) return;

        long currentDay = level.getDayTime() / 24000L;
        String currentKey = LAST_ROLL_DAY_TAG_PREFIX + currentDay;

        // Уже перебрасывали дежурство в эту конкретную ночь — не трогаем.
        if (villager.getTags().contains(currentKey)) return;

        // Ночь сменилась (или это первая проверка вообще) — чистим метки прошлых ночей и решаем заново.
        villager.getTags().removeIf(tag -> tag.startsWith(LAST_ROLL_DAY_TAG_PREFIX));
        villager.addTag(currentKey);

        boolean onDuty = villager.getRandom().nextFloat() < (float) (dutyChancePercent / 100.0);
        if (onDuty) {
            villager.addTag(NIGHT_DUTY_TAG);
        } else {
            villager.removeTag(NIGHT_DUTY_TAG);
        }
    }

    /** true, если сейчас день (дежурство неактуально — все ведут себя как обычно) либо житель на дежурстве. */
    public static boolean isActiveNow(Villager villager) {
        if (!isNight(villager.level())) return true;
        return villager.getTags().contains(NIGHT_DUTY_TAG);
    }

    public static double dutyChance() {
        return MaxonConfig.get().nightDutyChance;
    }
}
