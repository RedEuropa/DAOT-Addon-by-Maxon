package com.maxonaddon.mixin;

import com.maxonaddon.MaxonConfig;
import com.maxonaddon.skills.ModSkill;
import com.maxonaddon.util.IPlayerStats;
import com.maxonaddon.util.TraumaType;
import com.maxonaddon.util.Wall;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Mixin(Player.class)
public abstract class PlayerStatsMixin implements IPlayerStats {

    // Травмы
    @Unique
    private final Map<TraumaType, Integer> maxon$traumaTimers = new EnumMap<>(TraumaType.class);

    @Override
    public int getTraumaTimer(TraumaType type) {
        return this.maxon$traumaTimers.getOrDefault(type, 0);
    }

    @Override
    public void setTraumaTimer(TraumaType type, int ticks) {
        this.maxon$traumaTimers.put(type, Math.max(0, ticks));
    }

    @Override
    public boolean hasTrauma(TraumaType type) {
        return this.getTraumaTimer(type) > 0;
    }

    // Базовые статы
    @Unique private int daotTitansKilled = 0;
    @Unique private long daotTicksPlayed = 0;
    @Unique private long daotTicksSinceDeath = 0;
    @Unique private int daotLevel = 1;
    @Unique private int daotExp = 0;
    @Unique private int daotSkillPoints = 0;
    @Unique private int daotBloodlinePoints = 0;
    @Unique private int daotTitanPoints = 0;

    // Кровь титана на снаряжении
    @Unique private int maxonBloodStage = 0;
    @Unique private int maxonBladeTitanKills = 0;
    @Unique private int maxonBloodDryTicks = 0;
    @Unique private int maxonTitanicWillCooldown = 0;
    @Unique private int maxonWillToLiveCooldown = 0;

    // === ПЕРКИ: 5 слотов, пустая строка = слот свободен ===
    @Unique
    private String[] maxonEquippedPerks = new String[com.maxonaddon.perk.PerkManager.SLOTS];

    // === ПЕРКИ: какие вообще разблокированы (получены за достижения) ===
    @Unique
    private Set<String> maxonUnlockedPerks = new HashSet<>();

    @Override
    public Set<String> getUnlockedPerks() {
        return this.maxonUnlockedPerks;
    }

    @Override
    public void setUnlockedPerks(Set<String> perks) {
        this.maxonUnlockedPerks = new HashSet<>(perks);
    }

    @Override
    public boolean hasUnlockedPerk(String perkId) {
        return this.maxonUnlockedPerks.contains(perkId);
    }

    @Override
    public void unlockPerk(String perkId) {
        this.maxonUnlockedPerks.add(perkId);
    }

    // === АКТИВНЫЙ КВЕСТ: сервер — источник истины, клиент только зеркалит через SyncQuestPayload ===
    @Unique private String maxonActiveQuestId = "";
    @Unique private String maxonQuestTarget = "";

    @Override
    public String getActiveQuestId() {
        return this.maxonActiveQuestId;
    }

    @Override
    public String getActiveQuestTarget() {
        return this.maxonQuestTarget;
    }

    @Override
    public void setActiveQuest(String questId, String target) {
        this.maxonActiveQuestId = questId == null ? "" : questId;
        this.maxonQuestTarget = target == null ? "" : target;
    }

    // Счётчик убитых титанов ПО ТЕКУЩЕМУ КВЕСТУ — отдельно от общего daotTitansKilled
    // (тот считает вообще все убийства за всю игру и никогда не сбрасывается).
    @Unique private int maxonQuestTitansKilled = 0;

    // Стена, к которой привязан активный квест (имя Wall enum, "" если не определено) —
    // выставляется при принятии (см. QuestActionPayload action==0), награда репутацией при
    // успешной сдаче (action==2).
    @Unique private String maxonActiveQuestWall = "";

    @Override
    public String getActiveQuestWall() {
        return this.maxonActiveQuestWall;
    }

    @Override
    public void setActiveQuestWall(String wallName) {
        this.maxonActiveQuestWall = wallName == null ? "" : wallName;
    }

    // === КВЕСТ "ДОСТАВИТЬ ПРИПАСЫ" — координаты получателя, не UUID (см. IPlayerStats) ===
    @Unique private int maxonDeliveryTargetX = 0;
    @Unique private int maxonDeliveryTargetY = 0;
    @Unique private int maxonDeliveryTargetZ = 0;
    @Unique private String maxonDeliveryTargetWall = "";
    @Unique private long maxonDeliveryDeadlineTick = 0L;

