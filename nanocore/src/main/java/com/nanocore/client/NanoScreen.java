package com.nanocore.client;

import com.nanocore.core.NanoData;
import com.nanocore.net.SyncPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Evolution / learning overview (key M). */
public class NanoScreen extends Screen {
    private static final String[] LEARN_KEYS = {"blade", "cannon", "shield", "flight", "explosion"};

    public NanoScreen() {
        super(Text.translatable("screen.nanocore.title"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        SyncPayload s = ClientData.last;
        int w = 260, x = (width - w) / 2, y = height / 2 - 100;
        g.fill(x - 8, y - 8, x + w + 8, y + 206, 0xD0070B10);
        g.drawBorder(x - 8, y - 8, w + 16, 214, 0xFF38C8FF);
        g.drawTextWithShadow(textRenderer, title, x, y, 0xFF38C8FF);
        if (s == null || !s.installed()) {
            g.drawTextWithShadow(textRenderer, Text.translatable("message.nanocore.noinstall"), x, y + 20, 0xFFFF7777);
            return;
        }
        g.drawTextWithShadow(textRenderer, Text.literal(NanoData.STAGE_TAGS[s.stage()] + "  " + NanoData.STAGE_NAMES[s.stage()]), x, y + 14, 0xFFFFFFFF);
        g.drawTextWithShadow(textRenderer, Text.translatable("screen.nanocore.energy", s.energy(), s.max()), x, y + 26, 0xFFB6F0FF);
        g.drawTextWithShadow(textRenderer, Text.translatable("screen.nanocore.learning"), x, y + 44, 0xFF38C8FF);
        for (int i = 0; i < LEARN_KEYS.length; i++) {
            int ry = y + 58 + i * 20;
            int count = i < s.learn().size() ? s.learn().get(i) : 0;
            int lvl = NanoData.levelOf(count);
            g.drawTextWithShadow(textRenderer, Text.translatable("learn.nanocore." + LEARN_KEYS[i]).append("  Lv." + lvl), x, ry, 0xFFFFFFFF);
            float pct = lvl >= 3 ? 1f : (float) (count - (lvl == 0 ? 0 : NanoData.LEARN_STEPS[lvl - 1]))
                    / (NanoData.LEARN_STEPS[lvl] - (lvl == 0 ? 0 : NanoData.LEARN_STEPS[lvl - 1]));
            g.fill(x, ry + 11, x + w, ry + 15, 0xC005080C);
            g.fill(x, ry + 11, x + (int) (w * Math.max(0f, Math.min(1f, pct))), ry + 15, 0xFF38C8FF);
        }
        g.drawTextWithShadow(textRenderer, Text.translatable("screen.nanocore.evolve"), x, y + 164, 0xFF9AA6B4);
        g.drawTextWithShadow(textRenderer, Text.translatable("screen.nanocore.keys"), x, y + 178, 0xFF9AA6B4);
    }
}
