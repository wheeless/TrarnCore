package net.containerutil.scan;

import net.containerutil.ContainerUtil;
import net.containerutil.capture.ContentCapture;
import net.containerutil.config.ConfigManager;
import net.containerutil.config.ContainerUtilConfig;
import net.containerutil.container.ContainerKind;
import net.containerutil.data.ContainerIndex;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.IndexManager;
import net.containerutil.data.WorldIdentity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Container;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers containers in loaded chunks and keeps their positions current.
 *
 * <p>Seeing a container is not the same as knowing what is inside it: this class only ever
 * records that a container of some kind exists at some position. Contents come from
 * {@link net.containerutil.capture.ContentCapture} when you actually open one. That split is
 * what makes the highlights complete from the moment you walk into a base, while the search
 * index fills in as you use it.
 *
 * <p>The block sweep is spread across ticks with a rolling cursor rather than done all at once.
 * A 8-chunk radius is 289 chunks; walking all of them in a single tick is a visible stutter
 * every time it fires, whereas a slice per tick is unnoticeable and covers the same ground
 * within a second or two.
 */
public class ContainerScanner {

    /**
     * Ticks a full sweep of the loaded area should take. The per-tick chunk budget is derived
     * from this so coverage stays responsive at a 32-chunk view distance (4,225 chunks) without
     * the budget being wastefully large at a 4-chunk one.
     */
    private static final int TARGET_SWEEP_TICKS = 60;

    /** Floor and ceiling on that budget, so neither extreme produces a silly number. */
    private static final int MIN_CHUNKS_PER_TICK = 24;
    private static final int MAX_CHUNKS_PER_TICK = 128;

    /** Entities are cheaper to walk and move constantly, so they get a full pass on a fixed interval. */
    private static final int ENTITY_SCAN_INTERVAL_TICKS = 20;

    /** Pruning re-reads block states, so it runs on its own slower cadence. */
    private static final int PRUNE_INTERVAL_TICKS = 40;

    private static int sweepCursor = 0;
    private static int tickCounter = 0;

    public static void tick(Minecraft client) {
        if (!IndexManager.isActive()) return;
        if (client.level == null || client.player == null) return;

        ContainerUtilConfig config = ConfigManager.get();
        tickCounter++;

        scanBlockSlice(client, config);

        if (tickCounter % ENTITY_SCAN_INTERVAL_TICKS == 0) {
            scanEntities(client);
        }
        if (config.autoPrune && tickCounter % PRUNE_INTERVAL_TICKS == 0) {
            pruneNearby(client, config);
        }
    }

    /** Resets the rolling cursor — call on dimension change so the new area is swept from the start. */
    public static void reset() {
        sweepCursor = 0;
        tickCounter = 0;
    }

    // ── Block sweep ──────────────────────────────────────────────────────────

