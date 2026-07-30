package net.easyportallinker.portal;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Flood-fills a connected group of nether-portal blocks to recover the portal's bounding box,
 * orientation and interior size, then builds a {@link PortalTarget} (source fields filled and
 * destination computed via {@link LinkMath}).
 */
public final class PortalScan {

    /** Safety cap on how many portal blocks we will visit (guards against absurd custom portals). */
    private static final int MAX_BLOCKS = 512;

    private PortalScan() {}

    /**
     * @param world     the world containing the portal
     * @param start     any portal block belonging to the portal
     * @return a populated {@link PortalTarget}, or {@code null} if {@code start} is not a portal block
     */
    public static PortalTarget scan(World world, BlockPos start) {
        BlockState startState = world.getBlockState(start);
        if (!startState.isOf(Blocks.NETHER_PORTAL)) return null;

        Direction.Axis axis = startState.contains(Properties.HORIZONTAL_AXIS)
            ? startState.get(Properties.HORIZONTAL_AXIS)
            : Direction.Axis.X;

        int minX = start.getX(), minY = start.getY(), minZ = start.getZ();
        int maxX = minX, maxY = minY, maxZ = minZ;

        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);

        int count = 0;
        while (!queue.isEmpty() && count < MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            count++;

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());

            for (Direction dir : Direction.values()) {
                BlockPos next = pos.offset(dir);
                if (!visited.contains(next.asLong())
                        && world.getBlockState(next).isOf(Blocks.NETHER_PORTAL)) {
                    visited.add(next.asLong());
                    queue.add(next);
                }
            }
        }

        PortalTarget t = new PortalTarget();
        t.sourceDim = world.getRegistryKey().getValue().toString();
        t.sourceX = minX;
        t.sourceY = minY;
        t.sourceZ = minZ;
        t.axis = axis == Direction.Axis.Z ? "Z" : "X";
        t.width = axis == Direction.Axis.Z ? (maxZ - minZ + 1) : (maxX - minX + 1);
        t.height = maxY - minY + 1;

        LinkMath.computeDestination(t);
        return t;
    }
}