    @Override
    public int getDeliveryTargetX() {
        return this.maxonDeliveryTargetX;
    }

    @Override
    public int getDeliveryTargetY() {
        return this.maxonDeliveryTargetY;
    }

    @Override
    public int getDeliveryTargetZ() {
        return this.maxonDeliveryTargetZ;
    }

    @Override
    public void setDeliveryTarget(int x, int y, int z) {
        this.maxonDeliveryTargetX = x;
        this.maxonDeliveryTargetY = y;
        this.maxonDeliveryTargetZ = z;
    }

    @Override
    public String getDeliveryTargetWall() {
        return this.maxonDeliveryTargetWall;
    }

    @Override
    public void setDeliveryTargetWall(String wallName) {
        this.maxonDeliveryTargetWall = wallName == null ? "" : wallName;
    }

    @Override
    public long getDeliveryDeadlineTick() {
        return this.maxonDeliveryDeadlineTick;
    }

    @Override
    public void setDeliveryDeadlineTick(long tick) {
        this.maxonDeliveryDeadlineTick = tick;
    }

    // === КВЕСТ "СПАСЕНИЕ ЗАСТРЯВШИХ" ===
    @Unique private int maxonRescueTargetX = 0;
    @Unique private int maxonRescueTargetY = 0;
    @Unique private int maxonRescueTargetZ = 0;
    @Unique private boolean maxonRescueArrived = false;
    @Unique private int maxonRescueTitansRequired = 0;
    @Unique private int maxonRescueCadetsTotal = 0;
    @Unique private int maxonRescueCadetsAlive = 0;
    @Unique private long maxonRescueDeadlineTick = 0L;

    @Override
    public int getRescueTargetX() {
        return this.maxonRescueTargetX;
    }

    @Override
    public int getRescueTargetY() {
        return this.maxonRescueTargetY;
    }

    @Override
    public int getRescueTargetZ() {
        return this.maxonRescueTargetZ;
    }

    @Override
    public void setRescueTarget(int x, int y, int z) {
        this.maxonRescueTargetX = x;
        this.maxonRescueTargetY = y;
        this.maxonRescueTargetZ = z;
    }

    @Override
    public boolean isRescueArrived() {
        return this.maxonRescueArrived;
    }

    @Override
    public void setRescueArrived(boolean arrived) {
        this.maxonRescueArrived = arrived;
    }

    @Override
    public int getRescueTitansRequired() {
        return this.maxonRescueTitansRequired;
    }

    @Override
    public void setRescueTitansRequired(int amount) {
        this.maxonRescueTitansRequired = amount;
    }

    @Override
    public int getRescueCadetsTotal() {
        return this.maxonRescueCadetsTotal;
    }

    @Override
    public void setRescueCadetsTotal(int amount) {
        this.maxonRescueCadetsTotal = amount;
    }

    @Override
    public int getRescueCadetsAlive() {
        return this.maxonRescueCadetsAlive;
    }

    @Override
    public void setRescueCadetsAlive(int amount) {
        this.maxonRescueCadetsAlive = amount;
    }

    @Override
    public long getRescueDeadlineTick() {
        return this.maxonRescueDeadlineTick;
    }

    @Override
    public void setRescueDeadlineTick(long tick) {
        this.maxonRescueDeadlineTick = tick;
    }

    // === КВЕСТ "РАНЕНЫЙ ВЫЖИВШИЙ" ===
    @Unique private String maxonSurvivorUuid = "";
    @Unique private int maxonSurvivorX = 0;
    @Unique private int maxonSurvivorY = 0;
    @Unique private int maxonSurvivorZ = 0;

    @Override
    public String getSurvivorUuid() {
        return this.maxonSurvivorUuid;
    }

    @Override
    public void setSurvivorUuid(String uuid) {
        this.maxonSurvivorUuid = uuid == null ? "" : uuid;
    }

    @Override
    public int getSurvivorX() {
        return this.maxonSurvivorX;
    }

    @Override
    public int getSurvivorY() {
        return this.maxonSurvivorY;
    }

    @Override
    public int getSurvivorZ() {
        return this.maxonSurvivorZ;
    }

    @Override
    public void setSurvivorPos(int x, int y, int z) {
        this.maxonSurvivorX = x;
        this.maxonSurvivorY = y;
        this.maxonSurvivorZ = z;
    }

