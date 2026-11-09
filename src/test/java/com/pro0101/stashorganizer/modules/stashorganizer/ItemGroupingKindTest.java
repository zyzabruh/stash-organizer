package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The kind of a group, which decides what sits together in a chest that has to be shared when the
 * stash runs out of chests. The real classification is read from the game data (tags, item type,
 * block item); the key-based one is only the fallback for a group with no item at hand.
 */
class ItemGroupingKindTest {
    @Test
    void itemsAreClassifiedFromTheGameData() {
        Assumptions.assumeTrue(TestWorld.bootstrap(), "the game registries cannot be booted in this JVM");

        // Plain blocks are blocks whatever the grouping mode is, which the item id alone cannot say.
        assertEquals("blocks", ItemGrouping.kind(new ItemStack(Items.STONE)));
        assertEquals("blocks", ItemGrouping.kind(new ItemStack(Items.OAK_PLANKS)));
        assertEquals("blocks", ItemGrouping.kind(new ItemStack(Items.IRON_BLOCK)));

        assertEquals("redstone", ItemGrouping.kind(new ItemStack(Items.REDSTONE_TORCH)));
        assertEquals("ores", ItemGrouping.kind(new ItemStack(Items.IRON_INGOT)));
        assertEquals("food", ItemGrouping.kind(new ItemStack(Items.COOKED_BEEF)));
        assertEquals("gear", ItemGrouping.kind(new ItemStack(Items.DIAMOND_SWORD)));
        assertEquals("brewing", ItemGrouping.kind(new ItemStack(Items.POTION)));
        assertEquals("misc", ItemGrouping.kind(ItemStack.EMPTY));
    }

    @Test
    void categoryKeysNameTheirOwnKind() {
        assertEquals("redstone", ItemGrouping.kindOfKey("category:redstone"));
        assertEquals("blocks", ItemGrouping.kindOfKey("category:blocks"));
        assertEquals("ores", ItemGrouping.kindOfKey("category:ores"));
        assertEquals("misc", ItemGrouping.kindOfKey("category:misc"));
    }

    @Test
    void materialsAndKitsJoinTheirWiderKind() {
        assertEquals("ores", ItemGrouping.kindOfKey("material:iron"));
        assertEquals("gear", ItemGrouping.kindOfKey("kit:diamond"));
    }

    @Test
    void familiesLandInTheKindOfTheirItems() {
        assertEquals("blocks", ItemGrouping.kindOfKey("family:planks"));
        assertEquals("blocks", ItemGrouping.kindOfKey("family:wool"));
        assertEquals("redstone", ItemGrouping.kindOfKey("family:rails"));
        assertEquals("ores", ItemGrouping.kindOfKey("family:diamond-ores"));
        assertEquals("food", ItemGrouping.kindOfKey("family:meat"));
        assertEquals("gear", ItemGrouping.kindOfKey("family:swords"));
        assertEquals("misc", ItemGrouping.kindOfKey("family:shulker-boxes"));
    }

    @Test
    void exactItemKeysFallBackToTheirName() {
        assertEquals("redstone", ItemGrouping.kindOfKey("item:redstone_torch"));
        assertEquals("ores", ItemGrouping.kindOfKey("item:iron_ingot"));
        assertEquals("food", ItemGrouping.kindOfKey("item:cooked_beef"));
        assertEquals("gear", ItemGrouping.kindOfKey("item:diamond_sword"));
        assertEquals("misc", ItemGrouping.kindOfKey("item:cobblestone"));
    }

    @Test
    void unknownKeysStayReadable() {
        assertEquals("misc", ItemGrouping.kindOfKey(null));
        assertEquals("misc", ItemGrouping.kindOfKey(""));
        assertEquals("misc", ItemGrouping.kindOfKey("stone"));
        assertEquals("misc", ItemGrouping.kindOfKey("mod:create:cogwheel"));
    }
}
