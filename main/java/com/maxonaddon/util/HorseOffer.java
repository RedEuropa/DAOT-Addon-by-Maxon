package com.maxonaddon.util;

import java.util.Random;

/**
 * Одна лошадь в лавке перевозчика.
 * <p>
 * Статы не хранятся ни на сервере, ни в NBT — они выводятся из одного long-seed'а игрока.
 * Клиенту достаточно получить этот seed, чтобы отрисовать ровно те же три лошади, что
 * посчитает сервер при покупке: никакой рассинхронизации «в меню было одно, купил другое»
 * тут возникнуть не может, а «обновить лошадей» — это просто новый seed.
 */
public record HorseOffer(double speed, double jump, double health, int priceBlocks, int variantIndex) {

    /** Сколько лошадей продаётся за раз. */
    public static final int COUNT = 3;

    /** Цена в БЛОКАХ изумруда по порядку тиров. */
    private static final int[] PRICE_BLOCKS = {2, 3, 5};

    /** Стоимость переброса статов, в обычных изумрудах. */
    public static final int REROLL_PRICE = 7;

    /** Сколько мастей у ванильной лошади (Variant: WHITE, CREAMY, CHESTNUT, BROWN, BLACK, GRAY, DARK_BROWN). */
    public static final int VARIANT_COUNT = 7;

    // Ванильные границы атрибутов лошади — по ним же нормализуются полоски в интерфейсе.
    public static final double SPEED_MIN = 0.1125;
    public static final double SPEED_MAX = 0.3375;
    public static final double JUMP_MIN = 0.4;
    public static final double JUMP_MAX = 1.0;
    public static final double HEALTH_MIN = 15.0;
    public static final double HEALTH_MAX = 30.0;

    // Каждому тиру — свой кусок общего диапазона. Куски слегка перекрываются, поэтому
    // удачная дешёвая лошадь может выйти не хуже неудачной средней, но потолок всегда
    // растёт с ценой.
    private static final double[][] SPEED_RANGE = {{0.1125, 0.2000}, {0.1900, 0.2700}, {0.2600, 0.3375}};
    private static final double[][] JUMP_RANGE  = {{0.4000, 0.6200}, {0.6000, 0.8200}, {0.8000, 1.0000}};
    private static final double[][] HEALTH_RANGE = {{15.0, 20.0}, {20.0, 25.0}, {25.0, 30.0}};

    /**
     * Собирает предложение под номером index из seed'а игрока.
     * Одинаковый seed всегда даёт одинаковых лошадей — и на клиенте, и на сервере.
     */
    public static HorseOffer generate(long seed, int index) {
        int tier = Math.max(0, Math.min(PRICE_BLOCKS.length - 1, index));
        // Смещение по индексу, иначе три лошади из одного seed'а вышли бы одинаковыми.
        Random random = new Random(seed * 31L + tier * 7919L);

        return new HorseOffer(
                lerp(random, SPEED_RANGE[tier]),
                lerp(random, JUMP_RANGE[tier]),
                lerp(random, HEALTH_RANGE[tier]),
                PRICE_BLOCKS[tier],
                // Масть тянем из того же генератора — значит витрина и купленная лошадь
                // всегда одного цвета, и никакой отдельной синхронизации не нужно.
                random.nextInt(VARIANT_COUNT)
        );
    }

    private static double lerp(Random random, double[] range) {
        return range[0] + random.nextDouble() * (range[1] - range[0]);
    }

    /** Доля заполнения полоски скорости, 0..1. */
    public double speedFraction() {
        return normalize(speed, SPEED_MIN, SPEED_MAX);
    }

    public double jumpFraction() {
        return normalize(jump, JUMP_MIN, JUMP_MAX);
    }

    public double healthFraction() {
        return normalize(health, HEALTH_MIN, HEALTH_MAX);
    }

    private static double normalize(double value, double min, double max) {
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }
}
