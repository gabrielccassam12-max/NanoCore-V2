package com.nanocore.client;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;

import java.util.function.Supplier;

/** Draws plain coloured boxes (sizes in model pixels, 16 px = 1 block) with the current matrix. */
public final class Boxes {
    private Boxes() {}

    public static final int FULL_LIGHT = 0xF000F0;

    public static void box(MatrixStack.Entry e, Supplier<VertexConsumer> sup, float x0, float y0, float z0, float x1, float y1, float z1,
                           int rgb, int alpha, int light) {
        VertexConsumer vc = sup.get();
        float s = 1f / 16f;
        x0 *= s; y0 *= s; z0 *= s; x1 *= s; y1 *= s; z1 *= s;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        q(e, vc, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0, 0, -1, r, g, b, alpha, light);
        q(e, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r, g, b, alpha, light);
        q(e, vc, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r, g, b, alpha, light);
        q(e, vc, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1, 0, 0, r, g, b, alpha, light);
        q(e, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r, g, b, alpha, light);
        q(e, vc, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, r, g, b, alpha, light);
    }

    /** Same box scaled around its own centre (used for "growing" parts). */
    public static void boxK(MatrixStack.Entry e, Supplier<VertexConsumer> vc, float x0, float y0, float z0, float x1, float y1, float z1,
                            float k, int rgb, int alpha, int light) {
        if (k <= 0.01f) return;
        float cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, cz = (z0 + z1) / 2;
        float hx = (x1 - x0) / 2 * k, hy = (y1 - y0) / 2 * k, hz = (z1 - z0) / 2 * k;
        box(e, vc, cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz, rgb, alpha, light);
    }

    private static void q(MatrixStack.Entry e, VertexConsumer vc,
                          float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz,
                          float nx, float ny, float nz, int r, int g, int b, int a, int light) {
        v(e, vc, ax, ay, az, 0, 0, nx, ny, nz, r, g, b, a, light);
        v(e, vc, bx, by, bz, 1, 0, nx, ny, nz, r, g, b, a, light);
        v(e, vc, cx, cy, cz, 1, 1, nx, ny, nz, r, g, b, a, light);
        v(e, vc, dx, dy, dz, 0, 1, nx, ny, nz, r, g, b, a, light);
    }

    private static void v(MatrixStack.Entry e, VertexConsumer vc, float x, float y, float z, float u, float vv,
                          float nx, float ny, float nz, int r, int g, int b, int a, int light) {
        vc.vertex(e, x, y, z).color(r, g, b, a).texture(u, vv).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }
}
