package com.nanocore.core;

/** Per-player runtime state (not saved): suit on/off, equipped form, cooldowns, combo and charge. */
public final class PState {
    public boolean armor = false;
    public Form form = Form.NONE;
    public long[] cd = new long[Ability.values().length];
    public long nextPrimary = 0;
    public long lastCombo = 0;
    public int combo = 0;
    public int charge = 0;
    public long chargeSeen = -100;
    public float shieldStored = 0f;
    public long overdriveUntil = 0;
    public long adaptiveUntil = 0;
    public String adaptiveType = "";
    public String lastType = "";
    public boolean locked = false;
    public boolean grantedFlight = false;
    public boolean flying = false;
    public boolean beaming = false;
    public int rebuild = 0;
    public long lastHitFx = 0;
    public int lastSent = Integer.MIN_VALUE;
}
