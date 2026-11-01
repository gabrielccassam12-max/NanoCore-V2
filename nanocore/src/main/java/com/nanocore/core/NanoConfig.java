package com.nanocore.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** config/nanocore.json - multipliers for energy costs, cooldowns, damage and regeneration. */
public final class NanoConfig {
    public double costMultiplier = 1.0;
    public double cooldownMultiplier = 1.0;
    public double damageMultiplier = 1.0;
    public double regenMultiplier = 1.0;
    public boolean allowPvp = true;

    public static NanoConfig INSTANCE = new NanoConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("nanocore.json");
        try {
            if (Files.exists(p)) {
                NanoConfig c = GSON.fromJson(Files.readString(p), NanoConfig.class);
                if (c != null) INSTANCE = c;
            }
            Files.writeString(p, GSON.toJson(INSTANCE));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int cost(int base) {
        return Math.max(0, (int) Math.round(base * INSTANCE.costMultiplier));
    }

    public static int cooldown(int base) {
        return Math.max(1, (int) Math.round(base * INSTANCE.cooldownMultiplier));
    }
}
