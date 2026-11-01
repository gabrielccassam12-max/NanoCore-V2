package com.nanocore.client;

import com.nanocore.particle.CubeProfile;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A real 3D cube (not a flat billboard): tumbles in space, glows (full bright), translucent, with a hotter
 * inner cube. Colour goes from young to old along the profile palette.
 */
public class CubeParticle extends SpriteBillboardParticle {
    private final CubeProfile pf;
    private float size;
    private float rx, ry, rz, prx, pry, prz, srx, sry, srz;
    private final float tint;

    public CubeParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz,
                        SpriteProvider sprites, CubeProfile pf) {
        super(world, x, y, z);
        this.pf = pf;
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.maxAge = pf.life() + this.random.nextInt(pf.lifeVar() + 1);
        this.size = Math.max(0.03f, pf.size() + (this.random.nextFloat() - 0.5f) * 2f * pf.sizeVar());
        this.rx = this.random.nextFloat() * 6.2832f;
        this.ry = this.random.nextFloat() * 6.2832f;
        this.rz = this.random.nextFloat() * 6.2832f;
        float sp = (float) Math.toRadians(pf.spin());
        this.srx = (this.random.nextFloat() - 0.5f) * 2f * sp;
        this.sry = (this.random.nextFloat() - 0.5f) * 2f * sp;
        this.srz = (this.random.nextFloat() - 0.5f) * 2f * sp;
        this.prx = rx;
        this.pry = ry;
        this.prz = rz;
        this.tint = this.random.nextFloat();
        this.gravityStrength = pf.gravity();
        this.collidesWithWorld = false;
        this.setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        this.prevPosX = this.x;
        this.prevPosY = this.y;
        this.prevPosZ = this.z;
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }
        this.velocityY -= 0.04 * this.gravityStrength;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= pf.drag();
        this.velocityY *= pf.drag();
        this.velocityZ *= pf.drag();
        this.prx = rx;
        this.pry = ry;
        this.prz = rz;
        this.rx += srx;
        this.ry += sry;
        this.rz += srz;
        this.size *= pf.shrink();
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    private static float mix(int a, int b, float t, int shift) {
        float fa = ((a >> shift) & 255) / 255f, fb = ((b >> shift) & 255) / 255f;
        return fa + (fb - fa) * t;
    }

    @Override
    public void buildGeometry(VertexConsumer vc, Camera camera, float td) {
        Vec3d cam = camera.getPos();
        float px = (float) (MathHelper.lerp((double) td, this.prevPosX, this.x) - cam.x);
        float py = (float) (MathHelper.lerp((double) td, this.prevPosY, this.y) - cam.y);
        float pz = (float) (MathHelper.lerp((double) td, this.prevPosZ, this.z) - cam.z);

        float t = Math.min(1f, (this.age + td) / (float) this.maxAge);
        float r, g, b;
        if (t < 0.5f) {
            float k = t / 0.5f;
            r = mix(pf.c0(), pf.c1(), k, 16); g = mix(pf.c0(), pf.c1(), k, 8); b = mix(pf.c0(), pf.c1(), k, 0);
        } else {
            float k = (t - 0.5f) / 0.5f;
            r = mix(pf.c1(), pf.c2(), k, 16); g = mix(pf.c1(), pf.c2(), k, 8); b = mix(pf.c1(), pf.c2(), k, 0);
        }
        float shade = 0.9f + 0.2f * tint; // each cube is a little different
        r = Math.min(1f, r * shade);
        g = Math.min(1f, g * shade);
        b = Math.min(1f, b * shade);
        float a = pf.alpha() * (t < 0.6f ? 1f : Math.max(0f, 1f - (t - 0.6f) / 0.4f));

        Quaternionf q = new Quaternionf().rotationXYZ(
                MathHelper.lerp(td, prx, rx), MathHelper.lerp(td, pry, ry), MathHelper.lerp(td, prz, rz));
        float half = this.size * 0.5f;
        cube(vc, q, px, py, pz, half, pf.flat(), r, g, b, a);
        if (pf.inner()) {
            cube(vc, q, px, py, pz, half * 0.55f, pf.flat(),
                    r + (1f - r) * 0.55f, g + (1f - g) * 0.55f, b + (1f - b) * 0.55f, Math.min(1f, a + 0.25f));
        }
    }

    private static final int[][] FACES = {
            {0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}
    };

    private void cube(VertexConsumer vc, Quaternionf q, float px, float py, float pz, float h, float flat,
                      float r, float g, float b, float a) {
        float[][] c = new float[8][3];
        Vector3f v = new Vector3f();
        for (int i = 0; i < 8; i++) {
            v.set((i & 4) != 0 ? h : -h, (i & 2) != 0 ? h * flat : -h * flat, (i & 1) != 0 ? h : -h);
            q.transform(v);
            c[i][0] = v.x + px;
            c[i][1] = v.y + py;
            c[i][2] = v.z + pz;
        }
        float u0 = getMinU(), u1 = getMaxU(), v0 = getMinV(), v1 = getMaxV();
        float[] us = {u0, u1, u1, u0}, vs = {v0, v0, v1, v1};
        int light = 0xF000F0;
        for (int[] f : FACES) {
            for (int k = 0; k < 4; k++) put(vc, c[f[k]], us[k], vs[k], r, g, b, a, light);
            for (int k = 3; k >= 0; k--) put(vc, c[f[k]], us[k], vs[k], r, g, b, a, light); // back side too
        }
    }

    private static void put(VertexConsumer vc, float[] p, float u, float v, float r, float g, float b, float a, int light) {
        vc.vertex(p[0], p[1], p[2]).texture(u, v).color(r, g, b, a).light(light);
    }
}
