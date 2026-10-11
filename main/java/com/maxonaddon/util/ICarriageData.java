package com.maxonaddon.util;

import net.minecraft.core.BlockPos;
import java.util.Map;

public interface ICarriageData {
    Map<String, BlockPos> maxon$getUnlockedCities();
    void maxon$unlockCity(String cityId, BlockPos pos);
    boolean maxon$isForestUnlocked();
    void maxon$setForestUnlocked(boolean unlocked);
}