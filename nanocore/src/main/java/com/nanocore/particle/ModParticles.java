package com.nanocore.particle;

import com.nanocore.Nano;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.LinkedHashMap;
import java.util.Map;

/** Nano cube particles: black / silver / energy blue. Names match assets/nanocore/particles/<name>.json. */
public final class ModParticles {
    private ModParticles() {}

    public static final Map<String, CubeProfile> PROFILES = new LinkedHashMap<>();
    public static final Map<String, SimpleParticleType> TYPES = new LinkedHashMap<>();

    static {
        //                                      young     middle    old       size  var   life var grav  drag   shrink spin flat alpha inner
        PROFILES.put("nano_bit",  new CubeProfile(0xF0F6FF, 0x9AA6B4, 0x2A3140, 0.09f, 0.04f, 20, 8, 0.0f, 0.90f, 0.970f, 30f, 1f,   0.95f, false));
        PROFILES.put("nano_glow", new CubeProfile(0xFFFFFF, 0x4CCBFF, 0x1060D0, 0.22f, 0.10f, 12, 5, 0.0f, 0.90f, 0.940f, 14f, 1f,   0.75f, true));
        PROFILES.put("nano_core", new CubeProfile(0xFFFFFF, 0xBFF0FF, 0x3AA8FF, 0.40f, 0.10f, 5,  2, 0.0f, 0.80f, 0.860f, 20f, 1f,   0.95f, true));
        PROFILES.put("nano_arc",  new CubeProfile(0xE8FBFF, 0x58C8FF, 0x1A58D0, 0.16f, 0.03f, 7,  2, 0.0f, 0.85f, 0.900f, 6f,  0.30f, 0.90f, false));
        PROFILES.put("nano_dark", new CubeProfile(0x3A4254, 0x1C212C, 0x0C0E14, 0.20f, 0.08f, 18, 6, 0.0f, 0.92f, 0.960f, 10f, 1f,   0.85f, false));
        PROFILES.put("nano_spark",new CubeProfile(0xFFFFFF, 0x8AE4FF, 0x2C7CFF, 0.07f, 0.03f, 16, 6, 0.02f, 0.92f, 0.970f, 40f, 1f,  1.00f, false));
    }

    public static SimpleParticleType get(String name) {
        return TYPES.get(name);
    }

    public static void init() {
        for (String name : PROFILES.keySet()) {
            SimpleParticleType t = FabricParticleTypes.simple(true);
            Registry.register(Registries.PARTICLE_TYPE, Nano.id(name), t);
            TYPES.put(name, t);
        }
    }
}
