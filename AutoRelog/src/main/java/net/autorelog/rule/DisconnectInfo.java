package net.autorelog.rule;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Everything known about why the session ended, flattened into something matchable.
 *
 * @param text           the reason as the player sees it, siblings and arguments resolved
 * @param translationKeys every vanilla key found anywhere in the reason tree, outermost first
 * @param kind           the classification derived from those keys
 */
public record DisconnectInfo(String text, Set<String> translationKeys, DisconnectKind kind) {

    public static DisconnectInfo of(Component reason) {
        if (reason == null) {
            return new DisconnectInfo("", Set.of(), DisconnectKind.UNKNOWN);
        }
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(reason, keys, 0);
        return new DisconnectInfo(reason.getString(), keys, DisconnectKind.classify(keys));
    }

    /**
     * Walks the component tree collecting translation keys.
     *
     * <p>Arguments are walked as well as siblings: {@code multiplayer.disconnect.banned.reason}
     * carries the operator's message as an argument, and a server that translates part of a kick
     * hides its key there.
     *
     * <p>Depth-limited because a Component tree arrives from the network and nothing guarantees
     * it is shallow, or even acyclic once a hostile server is writing it.
     */
    private static void collectKeys(Component component, Set<String> into, int depth) {
        if (depth > 16 || into.size() > 64) return;

        if (component.getContents() instanceof TranslatableContents translatable) {
            into.add(translatable.getKey());
            for (Object arg : translatable.getArgs()) {
                if (arg instanceof Component nested) collectKeys(nested, into, depth + 1);
            }
        }
        for (Component sibling : component.getSiblings()) {
            collectKeys(sibling, into, depth + 1);
        }
    }

    /** True when the reason carried no vanilla key — text the server wrote itself. */
    public boolean isServerAuthored() {
        return translationKeys.isEmpty();
    }

    /** One-line summary for logs and chat. */
    public String describe() {
        String trimmed = text.replace('\n', ' ').strip();
        if (trimmed.length() > 120) trimmed = trimmed.substring(0, 117) + "...";
        return trimmed.isEmpty() ? kind.label() : trimmed;
    }
}
