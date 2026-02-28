package com.cobblemonbingo.bingo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemIconUtil {
    private ItemIconUtil() {}

    public static ItemStack itemFromIdOrFallback(String id) {
        if (id == null || id.isBlank()) return new ItemStack(Items.PAPER);

        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return new ItemStack(Items.PAPER);

        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) return new ItemStack(Items.PAPER);

        return new ItemStack(item);
    }
}