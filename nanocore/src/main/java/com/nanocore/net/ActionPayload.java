package com.nanocore.net;

import com.nanocore.Nano;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Client -> server key presses (see NanoManager for the action numbers). */
public record ActionPayload(int action, int arg) implements CustomPayload {
    public static final CustomPayload.Id<ActionPayload> ID = new CustomPayload.Id<>(Nano.id("action"));
    public static final PacketCodec<RegistryByteBuf, ActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ActionPayload::action,
            PacketCodecs.VAR_INT, ActionPayload::arg,
            ActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