    // === РЕПУТАЦИЯ У ТРЁХ СТЕН — старт 50 (нейтрально), клампится 0..100 ===
    @Unique private int maxonRepMaria = com.maxonaddon.util.ReputationHelper.DEFAULT;
    @Unique private int maxonRepRose = com.maxonaddon.util.ReputationHelper.DEFAULT;
    @Unique private int maxonRepSina = com.maxonaddon.util.ReputationHelper.DEFAULT;

    @Override
    public int getReputation(Wall wall) {
        return switch (wall) {
            case MARIA -> this.maxonRepMaria;
            case ROSE -> this.maxonRepRose;
            case SINA -> this.maxonRepSina;
        };
    }

    @Override
    public void setReputation(Wall wall, int amount) {
        int clamped = com.maxonaddon.util.ReputationHelper.clamp(amount);
        switch (wall) {
            case MARIA -> this.maxonRepMaria = clamped;
            case ROSE -> this.maxonRepRose = clamped;
            case SINA -> this.maxonRepSina = clamped;
        }
    }

    @Override
    public void addReputation(Wall wall, int delta) {
        // Единая точка гейта: система репутации выключена конфигом — ни награды, ни штрафы
        // не начисляются. Через этот метод идут ВСЕ изменения репутации в моде (квесты,
        // разбой, обыск сундуков, спасение жителей), поэтому одной проверки тут достаточно;
        // setReputation намеренно НЕ гейтим — им пользуется админская команда /reputation.
        if (!MaxonConfig.isReputationEnabled()) return;
        setReputation(wall, getReputation(wall) + delta);
    }

    @Override
    public int getQuestTitansKilled() {
        return this.maxonQuestTitansKilled;
    }

    @Override
    public void setQuestTitansKilled(int amount) {
        this.maxonQuestTitansKilled = amount;
    }

    // === ВЫПОЛНЕННЫЕ КВЕСТЫ: id уже сданных заданий — чтобы нельзя было взять то же
    // задание у того же НПС повторно. Отдельно от maxonQuestsCompleted (счётчик для статистики). ===
    @Unique private Set<String> maxonCompletedQuests = new HashSet<>();

    @Override
    public Set<String> getCompletedQuests() {
        return this.maxonCompletedQuests;
    }

    @Override
    public void setCompletedQuests(Set<String> questIds) {
        this.maxonCompletedQuests = new HashSet<>(questIds);
    }

    @Override
    public boolean hasCompletedQuest(String questId) {
        return this.maxonCompletedQuests.contains(questId);
    }

    @Override
    public void markQuestCompleted(String questId) {
        this.maxonCompletedQuests.add(questId);
    }

    // === СДЕЛКИ (для перка "Торговец") ===
    @Unique private int maxonTradesCompleted = 0;

    @Override
    public int getTradesCompleted() {
        return this.maxonTradesCompleted;
    }

    @Override
    public void setTradesCompleted(int amount) {
        this.maxonTradesCompleted = Math.max(0, amount);
    }

    @Override
    public void addTradeCompleted() {
        this.maxonTradesCompleted++;
    }

    // === ЛАВКА ЛОШАДЕЙ ===
    @Unique private long maxonHorseOfferSeed = 0L;
    @Unique private long maxonOutpostQuestReadyAt = 0L;
    @Unique private String maxonOwnedHorseUuid = "";

    @Override
    public long getHorseOfferSeed() {
        return this.maxonHorseOfferSeed;
    }

    @Override
    public long getOutpostQuestReadyAt() {
        return this.maxonOutpostQuestReadyAt;
    }

    @Override
    public void setOutpostQuestReadyAt(long gameTime) {
        this.maxonOutpostQuestReadyAt = gameTime;
    }

    @Override
    public void setHorseOfferSeed(long seed) {
        this.maxonHorseOfferSeed = seed;
    }

    @Override
    public String getOwnedHorseUuid() {
        return this.maxonOwnedHorseUuid == null ? "" : this.maxonOwnedHorseUuid;
    }

    @Override
    public void setOwnedHorseUuid(String uuid) {
        this.maxonOwnedHorseUuid = uuid == null ? "" : uuid;
    }

    // Сколько квестов игрок сдал за всю игру (для экрана статистики).
    @Unique private int maxonQuestsCompleted = 0;

    @Override
    public int getQuestsCompleted() {
        return this.maxonQuestsCompleted;
    }

