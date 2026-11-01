package com.nanocore.core;

import net.minecraft.text.Text;

/** The five independent abilities (keys H J K L N). */
public enum Ability {
    SWARM(1, 35, 400),
    DISASSEMBLY(1, 30, 300),
    RECONSTRUCTION(0, 40, 600),
    OVERDRIVE(2, 60, 1200),
    ADAPTIVE(2, 25, 900);

    public final int minStage;
    public final int cost;
    public final int cooldown;

    Ability(int minStage, int cost, int cooldown) {
        this.minStage = minStage;
        this.cost = cost;
        this.cooldown = cooldown;
    }

    public Text displayName() {
        return Text.translatable("ability.nanocore." + (ordinal() + 1));
    }
}
