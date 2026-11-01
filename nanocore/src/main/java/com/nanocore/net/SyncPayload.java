package com.nanocore.net;

import com.nanocore.Nano;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/** Server -> owner: everything the HUD and the menu show. cd = remaining cooldown ticks per ability. */
public record SyncPayload(boolean installed, int stage, int energy, int max, boolean locked, boolean armor, int form,
                          List<Integer> learn, int[] cd, int overdriveLeft, int adaptiveLeft, int charge,
                          int shield) implements CustomPayload {
    public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(Nano.id("sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.of(SyncPayload::write, SyncPayload::read);

    private static void write(SyncPayload p, RegistryByteBuf b) {
        b.writeBoolean(p.installed());
        b.writeVarInt(p.stage());
        b.writeVarInt(p.energy());
        b.writeVarInt(p.max());
        b.writeBoolean(p.locked());
        b.writeBoolean(p.armor());
        b.writeVarInt(p.form());
        b.writeVarInt(p.learn().size());
        for (int v : p.learn()) b.writeVarInt(v);
        b.writeVarInt(p.cd().length);
        for (int v : p.cd()) b.writeVarInt(v);
        b.writeVarInt(p.overdriveLeft());
        b.writeVarInt(p.adaptiveLeft());
        b.writeVarInt(p.charge());
        b.writeVarInt(p.shield());
    }

    private static SyncPayload read(RegistryByteBuf b) {
        boolean installed = b.readBoolean();
        int stage = b.readVarInt();
        int energy = b.readVarInt();
        int max = b.readVarInt();
        boolean locked = b.readBoolean();
        boolean armor = b.readBoolean();
        int form = b.readVarInt();
        int n = b.readVarInt();
        List<Integer> learn = new ArrayList<>();
        for (int i = 0; i < n; i++) learn.add(b.readVarInt());
        int m = b.readVarInt();
        int[] cd = new int[m];
        for (int i = 0; i < m; i++) cd[i] = b.readVarInt();
        int od = b.readVarInt();
        int ad = b.readVarInt();
        int ch = b.readVarInt();
        int sh = b.readVarInt();
        return new SyncPayload(installed, stage, energy, max, locked, armor, form, learn, cd, od, ad, ch, sh);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
