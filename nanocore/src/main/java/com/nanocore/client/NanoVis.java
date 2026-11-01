package com.nanocore.client;

import com.nanocore.net.StatePayload;
import com.nanocore.particle.ModParticles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** What every nearby player looks like right now (suit, form, animation progress). Animated every client tick. */
public final class NanoVis {
    private NanoVis() {}

    public static final class Vis {
        public int stage = -1;
        public int mask;
        public int form;
        public int charge;
        public int rebuild = Integer.MIN_VALUE;
        public float armorP, armorPrev;
        public final float[] formP = new float[5];
        public final float[] formPrev = new float[5];

        public boolean suit() {
            return (mask & 1) != 0 && stage >= 0;
        }

        public boolean overdrive() {
            return (mask & 2) != 0;
        }

        public boolean flying() {
            return (mask & 4) != 0;
        }
    }

    public static final Map<Integer, Vis> MAP = new HashMap<>();
    private static final Random R = new Random();

    public static void apply(StatePayload p) {
        Vis v = MAP.computeIfAbsent(p.entityId(), k -> new Vis());
        v.stage = p.stage();
        v.mask = p.mask();
        v.form = p.form();
        v.charge = p.charge();
        if (v.rebuild != p.rebuild()) {
            if (v.rebuild != Integer.MIN_VALUE && v.suit()) v.armorP = 0f; // forms again from nanobots
            v.rebuild = p.rebuild();
        }
    }

    public static void clear() {
        MAP.clear();
    }

    public static Vis get(int id) {
        return MAP.get(id);
    }

    private static float towards(float cur, float target, float step) {
        if (cur < target) return Math.min(target, cur + step);
        return Math.max(target, cur - step);
    }

    private static void spawn(ClientWorld w, String type, double x, double y, double z, double vx, double vy, double vz) {
        SimpleParticleType t = ModParticles.get(type);
        if (t != null) w.addParticle(t, x, y, z, vx, vy, vz);
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w == null) {
            MAP.clear();
            return;
        }
        var it = MAP.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Entity e = w.getEntityById(en.getKey());
            Vis v = en.getValue();
            if (!(e instanceof AbstractClientPlayerEntity pl) || v.stage < 0) {
                if (e == null) it.remove();
                continue;
            }
            v.armorPrev = v.armorP;
            float before = v.armorP;
            v.armorP = towards(v.armorP, v.suit() ? 1f : 0f, v.suit() ? 0.05f : 0.09f);
            Vec3d pos = pl.getPos();
            // suit assembling: nanobots flow over the body from the feet to the head
            if (v.armorP > 0.01f && v.armorP < 0.99f && v.armorP != before) {
                double hgt = v.armorP * 1.9;
                for (int i = 0; i < 5; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = 0.7 + R.nextDouble() * 0.4;
                    spawn(w, i % 3 == 0 ? "nano_glow" : "nano_bit", pos.x + Math.cos(a) * r, pos.y + hgt + R.nextGaussian() * 0.15, pos.z + Math.sin(a) * r,
                            -Math.cos(a) * 0.09, -0.01, -Math.sin(a) * 0.09);
                }
            }
            double yr = Math.toRadians(pl.getBodyYaw());
            Vec3d fwd = new Vec3d(-Math.sin(yr), 0, Math.cos(yr));
            Vec3d right = new Vec3d(-Math.cos(yr), 0, -Math.sin(yr));
            for (int f = 1; f < 5; f++) {
                v.formPrev[f] = v.formP[f];
                float tgt = (v.suit() && v.armorP > 0.9f && v.form == f) ? 1f : 0f;
                float old = v.formP[f];
                v.formP[f] = towards(v.formP[f], tgt, 0.09f);
                if (v.formP[f] != old && v.formP[f] > 0.02f && v.formP[f] < 0.98f) {
                    // blades / cannon / shield / wings forming: bits fly to the limb
                    for (int i = 0; i < 4; i++) {
                        double side = f == 1 ? (i % 2 == 0 ? 1 : -1) : f == 2 ? 1 : f == 3 ? -1 : 0;
                        Vec3d base = f == 4 ? pos.add(0, 1.3, 0).add(fwd.multiply(-0.4))
                                : pos.add(0, 0.9, 0).add(right.multiply(0.45 * side)).add(fwd.multiply(0.5 * v.formP[f]));
                        spawn(w, i % 2 == 0 ? "nano_core" : "nano_bit", base.x + R.nextGaussian() * 0.25, base.y + R.nextGaussian() * 0.3, base.z + R.nextGaussian() * 0.25,
                                0, 0.01, 0);
                    }
                }
            }
            // idle glitter of a running core
            if (v.suit() && v.armorP > 0.95f && R.nextInt(6) == 0) {
                spawn(w, "nano_spark", pos.x + R.nextGaussian() * 0.35, pos.y + 0.3 + R.nextDouble() * 1.5, pos.z + R.nextGaussian() * 0.35, 0, 0.02, 0);
            }
            // wing thrusters: blue jets pour out of the three rings of each wing
            if (v.formP[4] > 0.8f && (v.flying() || R.nextInt(5) == 0)) {
                double[] lat = {0.45, 1.0, 1.5}, up = {0.28, 0.68, 1.1};
                for (int ring = 0; ring < 3; ring++) {
                    for (int sd = -1; sd <= 1; sd += 2) {
                        Vec3d rp = pos.add(0, 1.2 + up[ring], 0).add(fwd.multiply(-0.4)).add(right.multiply(lat[ring] * sd));
                        double jetV = v.flying() ? -0.16 : -0.04;
                        spawn(w, "nano_glow", rp.x + R.nextGaussian() * 0.04, rp.y, rp.z + R.nextGaussian() * 0.04, 0, jetV - R.nextDouble() * 0.05, 0);
                        if (v.flying() && R.nextInt(3) == 0) spawn(w, "nano_spark", rp.x, rp.y, rp.z, R.nextGaussian() * 0.03, -0.2, R.nextGaussian() * 0.03);
                    }
                }
            }
            // overdrive: crackling energy off the whole suit
            if (v.suit() && v.overdrive() && R.nextInt(2) == 0) {
                spawn(w, "nano_arc", pos.x + R.nextGaussian() * 0.35, pos.y + R.nextDouble() * 1.9, pos.z + R.nextGaussian() * 0.35,
                        R.nextGaussian() * 0.05, 0.04, R.nextGaussian() * 0.05);
            }
        }
    }

    /** {form, strength} for the arm pose, or null. */
    public static float[] pose(int entityId) {
        Vis v = MAP.get(entityId);
        if (v == null || !v.suit()) return null;
        int f = v.form;
        if (f <= 0 || f > 4) return null;
        float k = v.formP[f];
        return k < 0.02f ? null : new float[]{f, k};
    }
}
