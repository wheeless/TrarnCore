package net.trarncore.chat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Prefixed, local-only chat feedback for one mod.
 *
 * <p>Everything here goes through {@link net.minecraft.client.gui.hud.ChatHud#addMessage(Text)},
 * which appends straight to the client's own chat log. No packet is sent, so nothing reaches the
 * server or other players — this is not the same as sending a chat message. ({@code ChatHud}
 * contains no networking code at all; sending chat goes through
 * {@code ClientPlayNetworkHandler.sendChatMessage}, which nothing here calls.)
 *
 * <p>Chat rather than the action bar deliberately. The action bar is a single slot that servers,
 * scoreboards and other mods all write to, so anything put there is liable to be overwritten a
 * tick later, or to overwrite something the player wanted to read. Chat is a log: several
 * sources coexist, and the player can scroll back to something they missed.
 *
 * <p>Create one per mod and keep it in a static field:
 * <pre>{@code
 * public static final ChatChannel CHAT = ChatChannel.of("ContainerUtil", Formatting.DARK_AQUA);
 * }</pre>
 */
public final class ChatChannel {

    private final String prefix;
    private final Formatting prefixColor;

    private ChatChannel(String modName, Formatting prefixColor) {
        this.prefix = "[" + modName + "] ";
        this.prefixColor = prefixColor;
    }

    /**
     * @param modName     shown in brackets, e.g. {@code "ContainerUtil"}
     * @param prefixColor give each mod its own so prefixes stay distinguishable when several
     *                    are installed together
     */
    public static ChatChannel of(String modName, Formatting prefixColor) {
        return new ChatChannel(modName, prefixColor);
    }

    /** Appends a prefixed line to the local chat log. */
    public void send(Text message) {
        raw(Text.literal(prefix).formatted(prefixColor).append(message));
    }

    /**
     * Appends a prefixed line. Legacy {@code §} colour codes inside {@code message} still apply,
     * since the text renderer processes them regardless of the style set here.
     */
    public void send(String message) {
        send(Text.literal(message).formatted(Formatting.WHITE));
    }

    /** Appends a prefixed line in a specific colour. */
    public void send(String message, Formatting color) {
        send(Text.literal(message).formatted(color));
    }

    /**
     * Appends a line exactly as given, with no prefix.
     *
     * <p>For messages that already carry their own formatted prefix, or continuation lines that
     * deliberately have none.
     */
    public void sendRaw(String message) {
        raw(Text.literal(message));
    }

    /** Appends a line exactly as given, with no prefix. */
    public void sendRaw(Text message) {
        raw(message);
    }

    private static void raw(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Safe to call before the HUD exists — during early init, or after a disconnect.
        if (client == null || client.inGameHud == null) return;
        client.inGameHud.getChatHud().addMessage(message);
    }
}
