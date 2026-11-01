package com.nanocore.core;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.nanocore.Nano;
import com.nanocore.combat.NanoCombat;
import com.nanocore.net.StatePayload;
import com.nanocore.net.SyncPayload;
import com.nanocore.particle.Fx;
import com.nanocore.util.Snd;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server brain: saved data, energy, suit and forms, damage handling, evolution, sync and commands. */
public final class NanoManager {
    private NanoManager() {}

    public static final int LEARN_BLADE = 0, LEARN_CANNON = 1, LEARN_SHIELD = 2, LEARN_FLIGHT = 3, LEARN_EXPLOSION = 4;

    public static final AttachmentType<NanoData> DATA = AttachmentRegistry.<NanoData>builder()
            .persistent(NanoData.CODEC)
            .copyOnDeath()
            .buildAndRegister(Nano.id("core"));

    private static final Map<UUID, PState> STATES = new HashMap<>();
    private static boolean GUARD = false;

    public static NanoData get(ServerPlayerEntity p) {
        return ((AttachmentTarget) p).getAttachedOrElse(DATA, NanoData.DEFAULT);
    }

    public static void set(ServerPlayerEntity p, NanoData d) {
        ((AttachmentTarget) p).setAttached(DATA, d);
    }

    public static PState st(ServerPlayerEntity p) {
        return STATES.computeIfAbsent(p.getUuid(), k -> new PState());
    }

    public static void note(ServerPlayerEntity p, String key, Formatting f) {
        p.sendMessage(Text.translatable(key).formatted(f), true);
    }

    public static float damageMult(ServerPlayerEntity p) {
        NanoData d = get(p);
        PState s = st(p);
        float m = (float) NanoConfig.INSTANCE.damageMultiplier * (1f + 0.08f * d.stage());
        if (s.overdriveUntil > p.getWorld().getTime()) m *= 1.4f;
        return m;
    }

    public static int lvl(ServerPlayerEntity p, int idx) {
        return get(p).level(idx);
    }

    // ------------------------------------------------------------ energy
    /** Pays an energy cost (config-scaled). Returns false (with a message) if there is not enough. */
    public static boolean spend(ServerPlayerEntity p, int base) {
        NanoData d = get(p);
        int c = NanoConfig.cost(base);
        if (d.energy() < c) {
            note(p, "message.nanocore.energy", Formatting.RED);
            return false;
        }
        d = d.withEnergy(d.energy() - c);
        set(p, d);
        if (d.energy() <= 0) lockOut(p);
        return true;
    }

    public static void addEnergy(ServerPlayerEntity p, int n) {
        NanoData d = get(p);
        set(p, d.withEnergy(d.energy() + n));
    }

    private static void lockOut(ServerPlayerEntity p) {
        PState s = st(p);
        if (s.locked) return;
        s.locked = true;
        note(p, "message.nanocore.locked", Formatting.RED);
        if (s.form.advanced) setForm(p, s, Form.NONE);
    }

