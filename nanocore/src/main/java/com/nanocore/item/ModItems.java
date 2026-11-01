package com.nanocore.item;

import com.nanocore.Nano;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

public final class ModItems {
    private ModItems() {}

    public static final Item CORE = Registry.register(Registries.ITEM, Nano.id("nanocore"),
            new NanoCoreItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
    public static final Item CHIP2 = chip("chip_mk2", 1, Rarity.RARE);
    public static final Item CHIP3 = chip("chip_mk3", 2, Rarity.EPIC);
    public static final Item CHIP4 = chip("chip_mk4", 3, Rarity.EPIC);

    private static Item chip(String name, int stage, Rarity r) {
        return Registry.register(Registries.ITEM, Nano.id(name), new ChipItem(stage, new Item.Settings().maxCount(1).rarity(r)));
    }

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> {
            e.add(CORE);
            e.add(CHIP2);
            e.add(CHIP3);
            e.add(CHIP4);
        });
    }
}
