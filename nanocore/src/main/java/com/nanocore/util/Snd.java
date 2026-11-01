package com.nanocore.util;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Plays a sound whether the vanilla constant is a SoundEvent or a RegistryEntry (it varies in 1.21). */
public final class Snd {
    private Snd() {}

    public static void play(World w, Vec3d p, Object sound, float volume, float pitch) {
        SoundEvent ev = null;
        if (sound instanceof SoundEvent s) ev = s;
        else if (sound instanceof RegistryEntry<?> re && re.value() instanceof SoundEvent s) ev = s;
        if (ev != null) w.playSound(null, p.x, p.y, p.z, ev, SoundCategory.PLAYERS, volume, pitch);
    }
}
