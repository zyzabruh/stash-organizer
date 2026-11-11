package com.pro0101.stashorganizer.modules.huntbuddy;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import com.pro0101.stashorganizer.modules.huntbuddy.util.QuietClicks;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/**
 * Tops hotbar stacks up from the main inventory before they run out.
 *
 * <p>Ported from HunterBuddy's {@code HotbarRefill} (GPL-3.0, see {@code CREDITS.md}). The addon
 * runs on a stash trip the way it runs a highway: mining and building empty the held stack long
 * before the trip is over, and stopping to open the inventory by hand is what breaks the rhythm.
 */
public class HotbarRefill extends Module {
    private static final int HOTBAR_SIZE = 9;
    private static final int MAIN_FIRST = 9;
    private static final int MAIN_LAST = 35;

    /** Which inventory stack is used to top a hotbar stack up. */
    public enum SourcePick {
        FirstMatch,
        FullStacks,
        SmallStacks;

        @Override
        public String toString() {
            return switch (this) {
                case FirstMatch -> "First Match";
                case FullStacks -> "Full Stacks";
                case SmallStacks -> "Small Stacks";
            };
        }
    }

    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup kinds = this.settings.createGroup("Items");
    private final SettingGroup extra = this.settings.createGroup("Advanced");

    private final Setting<Integer> topUpAt = this.general.add(new IntSetting.Builder()
        .name("threshold")
        .description("A hotbar stack at or below this count gets topped up.")
        .defaultValue(8)
        .min(1)
        .max(63)
        .sliderMin(1)
        .sliderMax(63)
        .build()
    );

