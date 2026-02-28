package com.cobblemonbingo.bingo;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class BingoScreenHandler extends ChestMenu {

    public final String bingoId;
    public final SimpleContainer containerRef;

    private static final int ROWS = 5;
    private static final int MENU_SIZE = ROWS * 9;

    public BingoScreenHandler(int syncId, Inventory playerInv, SimpleContainer container, String bingoId) {
        super(net.minecraft.world.inventory.MenuType.GENERIC_9x5, syncId, playerInv, container, ROWS);
        this.bingoId = bingoId;
        this.containerRef = container;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public void clicked(int slotIndex, int button, ClickType clickType, Player player) {
        // Block interactions with container slots
        if (slotIndex >= 0 && slotIndex < MENU_SIZE) return;

        // Block drag distribution (prevents ghost insertion attempts)
        if (clickType == ClickType.QUICK_CRAFT) return;

        super.clicked(slotIndex, button, clickType, player);
    }
}