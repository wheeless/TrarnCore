package net.containerutil.container;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CopperChestBlock;
import net.minecraft.block.ShelfBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AbstractDonkeyEntity;
import net.minecraft.entity.vehicle.AbstractChestBoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Every container ContainerUtil knows how to highlight and index.
 *
 * <p>The {@code id} is what gets written to the on-disk index and to the colour map in the
 * config, so it must stay stable across versions even if the enum constant is renamed.
 *
 * <p>Default colours are grouped by {@link ContainerFamily} — related kinds sit in the same
 * hue band so a wall of chests and barrels reads as "storage" at a glance, while a trapped
 * chest or an ender chest is unmistakably not one of them.
 */
public enum ContainerKind {

    // ── Storage ──────────────────────────────────────────────────────────────
    CHEST              ("chest",              "Chest",              0xFFC107, ContainerFamily.STORAGE),
    TRAPPED_CHEST      ("trapped_chest",      "Trapped Chest",      0xF44336, ContainerFamily.STORAGE),
    BARREL             ("barrel",             "Barrel",             0xFF8F00, ContainerFamily.STORAGE),
    // Muted copper rather than a maximally-separated hue: people expect a copper chest to look
    // like copper, and guessing right matters more here than squeezing the last bit of contrast
    // out of the storage band. It is clearly darker than both chest and barrel, and overridable.
    COPPER_CHEST       ("copper_chest",       "Copper Chest",       0xC77B3C, ContainerFamily.STORAGE),
    SHULKER_BOX        ("shulker_box",        "Shulker Box",        0xAB47BC, ContainerFamily.STORAGE),
    ENDER_CHEST        ("ender_chest",        "Ender Chest",        0x00E5FF, ContainerFamily.STORAGE),

    // ── Redstone I/O ─────────────────────────────────────────────────────────
    HOPPER             ("hopper",             "Hopper",             0x546E7A, ContainerFamily.REDSTONE),
    DISPENSER          ("dispenser",          "Dispenser",          0x5C6BC0, ContainerFamily.REDSTONE),
    DROPPER            ("dropper",            "Dropper",            0x7986CB, ContainerFamily.REDSTONE),

    // ── Smelting ─────────────────────────────────────────────────────────────
    FURNACE            ("furnace",            "Furnace",            0xFF7043, ContainerFamily.SMELTING),
    BLAST_FURNACE      ("blast_furnace",      "Blast Furnace",      0xE64A19, ContainerFamily.SMELTING),
    SMOKER             ("smoker",             "Smoker",             0xFFAB91, ContainerFamily.SMELTING),

    // ── Utility ──────────────────────────────────────────────────────────────
    BREWING_STAND      ("brewing_stand",      "Brewing Stand",      0x66BB6A, ContainerFamily.UTILITY),
    CRAFTER            ("crafter",            "Crafter",            0x26A69A, ContainerFamily.UTILITY),
    CHISELED_BOOKSHELF ("chiseled_bookshelf", "Chiseled Bookshelf", 0x8D6E63, ContainerFamily.UTILITY),
    SHELF              ("shelf",              "Shelf",              0xB39DDB, ContainerFamily.UTILITY),
    DECORATED_POT      ("decorated_pot",      "Decorated Pot",      0xEC407A, ContainerFamily.UTILITY),
    LECTERN            ("lectern",            "Lectern",            0xA1887F, ContainerFamily.UTILITY),

    // ── Mobile (entity-backed) ───────────────────────────────────────────────
    CHEST_MINECART     ("chest_minecart",     "Chest Minecart",     0x4CAF50, ContainerFamily.MOBILE),
    HOPPER_MINECART    ("hopper_minecart",    "Hopper Minecart",    0x9CCC65, ContainerFamily.MOBILE),
    FURNACE_MINECART   ("furnace_minecart",   "Furnace Minecart",   0xD4E157, ContainerFamily.MOBILE),
    CHEST_BOAT         ("chest_boat",         "Chest Boat",         0x29B6F6, ContainerFamily.MOBILE),
    CHESTED_ANIMAL     ("chested_animal",     "Chested Animal",     0xFFEE58, ContainerFamily.MOBILE);

    private final String id;
    private final String displayName;
    private final int defaultColor;
    private final ContainerFamily family;

