package com.nanocore.client;

import com.nanocore.net.SyncPayload;

/** Latest HUD data from the server; cooldowns become client-tick deadlines. */
public final class ClientData {
    private ClientData() {}

    public static SyncPayload last;
    public static long[] cdEnd = new long[8];
    public static long odEnd, adEnd;
    public static float shownEnergy;
    public static long clock;

    public static void set(SyncPayload p) {
        last = p;
        if (p == null) return;
        for (int i = 0; i < cdEnd.length && i < p.cd().length; i++) cdEnd[i] = clock + p.cd()[i];
        odEnd = clock + p.overdriveLeft();
        adEnd = clock + p.adaptiveLeft();
    }
}
