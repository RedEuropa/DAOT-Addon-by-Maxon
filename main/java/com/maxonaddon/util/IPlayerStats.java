package com.maxonaddon.util;

import com.maxonaddon.skills.ModSkill;
import java.util.Set;

public interface IPlayerStats {
    int getTitansKilled();
    void addTitanKill();
    void setTitansKilled(int amount);

    long getTicksPlayedTotal();
    void incrementTicksPlayed();
    void setTicksPlayedTotal(long amount);

    long getTicksSinceDeath();
    void resetTicksSinceDeath();
    void setTicksSinceDeath(long amount);

    int getTraumaTimer(TraumaType type);
    void setTraumaTimer(TraumaType type, int ticks);
    boolean hasTrauma(TraumaType type);

    String getActiveQuestId();
    String getActiveQuestTarget();
    void setActiveQuest(String questId, String target);
    int getQuestTitansKilled();
    void setQuestTitansKilled(int amount);

    /** К какой стене привязан активный квест (имя enum Wall или "") — награда репутацией при сдаче. */
    String getActiveQuestWall();
    void setActiveQuestWall(String wallName);

    // ===============================================
    // КВЕСТ "ДОСТАВИТЬ ПРИПАСЫ" — отдельное состояние поверх общего активного квеста
    // (questId == "delivery_quest"). См. com.maxonaddon.DeliveryQuestHandler.
    //
    // Координаты получателя, а НЕ UUID сущности: точки командиров (QuestCommanderSpawner.
    // ALL_POINTS) фиксированы и известны заранее, entity для выбора цели не нужен вообще —
    // раньше выбор шёл через level.getEntities() по всей карте, что зависело от того,
    // загружен ли чанк нужного командира (а он почти никогда не загружен, если игрок туда
    // не ходил) — отсюда "нет другого поста" даже когда командиры реально существуют.
    // ===============================================
    int getDeliveryTargetX();
    int getDeliveryTargetY();
    int getDeliveryTargetZ();
    void setDeliveryTarget(int x, int y, int z);
    /** Стена командира-получателя (имя enum Wall). */
    String getDeliveryTargetWall();
    void setDeliveryTargetWall(String wallName);
    /** Абсолютный игровой тик дедлайна (level.getGameTime() + 10 минут при принятии). */
    long getDeliveryDeadlineTick();
    void setDeliveryDeadlineTick(long tick);

    // ===============================================
    // КВЕСТ "СПАСЕНИЕ ЗАСТРЯВШИХ" — отдельное состояние поверх общего активного квеста
    // (questId == "rescue_quest"). См. com.maxonaddon.RescueQuestHandler.
    //
    // Точка (здание+кадеты+титаны) создаётся СРАЗУ при принятии квеста, а не ищется —
    // координаты просто места, где всё это заспавнено.
    // ===============================================
    int getRescueTargetX();
    int getRescueTargetY();
    int getRescueTargetZ();
    void setRescueTarget(int x, int y, int z);
    /** Игрок добрался до точки (в радиусе прибытия) до истечения таймера в 5 минут. */
    boolean isRescueArrived();
    void setRescueArrived(boolean arrived);
    /** Сколько титанов нужно убить (случайно 5..10 при принятии) — сравнивается с общим getQuestTitansKilled(). */
    int getRescueTitansRequired();
    void setRescueTitansRequired(int amount);
    /** Сколько кадетов было заспавнено (случайно 3..5 при принятии). */
    int getRescueCadetsTotal();
    void setRescueCadetsTotal(int amount);
    /** Сколько из них ещё живы (уменьшается при гибели, финальное значение — на момент зачистки титанов). */
    int getRescueCadetsAlive();
    void setRescueCadetsAlive(int amount);
    /** Абсолютный игровой тик дедлайна ДОБРАТЬСЯ до точки (level.getGameTime() + 5 минут при принятии). */
    long getRescueDeadlineTick();
    void setRescueDeadlineTick(long tick);

    // ===============================================
    // КВЕСТ "РАНЕНЫЙ ВЫЖИВШИЙ" — отдельное состояние поверх общего активного квеста
    // (questId == "survivor_quest"). См. com.maxonaddon.SurvivorQuestHandler.
    //
    // Тут цель — КОНКРЕТНАЯ сущность (её несут на спине), поэтому храним именно UUID, а не
    // координаты: житель всё время либо на игроке, либо там, где его положили в паре шагов —
    // то есть в загруженном чанке, и проблемы "сущность в выгруженном чанке не находится",
    // из-за которой квест доставки переехал на координаты, тут не возникает. Координаты
    // дополнительно храним как ПОСЛЕДНЮЮ ИЗВЕСТНУЮ позицию — только для маркера-локатора,
    // чтобы стрелка не пропадала, если игрок отошёл и чанк с жителем выгрузился.
    // ===============================================
    String getSurvivorUuid();
    void setSurvivorUuid(String uuid);
    int getSurvivorX();
    int getSurvivorY();
    int getSurvivorZ();
    void setSurvivorPos(int x, int y, int z);

