package com.maxonaddon.mixin;

import daot.AwakenedPowerTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

@Mixin(AwakenedPowerTracker.class)
public interface AwakenedPowerTrackerAccessor {
    @Accessor("charges")
    static Map<UUID, Float> getCharges() { throw new AssertionError(); }
}