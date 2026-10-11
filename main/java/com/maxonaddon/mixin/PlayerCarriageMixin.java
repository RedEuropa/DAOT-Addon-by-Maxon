package com.maxonaddon.mixin;

import com.maxonaddon.util.ICarriageData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(Player.class)
public abstract class PlayerCarriageMixin implements ICarriageData {

    @Unique
    private final Map<String, BlockPos> maxon$unlockedCities = new HashMap<>();

    @Unique
    private boolean maxon$forestUnlocked = false;

    @Override
    public Map<String, BlockPos> maxon$getUnlockedCities() {
        return this.maxon$unlockedCities;
    }

    @Override
    public void maxon$unlockCity(String cityId, BlockPos pos) {
        this.maxon$unlockedCities.put(cityId, pos);
    }

    @Override
    public boolean maxon$isForestUnlocked() {
        return this.maxon$forestUnlocked;
    }

    @Override
    public void maxon$setForestUnlocked(boolean unlocked) {
        this.maxon$forestUnlocked = unlocked;
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onAddAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        ListTag citiesList = new ListTag();
        for (Map.Entry<String, BlockPos> entry : this.maxon$unlockedCities.entrySet()) {
            CompoundTag cityTag = new CompoundTag();
            cityTag.putString("CityId", entry.getKey());
            cityTag.putInt("X", entry.getValue().getX());
            cityTag.putInt("Y", entry.getValue().getY());
            cityTag.putInt("Z", entry.getValue().getZ());
            citiesList.add(cityTag);
        }
        compound.put("maxon$UnlockedCitiesMap", citiesList);
        compound.putBoolean("maxon$ForestUnlocked", this.maxon$forestUnlocked);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadAdditionalSaveData(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("maxon$UnlockedCitiesMap", Tag.TAG_LIST)) {
            this.maxon$unlockedCities.clear();
            ListTag citiesList = compound.getList("maxon$UnlockedCitiesMap", Tag.TAG_COMPOUND);
            for (int i = 0; i < citiesList.size(); i++) {
                CompoundTag cityTag = citiesList.getCompound(i);
                BlockPos pos = new BlockPos(cityTag.getInt("X"), cityTag.getInt("Y"), cityTag.getInt("Z"));
                this.maxon$unlockedCities.put(cityTag.getString("CityId"), pos);
            }
        }
        if (compound.contains("maxon$ForestUnlocked")) {
            this.maxon$forestUnlocked = compound.getBoolean("maxon$ForestUnlocked");
        }
    }
}