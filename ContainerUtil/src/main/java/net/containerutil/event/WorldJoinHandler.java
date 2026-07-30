package net.containerutil.event;

import net.containerutil.ContainerUtil;
import net.containerutil.capture.ContentCapture;
import net.containerutil.data.IndexManager;
import net.containerutil.data.WorldIdentity;
import net.containerutil.render.TrackedContainer;
import net.containerutil.scan.ContainerScanner;
import net.containerutil.search.SearchHighlight;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Ties the index's lifetime to the world you are in.
 *
 * <p>The index is loaded on join rather than at startup because which file to load depends
 * entirely on where you connected, and flushed on disconnect (and again at game shutdown) so
 * quitting straight after sorting a chest never loses the capture.
 */
public class WorldJoinHandler {

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            try {
                String worldKey = WorldIdentity.current();
                if (worldKey == null) {
                    ContainerUtil.LOGGER.warn("[ContainerUtil] Joined a world with no identifiable key — not indexing");
                    return;
                }
                IndexManager.openWorld(worldKey);
                ContainerScanner.reset();
            } catch (Exception e) {
                ContainerUtil.LOGGER.error("[ContainerUtil] World join handler crashed", e);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            try {
                IndexManager.closeWorld();
                ContentCapture.clear();
                TrackedContainer.clear();
                SearchHighlight.clear();
                ContainerScanner.reset();
            } catch (Exception e) {
                ContainerUtil.LOGGER.error("[ContainerUtil] Disconnect handler crashed", e);
            }
        });

        // Backstop for the case where the game is closed without a clean disconnect.
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                IndexManager.flushNow();
            } catch (Exception e) {
                ContainerUtil.LOGGER.error("[ContainerUtil] Shutdown flush failed", e);
            }
        });
    }
}
