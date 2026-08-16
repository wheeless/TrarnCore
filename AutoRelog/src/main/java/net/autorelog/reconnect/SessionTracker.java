package net.autorelog.reconnect;

import net.autorelog.AutoRelog;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Remembers which server to go back to.
 *
 * <p>Necessary because {@link Minecraft#getCurrentServer()} is cleared as part of tearing the
 * session down, and by the time a disconnect screen exists there is nothing left to ask. The
 * server has to be captured while still connected.
 *
 * <p>Also records how long the session lasted, which is what lets the attempt counter reset after
 * a healthy stretch of play instead of carrying yesterday's failures forward.
 */
public final class SessionTracker {

    private SessionTracker() {
    }

    private static volatile ServerData lastServer;
    private static volatile long joinedAtMillis;
    private static volatile long sessionSeconds;
    private static volatile boolean lastWasSingleplayer;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            lastWasSingleplayer = client.hasSingleplayerServer();

            ServerData current = client.getCurrentServer();
            if (current != null && !lastWasSingleplayer) {
                lastServer = current;
                joinedAtMillis = System.currentTimeMillis();
                if (AutoRelog.config().verboseLogging) {
                    AutoRelog.LOGGER.info("[AutoRelog] tracking server {} ({})", current.name, current.ip);
                }
            } else {
                // Singleplayer or LAN. Deliberately does not clear lastServer: a quick trip to a
                // local world should not lose the multiplayer server you were on before it.
                joinedAtMillis = 0;
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            sessionSeconds = joinedAtMillis == 0
                ? 0
                : (System.currentTimeMillis() - joinedAtMillis) / 1000L;
            joinedAtMillis = 0;
        });
    }

    /** The last real multiplayer server joined, or null if there has not been one this launch. */
    public static ServerData lastServer() {
        return lastServer;
    }

    /** How long the session that just ended lasted, in seconds. Zero for singleplayer. */
    public static long lastSessionSeconds() {
        return sessionSeconds;
    }

    /** True when the session that just ended was singleplayer or a LAN world. */
    public static boolean lastWasSingleplayer() {
        return lastWasSingleplayer;
    }

    /**
     * True when a reconnect is possible at all: a remote server was recorded and we are not
     * sitting in a single-player world.
     */
    public static boolean canReconnect() {
        return lastServer != null && !lastWasSingleplayer;
    }
}
