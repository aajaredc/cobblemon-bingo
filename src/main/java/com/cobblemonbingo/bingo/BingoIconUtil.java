package com.cobblemonbingo.bingo;

import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class BingoIconUtil {
    private BingoIconUtil() {}

    public static ItemStack createIconStack(BingoConfig.BingoChallenge ch, List<Component> extraLoreLines) {
        ItemStack stack = resolveIcon(ch == null ? null : ch.icon);

        // Display name
        if (ch != null && ch.name != null && !ch.name.isBlank()) {
            stack.set(DataComponents.ITEM_NAME, Component.literal(ch.name));
        }

        // Lore
        List<Component> lines = new ArrayList<>();
        if (ch != null && ch.lore != null) {
            for (String s : ch.lore) {
                if (s == null || s.isBlank()) continue;
                lines.add(Component.literal(s));
            }
        }
        if (extraLoreLines != null) {
            for (Component c : extraLoreLines) {
                if (c != null) lines.add(c);
            }
        }
        if (!lines.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(lines));
        }

        return stack;
    }

    private static ItemStack resolveIcon(String icon) {
        if (icon == null || icon.isBlank()) return new ItemStack(Items.PAPER);

        String t = icon.trim();
        String lower = t.toLowerCase(Locale.ROOT);

        // player head format: head:<playerName>
        if (lower.startsWith("head:")) {
            String name = t.substring("head:".length()).trim();
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);

            if (!name.isEmpty()) {
                // Name-based profile (may require online-mode / Mojang services to resolve skin)
                ResolvableProfile profile = new ResolvableProfile(Optional.of(name), Optional.empty(), new PropertyMap());
                head.set(DataComponents.PROFILE, profile);
            }

            return head;
        }

        // custom head texture format: headvalue:<base64>
        // (Use the "Value" from Minecraft-Heads custom head pages)
        if (lower.startsWith("headvalue:")) {
            String value = t.substring("headvalue:".length()).trim();
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);

            if (!value.isEmpty()) {
                PropertyMap props = new PropertyMap();
                props.put("textures", new Property("textures", value));

                // Provide a stable-ish identity; UUID isn't required but avoids null gameprofile edge cases
                ResolvableProfile profile = new ResolvableProfile(Optional.empty(), Optional.of(UUID.randomUUID()), props);
                head.set(DataComponents.PROFILE, profile);
            }

            return head;
        }

        // Normal item icon
        ResourceLocation rl = ResourceLocation.tryParse(t);
        if (rl == null) return new ItemStack(Items.PAPER);

        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) return new ItemStack(Items.PAPER);

        return new ItemStack(item);
    }
}