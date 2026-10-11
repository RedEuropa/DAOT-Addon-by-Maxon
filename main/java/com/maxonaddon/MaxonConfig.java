package com.maxonaddon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.GameRules;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class MaxonConfig {
    // === ОТКЛЮЧЕНИЕ СИСТЕМ (СЕРВЕРНЫЙ КОНФИГ, НЕ КЛИЕНТСКИЙ) ===
    // Если false — проверки скилов обходятся (кроме пассивных скилов на повышение статов,
    // см. ModSkill.AUTO_GRANT_IDS), меню/команда покупки скилов можно оставить как есть.
    public boolean skillsSystemEnabled = true;
    // Если false — перков нет совсем: они не выдаются, надетые не дают бонусов, кнопка
    // перков в инвентаре скрыта. Уже открытые не стираются — при обратном включении
    // вернутся вместе с тем, что было надето.
    public boolean perksSystemEnabled = true;
    // === НАСТРОЙКИ СМЕРТИ ТИТАНОВ ===
    // Если false — титаны будут умирать со стандартной анимацией без появления скелета
    public boolean enableTitanSkeletons = true;
    // Если false — команда /statistics и кнопка статистики в инвентаре полностью скрыты.
    public boolean statisticsSystemEnabled = true;
    // === СИСТЕМА КВЕСТОВ И РЕПУТАЦИИ ===
    // Если false — выключается разом весь этот пласт: командиры не выдают задания, раненые
    // выжившие не появляются на аванпостах, репутация не начисляется и не отнимается (и не
    // показывается в статистике), контрабандисты торгуют со всеми без оглядки на неё.
    // Одним флагом, а не тремя, потому что квесты и репутация тут неразделимы: почти каждый
    // квест платит репутацией, а почти каждый источник репутации — это квест.
    public boolean reputationAllow = true;
    // === НАСТРОЙКИ СПАВНА В ПАРАДИЗЕ ===
    public boolean enableRandomParadisSpawn = true;
    // === ВКЛЮЧЕНИЕ/ВЫКЛЮЧЕНИЕ СИСТЕМЫ HP И КРИТОВ ТИТАНОВ ===
    public boolean pureTitanRandomHp = true;

    // === НАСТРОЙКИ ФИЗИКИ И РЕНДЕРА ТРОСОВ УПМ ===
    // Если false — отключает провисание, инерцию, толщину в 5 пикселей и полет со скоростью стрелы.
    // Тросы будут работать и рисоваться ровно как в оригинальном dannys-aot.
    public boolean enableAdvancedHookMechanics = true;

    // === СВОЁ УПМ (см. OdmPhysicsMixin) ===
    // Главный выключатель всего, что аддон добавил к снаряжению. Если false — УПМ работает
    // ровно как в оригинальном dannys-aot, целиком:
    //   * полёт считает сам базовый мод (мы его метод не отменяем);
    //   * трос без управляемой длины, натяжения и провисания;
    //   * крюком нельзя подтягивать зацепленных мобов;
    //   * УПМ не работает верхом, и спрыгнуть с седла рывком нельзя
    //     (навыки «Всадник» и «Спрыг с седла» при этом просто ни на что не влияют).
    // Сделано отдельным флагом от enableAdvancedHookMechanics: тот отвечает только за вид и
    // скорость самого крюка, а этот — за всю механику полёта.
    public boolean enableOdmPhysics = true;
    // Насколько быстро пробел сматывает трос, а шифт его стравливает (блоков за тик).
    public double odmReelSpeed = 1.3;
    public double odmUnspoolSpeed = 0.6;
    // Потолок скорости, когда игрок тянется только лебёдкой (W), без газа.
    public double odmMaxSpeedReelOnly = 1.43;
    // С какой силой лебёдка тянет игрока к точке зацепа. Это разгон по тросу, а не полёт:
    // высоту набирают маятником, поэтому значение намеренно скромное.
    public double odmReelPullForce = 0.29;
    // true — у самой точки зацепа скорость не гасится, и игрока проносит мимо «рогаткой».
    public boolean odmAllowSlingshot = false;
    // Множители расхода газа: на форсаже (W) и на лебёдке/орбите.
    public double odmGasMultiplierBoost = 2.0;
    public double odmGasMultiplierReel = 1.0;

    // === ЧИСТКА ЗАСТРЯВШИХ ЧИСТЫХ ТИТАНОВ (см. StuckTitanCleaner) ===
    // Титаны проваливаются в пещеры, сами не деспавнятся и занимают лимит спавна.
    public boolean stuckTitanCleanup = true;
    // Как часто проходить по миру, в секундах.
    public int stuckTitanCleanupIntervalSeconds = 60;
    // Нет ни одного игрока в этом радиусе — титана убираем.
    public double stuckTitanKeepRadius = 512.0;
    // Титан без неба над головой (пещера) и игрок дальше этого радиуса — убираем.
    public double stuckTitanCaveRadius = 128.0;
    // === ИГРОВЫЕ ПРАВИЛА (GAMERULES) ===
    public static GameRules.Key<GameRules.BooleanValue> DO_SOLDIER_SPAWN;
    public static GameRules.Key<GameRules.BooleanValue> ALLOW_FLARE_REINFORCEMENTS;
    public static GameRules.Key<GameRules.BooleanValue> ALLOW_FEAR_SYSTEM;
    public static GameRules.Key<GameRules.BooleanValue> ALLOW_INJURES;
    public static GameRules.Key<GameRules.BooleanValue> DO_ODM_CRACKED;
    public static GameRules.Key<GameRules.BooleanValue> DO_BANDIT_OUTPOSTS;
    public static GameRules.Key<GameRules.BooleanValue> DO_CADET_OUTPOSTS;

    // === ВРЕМЯ ЛЕЧЕНИЯ КОНЕЧНОСТЕЙ (В СЕКУНДАХ) ===
    public int headTraumaDurationSeconds = 300;
    public int bodyTraumaDurationSeconds = 300;
    public int armsTraumaDurationSeconds = 300;
    public int legsTraumaDurationSeconds = 300;

    // === ШАНСЫ ПОЛУЧЕНИЯ ТРАВМ (%) ===
    public double headTraumaChance = 25.0;
    public double bodyTraumaChance = 25.0;
    public double armsTraumaChance = 25.0;
    public double legsTraumaChance = 25.0;
    // === НАСТРОЙКИ ТРАВМ И УПМ ===
    public float traumaDamageThreshold = 3.0F; // Урон, от которого начинается риск травмы (по умолчанию 3)
    public double traumaChanceOnDamage = 0.3;   // Шанс травмы при получении урона (30%)

    // === ВРЕМЯ ЛЕЧЕНИЯ ДЛЯ ШИФТЕРОВ (В СЕКУНДАХ) ===
    public int shifterTraumaDurationSeconds = 5; // Ускоренное лечение шифтера (по умолчанию 5 секунд)

    // === НАСТРОЙКИ СТРАХА ===
    public int titansForFearImmunity = 3;

    // === НАСТРОЙКИ ОПЫТА И ОЧКОВ ===
    // Награда за убитого титана зависит от облика игрока — начисляется что-то одно.
    // В человеческом облике: опыт за убийство. Опыт копится в уровень, а уровень даёт очко навыка.
    public int expPerTitanKill = 25;
    // В облике титана (игрок сидит на своём шифтере): очки титана вместо опыта.
    public int titanPointsPerKill = 1;
    public int bloodlinePointsAmount = 1;
    public int bloodlinePointsTicks = 1200;

    // === МЕХАНИКА ЖИТЕЛЕЙ-ШИФТЕРОВ ===
    public boolean enableVillagerShifterTransformation = true;
    public double villagerTransformationChance = 100.0;

    // === КУЛДАУНЫ И ОТРЯДЫ ===
    public int scoutReinforcementCooldown = 120;
    public int squadGroupSize = 3;
    public double braveGarrisonChance = 25.0;

    // === КОНТРАБАНДИСТЫ И ПОЛИЦИЯ (только в радиусе города, как гарнизон) ===
    // Шанс, что новый житель в городе окажется контрабандистом (продаёт чёрный рынок ночью).
    public double smugglerSpawnChance = 12.0;
    // Шанс, что новый житель в городе окажется полицейским (ловит контрабандистов).
    public double policeSpawnChance = 15.0;
    // Шанс для гарнизона/скаутов/полиции остаться на ночное дежурство вместо "сна" —
    // перебрасывается заново каждую игровую ночь для каждого такого жителя.
    public double nightDutyChance = 50.0;
    // Прочность УПМ (из нашей кастомной системы, максимум 500), которое продают контрабандисты.
    public int smugglerOdmGearDurability = 100;
    // Газ в том же УПМ (максимум ODMGearItem.MAX_GAS) — тоже продают початым, не полным.
    public int smugglerOdmGearGas = 100;

    // === УСЛОЖНЁННАЯ ОХОТА ТИТАНОВ ===
    // Захват зависших рядом летунов, доворот, предугадывание траектории,
    // бег и прыжки красноглазых. Не касается шифтеров и огра.
    public boolean enhancedTitanHunting = true;

    // === КРОВЬ ТИТАНА НА СНАРЯЖЕНИИ ===
    // Сколько убийств титанов клинком нужно для 2-й и 3-й стадии окровавленности.
    public int bloodStage2Kills = 3;
    public int bloodStage3Kills = 10;
    // Через сколько секунд без убийств стадия крови испаряется на одну ступень.
    public int bloodDrySeconds = 60;

    // Скорость бега полицейских/контрабандистов — значение атрибута MOVEMENT_SPEED
    // (не множитель), выставляется напрямую на сущность. 0.35 примерно соответствует
    // темпу бегущего игрока для обычного жителя.
    public double policeChaseSpeed = 0.35;
    public double smugglerFleeSpeed = 0.35;

    // === ШАНСЫ ПРЕВРАЩЕНИЯ В БЕРСЕРКА (%) ===
    public double attackChance = 100.0;
    public double armoredChance = 100.0;
    public double femaleChance = 100.0;
    public double beastChance = 100.0;
    public double colossalChance = 100.0;
    public double warhammerChance = 100.0;

    // === ЗДОРОВЬЕ БЕРСЕРКОВ (HP) ===
    public double attackHealth = 600.0;
    public double armoredHealth = 800.0;
    public double femaleHealth = 500.0;
    public double beastHealth = 700.0;
    public double colossalHealth = 2000.0;
    public double warhammerHealth = 600.0;

    private static MaxonConfig INSTANCE = new MaxonConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void registerGameRules() {
        DO_SOLDIER_SPAWN = GameRuleRegistry.register(
                "doSoldierSpawn",
                GameRules.Category.SPAWNING,
                GameRuleFactory.createBooleanRule(true)
        );
        DO_ODM_CRACKED = GameRuleRegistry.register(
                "doODMCracked",
                GameRules.Category.PLAYER,
                GameRuleFactory.createBooleanRule(true)
        );
        // См. com.maxonaddon.world.BanditOutpostGenerator — генерация редких башен-аванпостов
        // разбойников (без самих разбойников, только лут) на Парадизе.
        DO_BANDIT_OUTPOSTS = GameRuleRegistry.register(
                "doBanditOutposts",
                GameRules.Category.SPAWNING,
                GameRuleFactory.createBooleanRule(true)
        );

        // Сторожевые вышки с кадетами-стрелками (PillagerOutpostGenerator).
        DO_CADET_OUTPOSTS = GameRuleRegistry.register(
                "doCadetOutposts",
                GameRules.Category.SPAWNING,
                GameRuleFactory.createBooleanRule(true)
        );
        ALLOW_FLARE_REINFORCEMENTS = GameRuleRegistry.register(
                "allowFlareReinforcements",
                GameRules.Category.PLAYER,
                GameRuleFactory.createBooleanRule(true)
        );
        // changedCallback нужен, чтобы новое значение НЕМЕДЛЕННО разошлось всем уже
        // подключённым игрокам через собственный пакет мода, а не полагаться на то,
        // что клиент сам когда-нибудь перечитает client.level.getGameRules() —
        // это было причиной бага "страх не выключается у части игроков / у новых
        // игроков после захода, хотя правило выключено".
        ALLOW_FEAR_SYSTEM = GameRuleRegistry.register(
                "allowFearSystem",
                GameRules.Category.PLAYER,
                GameRuleFactory.createBooleanRule(true, (server, rule) -> MaxonDaotAddon.broadcastModFlags(server))
        );
        // Регистрация нового геймрула травм (по умолчанию true)
        ALLOW_INJURES = GameRuleRegistry.register(
                "allowInjures",
                GameRules.Category.PLAYER,
                GameRuleFactory.createBooleanRule(true)
        );
    }

    public static MaxonConfig get() {
        return INSTANCE;
    }

    /**
     * Включена ли система квестов и репутации (см. reputationAllow). Отдельный хелпер, чтобы
     * во всех точках-гейтах читалось одинаково и было легко найти их поиском по коду.
     * Это СЕРВЕРНЫЙ конфиг — на клиенте актуальное значение живёт в ClientModState
     * (присылается через SyncModFlagsPayload).
     */
    public static boolean isReputationEnabled() {
        return INSTANCE.reputationAllow;
    }

    /** Конфиг, прочитанный с диска этой копией игры. Клиент возвращается к нему, выйдя с сервера. */
    private static MaxonConfig LOCAL = new MaxonConfig();

    /** true, пока действует конфиг, присланный сервером. */
    private static volatile boolean fromServer = false;

    /**
     * Действует ли сейчас серверный конфиг. Полезно там, где клиенту нужно понимать,
     * что настройка не его собственная.
     */
    public static boolean isFromServer() {
        return fromServer;
    }

    /** Весь конфиг одной строкой — для отправки клиенту (см. ConfigSyncPayload). */
    public static String toJson() {
        return GSON.toJson(INSTANCE);
    }

    /**
     * Клиент получил конфиг сервера и полностью подменяет им свой.
     * <p>
     * Именно "подменяет", а не "сливает": на сервере с модом клиентский maxon_addon.json не
     * должен значить ничего. Иначе игрок с enableAdvancedHookMechanics=true продолжал бы
     * тянуть свою механику крюка на сервере, где она выключена (см. ODMTickHandlerMixin,
     * HookLineRendererMixin) — а это уже рассинхрон, а не вопрос вкуса.
     */
    public static void applyFromServer(String json) {
        try {
            MaxonConfig received = GSON.fromJson(json, MaxonConfig.class);
            if (received == null) return;
            INSTANCE = received;
            fromServer = true;
            System.out.println("[MaxonAddon] Применён конфиг сервера — локальный maxon_addon.json на этом сервере не действует.");
        } catch (Exception e) {
            // Не сумели разобрать — остаёмся на своём конфиге, это лучше, чем пустой.
            System.err.println("Не удалось применить конфиг сервера: " + e.getMessage());
        }
    }

    /** Отключились от сервера — снова живём по своему конфигу. */
    public static void restoreLocal() {
        if (!fromServer) return;
        INSTANCE = LOCAL;
        fromServer = false;
    }

    public static void load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("maxon_addon.json");
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                MaxonConfig loaded = GSON.fromJson(reader, MaxonConfig.class);
                // Пустой или битый файл даёт null — в этом случае остаёмся на значениях
                // по умолчанию, иначе весь конфиг превратился бы в NPE при первом чтении.
                if (loaded != null) INSTANCE = loaded;
            } catch (Exception e) {
                System.err.println("Ошибка чтения maxon_addon.json: " + e.getMessage());
            }
        } else {
            save();
        }
        LOCAL = INSTANCE;
    }

    public static void save() {
        // Пока действует конфиг сервера, записывать его в свой файл нельзя — затёрли бы
        // собственные настройки игрока чужими.
        if (fromServer) return;

        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("maxon_addon.json");
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(INSTANCE, writer);
        } catch (Exception e) {
            System.err.println("Ошибка записи maxon_addon.json: " + e.getMessage());
        }
    }
}