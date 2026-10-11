package com.maxonaddon.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ОТКЛЮЧЁН. Класс больше нигде не вызывается: register() и tickCooldowns() убраны из
 * MaxonDaotAddon, а навыки titanic_will_1/2 заменены на steel_nape_1/2 (см. SteelNapeMixin).
 * Причина: превращение упирается в запрет на стороне dannys-aot, снять который надёжно
 * не вышло. Файл оставлен как есть на случай, если к идее вернутся.
 *
 * Ниже — исходное описание.
 *
 * "Титаническая воля" — снимает послебоевое истощение, чтобы игрок мог СРАЗУ превратиться снова.
 *
 * Почему именно так, а не "принудительный ре-шифт":
 *
 * 1. Настоящий блокиратор повторного превращения — эффект СЛАБОСТИ с усилением >= 4,
 *    который сам dannys-aot вешает на шифтера в методе смерти его титана
 *    (AttackTitanEntity.die и аналоги): new MobEffectInstance(WEAKNESS, 900, 4), то есть
 *    Слабость V на 45 секунд. А daot.network.ModNetworking.handleTitanShift первым делом
 *    проверяет ровно это условие (hasEffect(WEAKNESS) и getAmplifier() >= 4) и отвечает
 *    "Too exhausted to transform" — всё проверено байткодом. Отсюда и "несъёмная слабость,
 *    пишет, что сильно устал для превращения".
 *
 * 2. Раньше здесь вызывался ModNetworking.initiatePureTitanShift(...) — это метод шифта
 *    в ЧИСТОГО титана, отсюда и "превращал в тупого чистого титана". forceShiftPlayer(...)
 *    превращал бы в правильного, но навык по задумке должен РАЗРЕШАТЬ превращение, а не
 *    делать его за игрока, поэтому автопревращения здесь больше нет вообще.
 *
 * Истощение гасим не разово, а в течение окна: момент "выбило из титана"
 * (ShifterTitan.isIncapacitated() false -> true) наступает РАНЬШЕ, чем титан фактически
 * умирает и вешает слабость, поэтому разовая очистка ничего бы не дала.
 */
public final class TitanicWillHandler {

    /** Сколько тиков после срабатывания держим тег стелса (с запасом на всю анимацию превращения). */
    private static final int STEALTH_WINDOW_TICKS = 100; // 5 секунд

    /**
     * Сколько тиков гасим истощение. Слабость вешается на 900 тиков (45 с) в момент СМЕРТИ титана,
     * который наступает уже после выбивания, поэтому окно должно перекрывать всю её длительность.
     */
    private static final int RELIEF_WINDOW_TICKS = 1000; // 50 секунд

    /** Ровно то усиление, по которому daot считает игрока "слишком истощённым" (см. handleTitanShift). */
    private static final int EXHAUSTION_AMPLIFIER = 4;

    private static final Map<UUID, Boolean> wasIncapacitated = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> stealthTicksLeft = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> reliefTicksLeft = new ConcurrentHashMap<>();

    private TitanicWillHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (!(entity instanceof daot.ShifterTitan shifter)) continue;

                    boolean incapacitated = shifter.isIncapacitated();
                    UUID titanUuid = entity.getUUID();
                    boolean was = wasIncapacitated.getOrDefault(titanUuid, false);
                    wasIncapacitated.put(titanUuid, incapacitated);

                    if (incapacitated && !was) {
                        maxon$onKnockedOut(level, entity);
                    }
                }
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();

                // Снимаем тег стелса, когда окно истекло — независимо от того, сработала способность
                // прямо сейчас или тег висит с прошлого раза (защита от "вечного стелса" при баге).
                Integer stealth = stealthTicksLeft.get(uuid);
                if (stealth != null) {
                    if (stealth <= 0) {
                        player.removeTag("titan_stealth");
                        stealthTicksLeft.remove(uuid);
                    } else {
                        stealthTicksLeft.put(uuid, stealth - 1);
                    }
                }

                // Пока открыто окно — давим истощение каждый тик: титан умирает уже ПОСЛЕ выбивания
                // и вешает слабость задним числом.
                Integer relief = reliefTicksLeft.get(uuid);
                if (relief != null) {
                    if (relief <= 0) {
                        reliefTicksLeft.remove(uuid);
                    } else {
                        maxon$clearExhaustion(player);
                        reliefTicksLeft.put(uuid, relief - 1);
                    }
                }
            }
        });
    }

    private static void maxon$onKnockedOut(ServerLevel level, Entity titan) {
        UUID shifterUuid = maxon$getShifterUuid(titan);
        if (shifterUuid == null) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(shifterUuid);
        if (player == null) return;

        IPlayerStats stats = (IPlayerStats) player;
        if (!stats.hasSkill("titanic_will_1")) return;
        if (stats.getTitanicWillCooldown() > 0) return;

        // Кулдаун: 3000 сек без второго уровня, 1500 сек с ним.
        int cooldownSeconds = stats.hasSkill("titanic_will_2") ? 1500 : 3000;
        stats.setTitanicWillCooldown(cooldownSeconds * 20);

        player.addTag("titan_stealth");
        stealthTicksLeft.put(shifterUuid, STEALTH_WINDOW_TICKS);

        // Открываем окно подавления истощения и сразу гасим то, что уже висит.
        reliefTicksLeft.put(shifterUuid, RELIEF_WINDOW_TICKS);
        maxon$clearExhaustion(player);

        // Полная шкала стамины — иначе "сразу превратиться" всё равно не выйдет.
        daot.network.ModNetworking.fillStamina(player.getUUID());

        player.displayClientMessage(
                Component.translatable("message.maxonaddon.titanic_will.ready").withStyle(ChatFormatting.GOLD),
                true
        );
    }

    /**
     * Снимает ИМЕННО послебоевое истощение. Слабость слабее 4 уровня (зелья, чужие эффекты)
     * не трогаем — daot её и не считает препятствием для превращения.
     */
    private static void maxon$clearExhaustion(ServerPlayer player) {
        MobEffectInstance weakness = player.getEffect(MobEffects.WEAKNESS);
        if (weakness != null && weakness.getAmplifier() >= EXHAUSTION_AMPLIFIER) {
            player.removeEffect(MobEffects.WEAKNESS);
        }
    }

    /** У всех классов-шифтеров есть публичный getShifterUUID(), но не через общий интерфейс — рефлексия. */
    private static UUID maxon$getShifterUuid(Entity titan) {
        try {
            Method getShifterUuid = titan.getClass().getMethod("getShifterUUID");
            Object result = getShifterUuid.invoke(titan);
            return result instanceof UUID uuid ? uuid : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Тикает кулдауны — вызывается из общего игрового тика (см. MaxonDaotAddon). */
    public static void tickCooldowns(ServerPlayer player) {
        IPlayerStats stats = (IPlayerStats) player;
        if (stats.getTitanicWillCooldown() > 0) {
            stats.setTitanicWillCooldown(stats.getTitanicWillCooldown() - 1);
        }
    }
}
