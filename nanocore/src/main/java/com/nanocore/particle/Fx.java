package com.nanocore.particle;

import com.nanocore.util.Effects;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/** Server-side helpers that spawn nano cube particles. */
public final class Fx {
    private Fx() {}

    public static final Random R = new Random();

    public static void vel(ServerWorld w, String type, double x, double y, double z, double vx, double vy, double vz) {
        w.spawnParticles(ModParticles.get(type), x, y, z, 0, vx, vy, vz, 1.0);
    }

    public static void vel(ServerWorld w, String type, Vec3d p, Vec3d v) {
        vel(w, type, p.x, p.y, p.z, v.x, v.y, v.z);
    }

    public static Vec3d jitter(double s) {
        return new Vec3d(R.nextGaussian() * s, R.nextGaussian() * s, R.nextGaussian() * s);
    }

    /** Nano explosion: flash, metal shards, energy cubes and an expanding ring. */
    public static void blast(ServerWorld w, Vec3d c, int count, double speed) {
        for (int i = 0; i < count; i++) {
            Vec3d d = new Vec3d(R.nextGaussian(), R.nextGaussian() * 0.8, R.nextGaussian()).normalize()
                    .multiply(speed * (0.3 + R.nextDouble() * 0.9));
            vel(w, "nano_glow", c, d);
            if (i % 2 == 0) vel(w, "nano_bit", c, d.multiply(1.2).add(0, 0.05, 0));
            if (i % 3 == 0) vel(w, "nano_spark", c, d.multiply(1.5));
        }
        for (int i = 0; i < 10; i++) vel(w, "nano_core", c.add(jitter(0.25)), jitter(0.03));
        Effects.groundRing(w, c.add(0, -0.3, 0), speed * 9, 8);
    }
}
