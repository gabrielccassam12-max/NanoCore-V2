package com.nanocore.mixin;

import com.nanocore.client.NanoVis;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arm poses of the transformations: blades ready, cannon aimed, shield raised. */
@Mixin(PlayerEntityModel.class)
public abstract class PlayerPoseMixin {
    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("RETURN"), require = 0)
    private void nanocore$pose(LivingEntity entity, float limbAngle, float limbDistance, float age, float headYaw,
                               float headPitch, CallbackInfo ci) {
        float[] s = NanoVis.pose(entity.getId());
        if (s == null) return;
        PlayerEntityModel<?> m = (PlayerEntityModel<?>) (Object) this;
        int form = (int) s[0];
        float k = s[1];
        float aim = m.head.pitch;
        float half = (float) (Math.PI / 2);
        switch (form) {
            case 1 -> { // blades: arms slightly out, ready to cut
                m.rightArm.pitch = lerp(k, m.rightArm.pitch, -0.35f);
                m.leftArm.pitch = lerp(k, m.leftArm.pitch, -0.35f);
                m.rightArm.roll = lerp(k, m.rightArm.roll, 0.28f);
                m.leftArm.roll = lerp(k, m.leftArm.roll, -0.28f);
            }
            case 2 -> { // cannon: right arm aims where you look
                m.rightArm.pitch = lerp(k, m.rightArm.pitch, -half + aim);
                m.rightArm.yaw = lerp(k, m.rightArm.yaw, -0.06f);
                m.rightArm.roll = lerp(k, m.rightArm.roll, 0f);
            }
            case 3 -> { // shield: left arm raised in front
                m.leftArm.pitch = lerp(k, m.leftArm.pitch, -half * 0.85f + aim * 0.5f);
                m.leftArm.yaw = lerp(k, m.leftArm.yaw, 0.35f);
                m.leftArm.roll = lerp(k, m.leftArm.roll, 0f);
            }
            default -> { }
        }
        m.leftSleeve.copyTransform(m.leftArm);
        m.rightSleeve.copyTransform(m.rightArm);
    }

    private static float lerp(float k, float a, float b) {
        return a + (b - a) * k;
    }
}
