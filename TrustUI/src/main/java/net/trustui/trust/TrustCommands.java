package net.trustui.trust;

import net.minecraft.client.Minecraft;
import net.trustui.TrustUI;
import net.trustui.config.ConfigManager;

/**
 * Sends GriefPrevention's trust commands.
 *
 * <p>Nothing clever: these go out through {@code sendChatCommand}, exactly as if you had typed
 * them. There is no packet a server could distinguish from a human running {@code /trust}, which
 * is the whole reason this works without a server-side component.
 *
 * <p>Command names come from config rather than being hardcoded, because servers alias them and
 * some run GriefPrevention forks with different names.
 */
public final class TrustCommands {

    private TrustCommands() {
    }

    /** Grants a tier to a player. */
    public static void grant(TrustLevel level, String playerName) {
        send(ConfigManager.get().commandFor(level) + " " + playerName);
    }

    /** Removes all of a player's trust in the current claim. */
    public static void revoke(String playerName) {
        send(ConfigManager.get().untrustCommand + " " + playerName);
    }

    /** Asks the server to list the current claim's permissions. */
    public static void requestTrustList() {
        send(ConfigManager.get().trustListCommand);
    }

    private static void send(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() == null) {
            TrustUI.LOGGER.warn("[TrustUI] not connected; dropping '{}'", command);
            return;
        }
        TrustUI.LOGGER.debug("[TrustUI] sending /{}", command);
        client.getConnection().sendCommand(command);
    }
}
