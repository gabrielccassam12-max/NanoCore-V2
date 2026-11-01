package com.nanocore.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved with the player forever (kept after death): installed core, evolution stage (0..3 = MK-I..MK-IV),
 * energy and the learning counters (0 blade, 1 cannon, 2 shield, 3 flight, 4 explosions).
 */
public record NanoData(boolean installed, int stage, int energy, List<Integer> learn) {
    public static final NanoData DEFAULT = new NanoData(false, 0, 0, List.of(0, 0, 0, 0, 0));
    public static final int[] MAX_ENERGY = {100, 150, 220, 320};
    public static final int[] LEARN_STEPS = {40, 140, 360};
    public static final String[] STAGE_NAMES = {"PROTOTYPE", "COMBAT", "ADVANCED", "SINGULARITY"};
    public static final String[] STAGE_TAGS = {"MK-I", "MK-II", "MK-III", "MK-IV"};

    public static final Codec<NanoData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.fieldOf("installed").forGetter(NanoData::installed),
            Codec.INT.fieldOf("stage").forGetter(NanoData::stage),
            Codec.INT.fieldOf("energy").forGetter(NanoData::energy),
            Codec.INT.listOf().fieldOf("learn").forGetter(NanoData::learn)
    ).apply(i, NanoData::new));

    public int max() {
        return MAX_ENERGY[Math.max(0, Math.min(3, stage))];
    }

    public int count(int idx) {
        return idx >= 0 && idx < learn.size() ? learn.get(idx) : 0;
    }

    public static int levelOf(int count) {
        int l = 0;
        for (int s : LEARN_STEPS) if (count >= s) l++;
        return l;
    }

    public int level(int idx) {
        return levelOf(count(idx));
    }

    public NanoData withInstalled(boolean v) { return new NanoData(v, stage, energy, learn); }
    public NanoData withStage(int v) { return new NanoData(installed, Math.max(0, Math.min(3, v)), energy, learn); }
    public NanoData withEnergy(int v) { return new NanoData(installed, stage, Math.max(0, Math.min(max(), v)), learn); }

    public NanoData addLearn(int idx, int n) {
        List<Integer> l = new ArrayList<>(learn);
        while (l.size() < 5) l.add(0);
        l.set(idx, l.get(idx) + n);
        return new NanoData(installed, stage, energy, l);
    }

    public NanoData withLearn(List<Integer> l) { return new NanoData(installed, stage, energy, l); }
}
