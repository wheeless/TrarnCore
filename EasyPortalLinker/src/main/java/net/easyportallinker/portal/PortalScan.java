package net.easyportallinker.portal;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

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
    public static PortalTarget scan(Level world, BlockPos start) {
        BlockState startState = world.getBlockState(start);
        if (startState.getBlock() != Blocks.NETHER_PORTAL) return null;

        Direction.Axis axis = startState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
            ? startState.getValue(BlockStateProperties.HORIZONTAL_AXIS)
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
                BlockPos next = pos.relative(dir);
                if (!visited.contains(next.asLong())
                        && world.getBlockState(next).getBlock() == Blocks.NETHER_PORTAL) {
                    visited.add(next.asLong());
                    queue.add(next);
                }
            }
        }

        PortalTarget t = new PortalTarget();
        t.sourceDim = world.dimension().identifier().toString();
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