    private static void scanBlockSlice(Minecraft client, ContainerUtilConfig config) {
        ClientLevel world = client.level;
        String dim = WorldIdentity.currentDimension();
        if (dim == null) return;

        // Sweep everything the client actually has loaded, not the highlight radius. Those are
        // different questions: turning the highlight distance down for frame rate should not also
        // stop the mod learning about your base. Chunks outside the loaded area simply come back
        // null below, so overshooting costs nothing but a few wasted iterations.
        int radius = Math.max(config.renderChunkRadius, client.options.getEffectiveRenderDistance());
        int side = radius * 2 + 1;
        int totalChunks = side * side;

        // Always centred on the player, never the view anchor: chunk loading follows the player,
        // so this is what covers the loaded set. A freecam does not load chunks, and sweeping
        // around it would just walk a lot of null chunks while missing real ones behind you.
        int playerChunkX = (int) Math.floor(client.player.getX()) >> 4;
        int playerChunkZ = (int) Math.floor(client.player.getZ()) >> 4;

        ContainerIndex index = IndexManager.index();

        int chunksPerTick = Math.clamp(
            (totalChunks + TARGET_SWEEP_TICKS - 1) / TARGET_SWEEP_TICKS,
            MIN_CHUNKS_PER_TICK, MAX_CHUNKS_PER_TICK);

        for (int i = 0; i < chunksPerTick; i++) {
            int slot = sweepCursor % totalChunks;
            sweepCursor = (sweepCursor + 1) % totalChunks;

            int chunkX = playerChunkX - radius + (slot % side);
            int chunkZ = playerChunkZ - radius + (slot / side);

            LevelChunk chunk = world.getChunkSource().getChunk(chunkX, chunkZ, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
            if (chunk == null) continue;

            for (var entry : chunk.getBlockEntities().entrySet()) {
                BlockPos pos = entry.getKey();
                BlockEntity blockEntity = entry.getValue();
                if (blockEntity == null) continue;

                BlockState state = world.getBlockState(pos);
                ContainerKind kind = ContainerKind.fromBlockState(state);
                if (kind == null) continue;

                ContainerRecord record = recordBlockContainer(index, dim, pos, state, kind);

                // Screenless containers we can just read — see ContainerKind#hasClientVisibleContents.
                if (record != null && kind.hasClientVisibleContents() && blockEntity instanceof Container inventory) {
                    captureVisibleContents(index, record, inventory);
                }
            }
        }
    }

    /**
     * Records the contents of a container that has no GUI, but only when they have actually
     * changed — this runs on every sweep, and unconditional writes would keep the index
     * permanently dirty and defeat the debounced autosave.
     */
    private static void captureVisibleContents(ContainerIndex index, ContainerRecord record, Container inventory) {
        ContentCapture.Snapshot snapshot = ContentCapture.snapshotInventory(inventory);
        if (record.lastScanned != 0
            && record.usedSlots == snapshot.usedSlots()
            && ContentCapture.sameContents(record.items, snapshot.items())) {
            return;
        }
        index.recordContents(record, snapshot.items(), snapshot.slotCount(), snapshot.usedSlots(),
            record.customName);
    }

    /**
     * Upserts one block container, collapsing the two halves of a double chest into a single
     * record so it highlights as one box and search never reports the same chest twice.
     */
    private static ContainerRecord recordBlockContainer(ContainerIndex index, String dim,
                                                        BlockPos pos, BlockState state, ContainerKind kind) {
        BlockPos other = doubleChestPartner(state, pos);
        if (other == null) {
            return index.upsertBlockSighting(kind, dim, pos.getX(), pos.getY(), pos.getZ(), null);
        }

        // Both halves resolve to the same primary, so whichever we scan first wins consistently.
        BlockPos primary = pos.asLong() <= other.asLong() ? pos : other;
        BlockPos secondary = primary.equals(pos) ? other : pos;

        ContainerRecord record = index.upsertBlockSighting(kind, dim,
            primary.getX(), primary.getY(), primary.getZ(),
            new int[]{secondary.getX(), secondary.getY(), secondary.getZ()});

        // If we previously recorded the far half as its own single chest — because we saw it
        // before its partner was placed — drop that now-duplicate record.
        String staleKey = ContainerRecord.blockKey(dim, secondary.getX(), secondary.getY(), secondary.getZ());
        ContainerRecord stale = index.get(staleKey);
        if (stale != null && !stale.hasSecondary) {
            index.remove(staleKey);
        }
        return record;
    }

    /** The far half of a double chest, or {@code null} for a single chest or any other block. */
    public static BlockPos doubleChestPartner(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        if (!state.hasProperty(ChestBlock.TYPE)) return null;

        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return null;

        Direction facing = state.getValue(ChestBlock.FACING);
        Direction toPartner = type == ChestType.LEFT
            ? facing.getClockWise()
            : facing.getCounterClockWise();
        return pos.relative(toPartner);
    }

    // ── Entity sweep ─────────────────────────────────────────────────────────

    private static void scanEntities(Minecraft client) {
        String dim = WorldIdentity.currentDimension();
        if (dim == null) return;

        ContainerIndex index = IndexManager.index();
        for (Entity entity : client.level.entitiesForRendering()) {
            ContainerKind kind = ContainerKind.fromEntity(entity);
            if (kind == null) continue;
            index.upsertEntitySighting(kind, dim, entity.getStringUUID(),
                (int) Math.floor(entity.getX()),
                (int) Math.floor(entity.getY()),
                (int) Math.floor(entity.getZ()));
        }
    }

    // ── Pruning ──────────────────────────────────────────────────────────────

    /**
     * Drops records for containers that are demonstrably gone.
     *
     * <p>Deliberately conservative: it only fires for block containers close enough that their
     * chunk is certainly loaded, and only when the block there is genuinely not the kind we
     * recorded. An unloaded chunk reads as air, and treating that as "deleted" would quietly
     * erase the index every time you walked past the edge of render distance.
     */
    private static void pruneNearby(Minecraft client, ContainerUtilConfig config) {
        ClientLevel world = client.level;
        String dim = WorldIdentity.currentDimension();
        if (dim == null) return;

        // Player-anchored on purpose, like the sweep above: pruning deletes data, so it may only
        // run where the chunk is certainly loaded and the block reading can be trusted.
        double px = client.player.getX();
        double py = client.player.getY();
        double pz = client.player.getZ();
        double radiusSq = (double) config.pruneRadius * config.pruneRadius;

        ContainerIndex index = IndexManager.index();
        List<String> doomed = new ArrayList<>();

        for (ContainerRecord record : index.all()) {
            if (record.isEntityBacked()) continue;
            if (!dim.equals(record.dim)) continue;
            if (record.distanceSqTo(px, py, pz) > radiusSq) continue;

            BlockPos pos = new BlockPos(record.x, record.y, record.z);
            // Only trust the reading if the chunk is actually loaded.
            if (world.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) == null) continue;

            ContainerKind actual = ContainerKind.fromBlockState(world.getBlockState(pos));
            if (actual == null) {
                doomed.add(record.key());
            }
        }

        for (String key : doomed) {
            index.remove(key);
        }
        if (!doomed.isEmpty()) {
            ContainerUtil.LOGGER.debug("[ContainerUtil] Pruned {} container(s) that no longer exist", doomed.size());
        }
    }
}
