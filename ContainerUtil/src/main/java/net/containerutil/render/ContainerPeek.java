package net.containerutil.render;

import net.containerutil.container.ContainerKind;
import net.containerutil.data.ContainerRecord;
import net.containerutil.data.IndexManager;
import net.containerutil.data.WorldIdentity;
import net.containerutil.scan.ContainerScanner;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

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
    public static ContainerRecord lookedAt(MinecraftClient client, int maxDistance) {
        if (client.player == null || client.world == null) return null;
        if (!IndexManager.isActive()) return null;

        String dim = WorldIdentity.currentDimension();
        if (dim == null) return null;

        Vec3d start = ViewAnchor.eyePos(client);
        Vec3d end = start.add(ViewAnchor.lookVec(client).multiply(maxDistance));

        BlockHitResult hit = client.world.raycast(new RaycastContext(
            start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, client.player));

        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = hit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
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
