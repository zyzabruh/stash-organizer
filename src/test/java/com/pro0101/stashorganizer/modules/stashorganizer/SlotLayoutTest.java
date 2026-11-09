package com.pro0101.stashorganizer.modules.stashorganizer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tidying permutation, checked on plain values: it must reach the target order with a few
 * swaps, never ask to swap two interchangeable elements, and leave nothing to do afterwards.
 */
class SlotLayoutTest {
    @Test
    void anOrderedContainerNeedsNoSwap() {
        List<String> contents = Arrays.asList("a", "a", "b", null, null);

        assertTrue(SlotLayout.permutation(contents, contents, Objects::equals).isEmpty());
    }

    @Test
    void swapsReachTheTargetOrderAndStopThere() {
        List<String> current = Arrays.asList("b", null, "a", "b");
        List<String> target = Arrays.asList("a", "b", "b", null);

        List<SlotLayout.Swap> swaps = SlotLayout.permutation(current, target, Objects::equals);
        assertFalse(swaps.isEmpty(), "the container is out of order");

        List<String> result = apply(current, swaps);
        assertEquals(target, result, "the swaps produce the tidied order");
        assertTrue(SlotLayout.permutation(result, target, Objects::equals).isEmpty(),
            "and the same pass run again finds nothing left to do");
    }

    @Test
    void everySwapPairsTwoDifferentElements() {
        List<String> current = Arrays.asList("c", "a", "b", null, "c");
        List<String> target = Arrays.asList("a", "b", "c", "c", null);

        for (SlotLayout.Swap swap : SlotLayout.permutation(current, target, Objects::equals)) {
            assertTrue(swap.from() < swap.to(), "swaps only ever move a stack up the chest");
            assertFalse(Objects.equals(current.get(swap.from()), current.get(swap.to())),
                "two equal stacks are never swapped: clicking one onto the other would merge them");
        }
    }

    private static List<String> apply(List<String> current, List<SlotLayout.Swap> swaps) {
        List<String> result = new ArrayList<>(current);

        for (SlotLayout.Swap swap : swaps) {
            String held = result.get(swap.from());
            result.set(swap.from(), result.get(swap.to()));
            result.set(swap.to(), held);
        }

        return result;
    }
}
