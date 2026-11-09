package com.pro0101.stashorganizer.util;

import com.pro0101.stashorganizer.StashOrganizerAddon;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One worker for every Discord webhook the addon sends.
 *
 * <p>The modules used to spawn a thread per notification, which on a stash-dense server means dozens of threads
 * competing to POST. A single daemon worker with a bounded backlog keeps the cost flat, and dropping instead of
 * queueing forever is the right failure mode for a notification.
 */
public final class WebhookQueue {
    private static final int MAX_PENDING = 32;
    private static final AtomicInteger PENDING = new AtomicInteger();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "stash-organizer-webhook");
        thread.setDaemon(true);
        return thread;
    });

    private WebhookQueue() {}

    public static void submit(String label, Runnable task) {
        if (task == null) return;

        if (PENDING.get() >= MAX_PENDING) {
            StashOrganizerAddon.LOG.warn("Dropping webhook {}: {} already queued", label, PENDING.get());
            return;
        }

        PENDING.incrementAndGet();
        WORKER.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                StashOrganizerAddon.LOG.warn("Webhook {} failed: {}", label, t.toString());
            } finally {
                PENDING.decrementAndGet();
            }
        });
    }
}
