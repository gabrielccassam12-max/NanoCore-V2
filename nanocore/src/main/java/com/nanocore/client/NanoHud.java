package com.nanocore.client;

import com.nanocore.core.Ability;
import com.nanocore.core.Form;
import com.nanocore.core.NanoData;
import com.nanocore.net.SyncPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** Minimal tech HUD: stage, energy, equipped form, five abilities with cooldowns. */
public final class NanoHud {
    private NanoHud() {}

    private static final int[] STAGE_COL = {0x38C8FF, 0x2E9BFF, 0x63E6FF, 0xB6F0FF};

    private static int argb(int rgb, int a) {
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(DrawContext g, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        SyncPayload s = ClientData.last;
        if (mc.player == null || mc.options.hudHidden || s == null || !s.installed()) return;
        long t = ClientData.clock;
        int col = STAGE_COL[Math.max(0, Math.min(3, s.stage()))];
        boolean od = ClientData.odEnd > t;

        ClientData.shownEnergy += (s.energy() - ClientData.shownEnergy) * 0.2f;
        float pct = Math.max(0f, Math.min(1f, ClientData.shownEnergy / Math.max(1, s.max())));

        int w = 128, x = g.getScaledWindowWidth() - w - 10, rows = Ability.values().length;
        int y = g.getScaledWindowHeight() - 30 - rows * 11 - 50;

        // header
        Text head = Text.literal("NANOCORE  " + NanoData.STAGE_TAGS[s.stage()] + " " + NanoData.STAGE_NAMES[s.stage()]);
        g.drawTextWithShadow(mc.textRenderer, head, x, y, argb(col, 255));
        if (od && (t / 4) % 2 == 0) g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.nanocore.overdrive"), x, y - 11, 0xFFFFFFFF);
        if (s.locked() && (t / 6) % 2 == 0) g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.nanocore.locked"), x, y - 11, 0xFFFF5555);

        // energy bar
        int by = y + 12;
        g.fill(x - 3, by - 3, x + w + 3, by + 11, argb(0x05080C, 0xC0));
        g.drawBorder(x - 3, by - 3, w + 6, 14, argb(col, s.locked() ? 0x60 : 0xFF));
        int fw = (int) (w * pct);
        int barCol = s.locked() ? 0xFF4040 : col;
        if (fw > 0) {
            g.fillGradient(x, by, x + fw, by + 4, argb(0xFFFFFF, 0xFF), argb(barCol, 0xFF));
            g.fillGradient(x, by + 4, x + fw, by + 8, argb(barCol, 0xFF), argb(barCol, 0x70));
            int gx = x + (int) ((t * 3) % Math.max(1, fw));
            g.fill(gx, by, Math.min(gx + 2, x + fw), by + 8, argb(0xFFFFFF, 0x70));
        }
        for (int i = 1; i < 10; i++) g.fill(x + w * i / 10, by, x + w * i / 10 + 1, by + 8, argb(0x000000, 0x90));
        String en = s.energy() + "/" + s.max();
        g.drawTextWithShadow(mc.textRenderer, en, x + w - mc.textRenderer.getWidth(en), by - 12 + 12 + 11 - 11 + 0, 0xFFC8E8F8);

        // form / suit line
        int fy = by + 14;
        Form f = Form.of(s.form());
        Text formName = s.armor() ? Text.translatable("form.nanocore." + f.ordinal()) : Text.translatable("hud.nanocore.suit_off");
        g.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.nanocore.form").append(": ").append(formName), x, fy, argb(0xFFFFFF, 0xFF));
        if (f == Form.SHIELD && s.armor()) {
            int sw = (int) (60 * Math.min(1f, s.shield() / 60f));
            g.fill(x + w - 62, fy + 1, x + w - 2, fy + 7, argb(0x05080C, 0xC0));
            g.fill(x + w - 62, fy + 1, x + w - 62 + sw, fy + 7, argb(col, 0xFF));
        }
        if (s.charge() > 0) {
            g.fill(x, fy + 10, x + w, fy + 13, argb(0x05080C, 0xC0));
            g.fill(x, fy + 10, x + (int) (w * s.charge() / 100f), fy + 13, argb(0xFFFFFF, 0xFF));
        }

        // abilities
        int ay = fy + 17;
        for (int i = 0; i < rows; i++) {
            Ability a = Ability.values()[i];
            int ry = ay + i * 11;
            boolean stageLocked = s.stage() < a.minStage;
            long left = Math.max(0, ClientData.cdEnd[i] - t);
            boolean afford = s.energy() >= a.cost;
            int tc2 = stageLocked ? 0xFF666E7A : left > 0 ? 0xFF8A94A0 : afford ? 0xFFFFFFFF : 0xFFFF7777;
            Text key = NanoClient.ABILITY[i].getBoundKeyLocalizedText();
            g.fill(x - 3, ry - 1, x + w + 3, ry + 9, argb(0x05080C, 0x90));
            g.drawTextWithShadow(mc.textRenderer, Text.literal("[").append(key).append("] ").append(a.displayName()), x, ry, tc2);
            String right = stageLocked ? NanoData.STAGE_TAGS[a.minStage] : String.valueOf(a.cost);
            g.drawTextWithShadow(mc.textRenderer, right, x + w - mc.textRenderer.getWidth(right), ry, stageLocked ? 0xFF666E7A : 0xFF9AD8FF);
            if (left > 0) {
                int total = Math.max(1, a.cooldown);
                int cw = (int) (w * (1f - Math.min(1f, (float) left / total)));
                g.fill(x, ry + 8, x + w, ry + 9, argb(0x000000, 0xA0));
                g.fill(x, ry + 8, x + cw, ry + 9, argb(col, 0xFF));
            } else if (i == 3 && od) {
                g.fill(x, ry + 8, x + w, ry + 9, argb(0xFFFFFF, 0xFF));
            } else if (i == 4 && ClientData.adEnd > t) {
                g.fill(x, ry + 8, x + w, ry + 9, argb(0x7CFFB0, 0xFF));
            }
        }
    }
}
