package net.containerutil.data;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Works out which index file the world you are in belongs to.
 *
 * <p>Getting this wrong is the one failure mode that actively destroys data — if two worlds
 * collapse onto the same key, opening a chest in one overwrites the record of a chest in the
 * other. So singleplayer saves key on the save folder name and multiplayer keys on the server
 * address, with the two namespaced apart ({@code sp_} / {@code mp_}) so a save called
 * "example.com" can never collide with the server of the same name.
 */
public final class WorldIdentity {

    private WorldIdentity() {
    }

    /** Returns a filesystem-safe key for the current world, or {@code null} if not in one. */
    public static String current() {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.isIntegratedServerRunning() && client.getServer() != null) {
            String levelName = client.getServer().getSaveProperties().getLevelName();
            return "sp_" + sanitize(levelName);
        }

        ServerInfo entry = client.getCurrentServerEntry();
        if (entry != null && entry.address != null && !entry.address.isBlank()) {
            return "mp_" + sanitize(entry.address);
        }

        // Connected to something we cannot name (a direct-connect that has not populated the
        // server entry yet, for instance). Better one shared bucket than silently dropping data.
        if (client.getNetworkHandler() != null) {
            return "mp_unknown";
        }

        return null;
    }

    /** Registry id of the dimension the player is currently in, e.g. {@code minecraft:overworld}. */
    public static String currentDimension() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        return client.world.getRegistryKey().getValue().toString();
    }

    /**
     * Reduces an arbitrary name to something safe on every filesystem we might land on.
     * Collisions after sanitising are possible in principle but need two worlds whose names
     * differ only in punctuation, which is a far smaller risk than an unwritable path.
     */
    private static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";
        StringBuilder out = new StringBuilder(raw.length());
        for (char c : raw.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '.') {
                out.append(Character.toLowerCase(c));
            } else {
                out.append('_');
            }
        }
        String result = out.toString();
        // Windows caps path components well under this; 96 leaves room for the ".json" and prefix.
        if (result.length() > 96) result = result.substring(0, 96);
        return result;
    }
}
