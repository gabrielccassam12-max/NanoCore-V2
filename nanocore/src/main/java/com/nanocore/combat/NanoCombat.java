package com.nanocore.combat;

import com.nanocore.core.Ability;
import com.nanocore.core.NanoConfig;
import com.nanocore.core.NanoData;
import com.nanocore.core.NanoManager;
import com.nanocore.core.PState;
import com.nanocore.particle.Fx;
import com.nanocore.util.Effects;
import com.nanocore.util.Snd;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Every nano weapon and ability: mechanics, hit detection, sounds and the particle choreography. */
public final class NanoCombat {
    private NanoCombat() {}

    private static final Random R = Fx.R;

    // ------------------------------------------------------------------ helpers
    private static ServerWorld world(ServerPlayerEntity p) {
        return (ServerWorld) p.getWorld();
    }

    private static Vec3d rightOf(Vec3d look) {
        Vec3d r = look.crossProduct(new Vec3d(0, 1, 0));
        return r.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : r.normalize();
    }

    public static List<LivingEntity> targets(ServerWorld w, Box box, PlayerEntity owner) {
        boolean pvp = NanoConfig.INSTANCE.allowPvp && w.getServer() != null && w.getServer().isPvpEnabled();
        return w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != owner && !e.isSpectator()
                && !(e instanceof ArmorStandEntity) && (pvp || !(e instanceof PlayerEntity)));
    }

    private static Box around(Vec3d c, double r) {
        return new Box(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
    }

    private static void hurt(ServerWorld w, LivingEntity t, ServerPlayerEntity p, float amount) {
        t.damage(w.getDamageSources().playerAttack(p), amount);
    }

    private static Vec3d handOrigin(ServerPlayerEntity p, double side) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d right = rightOf(look);
        Vec3d up = right.crossProduct(look);
        return p.getEyePos().add(look.multiply(0.9)).add(right.multiply(0.35 * side)).add(up.multiply(-0.3));
    }

    private static void spend(ServerPlayerEntity p, PState s, long ticks) {
        s.nextPrimary = p.getWorld().getTime() + ticks;
    }

    // ------------------------------------------------------------------ dispatch
    public static void primary(ServerPlayerEntity p, PState s) {
        if (p.getWorld().getTime() < s.nextPrimary) return;
        switch (s.form) {
            case BLADE -> bladeCombo(p, s);
            case CANNON -> cannonShot(p, s);
            case SHIELD -> shieldBash(p, s);
            case WINGS -> wingsPrimary(p, s);
            default -> { }
        }
    }

    public static void release(ServerPlayerEntity p, PState s) {
        if (s.charge < 14) return;
        float k = Math.min(1f, s.charge / 40f);
        if (s.form == com.nanocore.core.Form.BLADE) bladeWave(p, s, k);
        else if (s.form == com.nanocore.core.Form.CANNON) cannonBlast(p, s, k);
    }

    public static void special(ServerPlayerEntity p, PState s) {
        switch (s.form) {
            case BLADE -> bladeFinisher(p, s);
            case CANNON -> cannonBeam(p, s);
            case SHIELD -> shieldRelease(p, s);
            case WINGS -> wingsCharge(p, s);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ NANO BLADE
    private static void bladeCombo(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        long now = w.getTime();
        int lvl = NanoManager.lvl(p, NanoManager.LEARN_BLADE);
        int maxCombo = lvl >= 1 ? 4 : 3;
        if (now - s.lastCombo > 26) s.combo = 0;
        if (!NanoManager.spend(p, 3)) return;
        int step = s.combo % maxCombo;
        s.combo++;
        s.lastCombo = now;
        spend(p, s, lvl >= 2 ? 6 : 8);
        boolean last = step == maxCombo - 1;
        float dmg = (last ? 10f : 6.5f) * NanoManager.damageMult(p) * (1f + 0.1f * lvl);
        p.swingHand(Hand.MAIN_HAND, true);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.4f + step * 0.1f);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
        Vec3d look = p.getRotationVec(1f);
        p.addVelocity(look.x * 0.35, 0, look.z * 0.35);
        p.velocityModified = true;
        slashFx(p, step, last && maxCombo == 4);
        boolean spin = last && maxCombo == 4;
        int hits = 0;
        Vec3d eye = p.getEyePos();
        for (LivingEntity e : targets(w, around(eye, 4.5), p)) {
            Vec3d to = e.getPos().add(0, e.getHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > (spin ? 3.8 : 3.7)) continue;
            if (!spin && d > 0.8 && to.normalize().dotProduct(look) < 0.3) continue;
            hurt(w, e, p, dmg);
            Vec3d k = to.normalize().multiply(last ? 0.9 : 0.35);
            e.addVelocity(k.x, last ? 0.35 : 0.1, k.z);
            e.velocityModified = true;
            impact(w, e.getPos().add(0, e.getHeight() / 2, 0), 10);
            hits++;
        }
        if (hits > 0) NanoManager.learn(p, NanoManager.LEARN_BLADE, hits);
    }

    private static void slashFx(ServerPlayerEntity p, int step, boolean spin) {
        ServerWorld w = world(p);
        Effects.add(t -> {
            if (t > 3 || !p.isAlive()) return false;
            Vec3d look = p.getRotationVec(1f);
            Vec3d right = rightOf(look);
            Vec3d up = right.crossProduct(look);
            Vec3d eye = p.getEyePos().add(0, -0.3, 0);
            int n = 26;
            int from = (t - 1) * n / 3, to = t * n / 3;
            for (int k = from; k < to; k++) {
                double f = k / (double) (n - 1);
                double ang = Math.toRadians(-70 + 140 * f);
                for (double r = 1.7; r <= 3.1; r += 0.7) {
                    Vec3d dir;
                    if (spin) {
                        double a = f * Math.PI * 2;
                        dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
                    } else if (step == 2) {
                        dir = look.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang)));
                    } else {
                        double sgn = step == 0 ? 1 : -1;
                        dir = look.multiply(Math.cos(ang)).add(right.multiply(Math.sin(ang) * sgn))
                                .add(up.multiply(Math.sin(ang) * 0.35 * sgn));
                    }
                    Fx.vel(w, "nano_arc", eye.add(dir.multiply(r)), dir.multiply(0.04));
                }
                if (k % 4 == 0) Fx.vel(w, "nano_spark", eye.add(look.multiply(2.6)), Fx.jitter(0.1));
            }
            return true;
        });
    }

    private static void bladeWave(ServerPlayerEntity p, PState s, float k) {
        ServerWorld w = world(p);
        if (!NanoManager.spend(p, 16 + (int) (k * 14))) return;
        spend(p, s, 14);
        float dmg = (10f + 14f * k) * NanoManager.damageMult(p);
        Vec3d dir = p.getRotationVec(1f);
        Vec3d right = rightOf(dir);
        Vec3d up = right.crossProduct(dir);
        Vec3d[] pos = {p.getEyePos().add(dir.multiply(1.0)).add(0, -0.3, 0)};
        Set<Integer> hit = new HashSet<>();
        p.swingHand(Hand.MAIN_HAND, true);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.9f);
        double half = 2.0 + 1.6 * k;
        Effects.add(t -> {
            if (t > 14 || !p.isAlive()) return false;
            Vec3d old = pos[0], next = old.add(dir.multiply(1.5));
            BlockHitResult bh = w.raycast(new RaycastContext(old, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) {
                impact(w, bh.getPos(), 40);
                return false;
            }
            pos[0] = next;
            for (int i = 0; i <= 28; i++) {
                double a = Math.toRadians(-80 + 160 * i / 28.0);
                Vec3d off = right.multiply(Math.sin(a) * half).add(up.multiply((Math.cos(a) - 0.6) * half * 0.8));
                Fx.vel(w, "nano_arc", next.add(off), dir.multiply(0.15));
                if (i % 4 == 0) Fx.vel(w, "nano_core", next.add(off), Vec3d.ZERO);
            }
            for (LivingEntity e : targets(w, around(next, half + 1.5), p)) {
                if (!hit.add(e.getId())) continue;
                hurt(w, e, p, dmg);
                e.addVelocity(dir.x * 0.8, 0.3, dir.z * 0.8);
                e.velocityModified = true;
                impact(w, e.getPos().add(0, 1, 0), 20);
                NanoManager.learn(p, NanoManager.LEARN_BLADE, 2);
            }
            return true;
        });
    }

    private static void bladeFinisher(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        if (p.getWorld().getTime() < s.nextPrimary) return;
        Vec3d look = p.getRotationVec(1f);
        Vec3d eye = p.getEyePos();
        LivingEntity best = null;
        double bd = 99;
        for (LivingEntity e : targets(w, around(eye, 5.5), p)) {
            Vec3d to = e.getPos().add(0, e.getHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d < bd && to.normalize().dotProduct(look) > 0.5) {
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            NanoManager.note(p, "message.nanocore.notarget", Formatting.GRAY);
            return;
        }
        if (!NanoManager.spend(p, 18)) return;
        spend(p, s, 24);
        p.swingHand(Hand.MAIN_HAND, true);
        int lvl = NanoManager.lvl(p, NanoManager.LEARN_BLADE);
        float dmg = (12f + 2f * lvl) * NanoManager.damageMult(p);
        hurt(w, best, p, dmg);
        Vec3d dir = new Vec3d(look.x, 0, look.z).normalize();
        best.setVelocity(dir.x * 2.2, 0.75, dir.z * 2.2);
        best.velocityModified = true;
        Vec3d c = best.getPos().add(0, best.getHeight() / 2, 0);
        Fx.blast(w, c, 40, 0.35);
        Snd.play(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
        Snd.play(w, c, SoundEvents.ITEM_TRIDENT_THUNDER, 0.8f, 1.8f);
        LivingEntity target = best;
        Effects.add(t -> {
            if (t > 10 || !target.isAlive()) return false;
            Vec3d q = target.getPos().add(0, target.getHeight() / 2, 0);
            for (int i = 0; i < 6; i++) Fx.vel(w, "nano_glow", q.add(Fx.jitter(0.25)), dir.multiply(-0.15).add(Fx.jitter(0.03)));
            Fx.vel(w, "nano_arc", q, dir.multiply(-0.2));
            return true;
        });
        NanoManager.learn(p, NanoManager.LEARN_BLADE, 4);
    }

    // ------------------------------------------------------------------ NANO CANNON
    private static void cannonShot(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        int lvl = NanoManager.lvl(p, NanoManager.LEARN_CANNON);
        int cost = Math.max(1, Math.round(4 * (1f - 0.15f * lvl)));
        if (!NanoManager.spend(p, cost)) return;
        spend(p, s, 4);
        Vec3d look = p.getRotationVec(1f);
        Vec3d o = handOrigin(p, 1);
        Vec3d aim = look.add(Fx.jitter(0.01)).normalize();
        float dmg = (5.5f + 0.8f * lvl) * NanoManager.damageMult(p);
        bolt(w, p, o, aim, 2.2, dmg, false, 0, 0);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 0.6f, 2.0f);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.9f);
        for (int i = 0; i < 6; i++) Fx.vel(w, "nano_core", o.add(look.multiply(0.4)), look.multiply(0.1).add(Fx.jitter(0.05)));
        p.swingHand(Hand.MAIN_HAND, true);
        NanoManager.learn(p, NanoManager.LEARN_CANNON, 1);
    }

    private static void cannonBlast(ServerPlayerEntity p, PState s, float k) {
        ServerWorld w = world(p);
        if (!NanoManager.spend(p, 18 + (int) (14 * k))) return;
        spend(p, s, 16);
        Vec3d look = p.getRotationVec(1f);
        int lvl = NanoManager.lvl(p, NanoManager.LEARN_CANNON);
        float dmg = (12f + 10f * k + lvl) * NanoManager.damageMult(p);
        bolt(w, p, handOrigin(p, 1), look, 1.4, dmg, true, 3.2 + 1.8 * k, 0);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 1.8f);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.6f);
        p.swingHand(Hand.MAIN_HAND, true);
        NanoManager.learn(p, NanoManager.LEARN_CANNON, 3);
    }

    /** Travelling energy ball. blastR > 0 = explodes with an area, otherwise hits one target. */
    private static void bolt(ServerWorld w, ServerPlayerEntity p, Vec3d start, Vec3d dir, double speed, float dmg,
                             boolean big, double blastR, int unused) {
        Vec3d vel = dir.normalize().multiply(speed);
        Vec3d[] pos = {start};
        Effects.add(t -> {
            if (t > 40 || !p.isAlive()) return false;
            Vec3d old = pos[0], next = old.add(vel);
            BlockHitResult bh = w.raycast(new RaycastContext(old, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            boolean block = bh.getType() != HitResult.Type.MISS;
            Vec3d at = block ? bh.getPos() : next;
            List<LivingEntity> near = targets(w, new Box(old, next).expand(big ? 1.0 : 0.5), p);
            if (block || !near.isEmpty()) {
                if (blastR > 0) {
                    for (LivingEntity e : targets(w, around(at, blastR), p)) {
                        double d = e.getPos().add(0, e.getHeight() / 2, 0).distanceTo(at);
                        if (d > blastR) continue;
                        float f = (float) (1.0 - 0.5 * d / blastR);
                        hurt(w, e, p, dmg * f);
                        Vec3d push = e.getPos().add(0, 0.5, 0).subtract(at).normalize();
                        e.addVelocity(push.x * 1.0 * f, 0.4, push.z * 1.0 * f);
                        e.velocityModified = true;
                    }
                    Fx.blast(w, at, 90, 0.3 + blastR * 0.06);
                    Snd.play(w, at, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.4f, 1.2f);
                } else {
                    if (!near.isEmpty()) hurt(w, near.get(0), p, dmg);
                    impact(w, at, 12);
                }
                return false;
            }
            pos[0] = next;
            double sz = big ? 0.28 : 0.1;
            for (int i = 0; i < (big ? 6 : 3); i++) Fx.vel(w, "nano_core", next.add(Fx.jitter(sz)), Vec3d.ZERO);
            for (int i = 0; i < (big ? 8 : 4); i++) Fx.vel(w, "nano_glow", next.add(Fx.jitter(sz * 1.4)), vel.multiply(-0.1).add(Fx.jitter(0.02)));
            Fx.vel(w, "nano_spark", next.add(Fx.jitter(sz)), Fx.jitter(0.05));
            if (big) Fx.vel(w, "nano_bit", next.add(Fx.jitter(0.5)), Fx.jitter(0.06));
            return true;
        });
    }

    private static void cannonBeam(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        if (p.getWorld().getTime() < s.nextPrimary || s.beaming) return;
        if (!NanoManager.spend(p, 28)) return;
        spend(p, s, 40);
        s.beaming = true;
        int lvl = NanoManager.lvl(p, NanoManager.LEARN_CANNON);
        float dmg = (4.5f + 0.5f * lvl) * NanoManager.damageMult(p);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.6f);
        Effects.add(t -> {
            if (t > 32 || !p.isAlive() || s.form != com.nanocore.core.Form.CANNON) {
                s.beaming = false;
                return false;
            }
            Vec3d look = p.getRotationVec(1f);
            Vec3d o = handOrigin(p, 1);
            Vec3d end = o.add(look.multiply(30));
            BlockHitResult bh = w.raycast(new RaycastContext(o, end, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            double len = bh.getType() == HitResult.Type.MISS ? 30 : bh.getPos().distanceTo(o);
            for (double d = 0.3; d < len; d += 0.45) {
                Vec3d q = o.add(look.multiply(d));
                Fx.vel(w, "nano_core", q, Vec3d.ZERO);
                if (R.nextInt(3) == 0) Fx.vel(w, "nano_glow", q.add(Fx.jitter(0.15)), Fx.jitter(0.03));
            }
            Vec3d tip = o.add(look.multiply(len));
            for (int i = 0; i < 4; i++) Fx.vel(w, "nano_spark", tip, Fx.jitter(0.15));
            if (t % 4 == 0) Fx.vel(w, "nano_bit", tip, Fx.jitter(0.12));
            if (t % 8 == 1) Snd.play(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.8f);
            if (t % 3 == 0) {
                for (LivingEntity e : targets(w, around(o.add(look.multiply(len / 2)), len / 2 + 2), p)) {
                    Vec3d to = e.getPos().add(0, e.getHeight() / 2, 0).subtract(o);
                    double along = to.dotProduct(look);
                    if (along < 0 || along > len) continue;
                    if (to.subtract(look.multiply(along)).length() > 1.1) continue;
                    hurt(w, e, p, dmg);
                    impact(w, e.getPos().add(0, e.getHeight() / 2, 0), 6);
                }
            }
            return true;
        });
        NanoManager.learn(p, NanoManager.LEARN_CANNON, 4);
    }

    // ------------------------------------------------------------------ NANO SHIELD
    private static void shieldBash(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        if (!NanoManager.spend(p, 8)) return;
        spend(p, s, 24);
        Vec3d look = p.getRotationVec(1f);
        Vec3d dir = new Vec3d(look.x, 0, look.z).normalize();
        Set<Integer> hit = new HashSet<>();
        float dmg = 6f * NanoManager.damageMult(p);
        p.swingHand(Hand.OFF_HAND, true);
        Snd.play(w, p.getPos(), SoundEvents.ITEM_SHIELD_BLOCK, 1.2f, 0.6f);
        Effects.add(t -> {
            if (t > 9 || !p.isAlive()) return false;
            p.setVelocity(dir.x * 1.15, p.getVelocity().y, dir.z * 1.15);
            p.velocityModified = true;
            Vec3d front = p.getPos().add(0, 1.0, 0).add(dir.multiply(1.1));
            for (int i = 0; i < 10; i++) {
                Vec3d off = rightOf(dir).multiply(R.nextGaussian() * 0.7).add(0, R.nextGaussian() * 0.6, 0);
                Fx.vel(w, "nano_glow", front.add(off), dir.multiply(0.05));
            }
            for (LivingEntity e : targets(w, p.getBoundingBox().expand(1.3), p)) {
                if (!hit.add(e.getId())) continue;
                hurt(w, e, p, dmg);
                e.addVelocity(dir.x * 1.5, 0.35, dir.z * 1.5);
                e.velocityModified = true;
                impact(w, e.getPos().add(0, 1, 0), 14);
            }
            return true;
        });
        NanoManager.learn(p, NanoManager.LEARN_SHIELD, 1);
    }

    private static void shieldRelease(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        if (s.shieldStored < 8f) {
            NanoManager.note(p, "message.nanocore.nostored", Formatting.GRAY);
            return;
        }
        float stored = s.shieldStored;
        s.shieldStored = 0;
        double radius = Math.min(9.0, 3.0 + stored / 9.0);
        float dmg = (4f + stored * 0.7f) * NanoManager.damageMult(p);
        Vec3d c = p.getPos().add(0, 1, 0);
        for (LivingEntity e : targets(w, around(c, radius), p)) {
            double d = e.getPos().distanceTo(p.getPos());
            if (d > radius) continue;
            hurt(w, e, p, dmg * (float) (1.0 - 0.4 * d / radius));
            Vec3d push = e.getPos().subtract(p.getPos()).multiply(1, 0, 1).normalize();
            e.addVelocity(push.x * 1.4, 0.55, push.z * 1.4);
            e.velocityModified = true;
        }
        Fx.blast(w, c, 140, 0.25 + radius * 0.05);
        Effects.groundRing(w, p.getPos().add(0, -0.3, 0), radius, 10);
        Snd.play(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.6f, 1.0f);
        Snd.play(w, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.4f);
        p.swingHand(Hand.OFF_HAND, true);
        NanoManager.learn(p, NanoManager.LEARN_SHIELD, 3);
    }

    public static void shieldHitFx(ServerWorld w, ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d front = p.getPos().add(0, 1.1, 0).add(look.multiply(1.0));
        Vec3d right = rightOf(look);
        for (int i = 0; i < 16; i++) {
            Vec3d off = right.multiply(R.nextGaussian() * 0.6).add(0, R.nextGaussian() * 0.6, 0);
            Fx.vel(w, "nano_spark", front.add(off), look.multiply(0.12).add(Fx.jitter(0.06)));
        }
        Fx.vel(w, "nano_core", front, Vec3d.ZERO);
        Snd.play(w, p.getPos(), SoundEvents.ITEM_SHIELD_BLOCK, 0.9f, 1.5f);
    }

    // ------------------------------------------------------------------ NANO WINGS
    private static void wingsPrimary(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        boolean air = p.getAbilities().flying || !p.isOnGround();
        Vec3d look = p.getRotationVec(1f);
        if (air) {
            if (!NanoManager.spend(p, 3)) return;
            spend(p, s, 5);
            Vec3d right = rightOf(look);
            float dmg = 3.8f * NanoManager.damageMult(p);
            for (int side = -1; side <= 1; side += 2) {
                Vec3d o = p.getEyePos().add(right.multiply(0.5 * side)).add(0, -0.1, 0).add(look.multiply(0.6));
                bolt(w, p, o, look.add(right.multiply(0.02 * side)), 2.0, dmg, false, 0, 0);
            }
            Snd.play(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 0.5f, 2.0f);
        } else {
            if (!NanoManager.spend(p, 5)) return;
            spend(p, s, 20);
            p.addVelocity(look.x * 0.9, 0.6, look.z * 0.9);
            p.velocityModified = true;
            Snd.play(w, p.getPos(), SoundEvents.ENTITY_BREEZE_JUMP, 1.0f, 1.2f);
            for (int i = 0; i < 20; i++) Fx.vel(w, "nano_glow", p.getPos().add(Fx.jitter(0.4)), Fx.jitter(0.1).add(0, 0.05, 0));
        }
    }

    private static void wingsCharge(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        if (p.getWorld().getTime() < s.nextPrimary) return;
        if (!NanoManager.spend(p, 16)) return;
        spend(p, s, 36);
        Set<Integer> hit = new HashSet<>();
        float dmg = 8f * NanoManager.damageMult(p);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.4f);
        Effects.add(t -> {
            if (t > 15 || !p.isAlive()) return false;
            Vec3d look = p.getRotationVec(1f);
            p.setVelocity(look.multiply(1.6));
            p.velocityModified = true;
            p.fallDistance = 0;
            Vec3d back = look.multiply(-1);
            for (int i = 0; i < 10; i++) {
                Fx.vel(w, "nano_glow", p.getPos().add(0, 1, 0).add(Fx.jitter(0.35)), back.multiply(0.3).add(Fx.jitter(0.05)));
            }
            Fx.vel(w, "nano_arc", p.getPos().add(0, 1, 0), back.multiply(0.4));
            Fx.vel(w, "nano_core", p.getPos().add(0, 1, 0).add(look.multiply(0.8)), Vec3d.ZERO);
            for (LivingEntity e : targets(w, p.getBoundingBox().expand(1.5), p)) {
                if (!hit.add(e.getId())) continue;
                hurt(w, e, p, dmg);
                e.addVelocity(look.x * 1.0, 0.4, look.z * 1.0);
                e.velocityModified = true;
                impact(w, e.getPos().add(0, 1, 0), 16);
            }
            return true;
        });
        NanoManager.learn(p, NanoManager.LEARN_FLIGHT, 3);
    }

    // ------------------------------------------------------------------ charge visuals
    public static void chargeFx(ServerPlayerEntity p, PState s) {
        ServerWorld w = world(p);
        Vec3d center = s.form == com.nanocore.core.Form.CANNON ? handOrigin(p, 1).add(p.getRotationVec(1f).multiply(0.6)) : p.getPos().add(0, 1.2, 0);
        float k = Math.min(1f, s.charge / 40f);
        for (int i = 0; i < 2 + (int) (4 * k); i++) {
            Vec3d from = center.add(new Vec3d(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).normalize().multiply(1.4 - 0.5 * k));
            Fx.vel(w, "nano_spark", from, center.subtract(from).multiply(0.18));
        }
        if (s.charge % 10 == 1) Snd.play(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 0.7f, 0.8f + k);
        if (s.charge == 40) {
            Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 2.0f);
            for (int i = 0; i < 20; i++) Fx.vel(w, "nano_core", center.add(Fx.jitter(0.3)), Fx.jitter(0.1));
        }
    }

    // ------------------------------------------------------------------ small shared effects
    private static void impact(ServerWorld w, Vec3d c, int n) {
        for (int i = 0; i < n; i++) {
            Vec3d d = new Vec3d(R.nextGaussian(), R.nextGaussian(), R.nextGaussian()).normalize().multiply(0.1 + R.nextDouble() * 0.25);
            Fx.vel(w, i % 3 == 0 ? "nano_bit" : "nano_spark", c, d);
        }
        Fx.vel(w, "nano_core", c, Vec3d.ZERO);
        Fx.vel(w, "nano_glow", c, Fx.jitter(0.02));
    }

    public static void overdriveFx(ServerWorld w, ServerPlayerEntity p) {
        Vec3d c = p.getPos();
        for (int i = 0; i < 4; i++) {
            double a = R.nextDouble() * Math.PI * 2;
            Fx.vel(w, "nano_glow", c.x + Math.cos(a) * 0.6, c.y + R.nextDouble() * 1.9, c.z + Math.sin(a) * 0.6,
                    -Math.sin(a) * 0.04, 0.08 + R.nextDouble() * 0.06, Math.cos(a) * 0.04);
        }
        if (R.nextInt(2) == 0) Fx.vel(w, "nano_spark", c.add(Fx.jitter(0.4)).add(0, 1, 0), Fx.jitter(0.12));
    }

    /** Nanobots rush back over the suit where it was hit. */
    public static void repairFx(ServerWorld w, ServerPlayerEntity p) {
        Vec3d c = p.getPos();
        for (int i = 0; i < 18; i++) {
            double a = R.nextDouble() * Math.PI * 2;
            double y = R.nextDouble() * 1.9;
            Fx.vel(w, "nano_bit", c.x + Math.cos(a) * 1.2, c.y + y, c.z + Math.sin(a) * 1.2,
                    -Math.cos(a) * 0.1, 0, -Math.sin(a) * 0.1);
        }
    }

    // ------------------------------------------------------------------ ABILITIES
    public static void ability(ServerPlayerEntity p, PState s, int idx) {
        Ability[] all = Ability.values();
        if (idx < 0 || idx >= all.length) return;
        Ability a = all[idx];
        NanoData d = NanoManager.get(p);
        ServerWorld w = world(p);
        long now = w.getTime();
        if (d.stage() < a.minStage) {
            p.sendMessage(Text.translatable("message.nanocore.needs_stage", NanoData.STAGE_TAGS[a.minStage]).formatted(Formatting.RED), true);
            return;
        }
        if (a != Ability.RECONSTRUCTION && !s.armor) {
            NanoManager.note(p, "message.nanocore.suit_off", Formatting.GRAY);
            return;
        }
        if (now < s.cd[idx]) {
            NanoManager.note(p, "message.nanocore.cooldown", Formatting.GRAY);
            return;
        }
        if (s.locked && a != Ability.RECONSTRUCTION) {
            NanoManager.note(p, "message.nanocore.locked", Formatting.RED);
            return;
        }
        float mult = NanoManager.damageMult(p);
        boolean ok = switch (a) {
            case SWARM -> swarm(w, p, mult);
            case DISASSEMBLY -> disassembly(w, p, mult);
            case RECONSTRUCTION -> reconstruction(w, p, s);
            case OVERDRIVE -> overdrive(w, p, s);
            case ADAPTIVE -> adaptive(w, p, s);
        };
        if (!ok) return;
        s.cd[idx] = now + NanoConfig.cooldown(a.cooldown);
        NanoManager.sync(p);
    }

    private static boolean swarm(ServerWorld w, ServerPlayerEntity p, float mult) {
        if (!NanoManager.spend(p, Ability.SWARM.cost)) return false;
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
        int n = 8;
        Vec3d[] c = new Vec3d[n];
        for (int i = 0; i < n; i++) c[i] = p.getPos().add(0, 1.2, 0).add(Fx.jitter(0.3));
        Effects.add(t -> {
            if (t > 140 || !p.isAlive()) return false;
            LivingEntity target = null;
            double bd = 99;
            for (LivingEntity e : targets(w, around(p.getPos(), 14), p)) {
                double d = e.distanceTo(p);
                if (d < bd) {
                    bd = d;
                    target = e;
                }
            }
            for (int i = 0; i < n; i++) {
                Vec3d aim;
                if (target != null) {
                    double a = t * 0.25 + i * Math.PI * 2 / n;
                    aim = target.getPos().add(Math.cos(a) * 0.7, target.getHeight() * (0.2 + 0.6 * ((i % 4) / 3.0)), Math.sin(a) * 0.7);
                    Vec3d step = aim.subtract(c[i]);
                    double len = step.length();
                    c[i] = len < 0.01 ? c[i] : c[i].add(step.multiply(Math.min(0.6, len) / len)).add(Fx.jitter(0.06));
                } else {
                    double a = t * 0.15 + i * Math.PI * 2 / n;
                    aim = p.getPos().add(Math.cos(a) * 2.2, 1.2 + Math.sin(t * 0.1 + i) * 0.6, Math.sin(a) * 2.2);
                    c[i] = c[i].add(aim.subtract(c[i]).multiply(0.25));
                }
                for (int k = 0; k < 6; k++) Fx.vel(w, "nano_bit", c[i].add(Fx.jitter(0.25)), Fx.jitter(0.03));
                Fx.vel(w, "nano_dark", c[i].add(Fx.jitter(0.2)), Fx.jitter(0.02));
                if (t % 2 == 0) Fx.vel(w, "nano_spark", c[i].add(Fx.jitter(0.2)), Fx.jitter(0.05));
            }
            if (t % 5 == 0 && target != null) {
                for (LivingEntity e : targets(w, around(p.getPos(), 16), p)) {
                    for (int i = 0; i < n; i++) {
                        if (c[i].distanceTo(e.getPos().add(0, e.getHeight() / 2, 0)) < 1.7) {
                            hurt(w, e, p, 2.2f * mult);
                            break;
                        }
                    }
                }
            }
            if (t % 12 == 1) Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEEHIVE_WORK, 0.8f, 1.8f);
            return true;
        });
        return true;
    }

    private static boolean disassembly(ServerWorld w, ServerPlayerEntity p, float mult) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        LivingEntity best = null;
        double bd = 99;
        for (LivingEntity e : targets(w, around(eye, 22), p)) {
            Vec3d to = e.getPos().add(0, e.getHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d < 22 && to.normalize().dotProduct(look) > 0.97 && d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) {
            NanoManager.note(p, "message.nanocore.notarget", Formatting.GRAY);
            return false;
        }
        if (!NanoManager.spend(p, Ability.DISASSEMBLY.cost)) return false;
        LivingEntity target = best;
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.5f);
        Vec3d from = handOrigin(p, 1);
        Effects.add(t -> {
            if (t > 70 || !target.isAlive()) return false;
            Vec3d c = target.getPos().add(0, target.getHeight() / 2, 0);
            if (t <= 8) { // the nanobots fly to the target
                for (int i = 0; i < 14; i++) {
                    Vec3d q = from.add(c.subtract(from).multiply(t / 8.0 + R.nextDouble() * 0.12));
                    Fx.vel(w, "nano_bit", q.add(Fx.jitter(0.15)), Fx.jitter(0.02));
                }
            } else {
                for (int i = 0; i < 6; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = 0.5 + R.nextDouble() * 0.4;
                    Vec3d q = c.add(Math.cos(a) * r, (R.nextDouble() - 0.5) * target.getHeight(), Math.sin(a) * r);
                    Fx.vel(w, "nano_bit", q, c.subtract(q).multiply(0.07));
                }
                Fx.vel(w, "nano_dark", c.add(Fx.jitter(0.3)), new Vec3d(0, 0.06, 0));
                Fx.vel(w, "nano_spark", c.add(Fx.jitter(0.35)), Fx.jitter(0.1));
                if (t % 5 == 0) {
                    target.timeUntilRegen = 0;
                    hurt(w, target, p, 1.8f * mult);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 1, true, false, false));
                    Snd.play(w, c, SoundEvents.BLOCK_CHAIN_BREAK, 0.7f, 1.6f);
                }
            }
            return true;
        });
        return true;
    }

    private static boolean reconstruction(ServerWorld w, ServerPlayerEntity p, PState s) {
        if (!NanoManager.spend(p, Ability.RECONSTRUCTION.cost)) return false;
        s.armor = true;
        s.rebuild++;
        NanoManager.broadcast(p);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.4f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 1, true, false, false));
        Effects.add(t -> {
            if (t > 32 || !p.isAlive()) {
                if (p.isAlive()) NanoManager.addEnergy(p, 25);
                return false;
            }
            p.heal(0.7f);
            Vec3d c = p.getPos();
            for (int k = 0; k < 3; k++) {
                double a = t * 0.5 + k * 2.094;
                double y = (t / 32.0) * 2.0;
                Fx.vel(w, "nano_bit", c.x + Math.cos(a) * 0.9, c.y + y, c.z + Math.sin(a) * 0.9, -Math.cos(a) * 0.04, 0.02, -Math.sin(a) * 0.04);
                Fx.vel(w, "nano_glow", c.x + Math.cos(a + 3.14) * 0.7, c.y + y, c.z + Math.sin(a + 3.14) * 0.7, 0, 0.02, 0);
            }
            if (t % 8 == 1) Snd.play(w, c, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.0f + t * 0.03f);
            return true;
        });
        return true;
    }

    private static boolean overdrive(ServerWorld w, ServerPlayerEntity p, PState s) {
        if (!NanoManager.spend(p, Ability.OVERDRIVE.cost)) return false;
        s.overdriveUntil = w.getTime() + 400;
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.6f);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1.2f, 1.2f);
        Fx.blast(w, p.getPos().add(0, 1, 0), 80, 0.4);
        p.sendMessage(Text.translatable("message.nanocore.overdrive").formatted(Formatting.AQUA, Formatting.BOLD), true);
        NanoManager.broadcast(p);
        return true;
    }

    private static boolean adaptive(ServerWorld w, ServerPlayerEntity p, PState s) {
        if (s.lastType.isEmpty()) {
            NanoManager.note(p, "message.nanocore.nothing_to_adapt", Formatting.GRAY);
            return false;
        }
        if (!NanoManager.spend(p, Ability.ADAPTIVE.cost)) return false;
        s.adaptiveType = s.lastType;
        s.adaptiveUntil = w.getTime() + 600;
        String shown = s.lastType.contains(":") ? s.lastType.substring(s.lastType.indexOf(':') + 1) : s.lastType;
        p.sendMessage(Text.translatable("message.nanocore.adapted", shown).formatted(Formatting.AQUA), true);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.3f);
        Vec3d c = p.getPos().add(0, 1, 0);
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 20.0;
            double y = (i / 20) * 0.9 - 0.2;
            Fx.vel(w, "nano_arc", c.x + Math.cos(a) * 1.0, c.y + y, c.z + Math.sin(a) * 1.0, Math.cos(a) * 0.03, 0, Math.sin(a) * 0.03);
        }
        return true;
    }
}