    private final Setting<Integer> pauseTicks = this.general.add(new IntSetting.Builder()
        .name("tick-delay")
        .description("Ticks to wait after a top-up before the next one.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderMin(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> waitWhileUsing = this.general.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Wait while you are using an item, such as eating or drawing a bow.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lastItemTrigger = this.general.add(new BoolSetting.Builder()
        .name("smart-refill")
        .description("Instead of the threshold, top up only when a stack has just dropped to its last item.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SourcePick> sourcePick = this.general.add(new EnumSetting.Builder<SourcePick>()
        .name("stack-preference")
        .description("Which inventory stack is used: the first found, the biggest, or the smallest.")
        .defaultValue(SourcePick.SmallStacks)
        .build()
    );

    private final Setting<Boolean> wantBlocks = this.kinds.add(new BoolSetting.Builder()
        .name("blocks")
        .description("Blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantFood = this.kinds.add(new BoolSetting.Builder()
        .name("food")
        .description("Anything edible.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantTools = this.kinds.add(new BoolSetting.Builder()
        .name("tools")
        .description("Pickaxes, axes, shovels and hoes.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> wantWeapons = this.kinds.add(new BoolSetting.Builder()
        .name("weapons")
        .description("Swords, bows and crossbows.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> wantProjectiles = this.kinds.add(new BoolSetting.Builder()
        .name("projectiles")
        .description("Arrows and firework rockets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantPearls = this.kinds.add(new BoolSetting.Builder()
        .name("ender-pearls")
        .description("Ender pearls.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantPotions = this.kinds.add(new BoolSetting.Builder()
        .name("potions")
        .description("Potions.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantTotems = this.kinds.add(new BoolSetting.Builder()
        .name("totems")
        .description("Totems of undying.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantApples = this.kinds.add(new BoolSetting.Builder()
        .name("golden-apples")
        .description("Golden and enchanted golden apples.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> wantRockets = this.kinds.add(new BoolSetting.Builder()
        .name("fireworks")
        .description("Firework rockets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shiftMove = this.extra.add(new BoolSetting.Builder()
        .name("use-shift-click")
        .description("Move the stack with a shift-click instead of picking it up and placing it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> instantMove = this.extra.add(new BoolSetting.Builder()
        .name("silent-refill")
        .description("Send the shift-click right away instead of queueing it for a quiet moment.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> perTickLimit = this.extra.add(new IntSetting.Builder()
        .name("max-refills-per-tick")
        .description("Most top-ups done in one tick.")
        .defaultValue(1)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> keepNames = this.extra.add(new BoolSetting.Builder()
        .name("respect-custom-names")
        .description("A renamed stack is only topped up with the same name until it is down to its last item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> anyStackable = this.extra.add(new BoolSetting.Builder()
        .name("refill-all-stackable")
        .description("Top up every stackable item, whatever the boxes above say.")
        .defaultValue(true)
        .build()
    );

    private final Integer[] lastCounts = new Integer[HOTBAR_SIZE];
    private final String[] trackedNames = new String[HOTBAR_SIZE];
    private final Deque<Integer> pendingMoves = new ArrayDeque<>();
    private int waitTicks;

    public HotbarRefill() {
        super(StashOrganizerAddon.CATEGORY, "hotbar-refill",
            "Tops up hotbar stacks from your inventory before they run out."
        );
    }

    /**
     * Clears the per-slot tracking from the previous activation.
     */
    @Override
    public void onActivate() {
        Arrays.fill(this.lastCounts, null);
        Arrays.fill(this.trackedNames, null);

        this.pendingMoves.clear();
        this.waitTicks = 0;
    }

    /**
     * Reports how many top-ups are still queued.
     *
     * @return queue size, or null when nothing is waiting
     */
    @Override
    public String getInfoString() {
        int queued = this.pendingMoves.size();
        return queued > 0 ? "Refilling (" + queued + ")" : null;
    }

    /**
     * Waits out the pause, drains the queue, or scans the hotbar again.
     *
     * @param event pre-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (this.mc.player == null || this.mc.world == null) return;

        if (this.waitTicks > 0) {
            this.waitTicks--;
            return;
        }

        if (this.waitWhileUsing.get() && this.mc.player.isUsingItem()) return;

        if (!this.pendingMoves.isEmpty()) {
            this.processQueue();
            return;
        }

        this.scanHotbar();
    }

    /**
     * Sends the queued shift-clicks, up to the per-tick limit.
     */
    private void processQueue() {
        if (!this.inPlayerMenu()) return;
        if (!QuietClicks.readyOrSettle()) return;

        int done = 0;

        while (done < this.perTickLimit.get() && !this.pendingMoves.isEmpty()) {
            this.quickMove(this.pendingMoves.poll());
            done++;
        }

        if (done > 0) this.waitTicks = this.pauseTicks.get();
    }

    /**
     * Looks for hotbar stacks that qualify for a top-up.
     */
    private void scanHotbar() {
        int refills = 0;
        int limit = this.perTickLimit.get();

        for (int slot = 0; slot < HOTBAR_SIZE && refills < limit; slot++) {
            ItemStack stack = this.mc.player.getInventory().getStack(slot);

            if (stack.isEmpty()) {
                this.trackedNames[slot] = null;
                continue;
            }

            if (!this.isConcerned(stack)) continue;

            if (this.wantsRefill(slot, stack)) {
                if (this.tryRefill(slot, stack)) {
                    refills++;
                } else if (this.lastItemTrigger.get()) {
                    this.lastCounts[slot] = stack.getCount() + 1;
                }
            }
        }

        if (refills > 0) this.waitTicks = this.pauseTicks.get();
    }

    /**
     * Checks whether a hotbar stack is due for a top-up.
     *
     * @param slot hotbar slot to check
     * @param stack stack in that slot
     * @return true when the stack should be topped up
     */
    private boolean wantsRefill(int slot, ItemStack stack) {
        int count = stack.getCount();

        if (this.lastItemTrigger.get()) {
            Integer previous = this.lastCounts[slot];
            boolean dropped = previous != null && previous > count && count <= 1;

            this.lastCounts[slot] = count;
            return dropped;
        }

        return count <= this.topUpAt.get() && count < stack.getMaxCount();
    }

    /**
     * Checks whether a stack is one of the kinds the option asks for.
     *
     * @param stack stack to classify
     * @return true when the stack may be refilled
     */
    private boolean isConcerned(ItemStack stack) {
        Item item = stack.getItem();
        String path = Registries.ITEM.getId(item).getPath();

        if (this.anyStackable.get() && stack.getMaxCount() > 1) return true;
        if (this.wantTotems.get() && item == Items.TOTEM_OF_UNDYING) return true;
        if (this.wantPearls.get() && item == Items.ENDER_PEARL) return true;

        if (this.wantApples.get()
            && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) {
            return true;
        }

        if (this.wantRockets.get() && item == Items.FIREWORK_ROCKET) return true;
        if (this.wantBlocks.get() && item instanceof BlockItem) return true;
        if (this.wantFood.get() && stack.contains(DataComponentTypes.FOOD)) return true;
        if (this.wantTools.get() && this.isTool(item, path)) return true;
        if (this.wantWeapons.get() && this.isWeapon(item, path)) return true;

        if (this.wantProjectiles.get()
            && (item instanceof ArrowItem || item == Items.FIREWORK_ROCKET)) {
            return true;
        }

        return this.wantPotions.get() && item instanceof PotionItem;
    }

    /**
     * Checks whether an item is a tool.
     *
     * @param item item to check
     * @param path registry path of the item
     * @return true when the item is a tool
     */
    private boolean isTool(Item item, String path) {
        return item instanceof ShovelItem || item instanceof AxeItem
            || item instanceof HoeItem || path.contains("pickaxe");
    }

    /**
     * Checks whether an item is a weapon.
     *
     * @param item item to check
     * @param path registry path of the item
     * @return true when the item is a weapon
     */
    private boolean isWeapon(Item item, String path) {
        return path.contains("sword") || item instanceof BowItem || item instanceof CrossbowItem;
    }

    /**
     * Finds a source stack and moves it into the hotbar.
     *
     * @param slot hotbar slot to fill
     * @param stack stack currently in that slot
     * @return true when a move was started
     */
    private boolean tryRefill(int slot, ItemStack stack) {
        int source = this.findSource(stack);

        if (this.keepNames.get() && stack.getMaxCount() > 1) {
            String current = this.displayName(stack);
            if (this.trackedNames[slot] == null) this.trackedNames[slot] = current;

            if (source == -1) {
                this.trackedNames[slot] = null;
                return false;
            }

            String sourceName = this.displayName(this.mc.player.getInventory().getStack(source));

            if (!sourceName.equals(this.trackedNames[slot])) {
                if (stack.getCount() > 1) return false;

                this.trackedNames[slot] = sourceName;
            }
        }

        if (source == -1) return false;

        return this.moveToHotbar(source, slot);
    }

    /**
     * Moves one inventory stack into a hotbar slot.
     *
     * @param source inventory slot to take from
     * @param slot hotbar slot to fill
     * @return true when the move was started
     */
    private boolean moveToHotbar(int source, int slot) {
        if (!this.shiftMove.get()) {
            if (!QuietClicks.readyOrSettle()) return false;

            InvUtils.move().from(source).to(slot);
            return true;
        }

        if (!this.inPlayerMenu()) return false;

        if (this.instantMove.get()) {
            if (!QuietClicks.readyOrSettle()) return false;

            this.quickMove(source);
        } else {
            this.pendingMoves.add(source);
        }

        return true;
    }

    /**
     * Sends a shift-click on an inventory slot.
     *
     * @param source inventory slot to move
     */
    private void quickMove(int source) {
        this.mc.interactionManager.clickSlot(
            this.mc.player.playerScreenHandler.syncId, source, 0,
            SlotActionType.QUICK_MOVE, this.mc.player
        );
    }

    /**
     * Checks whether the player inventory is the open handler.
     *
     * @return true when clicks land on the player screen handler
     */
    private boolean inPlayerMenu() {
        return this.mc.player.currentScreenHandler == this.mc.player.playerScreenHandler;
    }

    /**
     * Returns the display name of a stack, custom name first.
     *
     * @param stack stack to name
     * @return display name
     */
    private String displayName(ItemStack stack) {
        Text custom = stack.get(DataComponentTypes.CUSTOM_NAME);
        return custom != null ? custom.getString() : stack.getItem().getName().getString();
    }

    /**
     * Finds the inventory stack that should feed a hotbar stack.
     *
     * @param target hotbar stack to feed
     * @return matching inventory slot, or -1 when there is none
     */
    private int findSource(ItemStack target) {
        int best = -1;

        for (int idx = MAIN_FIRST; idx <= MAIN_LAST; idx++) {
            ItemStack candidate = this.mc.player.getInventory().getStack(idx);
            if (!this.isCandidate(target, candidate)) continue;

            if (best == -1) {
                best = idx;
                if (this.sourcePick.get() == SourcePick.FirstMatch) break;

                continue;
            }

            int bestCount = this.mc.player.getInventory().getStack(best).getCount();
            if (this.isBetter(candidate.getCount(), bestCount)) best = idx;
        }

        return best;
    }

    /**
     * Compares two source stacks under the chosen preference.
     *
     * @param count candidate stack size
     * @param bestCount size of the current best stack
     * @return true when the candidate is preferred
     */
    private boolean isBetter(int count, int bestCount) {
        return switch (this.sourcePick.get()) {
            case FullStacks -> count > bestCount;
            case SmallStacks -> count < bestCount;
            case FirstMatch -> false;
        };
    }

    /**
     * Checks whether an inventory stack may feed a hotbar stack.
     *
     * @param target hotbar stack to feed
     * @param candidate inventory stack to inspect
     * @return true when the candidate matches
     */
    private boolean isCandidate(ItemStack target, ItemStack candidate) {
        if (candidate.isEmpty() || candidate.getItem() != target.getItem()) return false;
        if (target.getMaxCount() <= 1) return true;

        // Two fireworks can share the item and still be different rockets: one plain, one with a
        // flight duration or a shape. Matching the whole component set is what keeps the plain
        // stack out of a hotbar slot the bounce or a rocket boost is feeding from.
        return !this.keepNames.get()
            || ItemStack.areItemsAndComponentsEqual(target, candidate);
    }
}
