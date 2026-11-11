package com.pro0101.stashorganizer.modules.render;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.MinecartEntity;

/**
 * Outlines entities through walls, one colour per kind.
 *
 * <p>Nothing is re-rendered: the module only answers two vanilla questions. {@code isGlowing}
 * decides whether an entity owns an outline at all, and {@code getTeamColorValue} decides its
 * colour, both of which vanilla already asks once per entity and per frame. Forcing those answers
 * keeps the whole vanilla outline pass, its depth handling and its post effect, instead of building
 * a second renderer beside it.
 *
 * <p>Textured chams are a different pass, already covered by Meteor's own {@code Chams} module.
 */
public class EntityGlow extends Module {
    private final SettingGroup general = this.settings.getDefaultGroup();
    private final SettingGroup colors = this.settings.createGroup("Colors");
    private final SettingGroup chain = this.settings.createGroup("Chain");

    private final Setting<Boolean> players = this.general.add(new BoolSetting.Builder()
        .name("players")
        .description("Outline other players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> self = this.general.add(new BoolSetting.Builder()
        .name("self")
        .description("Outline yourself, seen in third person.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> monsters = this.general.add(new BoolSetting.Builder()
        .name("monsters")
        .description("Outline hostile mobs.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> animals = this.general.add(new BoolSetting.Builder()
        .name("animals")
        .description("Outline passive animals.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> items = this.general.add(new BoolSetting.Builder()
        .name("items")
        .description("Outline dropped items.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> vehicles = this.general.add(new BoolSetting.Builder()
        .name("vehicles")
        .description("Outline boats and minecarts.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> storages = this.general.add(new BoolSetting.Builder()
        .name("storages")
        .description("Outline chest and hopper minecarts, the only storage entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> crystals = this.general.add(new BoolSetting.Builder()
        .name("crystals")
        .description("Outline end crystals.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> pearls = this.general.add(new BoolSetting.Builder()
        .name("pearls")
        .description("Outline ender pearls in flight.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SettingColor> playersColor = this.colors.add(new ColorSetting.Builder()
        .name("players-color")
        .description("Outline colour for players.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .build()
    );

    private final Setting<SettingColor> monstersColor = this.colors.add(new ColorSetting.Builder()
        .name("monsters-color")
        .description("Outline colour for hostile mobs.")
        .defaultValue(new SettingColor(255, 40, 40, 255))
        .build()
    );

    private final Setting<SettingColor> animalsColor = this.colors.add(new ColorSetting.Builder()
        .name("animals-color")
        .description("Outline colour for passive animals.")
        .defaultValue(new SettingColor(80, 255, 80, 255))
        .build()
    );

    private final Setting<SettingColor> itemsColor = this.colors.add(new ColorSetting.Builder()
        .name("items-color")
        .description("Outline colour for dropped items, crystals and pearls.")
        .defaultValue(new SettingColor(255, 220, 60, 255))
        .build()
    );

    private final Setting<SettingColor> otherColor = this.colors.add(new ColorSetting.Builder()
        .name("other-color")
        .description("Outline colour for vehicles and storage minecarts.")
        .defaultValue(new SettingColor(180, 120, 255, 255))
        .build()
    );

    private final Setting<SettingColor> fallbackColor = this.colors.add(new ColorSetting.Builder()
        .name("fallback-color")
        .description("Used when the game tints outlines itself instead of asking the entity, which leaves no per-kind colour to give.")
        .defaultValue(new SettingColor(0, 255, 255, 255))
        .build()
    );

    private final Setting<Boolean> customChain = this.chain.add(new BoolSetting.Builder()
        .name("custom-chain")
        .description("Replace the game's entity outline effect with the chams chain, which adds a glow and a solid fill.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> glow = this.chain.add(new BoolSetting.Builder()
        .name("glow")
        .description("Widen the outline into a halo.")
        .defaultValue(true)
        .visible(this.customChain::get)
        .build()
    );

    private final Setting<Integer> glowRadius = this.chain.add(new IntSetting.Builder()
        .name("glow-radius")
        .description("Halo radius in pixels. Every step costs another pair of texture reads per pixel, so keep it small.")
        .defaultValue(2)
        .range(0, 12)
        .visible(() -> this.customChain.get() && this.glow.get())
        .build()
    );

    private final Setting<Double> glowIntensity = this.chain.add(new DoubleSetting.Builder()
        .name("glow-intensity")
        .description("How strongly the halo shows.")
        .defaultValue(1.5)
        .range(0, 3)
        .visible(() -> this.customChain.get() && this.glow.get())
        .build()
    );

    private final Setting<Boolean> fill = this.chain.add(new BoolSetting.Builder()
        .name("fill")
        .description("Fill the silhouette with a semi-transparent colour.")
        .defaultValue(true)
        .visible(this.customChain::get)
        .build()
    );

    private final Setting<Double> fillAlpha = this.chain.add(new DoubleSetting.Builder()
        .name("fill-alpha")
        .description("Opacity of the filled silhouette.")
        .defaultValue(0.4)
        .range(0, 1)
        .visible(() -> this.customChain.get() && this.fill.get())
        .build()
    );

    public EntityGlow() {
        super(StashOrganizerAddon.CATEGORY, "entity-glow", "Outlines players, mobs and storage entities through walls, with a colour per kind.");
    }

    /**
     * Reports whether the chams chain should replace the vanilla outline effect.
     *
     * @return true when the chain is wanted
     */
    public boolean usesChain() {
        return this.customChain.get();
    }

    /**
     * Returns the glow radius to hand to the chain.
     *
     * @return radius in pixels, zero when the glow is off
     */
    public int chainGlowRadius() {
        return this.glow.get() ? this.glowRadius.get() : 0;
    }

    /**
     * Returns the glow intensity to hand to the chain.
     *
     * @return intensity, zero when the glow is off
     */
    public float chainGlowIntensity() {
        return this.glow.get() ? this.glowIntensity.get().floatValue() : 0.0F;
    }

    /**
     * Returns the fill opacity to hand to the chain.
     *
     * @return opacity, zero when the fill is off
     */
    public float chainFillAlpha() {
        return this.fill.get() ? this.fillAlpha.get().floatValue() : 0.0F;
    }

    /**
     * Reports whether this entity should own an outline.
     *
     * @param entity entity about to be rendered
     * @return true when the module targets it
     */
    public boolean shouldGlow(Entity entity) {
        return this.kindOf(entity) != null;
    }

    /**
     * Returns the packed outline colour of an entity.
     *
     * @param entity entity about to be rendered
     * @return packed ARGB colour, or {@code -1} when the entity is not targeted
     */
    public int packedColor(Entity entity) {
        SettingColor color = this.colorOf(this.kindOf(entity));
        if (color == null) return -1;

        return (color.a << 24) | (color.r << 16) | (color.g << 8) | color.b;
    }

    /**
     * Returns the colour to use when the game picks the outline tint itself.
     *
     * @return packed ARGB colour
     */
    public int fallback() {
        SettingColor color = this.fallbackColor.get();
        return (color.a << 24) | (color.r << 16) | (color.g << 8) | color.b;
    }

    /** Categories the module knows how to outline. */
    private enum Kind {
        Player,
        Monster,
        Animal,
        Item,
        Vehicle,
        Storage,
        Crystal,
        Pearl
    }

    /**
     * Classifies an entity against the enabled categories.
     *
     * @param entity entity to classify
     * @return its category, or null when it is not targeted
     */
    private Kind kindOf(Entity entity) {
        if (entity == null) return null;

        MinecraftClient client = MinecraftClient.getInstance();

        if (entity == client.player) return this.self.get() ? Kind.Player : null;

        // The order matters: a chest minecart is both a minecart and a storage entity.
        if (entity instanceof ChestMinecartEntity || entity instanceof HopperMinecartEntity) {
            return this.storages.get() ? Kind.Storage : null;
        }

        if (entity instanceof PlayerEntity) return this.players.get() ? Kind.Player : null;
        if (entity instanceof Monster) return this.monsters.get() ? Kind.Monster : null;
        if (entity instanceof AnimalEntity) return this.animals.get() ? Kind.Animal : null;
        if (entity instanceof ItemEntity) return this.items.get() ? Kind.Item : null;
        if (entity instanceof EndCrystalEntity) return this.crystals.get() ? Kind.Crystal : null;
        if (entity instanceof EnderPearlEntity) return this.pearls.get() ? Kind.Pearl : null;
        if (entity instanceof BoatEntity || entity instanceof MinecartEntity) {
            return this.vehicles.get() ? Kind.Vehicle : null;
        }

        return null;
    }

    /**
     * Maps a category to its configured colour.
     *
     * @param kind category, may be null
     * @return colour, or null when there is nothing to draw
     */
    private SettingColor colorOf(Kind kind) {
        if (kind == null) return null;

        return switch (kind) {
            case Player -> this.playersColor.get();
            case Monster -> this.monstersColor.get();
            case Animal -> this.animalsColor.get();
            case Item, Crystal, Pearl -> this.itemsColor.get();
            case Vehicle, Storage -> this.otherColor.get();
        };
    }
}
