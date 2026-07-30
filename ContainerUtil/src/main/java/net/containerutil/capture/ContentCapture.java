package net.containerutil.capture;

import net.containerutil.ContainerUtil;
import net.containerutil.config.ConfigManager;
import net.containerutil.container.ContainerKind;
import net.containerutil.data.ContainerIndex;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.IndexManager;
import net.containerutil.data.ItemEntry;
import net.containerutil.data.WorldIdentity;
import net.containerutil.scan.ContainerScanner;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records what is inside a container when you open it.
 *
 * <p>Deliberately mixin-free. The two pieces of information needed — <em>which</em> container
 * you opened and <em>what</em> is in it — come from separate places and are joined here:
 *
 * <ul>
 *   <li>Fabric's use callbacks tell us the block or entity you interacted with. They fire
 *       before the screen exists, so the target is parked as {@link #pending}.</li>
 *   <li>Polling {@code client.currentScreen} each tick gives us the open {@link ScreenHandler}
 *       and therefore the slots.</li>
 * </ul>
 *
 * <p>Requiring a pending target is also what keeps crafting tables, anvils and enchanting
 * tables out of the index for free: they open a handled screen with non-player slots, but they
 * are not a {@link ContainerKind}, so no target is ever parked and nothing is captured.
 */
public class ContentCapture {

    /** How long a click stays eligible to be matched with a screen. Covers a bad server round trip. */
    private static final long PENDING_TIMEOUT_MS = 4000;

    /** What you last right-clicked, waiting for its screen to appear. */
    private record PendingTarget(ContainerKind kind, String dim, int x, int y, int z,
                                 int[] secondary, String entityUuid, long clickedAt) {

        boolean isExpired() {
            return System.currentTimeMillis() - clickedAt > PENDING_TIMEOUT_MS;
        }
    }

    private static volatile PendingTarget pending;

    /** The record currently being filled in, or {@code null} when no tracked container is open. */
    private static ContainerRecord openRecord;
    private static List<ItemEntry> liveItems;
    private static int liveSlotCount;
    private static int liveUsedSlots;
    private static String liveTitle;

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            try {
                if (world.isClient()) onUseBlock(world.getBlockState(hit.getBlockPos()), hit.getBlockPos());
            } catch (Exception e) {
                ContainerUtil.LOGGER.error("[ContainerUtil] Use-block capture hook crashed", e);
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            try {
                if (world.isClient()) onUseEntity(entity);
            } catch (Exception e) {
                ContainerUtil.LOGGER.error("[ContainerUtil] Use-entity capture hook crashed", e);
            }
            return ActionResult.PASS;
        });
    }

    private static void onUseBlock(BlockState state, BlockPos pos) {
        ContainerKind kind = ContainerKind.fromBlockState(state);
        if (kind == null) return;
        String dim = WorldIdentity.currentDimension();
        if (dim == null) return;

        BlockPos partner = ContainerScanner.doubleChestPartner(state, pos);
        if (partner == null) {
            pending = new PendingTarget(kind, dim, pos.getX(), pos.getY(), pos.getZ(),
                null, null, System.currentTimeMillis());
            return;
        }
        // Match the scanner's canonical half so both records converge on the same key.
        BlockPos primary = pos.asLong() <= partner.asLong() ? pos : partner;
        BlockPos secondary = primary.equals(pos) ? partner : pos;
        pending = new PendingTarget(kind, dim, primary.getX(), primary.getY(), primary.getZ(),
            new int[]{secondary.getX(), secondary.getY(), secondary.getZ()}, null, System.currentTimeMillis());
    }

    private static void onUseEntity(net.minecraft.entity.Entity entity) {
        ContainerKind kind = ContainerKind.fromEntity(entity);
        if (kind == null) return;
        String dim = WorldIdentity.currentDimension();
        if (dim == null) return;
        pending = new PendingTarget(kind, dim,
            (int) Math.floor(entity.getX()), (int) Math.floor(entity.getY()), (int) Math.floor(entity.getZ()),
            null, entity.getUuidAsString(), System.currentTimeMillis());
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void tick(MinecraftClient client) {
        if (!ConfigManager.get().indexingEnabled || !IndexManager.isActive()) {
            if (openRecord != null) reset();
            return;
        }

        if (client.currentScreen instanceof HandledScreen<?> screen) {
            if (openRecord == null) tryBind(screen);
            if (openRecord != null) snapshot(screen.getScreenHandler());
        } else if (openRecord != null) {
            commit();
        }
    }

    /** Joins the parked click target to the screen that just opened. */
    private static void tryBind(HandledScreen<?> screen) {
        PendingTarget target = pending;
        if (target == null || target.isExpired()) {
            pending = null;
            return;
        }
        pending = null;

        ContainerIndex index = IndexManager.index();
        ContainerRecord record = target.entityUuid() != null
            ? index.upsertEntitySighting(target.kind(), target.dim(), target.entityUuid(),
                target.x(), target.y(), target.z())
            : index.upsertBlockSighting(target.kind(), target.dim(),
                target.x(), target.y(), target.z(), target.secondary());

        openRecord = record;
        liveItems = new ArrayList<>();
        liveSlotCount = 0;
        liveUsedSlots = 0;
        liveTitle = screen.getTitle() != null ? screen.getTitle().getString() : null;
    }

    /** A captured set of contents, independent of where it was read from. */
    public record Snapshot(List<ItemEntry> items, int slotCount, int usedSlots) {
    }

    /**
     * Reads a plain {@link Inventory} directly, for screenless containers the client already
     * has the contents of — see {@link net.containerutil.container.ContainerKind#hasClientVisibleContents()}.
     */
    public static Snapshot snapshotInventory(Inventory inventory) {
        Map<String, ItemEntry> merged = new LinkedHashMap<>();
        int slotCount = inventory.size();
        int usedSlots = 0;

        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack == null || stack.isEmpty()) continue;
            usedSlots++;
            addStack(merged, stack, null);
            addNested(merged, stack);
        }
        return new Snapshot(new ArrayList<>(merged.values()), slotCount, usedSlots);
    }

    /**
     * True if two captured item lists are equivalent.
     *
     * <p>The scanner re-reads shelves on every sweep, and blindly re-recording would mark the
     * index dirty several times a second and turn the debounced autosave into a continuous one.
     */
    public static boolean sameContents(List<ItemEntry> a, List<ItemEntry> b) {
        if (a == null || b == null) return a == b;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ItemEntry left = a.get(i);
            ItemEntry right = b.get(i);
            if (left.count != right.count) return false;
            if (!java.util.Objects.equals(left.id, right.id)) return false;
            if (!java.util.Objects.equals(left.nestedIn, right.nestedIn)) return false;
            if (!java.util.Objects.equals(left.enchants, right.enchants)) return false;
        }
        return true;
    }

    /**
     * Reads the container's slots. Re-run every tick while the screen is open so the committed
     * snapshot reflects what you left behind, not what was there when you opened it.
     */
    private static void snapshot(ScreenHandler handler) {
        if (handler == null) return;

        // Merge equivalent stacks so five stacks of cobblestone become one "320× Cobblestone"
        // entry. Slot occupancy is tracked separately, so nothing is lost by collapsing them.
        Map<String, ItemEntry> merged = new LinkedHashMap<>();
        int slotCount = 0;
        int usedSlots = 0;

        for (Slot slot : handler.slots) {
            // The container's slots are everything that is not the player's own inventory.
            // This works uniformly across chests, hoppers, furnaces, minecarts and horses
            // without needing to know a single concrete handler type.
            if (slot.inventory instanceof PlayerInventory) continue;
            slotCount++;

            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;
            usedSlots++;

            addStack(merged, stack, null);
            addNested(merged, stack);
        }

        liveItems = new ArrayList<>(merged.values());
        liveSlotCount = slotCount;
        liveUsedSlots = usedSlots;
    }

    /**
     * Flattens a shulker box's contents onto the parent container. This is what makes "where are
     * my redstone torches" work when they are packed inside a shulker in a sorting hall rather
     * than sitting loose in a chest.
     */
    private static void addNested(Map<String, ItemEntry> merged, ItemStack stack) {
        ContainerComponent nested = stack.get(DataComponentTypes.CONTAINER);
        if (nested == null) return;
        String parentName = displayName(stack);
        for (ItemStack inner : nested.iterateNonEmpty()) {
            if (inner != null && !inner.isEmpty()) addStack(merged, inner, parentName);
        }
    }

    private static void addStack(Map<String, ItemEntry> merged, ItemStack stack, String nestedIn) {
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        List<String> enchants = enchantIds(stack);
        String name = displayName(stack);

        // Same item, same nesting, same enchantments and same custom name collapse together;
        // anything else stays distinct so a Mending pickaxe never merges into a plain one.
        String mergeKey = id + "|" + (nestedIn == null ? "" : nestedIn)
            + "|" + (enchants == null ? "" : String.join(",", enchants))
            + "|" + name;

        ItemEntry existing = merged.get(mergeKey);
        if (existing != null) {
            existing.count += stack.getCount();
        } else {
            merged.put(mergeKey, new ItemEntry(id, name, stack.getCount(), enchants, nestedIn));
        }
    }

    private static String displayName(ItemStack stack) {
        try {
            return stack.getName().getString();
        } catch (Exception e) {
            return Registries.ITEM.getId(stack.getItem()).getPath();
        }
    }

    /** Enchantment ids on a stack, covering both applied enchantments and enchanted books. */
    private static List<String> enchantIds(ItemStack stack) {
        List<String> out = null;
        out = collectEnchants(stack.get(DataComponentTypes.ENCHANTMENTS), out);
        out = collectEnchants(stack.get(DataComponentTypes.STORED_ENCHANTMENTS), out);
        return out;
    }

    private static List<String> collectEnchants(ItemEnchantmentsComponent component, List<String> out) {
        if (component == null || component.isEmpty()) return out;
        for (RegistryEntry<Enchantment> entry : component.getEnchantments()) {
            String id = entry.getKey().map(key -> key.getValue().toString()).orElse(null);
            if (id == null) continue;
            if (out == null) out = new ArrayList<>(2);
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    // ── Commit ───────────────────────────────────────────────────────────────

    private static void commit() {
        ContainerRecord record = openRecord;
        List<ItemEntry> items = liveItems;
        if (record == null || items == null) {
            reset();
            return;
        }

        ContainerIndex index = IndexManager.index();
        String customName = normaliseTitle(liveTitle, record);
        index.recordContents(record, items, liveSlotCount, liveUsedSlots, customName);

        // An ender chest's contents are the player's, not the block's — every ender chest in the
        // world shows the same inventory. Copying the capture onto all of them means searching
        // for something in your ender chest finds whichever one you happen to be nearest.
        if (ContainerKind.ENDER_CHEST.id().equals(record.kind)) {
            for (ContainerRecord other : index.all()) {
                if (other == record) continue;
                if (!ContainerKind.ENDER_CHEST.id().equals(other.kind)) continue;
                index.recordContents(other, new ArrayList<>(items), liveSlotCount, liveUsedSlots, other.customName);
            }
        }

        ContainerUtil.LOGGER.debug("[ContainerUtil] Captured {} stack kinds from {} at {}",
            items.size(), record.kind, record.coordsString());
        reset();
    }

    /**
     * Keeps the screen title only when it is an actual rename. Vanilla titles the screen after
     * the block, so storing "Chest" as a container's name would just make every label redundant.
     */
    private static String normaliseTitle(String title, ContainerRecord record) {
        if (title == null || title.isBlank()) return null;
        ContainerKind kind = record.kindOrNull();
        if (kind != null && title.equalsIgnoreCase(kind.displayName())) return null;
        if (title.equalsIgnoreCase("Large Chest") || title.equalsIgnoreCase("Container")) return null;
        return title;
    }

    private static void reset() {
        openRecord = null;
        liveItems = null;
        liveSlotCount = 0;
        liveUsedSlots = 0;
        liveTitle = null;
    }

    /** Drops any in-flight capture — called on disconnect so state never leaks across worlds. */
    public static void clear() {
        pending = null;
        reset();
    }
}
