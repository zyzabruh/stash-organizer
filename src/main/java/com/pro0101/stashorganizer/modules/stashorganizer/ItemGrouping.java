package com.pro0101.stashorganizer.modules.stashorganizer;

import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Classifies items into groups. A group is a chest-sized bucket of items that belong together.
 */
public final class ItemGrouping {
    public enum Mode {
        /** One group per exact item. */
        Exact,
        /** Items of the same vanilla family (planks, logs, wool, stairs, swords, ores, ...). */
        Family,
        /** Broad categories like blocks, ores, food, redstone or tools. */
        Category,
        /** Items of the same metal or gem (the ore, the raw drop, the ingot, the block, ...). */
        Material
    }

    private record Family(TagKey<Item> tag, String name) {}

    private static final List<Family> FAMILIES = new ArrayList<>();

    static {
        add(ItemTags.PLANKS, "planks");
        add(ItemTags.LOGS, "logs");
        add(ItemTags.WOOL, "wool");
        add(ItemTags.WOOL_CARPETS, "carpets");
        add(ItemTags.LEAVES, "leaves");
        add(ItemTags.SAPLINGS, "saplings");
        add(ItemTags.STAIRS, "stairs");
        add(ItemTags.SLABS, "slabs");
        add(ItemTags.DOORS, "doors");
        add(ItemTags.TRAPDOORS, "trapdoors");
        add(ItemTags.FENCES, "fences");
        add(ItemTags.FENCE_GATES, "fence-gates");
        add(ItemTags.BUTTONS, "buttons");
        add(ItemTags.SIGNS, "signs");
        add(ItemTags.HANGING_SIGNS, "hanging-signs");
        add(ItemTags.BOATS, "boats");
        add(ItemTags.BEDS, "beds");
        add(ItemTags.BANNERS, "banners");
        add(ItemTags.CANDLES, "candles");
        add(ItemTags.SKULLS, "skulls");
        add(ItemTags.SHULKER_BOXES, "shulker-boxes");
        add(ItemTags.TERRACOTTA, "terracotta");
        add(ItemTags.SAND, "sand");
        add(ItemTags.COALS, "coal");
        add(ItemTags.IRON_ORES, "iron-ores");
        add(ItemTags.GOLD_ORES, "gold-ores");
        add(ItemTags.COPPER_ORES, "copper-ores");
        add(ItemTags.DIAMOND_ORES, "diamond-ores");
        add(ItemTags.EMERALD_ORES, "emerald-ores");
        add(ItemTags.REDSTONE_ORES, "redstone-ores");
        add(ItemTags.LAPIS_ORES, "lapis-ores");
        add(ItemTags.RAILS, "rails");
        add(ItemTags.ARROWS, "arrows");
        add(ItemTags.FISHES, "fish");
        add(ItemTags.MEAT, "meat");
        add(ItemTags.SWORDS, "swords");
        add(ItemTags.PICKAXES, "pickaxes");
        add(ItemTags.AXES, "axes");
        add(ItemTags.SHOVELS, "shovels");
        add(ItemTags.HOES, "hoes");
        add(ItemTags.HEAD_ARMOR, "helmets");
        add(ItemTags.CHEST_ARMOR, "chestplates");
        add(ItemTags.LEG_ARMOR, "leggings");
        add(ItemTags.FOOT_ARMOR, "boots");
        add(ItemTags.BUNDLES, "bundles");
        add(ItemTags.COMPASSES, "compasses");
        add(ItemTags.LANTERNS, "lanterns");
        add(ItemTags.CHAINS, "chains");
        add(ItemTags.EGGS, "eggs");
    }

    private static void add(TagKey<Item> tag, String name) {
        FAMILIES.add(new Family(tag, name));
    }

    private ItemGrouping() {
    }

    public static String key(ItemStack stack, Mode mode) {
        if (stack.isEmpty()) return "empty";

        Item item = stack.getItem();
        String id = Registries.ITEM.getId(item).getPath();
        String namespace = Registries.ITEM.getId(item).getNamespace();

        if (!"minecraft".equals(namespace)) return "mod:" + namespace + ":" + id;

        return switch (mode) {
            case Exact -> "item:" + id;
            case Family -> familyKey(stack, id);
            case Category -> "category:" + kindOfCategory(stack, id);
            case Material -> materialKey(stack, id);
        };
    }

    private static String familyKey(ItemStack stack, String id) {
        for (Family family : FAMILIES) {
            if (stack.isIn(family.tag())) return "family:" + family.name();
        }

        return "item:" + id;
    }

    /**
     * Group of an item by the metal or gem it is made of: the ore, the raw drop, the ingot, the
     * nugget and the block of one material share a chest. Anything else falls back to the family
     * grouping, so unrelated items keep a sensible chest.
     */
    private static String materialKey(ItemStack stack, String id) {
        String material = materialName(id);
        if (material != null) return "material:" + material;

        return familyKey(stack, id);
    }

