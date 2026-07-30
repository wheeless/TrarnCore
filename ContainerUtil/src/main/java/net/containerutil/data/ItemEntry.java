package net.containerutil.data;

import java.util.List;

/**
 * One stack recorded inside a container.
 *
 * <p>Stacks are stored flattened: an item sitting inside a shulker box that is itself sitting
 * inside a chest gets one entry on the chest's record with {@link #nestedIn} set to the
 * shulker's display name. That keeps search a single pass over a flat list while still being
 * able to tell you "it's in the purple shulker in that chest".
 */
public class ItemEntry {

    /** Registry id, e.g. {@code minecraft:iron_ingot}. */
    public String id;

    /** Display name at capture time — the custom name if the stack had one, otherwise the translated name. */
    public String name;

    public int count;

    /** Enchantment registry ids present on the stack, or {@code null} if unenchanted. */
    public List<String> enchants;

    /** Display name of the containing shulker box, or {@code null} if the stack sits directly in the container. */
    public String nestedIn;

    public ItemEntry() {
    }

    public ItemEntry(String id, String name, int count, List<String> enchants, String nestedIn) {
        this.id = id;
        this.name = name;
        this.count = count;
        this.enchants = enchants;
        this.nestedIn = nestedIn;
    }

    public boolean hasEnchants() {
        return enchants != null && !enchants.isEmpty();
    }

    /** Short registry path, e.g. {@code iron_ingot} from {@code minecraft:iron_ingot}. */
    public String path() {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }
}
