package com.maxonaddon.client;

import com.maxonaddon.network.RifleAnimPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Клиентское состояние анимации винтовки у полицейских.
 * <p>
 * Сервер присылает только момент смены фазы, а время внутри фазы клиент отсчитывает сам —
 * ровно так же, как это уже сделано для укуса жителя и полёта на тросах.
 */
public final class VillagerRifleAnimTracker {

    /** Одна запись: текущая фаза и сколько секунд она уже идёт. */
    public static final class State {
        public int phase = RifleAnimPayload.IDLE;
        public float seconds = 0.0F;
    }

    private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();

    private VillagerRifleAnimTracker() {}

    public static void set(int entityId, int phase) {
        State state = STATES.computeIfAbsent(entityId, id -> new State());
        if (state.phase != phase) {
            state.phase = phase;
            state.seconds = 0.0F;   // фаза сменилась — отсчёт с нуля
        }
    }

    public static State get(int entityId) {
        return STATES.get(entityId);
    }

    /** Вызывается раз в клиентский тик: 1 тик = 0.05 секунды, как и в остальных трекерах. */
    public static void tick() {
        for (State state : STATES.values()) {
            state.seconds += 0.05F;
        }
    }

    public static void clear() {
        STATES.clear();
    }
}
