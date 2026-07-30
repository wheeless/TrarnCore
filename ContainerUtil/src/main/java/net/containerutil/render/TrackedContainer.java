package net.containerutil.render;

import net.containerutil.data.ContainerRecord;

/**
 * The one container you are currently being guided to, set by clicking a search result.
 *
 * <p>Deliberately a single slot rather than a list — the whole point is an unambiguous
 * "walk that way", and a screen full of beams is just the ESP view again.
 */
public final class TrackedContainer {

    private static volatile ContainerRecord tracked;

    /** The item that was being searched for when tracking started, shown in the HUD readout. */
    private static volatile String trackedItemLabel;

    private TrackedContainer() {
    }

    public static void set(ContainerRecord record, String itemLabel) {
        tracked = record;
        trackedItemLabel = itemLabel;
    }

    public static ContainerRecord get() {
        return tracked;
    }

    public static String itemLabel() {
        return trackedItemLabel;
    }

    public static boolean isTracking() {
        return tracked != null;
    }

    public static void clear() {
        tracked = null;
        trackedItemLabel = null;
    }
}
