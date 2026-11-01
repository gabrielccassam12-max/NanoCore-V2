package com.nanocore.item;

import com.nanocore.core.NanoManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Right-click once: the NanoCore is installed in your body for good. */
public class NanoCoreItem extends Item {
    public NanoCoreItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack);
        if (NanoManager.install(sp)) {
            if (!sp.isCreative()) stack.decrement(1);
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.fail(stack);
    }
}