    /**
     * Canonical material of an item path, or null when the item is not a raw material. The
     * prefixes and suffixes are stripped in the order the game names them, so
     * {@code deepslate_iron_ore}, {@code raw_iron}, {@code raw_iron_block}, {@code iron_ingot} and
     * {@code iron_block} all resolve to {@code iron}.
     */
    static String materialName(String id) {
        String base = id;

        if (base.startsWith("deepslate_")) base = base.substring("deepslate_".length());
        if (base.startsWith("nether_")) base = base.substring("nether_".length());
        if (base.startsWith("raw_")) base = base.substring("raw_".length());

        if (base.endsWith("_ore")) base = base.substring(0, base.length() - "_ore".length());
        else if (base.endsWith("_ingot")) base = base.substring(0, base.length() - "_ingot".length());
        else if (base.endsWith("_nugget")) base = base.substring(0, base.length() - "_nugget".length());
        else if (base.endsWith("_block")) base = base.substring(0, base.length() - "_block".length());
        else if (base.endsWith("_scrap")) base = base.substring(0, base.length() - "_scrap".length());
        else if (base.endsWith("_shard")) base = base.substring(0, base.length() - "_shard".length());

        String alias = MATERIAL_ALIASES.get(base);
        if (alias != null) return alias;

        return MATERIALS.contains(base) ? base : null;
    }

    /** Materials whose ore, raw drop, ingot, nugget and block share one group. */
    private static final Set<String> MATERIALS = Set.of(
        "iron", "gold", "copper", "netherite", "diamond", "emerald", "coal", "redstone", "lapis",
        "quartz", "amethyst"
    );

    /** Items that hold a material under another name. */
    private static final Map<String, String> MATERIAL_ALIASES = Map.of(
        "lapis_lazuli", "lapis",
        "charcoal", "coal",
        "ancient_debris", "netherite"
    );

    /**
     * Kind of a stack, read from the game itself: its tags, its item type and whether it is a block
     * item. A shulker box counts as the item it mostly holds, the same way its group is chosen, so
     * a chest of boxes follows their contents. This is the kind the planner and the sorter use, so
     * a group of plain blocks is recognised as blocks in every grouping mode.
     */
    public static String kind(ItemStack stack) {
        if (stack.isEmpty()) return "misc";

        if (stack.isIn(ItemTags.SHULKER_BOXES)) {
            ItemStack content = dominantContent(stack);
            if (content != null) return kind(content);
        }

        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (!"minecraft".equals(id.getNamespace())) return "misc";

        return kindOfCategory(stack, id.getPath());
    }

    /**
     * Kind guessed from a group key alone, used only when the items of the group are not at hand:
     * redstone, blocks, ores, food, gear, brewing or the catch-all misc. A key carries the grouping
     * mode in its prefix (category, material, kit, family, item), so most keys still name a kind;
     * an exact item key has only the item id to go on, which is why {@link #kind(ItemStack)} is
     * preferred whenever the real items are known.
     */
    public static String kindOfKey(String groupKey) {
        if (groupKey == null || groupKey.isEmpty()) return "misc";

        int separator = groupKey.indexOf(':');
        if (separator < 0) return "misc";

        String prefix = groupKey.substring(0, separator);
        String name = groupKey.substring(separator + 1);

        return switch (prefix) {
            // Category keys already name a kind.
            case "category" -> name;
            case "material" -> "ores";
            case "kit" -> "gear";
            case "family" -> familyKind(name);
            // An exact item key carries no category: its name is all there is to go on.
            case "item" -> nameKind(name);
            default -> "misc";
        };
    }

    /** Kind of a vanilla family, named as in {@link #FAMILIES}. */
    private static String familyKind(String family) {
        return switch (family) {
            case "planks", "logs", "wool", "carpets", "leaves", "saplings", "stairs", "slabs", "doors",
                 "trapdoors", "fences", "fence-gates", "signs", "hanging-signs", "terracotta", "sand",
                 "beds", "banners", "candles", "skulls" -> "blocks";
            case "buttons", "rails" -> "redstone";
            case "coal", "iron-ores", "gold-ores", "copper-ores", "diamond-ores", "emerald-ores",
                 "redstone-ores", "lapis-ores" -> "ores";
            case "fishes", "meat", "eggs" -> "food";
            case "swords", "pickaxes", "axes", "shovels", "hoes", "helmets", "chestplates", "leggings",
                 "boots", "arrows" -> "gear";
            default -> "misc";
        };
    }

    /** Kind guessed from an item id, for the modes whose keys name a single item. */
    private static String nameKind(String id) {
        if (is(id, REDSTONE)) return "redstone";
        if (is(id, FOOD)) return "food";
        if (is(id, BREWING)) return "brewing";
        if (is(id, TOOLS)) return "gear";
        if (is(id, ORES) || materialName(id) != null) return "ores";

        return "misc";
    }

