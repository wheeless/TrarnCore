package net.easyportallinker.portal;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;

/**
 * Finds the nether-portal block the player means to select. Portal blocks have no collision or
 * outline shape, so the vanilla crosshair ray never lands on them — we walk our own ray instead,
 * and fall back to the frame the player is aiming at or the portal they are standing in.
 */
public final class PortalDetector {

    private PortalDetector() {}

    /**
     * @return a portal block the player is looking at / standing in, or {@code null} if none is
     *         within {@code reach} blocks
     */
    public static BlockPos findPortal(Level world, Player player, double reach) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 dir = player.getViewVector(1.0f);

        // 1) Walk the look vector, sampling each block it passes through.
        BlockPos lastObsidian = null;
        BlockPos lastSampled = null;
        for (double d = 0; d <= reach; d += 0.1) {
            BlockPos bp = BlockPos.containing(start.x + dir.x * d, start.y + dir.y * d, start.z + dir.z * d);
            if (bp.equals(lastSampled)) continue;
            lastSampled = bp;

            BlockState st = world.getBlockState(bp);
            if (st.getBlock() == Blocks.NETHER_PORTAL) return bp;
            if (st.getBlock() == Blocks.OBSIDIAN) {
                lastObsidian = bp;   // remember the frame in case we don't hit the portal itself
                continue;
            }
            if (!st.isAir()) break;  // any other solid block occludes the view — stop here
        }

        // 2) Aimed at the obsidian frame: grab an adjacent portal block.
        if (lastObsidian != null) {
            BlockPos n = adjacentPortal(world, lastObsidian);
            if (n != null) return n;
        }

        // 3) Standing inside (or with head in) a portal.
        for (BlockPos bp : new BlockPos[]{
                player.blockPosition(),
                player.blockPosition().above(),
                BlockPos.containing(player.getEyePosition())
        }) {
            if (world.getBlockState(bp).getBlock() == Blocks.NETHER_PORTAL) return bp;
        }

        return null;
    }

    private static BlockPos adjacentPortal(Level world, BlockPos frame) {
        for (Direction dir : Direction.values()) {
            BlockPos n = frame.relative(dir);
            if (world.getBlockState(n).getBlock() == Blocks.NETHER_PORTAL) return n;
        }
        return null;
    }
}
