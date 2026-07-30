package net.containerutil.search;

import net.containerutil.data.ContainerRecord;
import net.containerutil.data.ItemEntry;

import java.util.List;

/**
 * One container that matched a query, together with the stacks inside it that were responsible.
 *
 * @param container    the matching container
 * @param matched      the item entries that satisfied the item-level part of the query;
 *                     empty when the query only constrained the container itself
 * @param matchedTotal summed count across {@link #matched}
 * @param distanceSq   squared distance from the player at the time the search ran
 */
public record SearchResult(ContainerRecord container,
                           List<ItemEntry> matched,
                           int matchedTotal,
                           double distanceSq) {

    public double distance() {
        return Math.sqrt(distanceSq);
    }

    /** The single most numerous matching stack, used as the row's representative item. */
    public ItemEntry primary() {
        ItemEntry best = null;
        for (ItemEntry entry : matched) {
            if (best == null || entry.count > best.count) best = entry;
        }
        return best;
    }
}