    private static String kindOfCategory(ItemStack stack, String id) {
        if (stack.isIn(ItemTags.MEAT) || stack.isIn(ItemTags.FISHES) || is(id, FOOD)) return "food";

        if (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.AXES)
            || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES)
            || stack.isIn(ItemTags.HEAD_ARMOR) || stack.isIn(ItemTags.CHEST_ARMOR)
            || stack.isIn(ItemTags.LEG_ARMOR) || stack.isIn(ItemTags.FOOT_ARMOR)
            || is(id, TOOLS)) return "gear";

        if (is(id, REDSTONE)) return "redstone";
        if (is(id, BREWING)) return "brewing";

        if (is(id, ORES)) return "ores";
        if (stack.getItem() instanceof BlockItem) return "blocks";

        return "misc";
    }

    /**
     * The item type that fills most of a shulker box, or null for an empty box. A shulker box is
     * stored with the chest of the item it holds instead of every box ending up in one chest.
     */
    public static ItemStack dominantContent(ItemStack shulker) {
        if (shulker.isEmpty() || !shulker.isIn(ItemTags.SHULKER_BOXES)) return null;

        List<ItemStack> contents = InventoryTools.contentsOf(shulker);
        ItemStack best = null;
        int bestCount = 0;

        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;

            int total = 0;
            for (ItemStack other : contents) {
                if (!other.isEmpty() && ItemStack.areItemsAndComponentsEqual(other, stack)) total += other.getCount();
            }

            if (total > bestCount) {
                best = stack;
                bestCount = total;
            }
        }

        return best;
    }

    /** Armour, tools and weapons that make up an equipment kit. */
    public static boolean isGear(ItemStack stack) {
        if (stack.isEmpty()) return false;

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        if (!"minecraft".equals(itemId.getNamespace())) return false;

        if (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.AXES)
            || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES)
            || stack.isIn(ItemTags.HEAD_ARMOR) || stack.isIn(ItemTags.CHEST_ARMOR)
            || stack.isIn(ItemTags.LEG_ARMOR) || stack.isIn(ItemTags.FOOT_ARMOR)) {
            return true;
        }

        String path = itemId.getPath();
        for (String extra : KIT_EXTRAS) {
            if (path.equals(extra)) return true;
        }

        return false;
    }

    /** A kit groups all equipment of one material together (for example all diamond gear). */
    public static String kitKey(ItemStack stack) {
        return "kit:" + materialOf(Registries.ITEM.getId(stack.getItem()).getPath());
    }

    private static String materialOf(String id) {
        for (String material : KIT_MATERIALS) {
            if (id.startsWith(material + "_")) return material;
        }

        return "other";
    }

    private static final String[] KIT_MATERIALS = {
        "netherite", "diamond", "iron", "golden", "chainmail", "leather", "stone", "wooden", "copper"
    };

    private static final String[] KIT_EXTRAS = {
        "bow", "crossbow", "trident", "mace", "shield", "elytra", "fishing_rod", "shears",
        "flint_and_steel", "turtle_helmet"
    };

    private static boolean is(String id, String[] words) {
        for (String word : words) {
            if (id.contains(word)) return true;
        }
        return false;
    }

    private static final String[] FOOD = {
        "apple", "bread", "carrot", "potato", "beetroot", "cookie", "cake", "pie", "stew", "soup",
        "beef", "porkchop", "chicken", "mutton", "rabbit", "cod", "salmon", "tropical_fish",
        "pufferfish", "melon_slice", "berries", "chorus_fruit", "honey_bottle", "milk_bucket",
        "sugar", "wheat", "seeds", "kelp", "dried_kelp", "mushroom_stew", "glow_berries"
    };

    private static final String[] TOOLS = {
        "sword", "pickaxe", "shovel", "axe", "hoe", "shears", "fishing_rod", "shield", "bow",
        "crossbow", "trident", "mace", "flint_and_steel", "elytra", "helmet", "chestplate",
        "leggings", "boots", "arrow", "rocket"
    };

    private static final String[] REDSTONE = {
        "redstone", "repeater", "comparator", "piston", "observer", "dispenser", "dropper",
        "hopper", "rail", "lever", "pressure_plate", "torch", "daylight_detector", "target",
        "tripwire", "note_block", "jukebox", "sculk_sensor", "calibrated_sculk_sensor", "crafter"
    };

    private static final String[] BREWING = {
        "potion", "glass_bottle", "brewing_stand", "blaze_powder", "nether_wart", "glowstone_dust",
        "gunpowder", "dragon_breath", "fermented_spider_eye", "rabbit_foot", "magma_cream",
        "spider_eye", "ghast_tear", "phantom_membrane", "sugar"
    };

    private static final String[] ORES = {
        "ore", "ingot", "nugget", "raw_", "diamond", "emerald", "coal", "redstone", "lapis",
        "quartz", "amethyst", "netherite", "echo_shard", "ancient_debris"
    };

    /** Human readable name for a group key, used in chat reports. */
    public static String displayName(String key) {
        int index = key.indexOf(':');
        String name = index >= 0 ? key.substring(index + 1) : key;
        return name.replace('_', ' ');
    }
}
