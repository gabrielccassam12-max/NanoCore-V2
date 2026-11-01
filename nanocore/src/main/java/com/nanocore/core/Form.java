package com.nanocore.core;

/** The four transformations. minStage: 0 = MK-I ... */
public enum Form {
    NONE(0, false),
    BLADE(0, false),
    CANNON(1, true),
    SHIELD(1, true),
    WINGS(2, true);

    public final int minStage;
    /** Advanced forms stop working while the core is out of energy. */
    public final boolean advanced;

    Form(int minStage, boolean advanced) {
        this.minStage = minStage;
        this.advanced = advanced;
    }

    public static Form of(int i) {
        Form[] v = values();
        return i >= 0 && i < v.length ? v[i] : NONE;
    }
}
