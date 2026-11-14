package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.visual.RenderOptimizer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drops the particles the client is about to spawn.
 *
 * <p>Only the client entry points are touched: the server still sends its particle packets, they are
 * simply not turned into particle entities, so nothing changes for anyone else.
 *
 * <p>{@code addParticleClient} has two overloads — one with the two flags and one without — and both
 * are hooked. Selecting by name alone is not enough: Mixin binds the first method it finds with that
 * name and then rejects the handler for having the wrong parameters, which aborts the whole
 * transformation. The descriptors are therefore written out, and both carry {@code require = 0} so a
 * version that keeps only one of them still starts.
 */
@Mixin(World.class)
public abstract class WorldParticlesMixin {
    /**
     * Ignores a particle spawn that carries the flags.
     *
     * @param effect particle to spawn
     * @param force whether the particle ignores the player's setting
     * @param important whether the particle must be sent to the server
     * @param x x position
     * @param y y position
     * @param z z position
     * @param velocityX x velocity
     * @param velocityY y velocity
     * @param velocityZ z velocity
     * @param info injection callback
     */
    @Inject(
        method = "addParticleClient(Lnet/minecraft/particle/ParticleEffect;ZZDDDDDD)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void stashorganizer$noParticlesWithFlags(
        ParticleEffect effect, boolean force, boolean important,
        double x, double y, double z, double velocityX, double velocityY, double velocityZ,
        CallbackInfo info
    ) {
        if (this.cancelled()) info.cancel();
    }

    /**
     * Ignores a particle spawn without the flags.
     *
     * @param effect particle to spawn
     * @param x x position
     * @param y y position
     * @param z z position
     * @param velocityX x velocity
     * @param velocityY y velocity
     * @param velocityZ z velocity
     * @param info injection callback
     */
    @Inject(
        method = "addParticleClient(Lnet/minecraft/particle/ParticleEffect;DDDDDD)V",
        at = @At("HEAD"),
        cancellable = true,
        require = 0
    )
    private void stashorganizer$noParticles(
        ParticleEffect effect,
        double x, double y, double z, double velocityX, double velocityY, double velocityZ,
        CallbackInfo info
    ) {
        if (this.cancelled()) info.cancel();
    }

    private boolean cancelled() {
        RenderOptimizer module = Modules.get().get(RenderOptimizer.class);

        return module != null && module.isActive() && module.noParticles();
    }
}
