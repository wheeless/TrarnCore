package net.containerutil.render;

import net.containerutil.container.ContainerKind;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.IndexManager;
import net.containerutil.data.WorldIdentity;
import net.containerutil.scan.ContainerScanner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/**
 * Resolves which indexed container you are currently looking at.
 *
 * <p>Uses its own raycast rather than {@code client.crosshairTarget} so the peek range can
 * exceed your interaction reach — being able to read a chest's contents from across the room
 * is most of the value, and vanilla's crosshair target stops at about five blocks.
 */
public final class ContainerPeek {

    private ContainerPeek() {
    }

    /** The indexed container under the crosshair within {@code maxDistance} blocks, or {@code null}. */
    public static ContainerRecord lookedAt(Minecraft client, int maxDistance) {
        if (client.player == null || client.level == null) return null;
        if (!IndexManager.isActive()) return null;

        String dim = WorldIdentity.currentDimension();
        if (dim == null) return null;

        Vec3 start = ViewAnchor.eyePos(client);
        Vec3 end = start.add(ViewAnchor.lookVec(client).scale(maxDistance));

        BlockHitResult hit = client.level.clip(new ClipContext(
            start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));

        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        ContainerKind kind = ContainerKind.fromBlockState(state);
        if (kind == null) return null;

        // A double chest is stored under whichever half has the lower packed position, so looking
        // at either half has to resolve to that same canonical key.
        BlockPos partner = ContainerScanner.doubleChestPartner(state, pos);
        BlockPos canonical = partner != null && partner.asLong() < pos.asLong() ? partner : pos;

        return IndexManager.index().get(
            ContainerRecord.blockKey(dim, canonical.getX(), canonical.getY(), canonical.getZ()));
    }
}