    @Override
    public void addQuestCompleted() {
        this.maxonQuestsCompleted++;
    }

    @Override
    public void setQuestsCompleted(int amount) {
        this.maxonQuestsCompleted = amount;
    }

    // Сколько раз сдано ПОВТОРЯЕМОЕ тестовое задание — от него растут его же требования
    // и открываются остальные задания у командиров (см. IPlayerStats).
    @Unique private int maxonTestQuestCompletions = 0;

    @Override
    public int getTestQuestCompletions() {
        return this.maxonTestQuestCompletions;
    }

    @Override
    public void setTestQuestCompletions(int amount) {
        this.maxonTestQuestCompletions = Math.max(0, amount);
    }

    // У каких командиров вводное уже сдано (битовая маска по индексам точек) и у кого взято
    // текущее — см. IPlayerStats.
    @Unique private int maxonTestQuestCommanderMask = 0;
    @Unique private int maxonTestQuestCommanderIndex = -1;

    @Override
    public int getTestQuestCommanderMask() {
        return this.maxonTestQuestCommanderMask;
    }

    @Override
    public void setTestQuestCommanderMask(int mask) {
        this.maxonTestQuestCommanderMask = mask;
    }

    @Override
    public int getTestQuestCommanderIndex() {
        return this.maxonTestQuestCommanderIndex;
    }

    @Override
    public void setTestQuestCommanderIndex(int index) {
        this.maxonTestQuestCommanderIndex = index;
    }

    // === НОВАЯ ЕДИНАЯ СИСТЕМА НАВЫКОВ ===
    @Unique
    private Set<String> unlockedSkills = new HashSet<>();

    @Override
    public Set<String> getUnlockedSkills() {
        return this.unlockedSkills;
    }

    @Override
    public void setUnlockedSkills(Set<String> skills) {
        this.unlockedSkills = new HashSet<>(skills);
    }

