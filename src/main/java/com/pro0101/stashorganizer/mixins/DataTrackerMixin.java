package com.pro0101.stashorganizer.mixins;

import com.pro0101.stashorganizer.modules.hyperglide.utilities.Flight;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracked;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the local gliding state while flight is spoofed.
 *
 * <p>Spoofing flies without a real elytra, so the server keeps reporting the player as not gliding. Those updates are
 * rewritten on the way out to keep the client in the simulated flight state.
 */
@Mixin(DataTracker.class)
public abstract class DataTrackerMixin {
    private static final int flags = 0;

    @Shadow
    @Final
    private DataTracked trackedEntity;

    /**
     * Rewrites outgoing flag entries while spoofed flight is active.
     *
     * @param entries data updates leaving the client
     * @return adjusted data updates
     */
    @ModifyVariable(method = "writeUpdatedEntries", at = @At("HEAD"), argsOnly = true)
    private List<DataTracker.SerializedEntry<?>> stashorganizer$entries(
        List<DataTracker.SerializedEntry<?>> entries) {

        MinecraftClient client = MinecraftClient.getInstance();

        if (!(this.trackedEntity instanceof Entity entity) ||
            entity != client.player || !Flight.get().active()) {
            return entries;
        }

        List<DataTracker.SerializedEntry<?>> changed = null;

        for (int idx = 0; idx < entries.size(); idx++) {
            DataTracker.SerializedEntry<?> entry = entries.get(idx);

            if (entry.id() != flags || !(entry.value() instanceof Byte value)) {
                continue;
            }

            byte synced = Flight.get().sync(value);
            if (synced == value) continue;

            if (changed == null) changed = new ArrayList<>(entries);
            changed.set(idx, stashorganizer$entry(entry, synced));
        }

        return changed != null ? changed : entries;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static DataTracker.SerializedEntry<?> stashorganizer$entry(
        DataTracker.SerializedEntry<?> entry, byte value) {

        return new DataTracker.SerializedEntry(entry.id(), entry.handler(), value);
    }
}
