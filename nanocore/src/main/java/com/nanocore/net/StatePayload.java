package com.nanocore.net;

import com.nanocore.Nano;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Server -> everyone nearby: how a player looks. stage -1 = no core. mask: 1 suit, 2 overdrive, 4 flying.
 * rebuild: counter that restarts the "armor forms" animation when it changes.
 */
public record StatePayload(int entityId, int stage, int mask, int form, int charge, int rebuild) implements CustomPayload {
    public static final CustomPayload.Id<StatePayload> ID = new CustomPayload.Id<>(Nano.id("state"));
    public static final PacketCodec<RegistryByteBuf, StatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, StatePayload::entityId,
            PacketCodecs.INTEGER, StatePayload::stage,
            PacketCodecs.VAR_INT, StatePayload::mask,
            PacketCodecs.VAR_INT, StatePayload::form,
            PacketCodecs.VAR_INT, StatePayload::charge,
            PacketCodecs.VAR_INT, StatePayload::rebuild,
            StatePayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
