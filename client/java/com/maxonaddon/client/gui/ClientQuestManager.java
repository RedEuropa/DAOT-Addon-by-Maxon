package com.maxonaddon.client.gui;

public class ClientQuestManager {
    public static String currentQuestId = "";
    public static String currentTarget = "";
    public static int questTitansKilled = 0;
    /**
     * Синк с сервера: сколько раз игрок уже сдал ПОВТОРЯЕМОЕ тестовое задание. От него
     * считается текущая планка (см. requiredTitans/requiredWheat) и зависит, открыты ли
     * остальные задания у командиров (см. otherQuestsUnlocked).
     */
    public static int testQuestCompletions = 0;
    /** Битовая маска командиров, у которых вводное уже сдано (см. IPlayerStats#getTestQuestCommanderMask). */
    public static int testQuestCommanderMask = 0;
    /** Абсолютный игровой тик дедлайна квеста "Доставить припасы" (0, если это не он). */
    public static long deliveryDeadlineTick = 0L;

    /**
     * Сколько титанов требует зачистка у вышки. Сервер приписывает это число к "цели" квеста
     * через разделитель (см. OutpostHuntQuestHandler.TARGET_SEPARATOR) — отдельного пакета
     * ради одного числа заводить не стали.
     */
    public static int outpostTitansRequired() {
        String part = outpostTargetPart(1);
        if (part.isEmpty()) return 0;
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Имя стены, в которой стоит вышка — им называется задание в журнале. */
    public static String outpostWallName() {
        return outpostTargetPart(2);
    }

    /** Цель квеста вышки собрана как "группа|целей|стена" — достаём нужный кусок. */
    private static String outpostTargetPart(int index) {
        // split() принимает регулярку, а "|" в ней — это «или», поэтому экранируем.
        String[] parts = currentTarget.split("\\|");
        return index < parts.length ? parts[index] : "";
    }

    public static boolean hasQuest() {
        return !currentQuestId.isEmpty();
    }

    /** Сколько титанов требует тестовое задание прямо сейчас. */
    public static int requiredTitans() {
        return com.maxonaddon.util.IPlayerStats.testQuestTitansRequired(testQuestCompletions);
    }

    /** Сколько пшеницы требует тестовое задание прямо сейчас. */
    public static int requiredWheat() {
        return com.maxonaddon.util.IPlayerStats.testQuestWheatRequired(testQuestCompletions);
    }

    /** Доставка и спасение открываются только после первой сдачи вводного тестового задания. */
    public static boolean otherQuestsUnlocked() {
        return testQuestCompletions > 0;
    }

    /**
     * Сдано ли уже вводное задание у ЭТОГО командира — у каждого его можно взять по разу.
     * Командир опознаётся по ближайшей фиксированной точке спавна, ровно как на сервере
     * (см. QuestCommanderSpawner#nearestPointIndex), поэтому результат совпадает с серверной
     * проверкой, а не расходится с ней.
     */
    public static boolean testQuestDoneAt(net.minecraft.world.entity.Entity commander) {
        if (commander == null) return false;
        int index = com.maxonaddon.event.QuestCommanderSpawner.nearestPointIndex(commander.getX(), commander.getZ());
        if (index < 0 || index >= 32) return false;
        return (testQuestCommanderMask & (1 << index)) != 0;
    }
}
