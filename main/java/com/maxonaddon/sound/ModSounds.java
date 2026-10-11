package com.maxonaddon.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSounds {
    public static final SoundEvent NAPE_HIT = register("nape_hit");
    public static final SoundEvent NAPE_DEATH = register("nape_death");
    public static final SoundEvent CRIT_HIT = register("crit_hit");

    // === Марлийская винтовка ===
    public static final SoundEvent RIFLE_SHOOT = register("shoot");
    public static final SoundEvent RIFLE_FULL_RELOAD = register("full_reload");
    public static final SoundEvent RIFLE_BOLT_RELOAD = register("bolt_reload");

    // === Дыхание от страха ===
    public static final SoundEvent FEAR_START_BREATH = register("start_breath");
    public static final SoundEvent FEAR_BREATH = register("breath");
    public static final SoundEvent FEAR_STOP_BREATH = register("stop_breath");

    // === Смех берсерка (слышит только сам игрок, пока теряет контроль) ===
    public static final SoundEvent BERSERK_LAUGH = register("berserk");

    // === Свист, которым подзывают лошадь ===
    public static final SoundEvent WHISTLING = register("whistling");

    private static SoundEvent register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("maxon-daot-addon", name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void init() {}
}