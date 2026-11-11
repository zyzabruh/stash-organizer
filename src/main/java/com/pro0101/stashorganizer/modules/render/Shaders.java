package com.pro0101.stashorganizer.modules.render;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
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
 * Draws a glowing, filled outline around entities, one colour per kind.
 *
 * <p>Nothing is re-rendered: the module only answers two vanilla questions. {@code isGlowing}
 * decides whether an entity owns an outline at all, and {@code getTeamColorValue} decides its
 * colour, both of which vanilla already asks once per entity and per frame. Forcing those answers
 * keeps the whole vanilla outline pass and its depth handling, and the chams chain in
 * {@link com.pro0101.stashorganizer.render.ChamsChain} replaces only the effect applied to the
 * silhouettes it produces.
 *
 * <p>Three layers make the picture, and each one switches independently: a solid rim of
 * {@code outline-width} pixels, a soft halo of {@code glow-radius}, and a translucent fill of the
 * body at {@code fill-alpha}. The modes preset those three rather than replacing them, so a preset
 * can still be tuned.
 */
public class Shaders extends Module {
    /** Presets that decide which of the three layers are drawn. */
    public enum Mode {
        /** Solid rim only. */
        Outline,
        /** Soft halo only. */
        Glow,
        /** Translucent body only, the see-through look. */
        Chams,
        /** Rim, halo and body together. */
        Full,
        /** Wide bright rim, tight halo and a faint body, for a neon sign look. */
        Neon,
        /** No rim, soft halo and a see-through body, for a ghost look. */
        Ghost,
        /** The individual switches below decide. */
        Custom
    }

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

    private final Setting<Double> range = this.general.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only entities within this distance are outlined. Zero removes the limit.")
        .defaultValue(128.0)
        .range(0.0, 512.0)
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

