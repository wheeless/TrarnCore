package net.trarncore.util;

import org.slf4j.Logger;

/**
 * Runs work that must never take the game down with it.
 *
 * <p>Client tick and render callbacks are shared: an exception escaping one mod's handler can
 * disrupt the frame for everything else. Every mod here wrapped its subsystems in the same
 * try/catch-and-log shape, and several isolated each subsystem separately so a fault in one did
 * not silently disable the others.
 */
public final class Guarded {

    private Guarded() {
    }

    /** Runs {@code task}, logging any exception against {@code label} instead of propagating it. */
    public static void run(Logger logger, String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            logger.error("[{}] crashed", label, e);
        }
    }

    /**
     * Same, but suppresses repeats.
     *
     * <p>For render callbacks specifically: a fault there recurs every frame, and an unthrottled
     * log turns one bug into gigabytes of identical stack traces.
     */
    public static void run(ErrorThrottle throttle, String label, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            throttle.error(label + " crashed", e);
        }
    }
}
