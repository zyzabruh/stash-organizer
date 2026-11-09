package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Locale;

/**
 * Decides which shulker boxes must be left intact instead of being unpacked: boxes renamed by the
 * player (for example "tools" or "kit"), boxes that only hold a single item type (bulk storage),
 * boxes that contain gear (armour, tools, weapons) and boxes holding an item from the protected
 * list.
 * <p>
 * The rules are written once and applied to both sources of contents: the screen of an opened box
 * and the components of a box still stored in a chest. The name filter lives in
 * {@link ShulkerNames}, the gear definition in {@link ItemGrouping#isGear}, so a new item only has
 * to be taught to one of them. Every rule also reports its name through {@link #reason}, which is
 * what the end-of-run report lists.
 */
public final class ShulkerProtection {
    /** A box matching one of the keep-names words. */
    public static final String NAME = "name";
    /** A box renamed by the player, when renamed boxes are kept shut. */
    public static final String RENAMED = "renamed";
    /** A box holding a single item type. */
    public static final String SINGLE_ITEM = "single-item";
    /** A box holding armour, tools or weapons. */
    public static final String GEAR = "gear";
    /** A box holding an item from the keep-items list. */
    public static final String ITEM = "item";

    private final StashSession session;

    public ShulkerProtection(StashSession session) {
        this.session = session;
    }

    /** True when the shulker box itself must not be opened (matched by its name). */
    public boolean protectsStack(ItemStack shulker) {
        String name = ShulkerNames.normalize(shulker.getName().getString());
        if (name.isEmpty()) return false;

        for (String entry : session.protectNames()) {
            if (ShulkerNames.matches(name, entry)) return true;
        }

        return false;
    }

    /** The name the player gave this box, or null when it still has its default name. */
    public String customName(ItemStack stack) {
        if (stack.isEmpty()) return null;

        var name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name == null ? null : name.getString();
    }

    /** True when the box was renamed and the player asked to keep renamed boxes shut. */
    public boolean protectsCustomName(ItemStack stack) {
        return session.keepsRenamed() && customName(stack) != null;
    }

    /**
     * True for a shulker box the organizer must never open. Such a box is still a normal item: it
     * can be moved and stored in a chest with the other items, unlike a box that is going to be
     * unpacked.
     */
    public boolean protectsShulkerAsItem(ItemStack stack) {
        return reason(stack) != null;
    }

    /** Why this box must stay shut, or null when it can be unpacked. */
    public String reason(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.isIn(ItemTags.SHULKER_BOXES)) return null;

        if (protectsStack(stack)) return NAME;
        if (protectsCustomName(stack)) return RENAMED;

        return reasonFor(InventoryTools.contentsOf(stack));
    }

    /** Checks the contents of the currently opened shulker box, without removing anything. */
    public boolean protectsContents() {
        return protects(session.access.liveContents());
    }

    /** Same protection rules, read from the contents stored in a shulker item. */
    public boolean protectsShulkerContents(ItemStack shulker) {
        if (shulker == null || shulker.isEmpty()) return false;

        return reasonFor(InventoryTools.contentsOf(shulker)) != null;
    }

    /** The single implementation of the content rules, used for both content sources. */
    public boolean protects(List<ItemStack> contents) {
        return reasonFor(contents) != null;
    }

    /** Why these contents must stay shut, or null when the box can be emptied. */
    public String reasonFor(List<ItemStack> contents) {
        if (session.protectSingleItem() && holdsSingleType(contents)) return SINGLE_ITEM;
        if (session.protectGear() && holdsGear(contents)) return GEAR;
        if (holdsProtectedItem(contents)) return ITEM;

        return null;
    }

    private boolean holdsSingleType(List<ItemStack> contents) {
        ItemStack first = ItemStack.EMPTY;

        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;
            if (first.isEmpty()) first = stack;
            else if (!ItemStack.areItemsAndComponentsEqual(first, stack)) return false;
        }

        return !first.isEmpty();
    }

    private boolean holdsGear(List<ItemStack> contents) {
        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;
            if (ItemGrouping.isGear(stack)) return true;
        }

        return false;
    }

    private boolean holdsProtectedItem(List<ItemStack> contents) {
        List<String> protectedItems = session.protectItems();
        if (protectedItems.isEmpty()) return false;

        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;

            Identifier id = Registries.ITEM.getId(stack.getItem());

            for (String entry : protectedItems) {
                String normalized = entry.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) continue;
                if (id.toString().equals(normalized) || id.getPath().equals(normalized)) return true;
            }
        }

        return false;
    }
}
