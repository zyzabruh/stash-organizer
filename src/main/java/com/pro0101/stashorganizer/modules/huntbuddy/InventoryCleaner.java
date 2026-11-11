package com.pro0101.stashorganizer.modules.huntbuddy;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.huntbuddy.util.QuietClicks;
import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Throws out the inventory slots that match, or do not match, a list.
 *
 * <p>Ported from HunterBuddy's {@code InventoryCleaner} (GPL-3.0, see {@code CREDITS.md}). A stash
 * run fills the inventory with whatever the chests held, and the trip home only needs the kit, so
 * the junk goes out one stack at a time while the player walks.
 */
public class InventoryCleaner extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup screens = this.settings.createGroup("Screens");

    private final Setting<Mode> mode = this.general.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Whitelist throws everything not in the list below. Blacklist throws only what is in the list.")
        .defaultValue(Mode.Whitelist)
        .build()
    );

    private final Setting<List<Item>> whitelist = this.general.add(new ItemListSetting.Builder()
        .name("whitelist")
        .description("The item list the mode above checks against.")
        .defaultValue(
            Items.EXPERIENCE_BOTTLE, Items.SHULKER_BOX, Items.WHITE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX,
            Items.MAGENTA_SHULKER_BOX, Items.LIGHT_BLUE_SHULKER_BOX, Items.YELLOW_SHULKER_BOX, Items.LIME_SHULKER_BOX,
            Items.PINK_SHULKER_BOX, Items.GRAY_SHULKER_BOX, Items.LIGHT_GRAY_SHULKER_BOX, Items.CYAN_SHULKER_BOX,
            Items.PURPLE_SHULKER_BOX, Items.BLUE_SHULKER_BOX, Items.BROWN_SHULKER_BOX, Items.GREEN_SHULKER_BOX,
            Items.RED_SHULKER_BOX, Items.BLACK_SHULKER_BOX, Items.CRAFTING_TABLE, Items.CRYING_OBSIDIAN,
            Items.DIAMOND_CHESTPLATE, Items.DIAMOND_PICKAXE, Items.ELYTRA, Items.ENCHANTED_GOLDEN_APPLE,
            Items.ENDER_CHEST, Items.FIREWORK_ROCKET, Items.FIREWORK_STAR, Items.FLINT_AND_STEEL, Items.GLOWSTONE,
            Items.GOLDEN_CARROT, Items.JUNGLE_SIGN, Items.NETHERITE_AXE, Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_PICKAXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_SWORD, Items.OBSIDIAN,
            Items.RESPAWN_ANCHOR, Items.TOTEM_OF_UNDYING
        )
        .build()
    );

    private final Setting<Integer> delay = this.general.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait between two throws.")
        .defaultValue(2)
        .min(0)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> hotbar = this.general.add(new BoolSetting.Builder()
        .name("hotbar")
        .description("Also clean the hotbar. The main inventory is always cleaned.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> inventoryScreen = this.screens.add(new BoolSetting.Builder()
        .name("inventory")
        .description("Clean while no screen or the player inventory is open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> craftingTableScreen = this.screens.add(new BoolSetting.Builder()
        .name("crafting-table")
        .description("Clean while a crafting table is open.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> containersScreen = this.screens.add(new BoolSetting.Builder()
        .name("containers")
        .description("Clean while a chest, shulker box or other container screen is open.")
        .defaultValue(false)
        .build()
    );

    private int ticksLeft;

    public InventoryCleaner() {
        super(StashOrganizerAddon.CATEGORY, "inventory-cleaner",
            "Throws items out of your inventory that match, or don't match, a list."
        );
    }

    /**
     * Clears the delay left over from the previous activation.
     */
    @Override
    public void onActivate() {
        this.ticksLeft = 0;
    }

    /**
     * Throws one matching stack every {@code delay} ticks while the screen allows it.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.player == null || this.mc.world == null) return;
        if (!this.screen()) return;

        // A stack on the cursor belongs to the player, not to the inventory: throwing while one is
        // held drops it into the world by accident.
        if (!this.mc.player.currentScreenHandler.getCursorStack().isEmpty()) return;

        if (this.ticksLeft > 0) {
            this.ticksLeft--;
            return;
        }

        int slot = this.find();
        if (slot == -1) return;
        if (!QuietClicks.readyOrSettle()) return;

        InvUtils.drop().slot(slot);
        this.ticksLeft = this.delay.get();
    }

    /**
     * Checks whether the open screen is one the option allows cleaning in.
     *
     * @return true when the current screen may be cleaned
     */
    private boolean screen() {
        if (this.mc.currentScreen == null) return this.inventoryScreen.get();
        if (this.mc.currentScreen instanceof InventoryScreen) return this.inventoryScreen.get();
        if (this.mc.currentScreen instanceof CraftingScreen) return this.craftingTableScreen.get();
        if (this.mc.currentScreen instanceof HandledScreen) return this.containersScreen.get();

        return false;
    }

    /**
     * Finds the first slot the mode wants thrown out.
     *
     * @return inventory slot, or -1 when nothing matches
     */
    private int find() {
        int start = this.hotbar.get() ? 0 : 9;

        for (int slot = start; slot < 36; slot++) {
            ItemStack stack = this.mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;

            if (this.throwable(stack)) return slot;
        }

        return -1;
    }

    /**
     * Checks whether a stack should be thrown out.
     *
     * @param stack stack to check
     * @return true when the stack matches the mode
     */
    private boolean throwable(ItemStack stack) {
        boolean listed = this.whitelist.get().contains(stack.getItem());
        return this.mode.get() == Mode.Whitelist ? !listed : listed;
    }

    /** How the list is read. */
    public enum Mode {
        Whitelist,
        Blacklist
    }
}