    @Override
    public boolean hasSkill(String skillId) {
        if (this.unlockedSkills.contains(skillId)) {
            return true;
        }
        // Система скилов отключена в конфиге сервера — все не-пассивные скилы
        // (способности) считаются выданными игроку без реальной покупки.
        // Пассивные скилы на повышение статов (agility, iron_bones, fast_regen,
        // ackerman_endurance, а также "вторые" тиры control/sacrifice) остаются платными.
        if (!MaxonConfig.get().skillsSystemEnabled) {
            ModSkill skill = ModSkill.getById(skillId);
            if (skill != null && skill.isAutoGrantedWhenSkillsDisabled()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void unlockSkill(String skillId) {
        this.unlockedSkills.add(skillId);
    }
    // ===================================

    @Override public int getBloodlinePoints() { return this.daotBloodlinePoints; }
    @Override public void setBloodlinePoints(int points) { this.daotBloodlinePoints = points; }
    @Override public int getTitanPoints() { return this.daotTitanPoints; }
    @Override public void setTitanPoints(int points) { this.daotTitanPoints = points; }
    @Override public void setTitansKilled(int amount) { this.daotTitansKilled = amount; }
    @Override public void setTicksPlayedTotal(long amount) { this.daotTicksPlayed = amount; }
    @Override public void setTicksSinceDeath(long amount) { this.daotTicksSinceDeath = amount; }
    @Override public int getLevel() { return this.daotLevel; }
    @Override public void setLevel(int level) { this.daotLevel = level; }
    @Override public int getExp() { return this.daotExp; }
    @Override public void setExp(int exp) { this.daotExp = exp; }
    @Override public int getSkillPoints() { return this.daotSkillPoints; }
    @Override public void setSkillPoints(int sp) { this.daotSkillPoints = sp; }

    @ModifyVariable(method = "displayClientMessage", at = @At("HEAD"), argsOnly = true)
    private Component replaceJamMessage(Component original) {
        // Перехватываем стандартное сообщение о клине (тут используется default метод из интерфейса)
        if (!this.hasOdmMastery() && original.getString().equals("ODM jammed!")) {
            return Component.translatable("gui.maxonaddon.skills.odm_required").withStyle(ChatFormatting.RED);
        }
        return original;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void saveDaotStats(CompoundTag tag, CallbackInfo ci) {
        // Сохраняем таймеры травм
        for (TraumaType type : TraumaType.values()) {
            tag.putInt("MaxonTrauma_" + type.name(), this.getTraumaTimer(type));
        }

        tag.putInt("DaotTitansKilled", this.daotTitansKilled);
        tag.putLong("DaotTicksPlayed", this.daotTicksPlayed);
        tag.putLong("DaotTicksSinceDeath", this.daotTicksSinceDeath);
        tag.putInt("DaotLevel", this.daotLevel);
        tag.putInt("DaotExp", this.daotExp);
        tag.putInt("DaotSkillPoints", this.daotSkillPoints);
        tag.putInt("DaotBloodlinePoints", this.daotBloodlinePoints);
        tag.putInt("DaotTitanPoints", this.daotTitanPoints);

        tag.putInt("MaxonBloodStage", this.maxonBloodStage);
        tag.putInt("MaxonBladeTitanKills", this.maxonBladeTitanKills);
        tag.putInt("MaxonBloodDryTicks", this.maxonBloodDryTicks);
        tag.putInt("MaxonTitanicWillCooldown", this.maxonTitanicWillCooldown);
        tag.putInt("MaxonWillToLiveCooldown", this.maxonWillToLiveCooldown);

        // Перки: фиксированный список из PerkManager.SLOTS строк, порядок = номер слота.
        ListTag perksList = new ListTag();
        for (String perkId : this.maxonEquippedPerks) {
            perksList.add(net.minecraft.nbt.StringTag.valueOf(perkId == null ? "" : perkId));
        }
        tag.put("MaxonEquippedPerks", perksList);

        // Разблокированные перки — отдельным списком от надетых слотов.
        ListTag unlockedPerksList = new ListTag();
        for (String perkId : this.maxonUnlockedPerks) {
            unlockedPerksList.add(net.minecraft.nbt.StringTag.valueOf(perkId));
        }
        tag.put("MaxonUnlockedPerks", unlockedPerksList);

        // Активный квест
        tag.putString("MaxonActiveQuestId", this.maxonActiveQuestId);
        tag.putString("MaxonQuestTarget", this.maxonQuestTarget);
        tag.putInt("MaxonQuestTitansKilled", this.maxonQuestTitansKilled);
        tag.putString("MaxonActiveQuestWall", this.maxonActiveQuestWall);

        // Квест "Доставить припасы"
        tag.putInt("MaxonDeliveryTargetX", this.maxonDeliveryTargetX);
        tag.putInt("MaxonDeliveryTargetY", this.maxonDeliveryTargetY);
        tag.putInt("MaxonDeliveryTargetZ", this.maxonDeliveryTargetZ);
        tag.putString("MaxonDeliveryTargetWall", this.maxonDeliveryTargetWall);
        tag.putLong("MaxonDeliveryDeadlineTick", this.maxonDeliveryDeadlineTick);

        // Квест "Спасение застрявших"
        tag.putInt("MaxonRescueTargetX", this.maxonRescueTargetX);
        tag.putInt("MaxonRescueTargetY", this.maxonRescueTargetY);
        tag.putInt("MaxonRescueTargetZ", this.maxonRescueTargetZ);
        tag.putBoolean("MaxonRescueArrived", this.maxonRescueArrived);
        tag.putInt("MaxonRescueTitansRequired", this.maxonRescueTitansRequired);
        tag.putInt("MaxonRescueCadetsTotal", this.maxonRescueCadetsTotal);
        tag.putInt("MaxonRescueCadetsAlive", this.maxonRescueCadetsAlive);
        tag.putLong("MaxonRescueDeadlineTick", this.maxonRescueDeadlineTick);

        // Квест "Раненый выживший"
        tag.putString("MaxonSurvivorUuid", this.maxonSurvivorUuid);
        tag.putInt("MaxonSurvivorX", this.maxonSurvivorX);
        tag.putInt("MaxonSurvivorY", this.maxonSurvivorY);
        tag.putInt("MaxonSurvivorZ", this.maxonSurvivorZ);

        // Репутация у трёх стен
        tag.putInt("MaxonRepMaria", this.maxonRepMaria);
        tag.putInt("MaxonRepRose", this.maxonRepRose);
        tag.putInt("MaxonRepSina", this.maxonRepSina);

        // Выполненные квесты (чтобы нельзя было взять то же задание повторно) + счётчик для статистики
        ListTag completedQuestsList = new ListTag();
        for (String questId : this.maxonCompletedQuests) {
            completedQuestsList.add(net.minecraft.nbt.StringTag.valueOf(questId));
        }
        tag.put("MaxonCompletedQuests", completedQuestsList);
        tag.putInt("MaxonQuestsCompleted", this.maxonQuestsCompleted);
        tag.putInt("MaxonTradesCompleted", this.maxonTradesCompleted);
        tag.putLong("MaxonHorseOfferSeed", this.maxonHorseOfferSeed);
        tag.putLong("MaxonOutpostQuestReadyAt", this.maxonOutpostQuestReadyAt);
        tag.putString("MaxonOwnedHorseUuid", this.getOwnedHorseUuid());
        tag.putInt("MaxonTestQuestCompletions", this.maxonTestQuestCompletions);
        tag.putInt("MaxonTestQuestCommanderMask", this.maxonTestQuestCommanderMask);
        tag.putInt("MaxonTestQuestCommanderIndex", this.maxonTestQuestCommanderIndex);

        // Сохранение новой системы навыков в виде списка
        ListTag skillsList = new ListTag();
        for (String skillId : this.unlockedSkills) {
            skillsList.add(StringTag.valueOf(skillId));
        }
        tag.put("MaxonUnlockedSkills", skillsList);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readDaotStats(CompoundTag tag, CallbackInfo ci) {
        // Читаем таймеры травм
        for (TraumaType type : TraumaType.values()) {
            if (tag.contains("MaxonTrauma_" + type.name())) {
                this.setTraumaTimer(type, tag.getInt("MaxonTrauma_" + type.name()));
            }
        }

        if (tag.contains("DaotTitansKilled")) {
            this.daotTitansKilled = tag.getInt("DaotTitansKilled");
            this.daotTicksPlayed = tag.getLong("DaotTicksPlayed");
            this.daotTicksSinceDeath = tag.getLong("DaotTicksSinceDeath");
        }

        if (tag.contains("DaotBloodlinePoints")) {
            this.daotBloodlinePoints = tag.getInt("DaotBloodlinePoints");
            this.daotTitanPoints = tag.getInt("DaotTitanPoints");
        }

        if (tag.contains("MaxonEquippedPerks", net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag perksList = tag.getList("MaxonEquippedPerks", net.minecraft.nbt.Tag.TAG_STRING);
            this.maxonEquippedPerks = new String[com.maxonaddon.perk.PerkManager.SLOTS];
            for (int i = 0; i < this.maxonEquippedPerks.length && i < perksList.size(); i++) {
                String id = maxon$migratePerkId(perksList.getString(i));
                this.maxonEquippedPerks[i] = id.isEmpty() ? null : id;
            }
        }

        if (tag.contains("MaxonUnlockedPerks", net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag unlockedPerksList = tag.getList("MaxonUnlockedPerks", net.minecraft.nbt.Tag.TAG_STRING);
            this.maxonUnlockedPerks = new HashSet<>();
            for (int i = 0; i < unlockedPerksList.size(); i++) {
                this.maxonUnlockedPerks.add(maxon$migratePerkId(unlockedPerksList.getString(i)));
            }
        }

        if (tag.contains("MaxonActiveQuestId")) {
            this.maxonActiveQuestId = tag.getString("MaxonActiveQuestId");
            this.maxonQuestTarget = tag.getString("MaxonQuestTarget");
            this.maxonQuestTitansKilled = tag.getInt("MaxonQuestTitansKilled");
        }
        this.maxonActiveQuestWall = tag.getString("MaxonActiveQuestWall");

        this.maxonDeliveryTargetX = tag.getInt("MaxonDeliveryTargetX");
        this.maxonDeliveryTargetY = tag.getInt("MaxonDeliveryTargetY");
        this.maxonDeliveryTargetZ = tag.getInt("MaxonDeliveryTargetZ");
        this.maxonDeliveryTargetWall = tag.getString("MaxonDeliveryTargetWall");
        if (tag.contains("MaxonDeliveryDeadlineTick")) {
            this.maxonDeliveryDeadlineTick = tag.getLong("MaxonDeliveryDeadlineTick");
        }

        this.maxonRescueTargetX = tag.getInt("MaxonRescueTargetX");
        this.maxonRescueTargetY = tag.getInt("MaxonRescueTargetY");
        this.maxonRescueTargetZ = tag.getInt("MaxonRescueTargetZ");
        this.maxonRescueArrived = tag.getBoolean("MaxonRescueArrived");
        this.maxonRescueTitansRequired = tag.getInt("MaxonRescueTitansRequired");
        this.maxonRescueCadetsTotal = tag.getInt("MaxonRescueCadetsTotal");
        this.maxonRescueCadetsAlive = tag.getInt("MaxonRescueCadetsAlive");
        if (tag.contains("MaxonRescueDeadlineTick")) {
            this.maxonRescueDeadlineTick = tag.getLong("MaxonRescueDeadlineTick");
        }

        this.maxonSurvivorUuid = tag.getString("MaxonSurvivorUuid");
        this.maxonSurvivorX = tag.getInt("MaxonSurvivorX");
        this.maxonSurvivorY = tag.getInt("MaxonSurvivorY");
        this.maxonSurvivorZ = tag.getInt("MaxonSurvivorZ");

        if (tag.contains("MaxonRepMaria")) {
            this.maxonRepMaria = tag.getInt("MaxonRepMaria");
            this.maxonRepRose = tag.getInt("MaxonRepRose");
            this.maxonRepSina = tag.getInt("MaxonRepSina");
        }

        if (tag.contains("MaxonCompletedQuests", net.minecraft.nbt.Tag.TAG_LIST)) {
            ListTag completedQuestsList = tag.getList("MaxonCompletedQuests", net.minecraft.nbt.Tag.TAG_STRING);
            this.maxonCompletedQuests = new HashSet<>();
            for (int i = 0; i < completedQuestsList.size(); i++) {
                this.maxonCompletedQuests.add(completedQuestsList.getString(i));
            }
            this.maxonQuestsCompleted = tag.getInt("MaxonQuestsCompleted");
        this.maxonTradesCompleted = tag.getInt("MaxonTradesCompleted");
        this.maxonHorseOfferSeed = tag.getLong("MaxonHorseOfferSeed");
        this.maxonOutpostQuestReadyAt = tag.getLong("MaxonOutpostQuestReadyAt");
        this.maxonOwnedHorseUuid = tag.getString("MaxonOwnedHorseUuid");
        }

        if (tag.contains("MaxonTestQuestCompletions")) {
            this.maxonTestQuestCompletions = tag.getInt("MaxonTestQuestCompletions");
        } else if (this.maxonCompletedQuests.contains("test_quest")) {
            // Сохранение со времён, когда тестовое было ОДНОРАЗОВЫМ: раз оно там отмечено
            // сданным — засчитываем ровно одно выполнение, чтобы у игрока сразу были открыты
            // остальные задания и планка выросла, как будто он прошёл первый круг.
            this.maxonTestQuestCompletions = 1;
        }

        this.maxonTestQuestCommanderMask = tag.getInt("MaxonTestQuestCommanderMask");
        // getInt вернёт 0 для отсутствующего тега, а "не взято" у нас -1 — поэтому читаем
        // только если тег реально есть, иначе оставляем дефолт из объявления поля.
        if (tag.contains("MaxonTestQuestCommanderIndex")) {
            this.maxonTestQuestCommanderIndex = tag.getInt("MaxonTestQuestCommanderIndex");
        }

        if (tag.contains("MaxonTitanicWillCooldown")) {
            this.maxonTitanicWillCooldown = tag.getInt("MaxonTitanicWillCooldown");
            this.maxonWillToLiveCooldown = tag.getInt("MaxonWillToLiveCooldown");
        }
        if (tag.contains("MaxonBloodStage")) {
            this.maxonBloodStage = tag.getInt("MaxonBloodStage");
            this.maxonBladeTitanKills = tag.getInt("MaxonBladeTitanKills");
            this.maxonBloodDryTicks = tag.getInt("MaxonBloodDryTicks");
        }

        if (tag.contains("DaotLevel")) {
            this.daotLevel = tag.getInt("DaotLevel");
            this.daotExp = tag.getInt("DaotExp");
            this.daotSkillPoints = tag.getInt("DaotSkillPoints");
        } else {
            this.daotLevel = 1;
        }

        // Очищаем текущие скилы перед загрузкой
        this.unlockedSkills.clear();

        // Читаем новую систему
        if (tag.contains("MaxonUnlockedSkills", Tag.TAG_LIST)) {
            ListTag skillsList = tag.getList("MaxonUnlockedSkills", Tag.TAG_STRING);
            for (int i = 0; i < skillsList.size(); i++) {
                this.unlockedSkills.add(skillsList.getString(i));
            }
        }
        // МИГРАЦИЯ со старых сохранений
        else {
            if (tag.getBoolean("DaotOdmMastery")) this.unlockedSkills.add("odm_mastery");
            if (tag.getBoolean("DaotDashMastery")) this.unlockedSkills.add("dash_mastery");
            if (tag.getBoolean("DaotConnectorMastery")) this.unlockedSkills.add("connector_mastery");
            if (tag.getBoolean("UngrabingMastery")) this.unlockedSkills.add("ungrabing_mastery");
            if (tag.getBoolean("DaotBloodlineAwakening")) this.unlockedSkills.add("bloodline_awakening");
            if (tag.getBoolean("DaotShifterRegen")) this.unlockedSkills.add("shifter_regen");
        }
    }

    /**
     * Перк "test" переименован в "cyclops" (ModPerk.CYCLOPS). Id перка лежит в сохранении
     * игрока, поэтому без подмены при загрузке у всех, кто его уже открыл, перк просто
     * исчез бы из списка (и из слота) — ModPerk.getById("test") больше ничего не находит.
     */
    @Unique
    private static String maxon$migratePerkId(String id) {
        return "test".equals(id) ? "cyclops" : id;
    }

    @Override
    public String[] getEquippedPerks() {
        if (this.maxonEquippedPerks == null || this.maxonEquippedPerks.length != com.maxonaddon.perk.PerkManager.SLOTS) {
            this.maxonEquippedPerks = new String[com.maxonaddon.perk.PerkManager.SLOTS];
        }
        return this.maxonEquippedPerks;
    }

    @Override
    public void setEquippedPerk(int slot, String perkId) {
        String[] slots = getEquippedPerks();
        if (slot < 0 || slot >= slots.length) return;
        slots[slot] = (perkId == null || perkId.isEmpty()) ? null : perkId;
    }

    @Override
    public boolean hasPerk(String perkId) {
        if (perkId == null) return false;
        for (String id : getEquippedPerks()) {
            if (perkId.equals(id)) return true;
        }
        return false;
    }

    @Override public int getTitansKilled() { return this.daotTitansKilled; }
    @Override public void addTitanKill() { this.daotTitansKilled++; }

    @Override public int getBloodStage() { return this.maxonBloodStage; }
    @Override public void setBloodStage(int stage) { this.maxonBloodStage = Math.max(0, Math.min(3, stage)); }
    @Override public int getBladeTitanKills() { return this.maxonBladeTitanKills; }
    @Override public void setBladeTitanKills(int kills) { this.maxonBladeTitanKills = Math.max(0, kills); }
    @Override public int getBloodDryTicks() { return this.maxonBloodDryTicks; }
    @Override public void setBloodDryTicks(int ticks) { this.maxonBloodDryTicks = Math.max(0, ticks); }

    @Override public int getTitanicWillCooldown() { return this.maxonTitanicWillCooldown; }
    @Override public void setTitanicWillCooldown(int ticks) { this.maxonTitanicWillCooldown = Math.max(0, ticks); }
    @Override public int getWillToLiveCooldown() { return this.maxonWillToLiveCooldown; }
    @Override public void setWillToLiveCooldown(int ticks) { this.maxonWillToLiveCooldown = Math.max(0, ticks); }
    @Override public long getTicksPlayedTotal() { return this.daotTicksPlayed; }

    @Override
    public void incrementTicksPlayed() {
        this.daotTicksPlayed++;
        this.daotTicksSinceDeath++;

        // Берем настройки из конфига
        int interval = MaxonConfig.get().bloodlinePointsTicks;
        int amount = MaxonConfig.get().bloodlinePointsAmount;

        // Если интервал больше нуля (защита от краша) и пришло время выдачи
        if (interval > 0 && this.daotTicksPlayed % interval == 0) {
            this.daotBloodlinePoints += amount;
        }
    }

    @Inject(method = "updatePlayerPose", at = @At("TAIL"))
    private void maxon$forceTraumaPose(CallbackInfo ci) {
        if (this.hasTrauma(com.maxonaddon.util.TraumaType.LEGS)) {
            // Превращаем 'this' в объект игрока
            net.minecraft.world.entity.player.Player player = (net.minecraft.world.entity.player.Player) (Object) this;

            // Вызываем методы у объекта player
            if (!player.isSwimming() && !player.isFallFlying() && !player.isSleeping()) {
                player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
            }
        }
    }



    @Override public long getTicksSinceDeath() { return this.daotTicksSinceDeath; }
    @Override public void resetTicksSinceDeath() { this.daotTicksSinceDeath = 0; }
}