    /** Adds learning progress and announces level ups. */
    public static void learn(ServerPlayerEntity p, int idx, int n) {
        NanoData d = get(p);
        int before = d.level(idx);
        d = d.addLearn(idx, n);
        set(p, d);
        if (d.level(idx) > before) {
            p.sendMessage(Text.translatable("message.nanocore.learned." + idx, d.level(idx)).formatted(Formatting.AQUA), true);
            Snd.play(p.getWorld(), p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
        }
    }

    // ------------------------------------------------------------ init
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            int tick = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                NanoData d = get(p);
                if (!d.installed()) continue;
                PState s = st(p);
                tickPlayer(p, d, s, tick);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            STATES.remove(p.getUuid());
            sync(p);
            for (ServerPlayerEntity o : server.getPlayerManager().getPlayerList()) {
                if (o != p && get(o).installed()) sendStateTo(p, o, st(o));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            PState s = STATES.remove(p.getUuid());
            if (s != null) setFlight(p, s, false);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (GUARD || !(entity instanceof ServerPlayerEntity sp)) return true;
            NanoData d = get(sp);
            if (!d.installed()) return true;
            return onDamage(sp, d, st(sp), source, amount);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("nano")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("install").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            install(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("stage").then(CommandManager.argument("mk", IntegerArgumentType.integer(1, 4))
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    NanoData d = get(p).withInstalled(true).withStage(IntegerArgumentType.getInteger(ctx, "mk") - 1);
                                    set(p, d.withEnergy(d.max()));
                                    sync(p);
                                    broadcast(p);
                                    return 1;
                                })))
                        .then(CommandManager.literal("master").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            NanoData d = get(p).withInstalled(true).withStage(3).withLearn(List.of(400, 400, 400, 400, 400));
                            set(p, d.withEnergy(d.max()));
                            sync(p);
                            broadcast(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("energy").then(CommandManager.argument("amount", IntegerArgumentType.integer(0, 1000))
                                .executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    set(p, get(p).withEnergy(IntegerArgumentType.getInteger(ctx, "amount")));
                                    sync(p);
                                    return 1;
                                })))
                        .then(CommandManager.literal("reset").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            PState s = st(p);
                            setFlight(p, s, false);
                            s.armor = false;
                            s.form = Form.NONE;
                            set(p, NanoData.DEFAULT);
                            sync(p);
                            broadcast(p);
                            return 1;
                        }))));
    }

    // ------------------------------------------------------------ install / evolve
    public static boolean install(ServerPlayerEntity p) {
        NanoData d = get(p);
        if (d.installed()) {
            note(p, "message.nanocore.already", Formatting.YELLOW);
            return false;
        }
        d = d.withInstalled(true).withStage(0);
        set(p, d.withEnergy(d.max()));
        ServerWorld w = (ServerWorld) p.getWorld();
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1.4f, 1.4f);
        Fx.blast(w, p.getPos().add(0, 1, 0), 60, 0.35);
        p.sendMessage(Text.translatable("message.nanocore.installed").formatted(Formatting.AQUA, Formatting.BOLD), true);
        sync(p);
        broadcast(p);
        return true;
    }

    /** Chip item: advances the core one stage if it is the next one. */
    public static boolean evolve(ServerPlayerEntity p, int targetStage) {
        NanoData d = get(p);
        if (!d.installed()) {
            note(p, "message.nanocore.noinstall", Formatting.RED);
            return false;
        }
        if (d.stage() >= targetStage) {
            note(p, "message.nanocore.already_stage", Formatting.YELLOW);
            return false;
        }
        if (d.stage() != targetStage - 1) {
            note(p, "message.nanocore.need_previous", Formatting.RED);
            return false;
        }
        d = d.withStage(targetStage);
        set(p, d.withEnergy(d.max()));
        PState s = st(p);
        s.rebuild++;
        ServerWorld w = (ServerWorld) p.getWorld();
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.8f);
        Fx.blast(w, p.getPos().add(0, 1, 0), 120, 0.5);
        p.sendMessage(Text.translatable("message.nanocore.evolved", NanoData.STAGE_TAGS[targetStage], NanoData.STAGE_NAMES[targetStage])
                .formatted(Formatting.AQUA, Formatting.BOLD), true);
        sync(p);
        broadcast(p);
        return true;
    }

    // ------------------------------------------------------------ tick
    private static void tickPlayer(ServerPlayerEntity p, NanoData d, PState s, int tick) {
        long now = p.getWorld().getTime();
        boolean dirty = false;

        if (!p.isAlive()) {
            if (s.armor || s.form != Form.NONE) {
                s.armor = false;
                setForm(p, s, Form.NONE);
            }
            return;
        }

        // regeneration (+ unlock after a lock-out)
        if (tick % 6 == 0 && d.energy() < d.max()) {
            int regen = (int) Math.max(1, Math.round((1 + d.stage() * 0.5) * NanoConfig.INSTANCE.regenMultiplier));
            d = d.withEnergy(d.energy() + regen);
            dirty = true;
        }
        if (s.locked && d.energy() >= d.max() / 4) {
            s.locked = false;
            note(p, "message.nanocore.unlocked", Formatting.AQUA);
        }

        boolean flyingNow = s.form == Form.WINGS && (p.getAbilities().flying || (!p.isOnGround() && !p.isTouchingWater()));
        s.flying = flyingNow;
        // continuous drains
        if (s.form == Form.WINGS && p.getAbilities().flying && tick % 10 == 0) {
            int c = NanoConfig.cost(2);
            d = d.withEnergy(d.energy() - c);
            dirty = true;
            if (tick % 20 == 0) {
                set(p, d);
                learn(p, LEARN_FLIGHT, 1);
                d = get(p);
            }
        }
        if (s.overdriveUntil > now && tick % 20 == 0) {
            d = d.withEnergy(d.energy() - NanoConfig.cost(3));
            dirty = true;
        }
        if (s.armor && tick % 80 == 0 && p.getHealth() < p.getMaxHealth()) {
            p.heal(0.5f * (1 + d.stage()));
        }
        // overdrive potion effects
        if (s.overdriveUntil > now) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 25, 1, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 25, 1, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 25, 1, true, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 25, 0, true, false, false));
            if (tick % 2 == 0) NanoCombat.overdriveFx((ServerWorld) p.getWorld(), p);
        }
        // charge decays when the key stops being sent
        if (s.charge > 0 && now - s.chargeSeen > 3) s.charge = 0;

        if (dirty) {
            set(p, d);
            if (d.energy() <= 0) lockOut(p);
        }
        if (dirty || tick % 10 == 0) sync(p);

        int h = (s.armor ? 1 : 0) | (s.form.ordinal() << 1) | (s.overdriveUntil > now ? 64 : 0) | (s.flying ? 128 : 0)
                | (Math.min(100, s.charge * 100 / 40) / 4 << 8) | (s.rebuild << 16) | (d.stage() << 28);
        if (h != s.lastSent || tick % 100 == 0) {
            s.lastSent = h;
            broadcast(p);
        }
    }

    // ------------------------------------------------------------ actions
    public static final int ARMOR = 1, FORM = 2, PRIMARY = 3, CHARGE = 4, RELEASE = 5, SPECIAL = 6, ABILITY = 7;

    public static void action(ServerPlayerEntity p, int action, int arg) {
        if (!p.isAlive() || p.isSpectator()) return;
        NanoData d = get(p);
        if (!d.installed()) {
            note(p, "message.nanocore.noinstall", Formatting.RED);
            return;
        }
        PState s = st(p);
        switch (action) {
            case ARMOR -> toggleArmor(p, s, !s.armor);
            case FORM -> {
                Form f = Form.of(arg);
                if (f == Form.NONE) return;
                if (d.stage() < f.minStage) {
                    p.sendMessage(Text.translatable("message.nanocore.needs_stage", NanoData.STAGE_TAGS[f.minStage]).formatted(Formatting.RED), true);
                    return;
                }
                if (f.advanced && s.locked) {
                    note(p, "message.nanocore.locked", Formatting.RED);
                    return;
                }
                if (!s.armor) toggleArmor(p, s, true);
                setForm(p, s, s.form == f ? Form.NONE : f);
            }
            case PRIMARY -> {
                if (s.armor && s.form != Form.NONE) NanoCombat.primary(p, s);
                else note(p, "message.nanocore.noform", Formatting.GRAY);
            }
            case CHARGE -> {
                if (s.armor && s.form != Form.NONE && s.form != Form.SHIELD && s.form != Form.WINGS) {
                    s.charge = Math.min(60, s.charge + 1);
                    s.chargeSeen = p.getWorld().getTime();
                    NanoCombat.chargeFx(p, s);
                }
            }
            case RELEASE -> {
                if (s.armor && s.form != Form.NONE) NanoCombat.release(p, s);
                s.charge = 0;
            }
            case SPECIAL -> {
                if (s.armor && s.form != Form.NONE) NanoCombat.special(p, s);
                else note(p, "message.nanocore.noform", Formatting.GRAY);
            }
            case ABILITY -> NanoCombat.ability(p, s, arg);
            default -> { }
        }
    }

    public static void toggleArmor(ServerPlayerEntity p, PState s, boolean on) {
        if (on == s.armor) return;
        ServerWorld w = (ServerWorld) p.getWorld();
        if (on) {
            if (get(p).energy() < 5) {
                note(p, "message.nanocore.energy", Formatting.RED);
                return;
            }
            s.armor = true;
            s.rebuild++;
            Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);
            Snd.play(w, p.getPos(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
            transformFx(w, p, 3.4, 22, true);
        } else {
            setForm(p, s, Form.NONE);
            s.armor = false;
            Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.8f);
            transformFx(w, p, 2.4, 14, false);
        }
        broadcast(p);
    }

    /** Nanobots swarm in a rising spiral around the player and a ring of light spreads over the ground. */
    public static void transformFx(ServerWorld w, ServerPlayerEntity p, double ringR, int ticks, boolean assemble) {
        com.nanocore.util.Effects.groundRing(w, p.getPos(), ringR, 14);
        com.nanocore.util.Effects.add(t -> {
            if (t > ticks || !p.isAlive()) return false;
            Vec3d c = p.getPos();
            double k = (double) t / ticks;
            double h = (assemble ? k : 1.0 - k) * 2.0;
            for (int j = 0; j < 4; j++) {
                double a = t * 0.55 + j * Math.PI / 2;
                double r = 1.3 - 0.5 * k;
                Fx.vel(w, j % 2 == 0 ? "nano_bit" : "nano_glow", c.x + Math.cos(a) * r, c.y + h, c.z + Math.sin(a) * r,
                        -Math.cos(a) * 0.07, 0.0, -Math.sin(a) * 0.07);
            }
            if (t == ticks / 2) Fx.blast(w, c.add(0, 1.0, 0), 22, 0.18);
            return true;
        });
    }

    public static void setForm(ServerPlayerEntity p, PState s, Form f) {
        if (s.form == f) return;
        s.form = f;
        s.beaming = false;
        s.shieldStored = f == Form.SHIELD ? s.shieldStored : s.shieldStored;
        setFlight(p, s, f == Form.WINGS);
        ServerWorld w = (ServerWorld) p.getWorld();
        Snd.play(w, p.getPos(), f == Form.NONE ? SoundEvents.BLOCK_PISTON_CONTRACT : SoundEvents.BLOCK_PISTON_EXTEND, 1.0f, 1.7f);
        if (f != Form.NONE) {
            Fx.blast(w, p.getPos().add(0, 1.2, 0), 18, 0.12);
            transformFx(w, p, f == Form.WINGS ? 4.6 : 2.2, f == Form.WINGS ? 18 : 10, true);
        }
        broadcast(p);
    }

    private static void setFlight(ServerPlayerEntity p, PState s, boolean on) {
        var ab = p.getAbilities();
        if (on) {
            if (!ab.allowFlying) {
                ab.allowFlying = true;
                s.grantedFlight = true;
            }
            if (s.grantedFlight) ab.setFlySpeed(0.05f * (1.35f + 0.12f * lvl(p, LEARN_FLIGHT)));
        } else if (s.grantedFlight) {
            ab.allowFlying = false;
            ab.flying = false;
            ab.setFlySpeed(0.05f);
            s.grantedFlight = false;
        }
        p.sendAbilitiesUpdate();
    }

    // ------------------------------------------------------------ damage rules
    private static boolean onDamage(ServerPlayerEntity sp, NanoData d, PState s, DamageSource source, float amount) {
        long now = sp.getWorld().getTime();
        String type = source.getTypeRegistryEntry().getKey().map(k -> k.getValue().toString()).orElse("");
        if (!type.isEmpty()) s.lastType = type;

        float mult = 1f;
        boolean explosion = source.isIn(DamageTypeTags.IS_EXPLOSION);
        if (explosion) {
            learn(sp, LEARN_EXPLOSION, Math.max(1, (int) amount));
            mult *= 1f - 0.15f * d.level(LEARN_EXPLOSION);
        }
        if (s.armor) {
            mult *= 1f - (0.10f + 0.07f * d.stage());
            if (s.overdriveUntil > now) mult *= 0.75f;
            if (s.adaptiveUntil > now && type.equals(s.adaptiveType)) mult *= 0.3f;
            if (s.form == Form.SHIELD) {
                Entity src = source.getSource() != null ? source.getSource() : source.getAttacker();
                Vec3d from = src != null ? src.getPos() : source.getPosition();
                if (from != null) {
                    Vec3d to = from.subtract(sp.getPos()).multiply(1, 0, 1);
                    Vec3d look = sp.getRotationVec(1f).multiply(1, 0, 1);
                    if (to.lengthSquared() > 1e-4 && look.lengthSquared() > 1e-4
                            && to.normalize().dotProduct(look.normalize()) > 0.2) {
                        float absorb = source.isIn(DamageTypeTags.IS_PROJECTILE) ? 1f : 0.6f + 0.08f * d.level(LEARN_SHIELD);
                        s.shieldStored = Math.min(60f, s.shieldStored + amount * absorb);
                        mult *= 1f - absorb;
                        learn(sp, LEARN_SHIELD, 1);
                        NanoCombat.shieldHitFx((ServerWorld) sp.getWorld(), sp);
                    }
                }
            }
            // visible "repair" when something hurts the suit
            if (now - s.lastHitFx > 30 && amount >= 2f) {
                s.lastHitFx = now;
                s.rebuild++;
                NanoCombat.repairFx((ServerWorld) sp.getWorld(), sp);
            }
        }
        if (mult >= 0.999f) return true;
        if (mult * amount < 0.05f) return false;
        GUARD = true;
        try {
            sp.damage(source, amount * mult);
        } finally {
            GUARD = false;
        }
        return false;
    }

    // ------------------------------------------------------------ sync
    public static void sync(ServerPlayerEntity p) {
        NanoData d = get(p);
        PState s = st(p);
        long now = p.getWorld().getTime();
        int[] cd = new int[Ability.values().length];
        for (int i = 0; i < cd.length; i++) cd[i] = (int) Math.max(0, s.cd[i] - now);
        if (!ServerPlayNetworking.canSend(p, SyncPayload.ID)) return;
        ServerPlayNetworking.send(p, new SyncPayload(d.installed(), d.stage(), d.energy(), d.max(), s.locked, s.armor,
                s.form.ordinal(), new ArrayList<>(d.learn()), cd, (int) Math.max(0, s.overdriveUntil - now),
                (int) Math.max(0, s.adaptiveUntil - now), (int) Math.min(100, s.charge * 100 / 40), (int) (s.shieldStored)));
    }

    private static StatePayload statePayload(ServerPlayerEntity o, PState s) {
        NanoData d = get(o);
        long now = o.getWorld().getTime();
        int mask = (s.armor ? 1 : 0) | (s.overdriveUntil > now ? 2 : 0) | (s.flying ? 4 : 0);
        return new StatePayload(o.getId(), d.installed() ? d.stage() : -1, mask, s.form.ordinal(),
                Math.min(100, s.charge * 100 / 40), s.rebuild);
    }

    private static void sendStateTo(ServerPlayerEntity to, ServerPlayerEntity o, PState s) {
        if (ServerPlayNetworking.canSend(to, StatePayload.ID)) ServerPlayNetworking.send(to, statePayload(o, s));
    }

    /** Sends the visual state of this player to everyone who can see them (and to themselves). */
    public static void broadcast(ServerPlayerEntity p) {
        PState s = st(p);
        sendStateTo(p, p, s);
        for (ServerPlayerEntity o : PlayerLookup.tracking(p)) sendStateTo(o, p, s);
    }
}
