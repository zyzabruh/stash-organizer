package com.pro0101.stashorganizer.modules.visual;

import com.pro0101.stashorganizer.StashOrganizerAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Spawns ambient particles in the air in front of the player.
 *
 * <p>The particles are ordinary client-side particles, added through the same call the game uses
 * for its own effects: nothing is sent to the server, and they live and die on their own. The module
 * only decides where new ones appear.
 *
 * <p>Positions are sampled in a cone around the exact look direction, pitch included, so they stay
 * in the view whether you look at the horizon, up into the canopy or down at the ground. Pale oak
 * leaves are the default because they are the small white petals the vanilla pale garden drifts
 * around; the other types cover the usual ambient looks without a resource pack.
 */
public class WorldParticles extends Module {
    /** Particle looks. */
    public enum Kind {
        /** Small white petals, the pale garden look. */
        PaleOak,
        /** Pink cherry petals. */
        Cherry,
        /** White flakes from the basalt deltas. */
        WhiteAsh,
        /** Tiny white spores. */
        SporeBlossom,
        /** White snowflakes. */
        Snowflake,
        /** Soft white sparks that drift upward. */
        EndRod,
        /** Short cyan sparks. */
        ElectricSpark,
        /** Small glowing dots. */
        Glow
    }

    /** Half-angle of the cone around the look direction. */
    private static final double cone = Math.toRadians(55.0);

    /** Closest a particle may appear, so none is spawned on the camera. */
    private static final double inner = 2.0;

    private final SettingGroup general = this.settings.getDefaultGroup();

    private final Setting<Kind> kind = this.general.add(new EnumSetting.Builder<Kind>()
        .name("particle")
        .description("Which particle is spawned.")
        .defaultValue(Kind.PaleOak)
        .build()
    );

    private final Setting<Integer> rate = this.general.add(new IntSetting.Builder()
        .name("rate")
        .description("How many particles are spawned per second.")
        .defaultValue(24)
        .range(1, 200)
        .sliderRange(1, 80)
        .build()
    );

    private final Setting<Integer> radius = this.general.add(new IntSetting.Builder()
        .name("radius")
        .description("How far in front of you particles are spawned, in blocks.")
        .defaultValue(16)
        .range(4, 48)
        .build()
    );

    private final Setting<Double> speed = this.general.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Multiplier applied to the random drift given to each particle.")
        .defaultValue(1.0)
        .range(0.0, 3.0)
        .build()
    );

    /** Fractional particles carried between ticks, so a rate below twenty still works. */
    private double pending;

    public WorldParticles() {
        super(StashOrganizerAddon.VISUALS, "world-particles", "Spawns ambient particles in front of you.");
    }

    @Override
    public void onDeactivate() {
        this.pending = 0.0;
    }

    /**
     * Spawns the particles earned by the current rate.
     *
     * @param event post-tick event
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (this.mc.world == null || this.mc.player == null) return;

        this.pending += this.rate.get() / 20.0;

        int count = (int) this.pending;
        if (count <= 0) return;

        this.pending -= count;

        Vec3d eye = this.mc.player.getEyePos();
        // getRotationVec is already a unit vector, and both cross products below are between
        // perpendicular unit vectors, so no extra normalisation is needed.
        Vec3d look = this.mc.player.getRotationVec(1.0F);
        Vec3d right = this.axis(look);
        Vec3d up = right.crossProduct(look);

        double limit = this.radius.get();
        double drift = this.speed.get() * 0.02;
        ParticleEffect effect = this.effect();

        for (int i = 0; i < count; i++) {
            double theta = Math.random() * Math.PI * 2.0;
            double angle = cone * Math.sqrt(Math.random());
            double distance = inner + Math.random() * (limit - inner);

            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            Vec3d direction = look.multiply(cos)
                .add(right.multiply(Math.cos(theta) * sin))
                .add(up.multiply(Math.sin(theta) * sin));

            Vec3d pos = eye.add(direction.multiply(distance));
            BlockPos block = BlockPos.ofFloored(pos);

            if (this.mc.world.getBlockState(block).isSolidBlock(this.mc.world, block)) continue;

            this.mc.world.addParticleClient(
                effect, pos.x, pos.y, pos.z,
                (Math.random() - 0.5) * drift,
                (Math.random() - 0.5) * drift,
                (Math.random() - 0.5) * drift
            );
        }
    }

    /**
     * Returns an axis perpendicular to the look direction.
     *
     * <p>Looking straight up or down makes the usual up-vector cross product vanish, so that case
     * falls back to the world X axis.
     *
     * @param look look direction
     * @return unit axis perpendicular to {@code look}
     */
    private Vec3d axis(Vec3d look) {
        Vec3d axis = look.crossProduct(new Vec3d(0.0, 1.0, 0.0));

        return axis.lengthSquared() < 1.0e-6 ? new Vec3d(1.0, 0.0, 0.0) : axis.normalize();
    }

    /**
     * Returns the particle type selected in the settings.
     *
     * @return selected particle
     */
    private ParticleEffect effect() {
        return switch (this.kind.get()) {
            case PaleOak -> ParticleTypes.PALE_OAK_LEAVES;
            case Cherry -> ParticleTypes.CHERRY_LEAVES;
            case WhiteAsh -> ParticleTypes.WHITE_ASH;
            case SporeBlossom -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case Snowflake -> ParticleTypes.SNOWFLAKE;
            case EndRod -> ParticleTypes.END_ROD;
            case ElectricSpark -> ParticleTypes.ELECTRIC_SPARK;
            case Glow -> ParticleTypes.GLOW;
        };
    }
}
