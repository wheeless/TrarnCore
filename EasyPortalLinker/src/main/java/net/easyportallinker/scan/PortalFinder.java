package net.easyportallinker.scan;

import net.easyportallinker.EasyPortalLinker;
import net.easyportallinker.config.ConfigManager;
import net.easyportallinker.config.EasyPortalLinkerConfig;
import net.easyportallinker.portal.PortalSighting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds every nether portal in the loaded area.
 *
 * <p>Portal blocks are not block entities, so there is no cheap per-chunk list to walk the way
 * {@code ContainerScanner} walks {@code getBlockEntities()}. What makes this affordable instead is
 * {@link LevelChunkSection#maybeHas}, which answers "could this 16x16x16 contain a portal block?"
 * from the section's palette without touching a single position. Almost every section answers no
 * instantly, and only the handful that say yes get their 4,096 blocks read.
 *
 * <p>The sweep is spread across ticks with a rolling cursor, for the same reason ContainerUtil's
 * is: doing a whole render distance at once is a visible stutter every time it fires.
 *
 * <p>Results are rebuilt and published as one immutable list when the cursor wraps, so the render
 * thread always reads a complete, self-consistent snapshot rather than a half-swept one.
 */
public final class PortalFinder {

    private PortalFinder() {
    }

    /** Ticks a full sweep should take. The per-tick chunk budget is derived from this. */
    private static final int TARGET_SWEEP_TICKS = 40;
    private static final int MIN_CHUNKS_PER_TICK = 16;
    private static final int MAX_CHUNKS_PER_TICK = 96;

    /**
     * Cap on unlit-frame probes per tick.
     *
     * <p>Frame detection asks Minecraft to validate a portal shape, which is bounded but not free,
     * and obsidian is not rare — a bastion or a ruined portal field can put hundreds of candidates
     * in range. The cap means a dense area takes a few more sweeps to fill in rather than costing
     * a frame drop.
     */
    private static final int MAX_FRAME_PROBES_PER_TICK = 48;

    /** Vanilla's own limit, and the bound on how far an interior walk can run. */
    private static final int MAX_PORTAL_SPAN = 23;

    private static int sweepCursor = 0;

    /** Portal-block positions accumulated during the sweep in progress. */
    private static final Set<Long> portalBlocks = new HashSet<>();

    /** Interior rectangles of unlit frames found during the sweep in progress, deduplicated. */
    private static final Set<Long> frameKeys = new HashSet<>();
    private static final List<PortalSighting> frameSightings = new ArrayList<>();

    /** The published snapshot. Volatile because the render thread reads it every frame. */
    private static volatile List<PortalSighting> published = List.of();

    /** Everything found by the last completed sweep. Never null, never mutated in place. */
    public static List<PortalSighting> sightings() {
        return published;
    }

    /** Drops everything and restarts the sweep. Call on dimension change. */
    public static void reset() {
        sweepCursor = 0;
        portalBlocks.clear();
        frameKeys.clear();
        frameSightings.clear();
        published = List.of();
    }

    public static void tick(Minecraft client) {
        EasyPortalLinkerConfig config = ConfigManager.get();
        if (!EasyPortalLinker.portalEsp) {
            if (!published.isEmpty()) reset();
            return;
        }
        if (client.level == null || client.player == null) return;

        ClientLevel world = client.level;

        // Sweep only as far as we will actually draw. Unlike ContainerUtil there is no index being
        // built up for later use, so scanning past the render radius would be pure waste.
        int radius = Math.min(config.portalEspChunkRadius, client.options.getEffectiveRenderDistance());
        int side = radius * 2 + 1;
        int totalChunks = side * side;

        int playerChunkX = (int) Math.floor(client.player.getX()) >> 4;
        int playerChunkZ = (int) Math.floor(client.player.getZ()) >> 4;

        int chunksPerTick = Math.clamp(
            (totalChunks + TARGET_SWEEP_TICKS - 1) / TARGET_SWEEP_TICKS,
            MIN_CHUNKS_PER_TICK, MAX_CHUNKS_PER_TICK);

        int frameProbes = 0;
        boolean wantFrames = config.detectUnlitFrames;
        int frameRadius = Math.min(config.unlitFrameChunkRadius, radius);

        for (int i = 0; i < chunksPerTick; i++) {
            int slot = sweepCursor;

            // Advance the cursor before anything can skip the rest of the iteration. An unloaded
            // chunk is a normal outcome, not a reason to sit on the same slot forever.
            sweepCursor++;
            boolean wrapped = sweepCursor >= totalChunks;
            if (wrapped) sweepCursor = 0;

            int offsetX = (slot % side) - radius;
            int offsetZ = (slot / side) - radius;

            LevelChunk chunk = world.getChunkSource()
                .getChunk(playerChunkX + offsetX, playerChunkZ + offsetZ, ChunkStatus.FULL, false);

            if (chunk != null) {
                collectPortalBlocks(world, chunk);

                boolean inFrameRange = Math.abs(offsetX) <= frameRadius && Math.abs(offsetZ) <= frameRadius;
                if (wantFrames && inFrameRange && frameProbes < MAX_FRAME_PROBES_PER_TICK) {
                    frameProbes += collectUnlitFrames(world, chunk, MAX_FRAME_PROBES_PER_TICK - frameProbes);
                }
            }

            // Publish only after the slot's chunk has been scanned, so the last chunk of a sweep
            // is part of that sweep rather than reporting one cycle late.
            if (wrapped) publish();
        }
    }

    // ── Lit portals ──────────────────────────────────────────────────────────

    private static void collectPortalBlocks(ClientLevel world, LevelChunk chunk) {
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();

        for (int index = 0; index < sections.length; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;
            // The palette check that makes this whole approach viable.
            if (!section.maybeHas(state -> state.is(Blocks.NETHER_PORTAL))) continue;

            int baseY = world.getSectionYFromSectionIndex(index) << 4;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        if (!section.getBlockState(x, y, z).is(Blocks.NETHER_PORTAL)) continue;
                        portalBlocks.add(BlockPos.asLong(baseX + x, baseY + y, baseZ + z));
                    }
                }
            }
        }
    }

    /**
     * Groups the collected portal blocks into individual portals.
     *
     * <p>Flood fill rather than per-chunk grouping, because a portal straddling a chunk boundary
     * is one portal and highlighting it as two boxes would look broken.
     */
    private static List<PortalSighting> clusterPortalBlocks() {
        List<PortalSighting> result = new ArrayList<>();
        Set<Long> remaining = new HashSet<>(portalBlocks);

        while (!remaining.isEmpty()) {
            long seed = remaining.iterator().next();
            remaining.remove(seed);

            int minX = BlockPos.getX(seed), maxX = minX;
            int minY = BlockPos.getY(seed), maxY = minY;
            int minZ = BlockPos.getZ(seed), maxZ = minZ;

            Deque<Long> queue = new ArrayDeque<>();
            queue.add(seed);

            while (!queue.isEmpty()) {
                long current = queue.poll();
                int cx = BlockPos.getX(current), cy = BlockPos.getY(current), cz = BlockPos.getZ(current);

                minX = Math.min(minX, cx); maxX = Math.max(maxX, cx);
                minY = Math.min(minY, cy); maxY = Math.max(maxY, cy);
                minZ = Math.min(minZ, cz); maxZ = Math.max(maxZ, cz);

                for (Direction direction : Direction.values()) {
                    long neighbour = BlockPos.asLong(
                        cx + direction.getStepX(), cy + direction.getStepY(), cz + direction.getStepZ());
                    if (remaining.remove(neighbour)) queue.add(neighbour);
                }
            }

            // A portal's width runs along whichever horizontal axis it spans; the other is 1 thick.
            Direction.Axis axis = (maxX - minX) >= (maxZ - minZ) ? Direction.Axis.X : Direction.Axis.Z;
            result.add(new PortalSighting(
                new AABB(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0), axis, true));
        }
        return result;
    }

    // ── Unlit frames ─────────────────────────────────────────────────────────

    /**
     * Finds complete but unlit frames by asking Minecraft.
     *
     * <p>{@link PortalShape#findEmptyPortalShape} is the same check the game runs when you strike
     * flint and steel, so what this highlights is exactly what would light — no hand-rolled
     * approximation of the frame rules, and no false positives from decorative obsidian.
     *
     * <p>Candidates are the air blocks directly above obsidian: every valid frame has its bottom
     * interior row sitting on the bottom frame row, so that one probe per obsidian block reaches
     * every real frame without testing the whole plane.
     *
     * @return how many probes were spent
     */
    private static int collectUnlitFrames(ClientLevel world, LevelChunk chunk, int budget) {
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int spent = 0;

        for (int index = 0; index < sections.length && spent < budget; index++) {
            LevelChunkSection section = sections[index];
            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(state -> state.is(Blocks.OBSIDIAN))) continue;

            int baseY = world.getSectionYFromSectionIndex(index) << 4;
            for (int x = 0; x < 16 && spent < budget; x++) {
                for (int y = 0; y < 16 && spent < budget; y++) {
                    for (int z = 0; z < 16 && spent < budget; z++) {
                        if (!section.getBlockState(x, y, z).is(Blocks.OBSIDIAN)) continue;

                        BlockPos above = new BlockPos(baseX + x, baseY + y + 1, baseZ + z);
                        BlockState state = world.getBlockState(above);
                        if (!state.isAir()) continue;

                        if (insideKnownFrame(above)) continue;

                        spent++;
                        probeFrame(world, above);
                    }
                }
            }
        }
        return spent;
    }

    /**
     * True when this position is already inside a frame found during this sweep.
     *
     * <p>A frame's whole bottom row probes to the same shape, so without this a wide portal costs
     * a full validation per block of its width, every sweep. The list is short by construction —
     * it only holds frames, not obsidian.
     */
    private static boolean insideKnownFrame(BlockPos pos) {
        for (PortalSighting sighting : frameSightings) {
            if (sighting.box().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
                return true;
            }
        }
        return false;
    }

    private static void probeFrame(ClientLevel world, BlockPos interior) {
        for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.X, Direction.Axis.Z}) {
            if (PortalShape.findEmptyPortalShape(world, interior, axis).isEmpty()) continue;

            AABB box = interiorBounds(world, interior, axis);
            if (box == null) continue;

            // Key on the interior rectangle so the same frame probed from several of its bottom
            // blocks is recorded once.
            long key = BlockPos.asLong((int) box.minX, (int) box.minY, (int) box.minZ);
            if (frameKeys.add(key)) {
                frameSightings.add(new PortalSighting(box, axis, false));
            }
            return;
        }
    }

    /**
     * Walks the air pocket out to its edges to get the frame's interior rectangle.
     *
     * <p>Safe to do naively because the shape has already been validated: the interior of a legal
     * frame is a clean rectangle, so extending along the axis and then vertically finds its real
     * bounds rather than wandering into an irregular cave.
     */
    private static AABB interiorBounds(ClientLevel world, BlockPos interior, Direction.Axis axis) {
        Direction negative = axis == Direction.Axis.X ? Direction.WEST : Direction.NORTH;
        Direction positive = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        BlockPos min = walk(world, interior, negative);
        BlockPos max = walk(world, interior, positive);
        BlockPos bottom = walk(world, interior, Direction.DOWN);
        BlockPos top = walk(world, interior, Direction.UP);

        if (min == null || max == null || bottom == null || top == null) return null;

        return new AABB(
            Math.min(min.getX(), interior.getX()), bottom.getY(), Math.min(min.getZ(), interior.getZ()),
            Math.max(max.getX(), interior.getX()) + 1.0, top.getY() + 1.0,
            Math.max(max.getZ(), interior.getZ()) + 1.0);
    }

    /** Last air block in a direction, or null if the run exceeds any legal portal. */
    private static BlockPos walk(ClientLevel world, BlockPos from, Direction direction) {
        BlockPos current = from;
        for (int step = 0; step < MAX_PORTAL_SPAN; step++) {
            BlockPos next = current.relative(direction);
            if (!world.getBlockState(next).isAir()) return current;
            current = next;
        }
        return null;
    }

    // ── Publishing ───────────────────────────────────────────────────────────

    private static void publish() {
        List<PortalSighting> result = clusterPortalBlocks();
        result.addAll(frameSightings);

        published = List.copyOf(result);

        portalBlocks.clear();
        frameKeys.clear();
        frameSightings.clear();
    }
}
