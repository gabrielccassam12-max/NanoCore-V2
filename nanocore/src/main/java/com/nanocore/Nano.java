package com.nanocore;

import com.nanocore.core.NanoConfig;
import com.nanocore.core.NanoManager;
import com.nanocore.item.ModItems;
import com.nanocore.net.ActionPayload;
import com.nanocore.net.StatePayload;
import com.nanocore.net.SyncPayload;
import com.nanocore.particle.ModParticles;
import com.nanocore.util.Effects;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Nano implements ModInitializer {
    public static final String ID = "nanocore";
    public static final Logger LOG = LoggerFactory.getLogger(ID);

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }

    @Override
    public void onInitialize() {
        NanoConfig.load();
        ModParticles.init();
        ModItems.init();
        Effects.init();
        NanoManager.init();

        PayloadTypeRegistry.playC2S().register(ActionPayload.ID, ActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncPayload.ID, SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StatePayload.ID, StatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.ID, (payload, context) ->
                NanoManager.action(context.player(), payload.action(), payload.arg()));
        LOG.info("NANOCORE loaded.");
    }
}
