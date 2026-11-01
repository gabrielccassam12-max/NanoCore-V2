package com.nanocore.item;

import com.nanocore.core.NanoManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Evolution chip: MK-II / MK-III / MK-IV. Must be used in order. */
public class ChipItem extends Item {
    private final int stage;

    public ChipItem(int stage, Settings settings) {
        super(settings);
        this.stage = stage;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack);
        if (NanoManager.evolve(sp, stage)) {
            if (!sp.isCreative()) stack.decrement(1);
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.fail(stack);
    }
}
