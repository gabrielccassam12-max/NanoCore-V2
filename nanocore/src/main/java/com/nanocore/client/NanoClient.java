package com.nanocore.client;

import com.nanocore.net.ActionPayload;
import com.nanocore.net.StatePayload;
import com.nanocore.net.SyncPayload;
import com.nanocore.core.NanoManager;
import com.nanocore.particle.CubeProfile;
import com.nanocore.particle.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.SimpleParticleType;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

public class NanoClient implements ClientModInitializer {
    private static final String CAT = "key.categories.nanocore";
    public static KeyBinding ARMOR, BLADE, CANNON, SHIELD, WINGS, PRIMARY, CHARGE, SPECIAL, MENU;
    public static final KeyBinding[] ABILITY = new KeyBinding[5];
    private static boolean charging = false;

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.nanocore." + name, InputUtil.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        // nano cube particles
        for (Map.Entry<String, SimpleParticleType> en : ModParticles.TYPES.entrySet()) {
            CubeProfile pf = ModParticles.PROFILES.get(en.getKey());
            ParticleFactoryRegistry.getInstance().register(en.getValue(), sprites ->
                    (ParticleFactory<SimpleParticleType>) (type, world, x, y, z, vx, vy, vz) ->
                            new CubeParticle(world, x, y, z, vx, vy, vz, sprites, pf));
        }

        // the suit and weapons drawn on every player
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer r) helper.register(new NanoFeature(r));
        });

        ARMOR = key("armor", GLFW.GLFW_KEY_V);
        BLADE = key("blade", GLFW.GLFW_KEY_Z);
        CANNON = key("cannon", GLFW.GLFW_KEY_X);
        SHIELD = key("shield", GLFW.GLFW_KEY_C);
        WINGS = key("wings", GLFW.GLFW_KEY_B);
        PRIMARY = key("primary", GLFW.GLFW_KEY_R);
        CHARGE = key("charge", GLFW.GLFW_KEY_F);
        SPECIAL = key("special", GLFW.GLFW_KEY_G);
        ABILITY[0] = key("swarm", GLFW.GLFW_KEY_H);
        ABILITY[1] = key("disassembly", GLFW.GLFW_KEY_J);
        ABILITY[2] = key("reconstruction", GLFW.GLFW_KEY_K);
        ABILITY[3] = key("overdrive", GLFW.GLFW_KEY_L);
        ABILITY[4] = key("adaptive", GLFW.GLFW_KEY_N);
        MENU = key("menu", GLFW.GLFW_KEY_M);

        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.ID, (payload, context) -> ClientData.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(StatePayload.ID, (payload, context) -> NanoVis.apply(payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientData.set(null);
            NanoVis.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientData.clock++;
            NanoVis.tick(client);
            if (client.player == null) return;
            if (client.currentScreen != null) {
                if (charging) {
                    send(NanoManager.RELEASE, 0);
                    charging = false;
                }
                return;
            }
            while (ARMOR.wasPressed()) send(NanoManager.ARMOR, 0);
            while (BLADE.wasPressed()) send(NanoManager.FORM, 1);
            while (CANNON.wasPressed()) send(NanoManager.FORM, 2);
            while (SHIELD.wasPressed()) send(NanoManager.FORM, 3);
            while (WINGS.wasPressed()) send(NanoManager.FORM, 4);
            while (PRIMARY.wasPressed()) send(NanoManager.PRIMARY, 0);
            if (PRIMARY.isPressed() && client.player.age % 3 == 0) send(NanoManager.PRIMARY, 0);
            while (SPECIAL.wasPressed()) send(NanoManager.SPECIAL, 0);
            for (int i = 0; i < ABILITY.length; i++) {
                while (ABILITY[i].wasPressed()) send(NanoManager.ABILITY, i);
            }
            if (CHARGE.isPressed()) {
                send(NanoManager.CHARGE, 0);
                charging = true;
            } else if (charging) {
                send(NanoManager.RELEASE, 0);
                charging = false;
            }
            while (MENU.wasPressed()) client.setScreen(new NanoScreen());
        });

        HudRenderCallback.EVENT.register(NanoHud::render);
    }

    private static void send(int action, int arg) {
        ClientPlayNetworking.send(new ActionPayload(action, arg));
    }
}
