package com.maxonaddon.util;

/**
 * Реализуется миксином на RandomizableContainerBlockEntity (сундук/сундук-ловушка/бочка и т.д.) —
 * различает "поставлен игроком" от "сгенерирован миром" (см. ChestPlacementMixin/ChestOwnershipMixin).
 * Нужно для репутации: обыск/слом СГЕНЕРИРОВАННОГО (то есть чужого, ничейного/жительского)
 * сундука на глазах жителя — разбой; свой же поставленный сундук трогать можно как угодно.
 */
public interface IChestOwnership {
    boolean maxon$isPlayerPlaced();
    void maxon$setPlayerPlaced(boolean value);

    /** Уже сработала штрафом репутации ("житель заметил") — чтобы один и тот же сундук не фармили. */
    boolean maxon$isNoticedAlready();
    void maxon$setNoticedAlready(boolean value);
}