    // ===============================================
    // РЕПУТАЦИЯ У ТРЁХ СТЕН (Мария/Роза/Сина) — см. com.maxonaddon.util.Wall. 0..100, старт 50.
    // ===============================================
    int getReputation(Wall wall);
    void setReputation(Wall wall, int amount);
    void addReputation(Wall wall, int delta);

    // ===============================================
    // ВЫПОЛНЕННЫЕ КВЕСТЫ — id квестов, уже сданных этим игроком, чтобы у одного и того
    // же НПС нельзя было второй раз взять уже закрытое задание. Отдельно от активного
    // квеста и от questsCompleted (счётчика "сколько всего сдано" для статистики).
    // ===============================================
    Set<String> getCompletedQuests();
    void setCompletedQuests(Set<String> questIds);
    boolean hasCompletedQuest(String questId);
    void markQuestCompleted(String questId);

    /** Сколько квестов игрок сдал за всю игру — для экрана статистики. */
    int getQuestsCompleted();
    void addQuestCompleted();
    void setQuestsCompleted(int amount);

    // ===============================================
    // ТЕСТОВОЕ ЗАДАНИЕ — теперь ПОВТОРЯЕМОЕ и растущее: каждое выполнение поднимает планку
    // (см. TEST_QUEST_TITANS_STEP/TEST_QUEST_WHEAT_STEP ниже), поэтому вместо булева "сдано
    // или нет" храним счётчик. Он же гейтит остальные задания у командиров: пока тестовое не
    // сдано ни разу, доставка и спасение недоступны (см. QuestScreen/MaxonDaotAddon).
    // ===============================================
    int getTestQuestCompletions();
    void setTestQuestCompletions(int amount);

    /**
     * Битовая маска командиров, у которых вводное задание уже сдано: бит N = точка N в
     * QuestCommanderSpawner.ALL_POINTS (их ровно 12, так что int с запасом). У каждого
     * командира задание берётся ровно один раз, но у СЛЕДУЮЩЕГО — снова, уже с поднятой
     * планкой; маска и счётчик выполнений отвечают за разные вещи и живут отдельно.
     * Привязка к индексу ФИКСИРОВАННОЙ точки, а не к UUID сущности: командира могут
     * перезаспавнить (см. QuestCommanderSpawner), и по UUID отметка бы потерялась.
     */
    int getTestQuestCommanderMask();
    void setTestQuestCommanderMask(int mask);

    /** Индекс точки командира, У КОГО взято текущее вводное задание (-1, если не взято). */
    int getTestQuestCommanderIndex();
    void setTestQuestCommanderIndex(int index);

    default boolean hasCompletedTestQuestAt(int commanderIndex) {
        if (commanderIndex < 0 || commanderIndex >= 32) return false;
        return (getTestQuestCommanderMask() & (1 << commanderIndex)) != 0;
    }

    default void markTestQuestCompletedAt(int commanderIndex) {
        if (commanderIndex < 0 || commanderIndex >= 32) return;
        setTestQuestCommanderMask(getTestQuestCommanderMask() | (1 << commanderIndex));
    }

    /** Базовые требования тестового задания и шаг роста за каждое выполнение. */
    int TEST_QUEST_BASE_TITANS = 3;
    int TEST_QUEST_BASE_WHEAT = 2;
    int TEST_QUEST_TITANS_STEP = 2;
    int TEST_QUEST_WHEAT_STEP = 2;

    /** Сколько титанов нужно убить при N уже выполненных тестовых заданиях. */
    static int testQuestTitansRequired(int completions) {
        return TEST_QUEST_BASE_TITANS + TEST_QUEST_TITANS_STEP * Math.max(0, completions);
    }

    /** Сколько пшеницы нужно принести при N уже выполненных тестовых заданиях. */
    static int testQuestWheatRequired(int completions) {
        return TEST_QUEST_BASE_WHEAT + TEST_QUEST_WHEAT_STEP * Math.max(0, completions);
    }

    int getLevel();
    void setLevel(int level);

    int getExp();
    void setExp(int exp);

    int getSkillPoints();
    void setSkillPoints(int skillPoints);
    int getBloodlinePoints();
    void setBloodlinePoints(int points);
    int getTitanPoints();
    void setTitanPoints(int points);

