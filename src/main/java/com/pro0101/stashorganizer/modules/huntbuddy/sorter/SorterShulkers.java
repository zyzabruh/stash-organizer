package com.pro0101.stashorganizer.modules.huntbuddy.sorter;

import com.pro0101.stashorganizer.modules.huntbuddy.util.ShulkerContents;
import com.pro0101.stashorganizer.modules.huntbuddy.sorter.logic.CategoryRules;
import com.pro0101.stashorganizer.modules.huntbuddy.sorter.logic.ShulkerClassifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

public final class SorterShulkers {
    private SorterShulkers() {
    }

    static String itemId(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    public static ShulkerClassifier.Result classify(ItemStack stack, CategoryRules rules, double purity) {
        List<ShulkerClassifier.Slot> slots = new ArrayList<>();
        for (ItemStack inner : ShulkerContents.stacks(stack)) {
            slots.add(new ShulkerClassifier.Slot(itemId(inner), inner.getCount()));
        }
        return ShulkerClassifier.classify(slots, rules, purity);
    }
}