    ContainerKind(String id, String displayName, int defaultColor, ContainerFamily family) {
        this.id = id;
        this.displayName = displayName;
        this.defaultColor = defaultColor;
        this.family = family;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultColor() {
        return defaultColor;
    }

    public ContainerFamily family() {
        return family;
    }

    /** True for kinds backed by an entity rather than a block entity — these move, so they are keyed by UUID. */
    public boolean isEntity() {
        return family == ContainerFamily.MOBILE;
    }

    /**
     * True for containers that have no screen but whose contents the client already knows.
     *
     * <p>Shelves and chiseled bookshelves are interacted with slot-by-slot rather than by
     * opening a GUI, so the "capture on close" path never fires for them — but the client is
     * sent their contents anyway, because it has to render the items sitting on them. The
     * scanner reads those directly, which is the only reason they are not permanently stuck
     * showing as never-opened.
     *
     * <p>Decorated pots deliberately do not qualify: the server does not sync their contents.
     */
    public boolean hasClientVisibleContents() {
        return this == SHELF || this == CHISELED_BOOKSHELF;
    }

    // ── Lookup ───────────────────────────────────────────────────────────────

    /**
     * Block → kind, resolved lazily.
     *
     * <p>Built on first use rather than in the enum constructor: {@link Blocks} is populated
     * during registry bootstrap, and touching it from an enum initialiser risks running before
     * that finishes. By the time anything asks us to resolve a block, the world exists.
     */
    private static Map<Block, ContainerKind> blockLookup;

    private static Map<Block, ContainerKind> blockLookup() {
        Map<Block, ContainerKind> map = blockLookup;
        if (map == null) {
            map = new IdentityHashMap<>();
            map.put(Blocks.CHEST, CHEST);
            map.put(Blocks.TRAPPED_CHEST, TRAPPED_CHEST);
            map.put(Blocks.BARREL, BARREL);
            map.put(Blocks.ENDER_CHEST, ENDER_CHEST);
            map.put(Blocks.HOPPER, HOPPER);
            map.put(Blocks.DISPENSER, DISPENSER);
            map.put(Blocks.DROPPER, DROPPER);
            map.put(Blocks.FURNACE, FURNACE);
            map.put(Blocks.BLAST_FURNACE, BLAST_FURNACE);
            map.put(Blocks.SMOKER, SMOKER);
            map.put(Blocks.BREWING_STAND, BREWING_STAND);
            map.put(Blocks.CRAFTER, CRAFTER);
            map.put(Blocks.CHISELED_BOOKSHELF, CHISELED_BOOKSHELF);
            map.put(Blocks.DECORATED_POT, DECORATED_POT);
            map.put(Blocks.LECTERN, LECTERN);
            blockLookup = map;
        }
        return map;
    }

    /** Returns the kind for a block state, or {@code null} if it is not a container we track. */
    public static ContainerKind fromBlockState(BlockState state) {
        if (state == null) return null;
        Block block = state.getBlock();

        // Class checks before the identity map, so every variant of a family is covered by one
        // case and new variants in a future version come along for free.

        // All 17 dyed shulker boxes plus the undyed one share a class.
        if (block instanceof ShulkerBoxBlock) return SHULKER_BOX;

        // All eight copper chests (four oxidation levels, waxed and unwaxed). This check MUST
        // come before any plain-chest handling: CopperChestBlock extends ChestBlock, so a
        // subtype test for chests would otherwise swallow them and report them as ordinary
        // chests. They still pair into double chests correctly, since they inherit CHEST_TYPE.
        if (block instanceof CopperChestBlock) return COPPER_CHEST;

        // All twelve wood shelves.
        if (block instanceof ShelfBlock) return SHELF;

        return blockLookup().get(block);
    }

    /** Returns the kind for an entity, or {@code null} if it is not a container we track. */
    public static ContainerKind fromEntity(Entity entity) {
        if (entity == null) return null;
        if (entity instanceof ChestMinecartEntity) return CHEST_MINECART;
        if (entity instanceof HopperMinecartEntity) return HOPPER_MINECART;
        if (entity instanceof FurnaceMinecartEntity) return FURNACE_MINECART;
        if (entity instanceof AbstractChestBoatEntity) return CHEST_BOAT;
        // Donkeys, mules, llamas and trader llamas — only once they are actually wearing a chest.
        if (entity instanceof AbstractDonkeyEntity donkey && donkey.hasChest()) return CHESTED_ANIMAL;
        return null;
    }

    /** Resolves a persisted {@link #id()} back to a kind, or {@code null} if unknown (e.g. written by a newer version). */
    public static ContainerKind byId(String id) {
        if (id == null) return null;
        for (ContainerKind kind : values()) {
            if (kind.id.equals(id)) return kind;
        }
        return null;
    }
}
