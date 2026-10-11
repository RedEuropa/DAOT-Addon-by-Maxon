package com.maxonaddon.util;

/**
 * Три "стены" человечества — игрок-ориентированные названия для трёх уже существующих военных
 * веток мода (теги garrison_soldier / police_hunter / scout_legion, см. VillagerEntityMixin,
 * CityFactionGuard, QuestCommanderSpawner). Игроку мы никогда не показываем "Полиция"/"Гарнизон"/
 * "Скауты" в контексте репутации — только имя стены.
 *
 * Соответствие взято из самой геометрии карты: и 12 округов (CarriageSpawner.SPAWN_POINTS), и
 * точки спавна квестовых командиров (QuestCommanderSpawner) стоят на тех же трёх кольцах-радиусах
 * от центра — Полиция и внутреннее кольцо (~1300) = Сина (столица), Гарнизон и среднее кольцо
 * (~7300) = Роза, Разведка и внешнее кольцо (~13300) = Мария (см. WallHelper).
 */
public enum Wall {
    MARIA("Мария", "Maria", "wall_maria"),
    ROSE("Роза", "Rose", "wall_rose"),
    SINA("Сина", "Sina", "wall_sina");

    public final String nameRu;
    public final String nameEn;
    public final String tag;

    Wall(String nameRu, String nameEn, String tag) {
        this.nameRu = nameRu;
        this.nameEn = nameEn;
        this.tag = tag;
    }

    public String displayName(boolean russian) {
        return russian ? nameRu : nameEn;
    }

    /**
     * Ключ перевода имени стены (gui.maxonaddon.wall.maria/rose/sina) — в отличие от
     * displayName(boolean), не завязан на угадывание языка на сервере: заворачивается в
     * Component.translatable и корректно резолвится на КАЖДОМ клиенте под его собственный язык
     * (нужно для мультиязычных серверных сообщений/предметов квеста доставки).
     */
    public String getTranslationKey() {
        return "gui.maxonaddon.wall." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Обратный поиск по сохранённому имени (например, из IPlayerStats#getActiveQuestWall). Null, если не распозналось. */
    public static Wall fromNameSafe(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            return Wall.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
