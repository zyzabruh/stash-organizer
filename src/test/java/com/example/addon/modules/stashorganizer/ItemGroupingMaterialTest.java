package com.example.addon.modules.stashorganizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression suite for the material grouping: every shape of an item id that belongs to one metal
 * or gem must land in the same group, and everything else must fall back to the family grouping.
 * Pure string logic, so no game is needed.
 */
class ItemGroupingMaterialTest {
    @Test
    void groupsEveryShapeOfOneMaterial() {
        assertEquals("iron", ItemGrouping.materialName("iron_ore"));
        assertEquals("iron", ItemGrouping.materialName("deepslate_iron_ore"));
        assertEquals("iron", ItemGrouping.materialName("raw_iron"));
        assertEquals("iron", ItemGrouping.materialName("raw_iron_block"));
        assertEquals("iron", ItemGrouping.materialName("iron_ingot"));
        assertEquals("iron", ItemGrouping.materialName("iron_nugget"));
        assertEquals("iron", ItemGrouping.materialName("iron_block"), "the block joins its ingots");

        assertEquals("gold", ItemGrouping.materialName("nether_gold_ore"));
        assertEquals("gold", ItemGrouping.materialName("gold_nugget"));
        assertEquals("copper", ItemGrouping.materialName("raw_copper_block"));
    }

    @Test
    void resolvesItemsNamedAfterTheirMaterial() {
        assertEquals("lapis", ItemGrouping.materialName("lapis_lazuli"));
        assertEquals("lapis", ItemGrouping.materialName("deepslate_lapis_ore"));
        assertEquals("coal", ItemGrouping.materialName("charcoal"));
        assertEquals("coal", ItemGrouping.materialName("deepslate_coal_ore"));
        assertEquals("netherite", ItemGrouping.materialName("netherite_scrap"));
        assertEquals("netherite", ItemGrouping.materialName("ancient_debris"));
        assertEquals("amethyst", ItemGrouping.materialName("amethyst_shard"));
        assertEquals("redstone", ItemGrouping.materialName("redstone_block"));
        assertEquals("quartz", ItemGrouping.materialName("nether_quartz_ore"));
    }

    @Test
    void leavesOtherItemsToTheFamilyGrouping() {
        assertNull(ItemGrouping.materialName("dirt"));
        assertNull(ItemGrouping.materialName("iron_door"), "a door is not raw material");
        assertNull(ItemGrouping.materialName("iron_bars"));
        assertNull(ItemGrouping.materialName("golden_apple"));
        assertNull(ItemGrouping.materialName("nether_wart"));
        assertNull(ItemGrouping.materialName("deepslate"));
        assertNull(ItemGrouping.materialName("glowstone_dust"));
    }
}