    // ===============================================
    // КРОВЬ ТИТАНА НА СНАРЯЖЕНИИ (0..3)
    // Копится за убийства титанов КЛИНКОМ, со временем испаряется.
    // ===============================================
    int getBloodStage();
    void setBloodStage(int stage);
    /** Убийства титанов клинком, накопленные к текущей стадии (для порогов 3 и 10). */
    int getBladeTitanKills();
    void setBladeTitanKills(int kills);
    /** Тики до следующего испарения одной стадии крови. */
    int getBloodDryTicks();
    void setBloodDryTicks(int ticks);

    // ===============================================
    // КУЛДАУНЫ НОВЫХ СКИЛОВ ШИФТЕРА (в тиках)
    // ===============================================
    /** "Титаническая воля": экстренный тайный ре-шифт после выбивания из титана. */
    int getTitanicWillCooldown();
    void setTitanicWillCooldown(int ticks);
    /** "Воля к жизни": авто-исцеление на пол-сердечка ценой сломанных конечностей. */
    int getWillToLiveCooldown();
    void setWillToLiveCooldown(int ticks);

    // ===============================================
    // ПЕРКИ (5 слотов, как ячейки брони)
    // Массив всегда длины PerkManager.SLOTS; пустая строка или null — слот свободен.
    // ===============================================
    String[] getEquippedPerks();
    void setEquippedPerk(int slot, String perkId);
    boolean hasPerk(String perkId);

    // ===============================================
    // РАЗБЛОКИРОВАННЫЕ ПЕРКИ — получаются за достижения (см. PerkUnlockHandler),
    // не покупаются за очки. Надеть в слот можно только то, что уже разблокировано —
    // это отдельный список от getEquippedPerks().
    // ===============================================
    Set<String> getUnlockedPerks();
    void setUnlockedPerks(Set<String> perks);
    boolean hasUnlockedPerk(String perkId);
    void unlockPerk(String perkId);

    // ===============================================
    // ЛАВКА ЛОШАДЕЙ У ПЕРЕВОЗЧИКА
    // Сами статы лошадей не храним — только seed, из которого они выводятся
    // (см. HorseOffer). Ноль означает "предложение ещё не собрано".
    // ===============================================
    long getHorseOfferSeed();
    void setHorseOfferSeed(long seed);

    /** Игровое время, раньше которого нельзя снова взять зачистку у сторожевой вышки. */
    long getOutpostQuestReadyAt();
    void setOutpostQuestReadyAt(long gameTime);

    /** Сколько сделок игрок закрыл — на сотой выдаётся перк "Торговец". */
    int getTradesCompleted();
    void setTradesCompleted(int amount);
    void addTradeCompleted();

    /** UUID купленной лошади — по нему её находит свист. Пустая строка, если лошади нет. */
    String getOwnedHorseUuid();
    void setOwnedHorseUuid(String uuid);

    // ===============================================
    // НОВАЯ СИСТЕМА (Хранит ID всех изученных скилов)
    // ===============================================
    Set<String> getUnlockedSkills();
    void setUnlockedSkills(Set<String> skills);
    boolean hasSkill(String skillId);
    void unlockSkill(String skillId);

    // ===============================================
    // АВТОМАТИЧЕСКАЯ СОВМЕСТИМОСТЬ СО СТАРЫМ КОДОМ
    // ===============================================
    default boolean hasOdmMastery() { return hasSkill("odm_mastery"); }
    default void setOdmMastery(boolean value) { if(value) unlockSkill("odm_mastery"); else getUnlockedSkills().remove("odm_mastery"); }

    default boolean hasDashMastery() { return hasSkill("dash_mastery"); }
    default void setDashMastery(boolean value) { if(value) unlockSkill("dash_mastery"); else getUnlockedSkills().remove("dash_mastery"); }

    default boolean hasConnectorMastery() { return hasSkill("connector_mastery"); }
    default void setConnectorMastery(boolean value) { if(value) unlockSkill("connector_mastery"); else getUnlockedSkills().remove("connector_mastery"); }

    default boolean hasUngrabingMastery() { return hasSkill("ungrabing_mastery"); }

    /** Можно ли пользоваться тросами ОДМ, сидя верхом. */
    default boolean hasRiderHooks() { return hasSkill("rider_hooks"); }

    /** Можно ли уходить в рывок прямо с седла. */
    default boolean hasRiderDismount() { return hasSkill("rider_dismount"); }
    default void setUngrabingMastery(boolean value) { if(value) unlockSkill("ungrabing_mastery"); else getUnlockedSkills().remove("ungrabing_mastery"); }

    default boolean hasBloodlineAwakening() { return hasSkill("bloodline_awakening"); }
    default void setBloodlineAwakening(boolean value) { if(value) unlockSkill("bloodline_awakening"); else getUnlockedSkills().remove("bloodline_awakening"); }

    default boolean hasShifterRegen() { return hasSkill("shifter_regen"); }
    default void setShifterRegen(boolean value) { if(value) unlockSkill("shifter_regen"); else getUnlockedSkills().remove("shifter_regen"); }
}