package com.nanocore.client;

import com.nanocore.Nano;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.function.Supplier;

/**
 * The living-metal suit: heavy black combat armour with energy-blue light lines, a slatted visor,
 * big pauldrons, and (Nano Wings) two long struts carrying ring thrusters that burn blue jets.
 * Parts grow from nothing while the nanobots assemble them (feet -> head).
 */
public class NanoFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier WHITE = Nano.id("textures/misc/white.png");

    private static final int BLACK = 0x13161B, DARK = 0x242A35, SILVER = 0xA9B5C6, STEEL = 0x66738A;
    private static final int[] GLOW = {0x38C8FF, 0x2E9BFF, 0x63E6FF, 0xB6F0FF};

    // per-render state (rendering is single threaded)
    private Supplier<VertexConsumer> S, G;
    private int L, GC, PULSE;

    public NanoFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
    }

    private static float step(float p, float a, float b) {
        float t = MathHelper.clamp((p - a) / (b - a), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    // ---- tiny helpers: solid plate / glowing light
    private void sb(MatrixStack.Entry e, float x0, float y0, float z0, float x1, float y1, float z1, float k, int rgb) {
        Boxes.boxK(e, S, x0, y0, z0, x1, y1, z1, k, rgb, 255, L);
    }

    private void gb(MatrixStack.Entry e, float x0, float y0, float z0, float x1, float y1, float z1, float k, int a) {
        Boxes.boxK(e, G, x0, y0, z0, x1, y1, z1, k, GC, a, Boxes.FULL_LIGHT);
    }

    /** Box mirrored by side (s = +1 keeps x, s = -1 flips it) so one description builds both wings. */
    private void wsb(MatrixStack.Entry e, int s, float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        if (s > 0) Boxes.box(e, S, x0, y0, z0, x1, y1, z1, rgb, 255, L);
        else Boxes.box(e, S, -x1, y0, z0, -x0, y1, z1, rgb, 255, L);
    }

    private void wgb(MatrixStack.Entry e, int s, float x0, float y0, float z0, float x1, float y1, float z1, int a) {
        if (s > 0) Boxes.box(e, G, x0, y0, z0, x1, y1, z1, GC, a, Boxes.FULL_LIGHT);
        else Boxes.box(e, G, -x1, y0, z0, -x0, y1, z1, GC, a, Boxes.FULL_LIGHT);
    }

    /** Octagonal slab (inradius r) centred on (cx, cz), from y0 to y1. */
    private void octa(MatrixStack m, Supplier<VertexConsumer> vc, float cx, float cz, float y0, float y1, float r, int rgb, int a, int light) {
        Boxes.box(m.peek(), vc, cx - r, y0, cz - r, cx + r, y1, cz + r, rgb, a, light);
        m.push();
        m.translate(cx / 16f, 0, cz / 16f);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45f));
        Boxes.box(m.peek(), vc, -r, y0 + 0.01f, -r, r, y1 - 0.01f, r, rgb, a, light);
        m.pop();
    }

    /** Octagonal ring of thickness t (inradius r). */
    private void ringOct(MatrixStack m, Supplier<VertexConsumer> vc, float cx, float cz, float y0, float y1, float r, float t, int rgb, int a, int light) {
        float half = r * 0.4142f + 0.06f;
        for (int j = 0; j < 8; j++) {
            double an = j * Math.PI / 4;
            m.push();
            m.translate((cx + (float) Math.cos(an) * r) / 16f, 0, (cz + (float) Math.sin(an) * r) / 16f);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-j * 45f));
            Boxes.box(m.peek(), vc, -t / 2, y0, -half, t / 2, y1, half, rgb, a, light);
            m.pop();
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity entity,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        NanoVis.Vis v = NanoVis.get(entity.getId());
        if (v == null || v.stage < 0 || entity.isInvisible()) return;
        float ap = MathHelper.lerp(tickDelta, v.armorPrev, v.armorP);
        if (ap <= 0.01f) return;

        // the buffer is fetched again for every box: grabbing a second layer closes the first one's buffer
        RenderLayer solidLayer = RenderLayer.getEntityCutoutNoCull(WHITE), glowLayer = RenderLayer.getEntityTranslucentEmissive(WHITE);
        S = () -> vcp.getBuffer(solidLayer);
        G = () -> vcp.getBuffer(glowLayer);
        L = light;
        PlayerEntityModel<AbstractClientPlayerEntity> m = getContextModel();
        float age = entity.age + tickDelta;
        int stage = v.stage;
        GC = v.overdrive() ? 0xDFFAFF : GLOW[Math.min(3, stage)];
        PULSE = (int) (205 + 50 * Math.sin(age * 0.2));

        float kLegs = step(ap, 0.00f, 0.35f), kBody = step(ap, 0.25f, 0.60f), kArms = step(ap, 0.45f, 0.80f), kHead = step(ap, 0.65f, 1.00f);

        // ------------------------------------------------------------ legs
        for (ModelPart leg : new ModelPart[]{m.rightLeg, m.leftLeg}) {
            matrices.push();
            leg.rotate(matrices);
            MatrixStack.Entry e = matrices.peek();
            sb(e, -2.9f, -0.4f, -2.9f, 2.9f, 6.4f, 2.9f, kLegs, BLACK);                  // thigh
            sb(e, -2.5f, 0.6f, -3.3f, 2.5f, 4.6f, -2.9f, kLegs, DARK);                   // thigh plate
            gb(e, -0.25f, 0.8f, -3.38f, 0.25f, 4.4f, -3.3f, kLegs, PULSE);
            gb(e, -3.05f, 0.4f, -0.5f, -2.9f, 5.8f, 0.5f, kLegs, 190);
            gb(e, 2.9f, 0.4f, -0.5f, 3.05f, 5.8f, 0.5f, kLegs, 190);
            sb(e, -2.8f, 5.4f, -3.9f, 2.8f, 8.2f, -2.8f, kLegs, DARK);                   // knee guard
            sb(e, -2.0f, 5.9f, -4.2f, 2.0f, 7.5f, -3.9f, kLegs, SILVER);
            gb(e, -1.3f, 6.3f, -4.3f, 1.3f, 7.1f, -4.2f, kLegs, 255);                    // knee light
            sb(e, -2.7f, 6.8f, -2.8f, 2.7f, 10.9f, 2.8f, kLegs, BLACK);                  // shin
            sb(e, -2.3f, 8.0f, -3.3f, 2.3f, 10.6f, -2.8f, kLegs, DARK);
            gb(e, -0.25f, 8.2f, -3.38f, 0.25f, 10.4f, -3.3f, kLegs, 220);
            sb(e, -3.1f, 10.4f, -4.2f, 3.1f, 12.7f, 3.1f, kLegs, BLACK);                  // boot
            sb(e, -2.7f, 11.0f, -4.7f, 2.7f, 12.7f, -4.2f, kLegs, SILVER);
            gb(e, -3.2f, 11.2f, -1.2f, -3.1f, 11.8f, 1.2f, kLegs, 220);
            gb(e, 3.1f, 11.2f, -1.2f, 3.2f, 11.8f, 1.2f, kLegs, 220);
            matrices.pop();
        }

        // ------------------------------------------------------------ body
        matrices.push();
        m.body.rotate(matrices);
        {
            MatrixStack.Entry e = matrices.peek();
            sb(e, -4.3f, 6.6f, -2.8f, 4.3f, 12.4f, 2.8f, kBody, DARK);                  // abdomen
            for (int i = 0; i < 3; i++) gb(e, -3.4f, 7.6f + i * 1.7f, -2.9f, 3.4f, 7.9f + i * 1.7f, -2.82f, kBody, 170);
            sb(e, -4.6f, 6.6f, -3.1f, 4.6f, 7.8f, 3.1f, kBody, SILVER);                  // belt
            gb(e, -0.9f, 6.8f, -3.2f, 0.9f, 7.6f, -3.1f, kBody, PULSE);
            sb(e, -5.3f, -0.6f, -3.2f, 5.3f, 7.0f, 3.2f, kBody, BLACK);                  // chest block
            sb(e, -4.4f, 0.2f, -3.7f, 4.4f, 6.2f, -3.2f, kBody, DARK);                   // chest plate
            sb(e, -3.4f, 0.0f, -3.9f, 3.4f, 2.6f, -3.7f, kBody, STEEL);                  // upper plate
            gb(e, -0.3f, 0.6f, -4.0f, 0.3f, 5.8f, -3.9f, kBody, PULSE);                  // emblem: cross + core
            gb(e, -2.4f, 3.2f, -4.0f, 2.4f, 3.8f, -3.9f, kBody, PULSE);
            gb(e, -0.9f, 2.4f, -4.1f, 0.9f, 4.6f, -4.0f, kBody, 255);
            gb(e, -4.45f, 0.4f, -3.75f, -4.3f, 6.0f, -3.25f, kBody, 200);               // edge lines
            gb(e, 4.3f, 0.4f, -3.75f, 4.45f, 6.0f, -3.25f, kBody, 200);
            gb(e, -5.4f, 0.4f, -1.3f, -5.3f, 6.4f, 1.3f, kBody, 190);
            gb(e, 5.3f, 0.4f, -1.3f, 5.4f, 6.4f, 1.3f, kBody, 190);
            // back unit that carries the wings
            sb(e, -4.4f, 0.4f, 3.2f, 4.4f, 9.6f, 5.4f, kBody, DARK);
            sb(e, -2.6f, 1.2f, 5.4f, 2.6f, 7.6f, 6.2f, kBody, BLACK);
            gb(e, -0.5f, 1.8f, 6.2f, 0.5f, 7.0f, 6.35f, kBody, PULSE);
            gb(e, -4.55f, 1.4f, 3.6f, -4.4f, 3.4f, 5.0f, kBody, 230);
            gb(e, 4.4f, 1.4f, 3.6f, 4.55f, 3.4f, 5.0f, kBody, 230);
            if (stage >= 3) {
                for (int i = 0; i < 3; i++) sb(e, -0.8f, 1.5f + i * 2.8f, 6.2f, 0.8f, 3.2f + i * 2.8f, 8.4f - i * 0.8f, kBody, SILVER);
            }
            // orbiting nano fragments around the core
            if (ap > 0.9f) {
                int n = 3 + stage * 2;
                for (int i = 0; i < n; i++) {
                    double a = age * 0.09 + i * Math.PI * 2 / n;
                    float ox = (float) Math.cos(a) * 10f, oz = (float) Math.sin(a) * 10f;
                    float oy = 5.5f + (float) Math.sin(age * 0.07 + i * 1.7) * 4.5f;
                    Boxes.box(e, S, ox - 0.7f, oy - 0.7f, oz - 0.7f, ox + 0.7f, oy + 0.7f, oz + 0.7f, SILVER, 255, L);
                    Boxes.box(e, G, ox - 0.4f, oy - 0.4f, oz - 0.4f, ox + 0.4f, oy + 0.4f, oz + 0.4f, GC, 255, Boxes.FULL_LIGHT);
                }
            }
        }
        float wk = MathHelper.lerp(tickDelta, v.formPrev[4], v.formP[4]);
        if (wk > 0.02f) wings(matrices, wk, age, v.flying());
        matrices.pop();

        // ------------------------------------------------------------ head
        matrices.push();
        m.head.rotate(matrices);
        {
            MatrixStack.Entry e = matrices.peek();
            sb(e, -5.0f, -9.4f, -5.0f, 5.0f, 0.5f, 5.0f, kHead, BLACK);                  // shell
            sb(e, -5.3f, -7.4f, -5.3f, 5.3f, -6.4f, 5.3f, kHead, DARK);                  // brow band
            sb(e, -4.0f, -6.6f, -5.6f, 4.0f, -2.4f, -5.0f, kHead, DARK);                 // visor housing
            for (int i = 0; i < 4; i++) gb(e, -3.5f, -6.1f + i * 0.95f, -5.7f, 3.5f, -5.6f + i * 0.95f, -5.6f, kHead, 255); // glowing slats
            gb(e, -4.15f, -6.7f, -5.65f, -3.95f, -2.3f, -5.55f, kHead, 235);
            gb(e, 3.95f, -6.7f, -5.65f, 4.15f, -2.3f, -5.55f, kHead, 235);
            sb(e, -3.2f, -2.2f, -5.6f, 3.2f, 0.5f, -5.0f, kHead, STEEL);                 // jaw guard
            gb(e, -1.6f, -1.8f, -5.7f, -1.0f, 0.0f, -5.6f, kHead, 200);
            gb(e, 1.0f, -1.8f, -5.7f, 1.6f, 0.0f, -5.6f, kHead, 200);
            sb(e, -5.5f, -6.2f, -2.6f, -5.0f, -1.0f, 2.6f, kHead, SILVER);               // cheek / ear plates
            sb(e, 5.0f, -6.2f, -2.6f, 5.5f, -1.0f, 2.6f, kHead, SILVER);
            gb(e, -5.6f, -4.6f, -1.0f, -5.5f, -3.0f, 1.0f, kHead, 230);
            gb(e, 5.5f, -4.6f, -1.0f, 5.6f, -3.0f, 1.0f, kHead, 230);
            sb(e, -1.0f, -10.8f, -4.6f, 1.0f, -9.4f, 4.6f, kHead, DARK);                 // crest
            sb(e, -3.2f, -6.0f, 5.0f, 3.2f, -1.0f, 6.0f, kHead, DARK);                   // back plate
            gb(e, -0.4f, -4.6f, 6.0f, 0.4f, -2.2f, 6.1f, kHead, PULSE);
            if (stage >= 2) {
                sb(e, -5.8f, -9.0f, -0.8f, -5.0f, -6.0f, 0.8f, kHead, STEEL);
                sb(e, 5.0f, -9.0f, -0.8f, 5.8f, -6.0f, 0.8f, kHead, STEEL);
            }
            if (stage >= 3) {
                sb(e, 2.6f, -12.4f, -1.8f, 3.8f, -9.4f, 0.6f, kHead, DARK);
                sb(e, -3.8f, -12.4f, -1.8f, -2.6f, -9.4f, 0.6f, kHead, DARK);
                gb(e, 2.7f, -12.8f, -1.6f, 3.7f, -12.4f, 0.4f, kHead, 255);
                gb(e, -3.7f, -12.8f, -1.6f, -2.7f, -12.4f, 0.4f, kHead, 255);
            }
        }
        matrices.pop();

        // ------------------------------------------------------------ arms (+ blades / cannon / shield)
        float bk = MathHelper.lerp(tickDelta, v.formPrev[1], v.formP[1]);
        float ck = MathHelper.lerp(tickDelta, v.formPrev[2], v.formP[2]);
        float sk = MathHelper.lerp(tickDelta, v.formPrev[3], v.formP[3]);
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            ModelPart arm = right ? m.rightArm : m.leftArm;
            float cx = right ? -1f : 1f;
            float o = right ? -1f : 1f;
            matrices.push();
            arm.rotate(matrices);
            MatrixStack.Entry e = matrices.peek();
            // pauldron
            sb(e, cx - 3.9f + o * 0.8f, -4.0f, -3.9f, cx + 3.9f + o * 0.8f, 0.9f, 3.9f, kArms, BLACK);
            sb(e, cx - 3.4f + o * 1.1f, -5.1f, -3.3f, cx + 3.4f + o * 1.1f, -4.0f, 3.3f, kArms, DARK);
            sb(e, cx - 3.0f + o * 1.4f, -5.7f, -2.4f, cx + 3.0f + o * 1.4f, -5.1f, 2.4f, kArms, STEEL);
            gb(e, cx + o * 4.5f - 0.1f, -3.4f, -1.8f, cx + o * 4.5f + 0.1f + o * 0.35f, 0.0f, 1.8f, kArms, 230);
            gb(e, cx - 3.0f + o * 0.8f, -2.9f, -3.98f, cx + 3.0f + o * 0.8f, -2.5f, -3.9f, kArms, 200);
            if (stage >= 2) sb(e, cx + o * 4.2f - 0.7f, -6.6f, -0.7f, cx + o * 4.2f + 0.7f, -4.0f, 0.7f, kArms, SILVER); // pauldron spike
            // upper arm, elbow, bracer
            sb(e, cx - 2.7f, 0.6f, -2.7f, cx + 2.7f, 6.0f, 2.7f, kArms, BLACK);
            sb(e, cx - 3.1f, 5.0f, -3.4f, cx + 3.1f, 7.4f, 3.1f, kArms, DARK);
            gb(e, cx - 3.2f, 6.0f, -3.5f, cx + 3.2f, 6.4f, 3.2f, kArms, PULSE);
            sb(e, cx - 3.1f, 7.4f, -3.1f, cx + 3.1f, 10.6f, 3.1f, kArms, BLACK);
            sb(e, cx - 3.3f, 8.0f, -3.3f, cx + 3.3f, 8.9f, 3.3f, kArms, SILVER);
            gb(e, cx - 0.25f, 9.0f, -3.2f, cx + 0.25f, 10.4f, -3.1f, kArms, 230);
            sb(e, cx - 2.6f, 10.5f, -2.7f, cx + 2.6f, 12.5f, 2.7f, kArms, DARK);        // gauntlet
            gb(e, cx - 2.7f, 10.2f, -2.8f, cx + 2.7f, 10.5f, 2.8f, kArms, 200);

            if (bk > 0.02f) blade(matrices, cx, bk, age);
            if (right && ck > 0.02f) cannon(matrices, cx, ck, v.charge / 100f, age);
            if (!right && sk > 0.02f) shield(matrices, cx, sk, age);
            matrices.pop();
        }
    }

    // ------------------------------------------------------------------ weapons
    /** Curved energy blade: forged from the gauntlet, glowing edges, aura. */
    private void blade(MatrixStack m, float cx, float k, float age) {
        MatrixStack.Entry e = m.peek();
        sb(e, cx - 3.7f, 11.8f, -3.7f, cx + 3.7f, 13.2f, 3.7f, k, STEEL);          // root block
        sb(e, cx - 5.4f, 12.2f, -0.8f, cx + 5.4f, 13.4f, 0.8f, k, SILVER);         // cross guard
        gb(e, cx - 3.9f, 12.9f, -3.9f, cx + 3.9f, 13.2f, 3.9f, k, 255);
        float len = 32f * k;
        int segs = 9;
        float sl = len / segs;
        int fl = (int) (200 + 55 * Math.sin(age * 0.35));
        for (int i = 0; i < segs; i++) {
            float t = (float) i / segs;
            float y0 = 13.2f + i * sl;
            float w = 3.5f * (1f - t * 0.78f);
            float zc = -t * t * 4.2f;                       // sweeps forward like a scimitar
            Boxes.box(e, S, cx - w, y0, zc - 0.8f, cx + w, y0 + sl + 0.06f, zc + 0.8f, i % 2 == 0 ? SILVER : 0xC9D3E0, 255, L);
            Boxes.box(e, S, cx - w * 0.35f, y0, zc - 0.9f, cx + w * 0.35f, y0 + sl + 0.06f, zc + 0.9f, BLACK, 255, L);   // fuller
            Boxes.box(e, G, cx - w - 0.35f, y0, zc - 0.6f, cx - w + 0.25f, y0 + sl + 0.06f, zc + 0.6f, GC, fl, Boxes.FULL_LIGHT);
            Boxes.box(e, G, cx + w - 0.25f, y0, zc - 0.6f, cx + w + 0.35f, y0 + sl + 0.06f, zc + 0.6f, GC, fl, Boxes.FULL_LIGHT);
            Boxes.box(e, G, cx - w - 1.3f, y0, zc - 1.7f, cx + w + 1.3f, y0 + sl + 0.06f, zc + 1.7f, GC, 38, Boxes.FULL_LIGHT);
        }
        float tipY = 13.2f + len, tipZ = -4.2f * k;
        Boxes.box(e, S, cx - 0.5f, tipY, tipZ - 0.6f, cx + 0.5f, tipY + 2.2f * k, tipZ + 0.6f, SILVER, 255, L);
        Boxes.box(e, G, cx - 0.3f, tipY, tipZ - 0.3f, cx + 0.3f, tipY + 3.4f * k, tipZ + 0.3f, GC, 255, Boxes.FULL_LIGHT);
    }

    /** Heavy plasma cannon: armoured housing, coiled barrel, four-prong muzzle and a charge core. */
    private void cannon(MatrixStack m, float cx, float k, float charge, float age) {
        MatrixStack.Entry e = m.peek();
        sb(e, cx - 4.1f, 3.8f, -4.1f, cx + 4.1f, 11.4f, 4.1f, k, DARK);
        sb(e, cx - 5.3f, 5.0f, -1.0f, cx - 4.1f, 10.4f, 1.0f, k, SILVER);          // side fins
        sb(e, cx + 4.1f, 5.0f, -1.0f, cx + 5.3f, 10.4f, 1.0f, k, SILVER);
        sb(e, cx - 1.0f, 3.0f, -5.3f, cx + 1.0f, 9.0f, -4.1f, k, SILVER);           // top rail
        gb(e, cx - 4.25f, 5.3f, -4.25f, cx + 4.25f, 5.8f, 4.25f, k, PULSE);
        gb(e, cx - 4.25f, 9.2f, -4.25f, cx + 4.25f, 9.7f, 4.25f, k, PULSE);
        sb(e, cx - 2.9f, 11.4f, -2.9f, cx + 2.9f, 13.4f, 2.9f, k, STEEL);          // collar
        float bl = 15f * k;
        float tip = 13.4f + bl;
        sb(e, cx - 1.7f, 13.4f, -1.7f, cx + 1.7f, tip, 1.7f, 1f, BLACK);
        for (float sx : new float[]{-1f, 1f})
            for (float sz : new float[]{-1f, 1f})
                Boxes.box(e, S, cx + sx * 2.2f - 0.4f, 13.4f, sz * 2.2f - 0.4f, cx + sx * 2.2f + 0.4f, tip, sz * 2.2f + 0.4f, SILVER, 255, L);
        int coils = 4;
        for (int i = 0; i < coils; i++) {
            float y = 14.4f + i * 3.4f;
            if (y + 0.8f < tip) {
                int a = (int) (170 + 85 * Math.sin(age * 0.5 - i * 0.9) * (0.6 + charge));
                Boxes.box(e, G, cx - 2.6f, y, -2.6f, cx + 2.6f, y + 0.8f, 2.6f, GC, MathHelper.clamp(a, 80, 255), Boxes.FULL_LIGHT);
            }
        }
        // muzzle prongs
        for (float sx : new float[]{-1f, 1f}) {
            Boxes.box(e, S, cx + sx * 2.4f - 0.5f, tip - 0.5f, -0.9f, cx + sx * 2.4f + 0.5f, tip + 2.6f, 0.9f, SILVER, 255, L);
            Boxes.box(e, S, -0.9f + cx, tip - 0.5f, sx * 2.4f - 0.5f, cx + 0.9f, tip + 2.6f, sx * 2.4f + 0.5f, SILVER, 255, L);
        }
        float s = 1.1f + 2.4f * charge;
        int a = (int) (130 + 125 * charge);
        Boxes.box(e, G, cx - s, tip, -s, cx + s, tip + 0.6f + 2f * s, s, GC, a, Boxes.FULL_LIGHT);
        if (charge > 0.02f) {
            Boxes.box(e, G, cx - s * 1.6f, tip - 0.4f, -s * 1.6f, cx + s * 1.6f, tip + 0.8f + 3.2f * s, s * 1.6f, GC, 45, Boxes.FULL_LIGHT);
            for (int i = 0; i < 6; i++) {
                double an = age * 0.6 + i * Math.PI / 3;
                float rad = 6.5f - 4.5f * charge;
                float ox = cx + (float) Math.cos(an) * rad, oz = (float) Math.sin(an) * rad;
                float oy = tip + 1.5f + (float) Math.sin(age * 0.3 + i) * 1.2f;
                Boxes.box(e, G, ox - 0.55f, oy - 0.55f, oz - 0.55f, ox + 0.55f, oy + 0.55f, oz + 0.55f, 0xFFFFFF, 255, Boxes.FULL_LIGHT);
            }
        }
    }

    /** Large octagonal energy shield. */
    private void shield(MatrixStack m, float cx, float k, float age) {
        float R = 11.5f * k;
        int pulse = (int) (200 + 55 * Math.sin(age * 0.18));
        octa(m, S, cx, 0f, 12.8f, 14.4f, R, DARK, 255, L);
        ringOct(m, S, cx, 0f, 12.3f, 15.0f, R, 1.6f, SILVER, 255, L);
        octa(m, S, cx, 0f, 14.4f, 15.4f, R * 0.62f, STEEL, 255, L);
        ringOct(m, G, cx, 0f, 14.4f, 15.7f, R * 0.82f, 0.6f, GC, pulse, Boxes.FULL_LIGHT);
        ringOct(m, G, cx, 0f, 15.3f, 16.0f, R * 0.40f, 0.6f, GC, 255, Boxes.FULL_LIGHT);
        octa(m, G, cx, 0f, 15.4f, 17.2f, R * 0.2f, GC, 255, Boxes.FULL_LIGHT);
        for (int j = 0; j < 4; j++) {                                       // radial spokes
            m.push();
            m.translate(cx / 16f, 0, 0);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(j * 45f));
            Boxes.box(m.peek(), G, -R * 0.78f, 15.3f, -0.25f, R * 0.78f, 15.7f, 0.25f, GC, 190, Boxes.FULL_LIGHT);
            m.pop();
        }
        octa(m, G, cx, 0f, 11.6f, 16.0f, R + 1.6f, GC, 34, Boxes.FULL_LIGHT);   // energy field
        for (int i = 0; i < 4; i++) {
            double an = age * 0.12 + i * Math.PI / 2;
            float ox = cx + (float) Math.cos(an) * (R + 2.4f), oz = (float) Math.sin(an) * (R + 2.4f);
            Boxes.box(m.peek(), G, ox - 0.7f, 13.4f, oz - 0.7f, ox + 0.7f, 14.8f, oz + 0.7f, 0xFFFFFF, 255, Boxes.FULL_LIGHT);
        }
    }

    // ------------------------------------------------------------------ wings
    /**
     * Two long struts swept up and out from the back unit. Each carries three octagonal ring thrusters
     * (big, huge, small) with blue jets streaming down - the silhouette of the reference art.
     */
    private void wings(MatrixStack m, float k, float age, boolean flying) {
        float flap = flying ? (float) Math.sin(age * 0.3) * 5f : (float) Math.sin(age * 0.07) * 1.5f;
        float fold = (1f - k) * 70f;
        float ang = 38f + flap + fold;
        float[] pos = {10f, 21.5f, 33f};
        float[] rad = {6.2f, 8.4f, 4.6f};
        float jet = flying ? 1f : 0.3f;
        for (int s = -1; s <= 1; s += 2) {
            m.push();
            m.translate(s * 3.6f / 16f, 2.0f / 16f, 5.6f / 16f);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s * -10f));        // swept slightly back
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-s * ang));
            MatrixStack.Entry e = m.peek();
            float len = 40f * k;
            wsb(e, s, -2f, -2.4f, -2.4f, 3f, 2.4f, 2.4f, SILVER);                 // shoulder joint
            wsb(e, s, 3f, -1.5f, -1.3f, len, 1.5f, 1.3f, BLACK);                  // main spar
            wsb(e, s, 5f, 1.5f, -0.8f, len * 0.86f, 2.6f, 0.8f, DARK);            // lower spar
            wsb(e, s, 3f, -2.5f, -0.9f, len * 0.7f, -1.5f, 0.9f, DARK);           // upper rail
            wgb(e, s, 4f, -1.7f, -0.4f, len, -1.5f, 0.4f, 230);                   // light line
            wsb(e, s, len - 1f, -1.1f, -0.9f, len + 4f * k, 1.1f, 0.9f, STEEL);   // tip
            for (int i = 0; i < 3; i++) {
                float x = pos[i] * k, r = rad[i] * k;
                if (r < 0.5f) continue;
                wsb(e, s, x - 0.6f, 1.0f, -0.6f, x + 0.6f, 4.0f * k, 0.6f, DARK);    // post to the ring
                m.push();
                m.translate(s * x / 16f, 4.4f * k / 16f, 0);
                m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s * ang));      // undo: ring faces straight down
                thruster(m, r, age + i * 5f, jet);
                m.pop();
            }
            m.pop();
        }
    }

    private void thruster(MatrixStack m, float r, float age, float jet) {
        int pulse = (int) (215 + 40 * Math.sin(age * 0.45));
        octa(m, S, 0f, 0f, -1.1f, 1.3f, r * 0.94f, DARK, 255, L);               // body disc
        ringOct(m, S, 0f, 0f, -1.4f, 1.6f, r, 1.5f, BLACK, 255, L);             // outer frame
        ringOct(m, G, 0f, 0f, -1.7f, 1.9f, r * 0.9f, 0.7f, GC, pulse, Boxes.FULL_LIGHT);   // blue ring
        octa(m, G, 0f, 0f, 1.1f, 1.4f, r * 0.72f, GC, 110, Boxes.FULL_LIGHT);   // glowing intake
        octa(m, S, 0f, 0f, -1.9f, -1.2f, r * 0.3f, STEEL, 255, L);              // hub
        float flick = 0.85f + 0.15f * (float) Math.sin(age * 1.3);
        float len = (7f + r * 2.2f) * jet * flick;
        int slices = 5;
        for (int i = 0; i < slices; i++) {
            float y0 = 1.5f + len * i / slices, y1 = 1.5f + len * (i + 1) / slices + 0.05f;
            float fade = 1f - (float) i / slices;
            float wCore = r * (0.38f - 0.04f * i), wOut = r * (0.7f - 0.07f * i);
            Boxes.box(m.peek(), G, -wCore, y0, -wCore, wCore, y1, wCore, 0xFFFFFF, (int) (150 * fade * (0.4f + 0.6f * jet)), Boxes.FULL_LIGHT);
            Boxes.box(m.peek(), G, -wOut, y0, -wOut, wOut, y1, wOut, GC, (int) (60 * fade * (0.4f + 0.6f * jet)), Boxes.FULL_LIGHT);
        }
    }
}
