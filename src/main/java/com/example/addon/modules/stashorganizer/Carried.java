package com.example.addon.modules.stashorganizer;

import net.minecraft.item.Item;

/**
 * A stack taken from a container and waiting in the player inventory to be delivered.
 * <p>
 * The group is decided once, when the stack is taken, instead of being recomputed from the slot
 * later: a shulker box belongs with the item it holds, and the inventory slot can change hands
 * (pickaxe swap, hotbar move) without the buffer losing track of what it carries.
 */
public record Carried(Item item, String group) {
}