    private final Setting<Mode> mode = this.chain.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Outline, Glow, Chams and Full preset the three layers; Custom leaves them to the switches below.")
        .defaultValue(Mode.Full)
        .visible(this.customChain::get)
        .build()
    );

    private final Setting<Integer> outlineWidth = this.chain.add(new IntSetting.Builder()
        .name("outline-width")
        .description("Thickness of the solid rim in pixels. Zero leaves only the glow and the fill.")
        .defaultValue(1)
        .range(0, 6)
        .visible(() -> this.customChain.get() && this.usesRim())
        .build()
    );

    private final Setting<Boolean> glow = this.chain.add(new BoolSetting.Builder()
        .name("glow")
        .description("Widen the outline into a halo.")
        .defaultValue(true)
        .visible(() -> this.customChain.get() && this.mode.get() == Mode.Custom)
        .build()
    );

    private final Setting<Integer> glowRadius = this.chain.add(new IntSetting.Builder()
        .name("glow-radius")
        .description("Halo radius in pixels. Every step costs another pair of texture reads per pixel, so keep it small.")
        .defaultValue(2)
        .range(0, 12)
        .visible(() -> this.customChain.get() && this.usesGlow())
        .build()
    );

    private final Setting<Double> glowIntensity = this.chain.add(new DoubleSetting.Builder()
        .name("glow-intensity")
        .description("How strongly the halo shows.")
        .defaultValue(1.5)
        .range(0, 3)
        .visible(() -> this.customChain.get() && this.usesGlow())
        .build()
    );

    private final Setting<Boolean> fill = this.chain.add(new BoolSetting.Builder()
        .name("fill")
        .description("Fill the silhouette with a semi-transparent colour.")
        .defaultValue(true)
        .visible(() -> this.customChain.get() && this.mode.get() == Mode.Custom)
        .build()
    );

    private final Setting<Double> fillAlpha = this.chain.add(new DoubleSetting.Builder()
        .name("fill-alpha")
        .description("Opacity of the filled silhouette.")
        .defaultValue(0.4)
        .range(0, 1)
        .visible(() -> this.customChain.get() && this.usesFill())
        .build()
    );

    private final Setting<Double> fillBrightness = this.chain.add(new DoubleSetting.Builder()
        .name("fill-brightness")
        .description("Multiplies the body colour, so the fill can sit darker than the rim it belongs to.")
        .defaultValue(1.0)
        .range(0, 2)
        .visible(() -> this.customChain.get() && this.usesFill())
        .build()
    );

    private final Setting<Double> innerGlow = this.chain.add(new DoubleSetting.Builder()
        .name("inner-glow")
        .description("Brightens the body with the blurred silhouette, so the core of an entity glows too instead of staying flat.")
        .defaultValue(0.35)
        .range(0, 1)
        .visible(() -> this.customChain.get() && this.usesFill())
        .build()
    );

    public Shaders() {
        super(StashOrganizerAddon.CATEGORY, "shaders", "Outlines players, mobs and storage entities through walls, with a glow, a rim and a fill.");
    }

    /**
     * Returns the active preset.
     *
     * @return shader mode
     */
    public Mode mode() {
        return this.mode.get();
    }

    /**
     * Returns the player outline colour, for previews.
     *
     * @return player colour
     */
    public SettingColor playerColor() {
        return this.playersColor.get();
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
     * @return radius in pixels, zero when the preset has no halo
     */
    public int chainGlowRadius() {
        return switch (this.mode.get()) {
            case Neon -> 4;
            case Ghost -> 2;
            case Glow, Full -> this.glowRadius.get();
            case Custom -> this.glow.get() ? this.glowRadius.get() : 0;
            default -> 0;
        };
    }

    /**
     * Returns the glow intensity to hand to the chain.
     *
     * @return intensity, zero when the preset has no halo
     */
    public float chainGlowIntensity() {
        return switch (this.mode.get()) {
            case Neon -> 2.2F;
            case Ghost -> 1.0F;
            case Glow, Full -> this.glowIntensity.get().floatValue();
            case Custom -> this.glow.get() ? this.glowIntensity.get().floatValue() : 0.0F;
            default -> 0.0F;
        };
    }

    /**
     * Returns the fill opacity to hand to the chain.
     *
     * @return opacity, zero when the preset has no fill
     */
    public float chainFillAlpha() {
        return switch (this.mode.get()) {
            case Neon -> 0.15F;
            case Ghost -> 0.25F;
            case Chams, Full -> this.fillAlpha.get().floatValue();
            case Custom -> this.fill.get() ? this.fillAlpha.get().floatValue() : 0.0F;
            default -> 0.0F;
        };
    }

    /**
     * Returns the rim thickness to hand to the chain.
     *
     * @return thickness in pixels, zero when the preset has no rim
     */
    public int chainOutlineWidth() {
        return switch (this.mode.get()) {
            case Neon -> 2;
            case Ghost -> 0;
            case Outline, Full, Custom -> this.outlineWidth.get();
            default -> 0;
        };
    }

    /**
     * Returns the inner glow to hand to the chain.
     *
     * @return how much the body is brightened by the blurred silhouette
     */
    public float chainInnerGlow() {
        return switch (this.mode.get()) {
            case Neon -> 0.6F;
            case Ghost -> 0.2F;
            // Chams and Full always fill, so the inner glow is theirs to use directly.
            case Chams, Full -> this.innerGlow.get().floatValue();
            case Custom -> this.fill.get() ? this.innerGlow.get().floatValue() : 0.0F;
            default -> 0.0F;
        };
    }

    /**
     * Returns the body brightness to hand to the chain.
     *
     * @return multiplier applied to the fill colour
     */
    public float chainFillBrightness() {
        return this.fillBrightness.get().floatValue();
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

    private boolean usesRim() {
        Mode current = this.mode.get();

        return current == Mode.Outline || current == Mode.Full || current == Mode.Neon || current == Mode.Custom;
    }

    private boolean usesGlow() {
        Mode current = this.mode.get();

        if (current == Mode.Custom) return this.glow.get();

        return current == Mode.Glow || current == Mode.Full || current == Mode.Neon || current == Mode.Ghost;
    }

    private boolean usesFill() {
        Mode current = this.mode.get();

        if (current == Mode.Custom) return this.fill.get();

        return current == Mode.Chams || current == Mode.Full || current == Mode.Neon || current == Mode.Ghost;
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
     * Classifies an entity against the enabled categories and the distance limit.
     *
     * @param entity entity to classify
     * @return its category, or null when it is not targeted
     */
    private Kind kindOf(Entity entity) {
        if (entity == null) return null;

        MinecraftClient client = MinecraftClient.getInstance();

        if (entity == client.player) return this.self.get() ? Kind.Player : null;

        double limit = this.range.get();
        if (limit > 0.0 && client.player != null
            && entity.squaredDistanceTo(client.player) > limit * limit) {
            return null;
        }

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
