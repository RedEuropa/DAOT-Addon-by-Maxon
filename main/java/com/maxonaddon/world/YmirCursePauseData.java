package com.maxonaddon.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class YmirCursePauseData extends SavedData {
    private final Map<UUID, Long> pausedTimes = new HashMap<>();

    public void setPausedTime(UUID uuid, long elapsedTicks) {
        this.pausedTimes.put(uuid, elapsedTicks);
        this.setDirty();
    }

    public Long getPausedTime(UUID uuid) {
        return this.pausedTimes.get(uuid);
    }

    public void removePausedTime(UUID uuid) {
        if (this.pausedTimes.remove(uuid) != null) {
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> entry : this.pausedTimes.entrySet()) {
            CompoundTag nbt = new CompoundTag();
            nbt.putUUID("uuid", entry.getKey());
            nbt.putLong("elapsed", entry.getValue());
            list.add(nbt);
        }
        tag.put("paused_curses", list);
        return tag;
    }

    public static YmirCursePauseData load(CompoundTag tag, HolderLookup.Provider registries) {
        YmirCursePauseData data = new YmirCursePauseData();
        ListTag list = tag.getList("paused_curses", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag nbt = list.getCompound(i);
            data.pausedTimes.put(nbt.getUUID("uuid"), nbt.getLong("elapsed"));
        }
        return data;
    }

    public static YmirCursePauseData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(
                        YmirCursePauseData::new,
                        YmirCursePauseData::load,
                        (DataFixTypes) null
                ), "maxonservaddon_ymir_pause"
        );
    }
}