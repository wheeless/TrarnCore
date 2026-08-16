package net.autorelog.rule;

import java.util.Set;

/**
 * What kind of disconnect this was, decided from the reason's translation key.
 *
 * <p>The distinction that matters is <em>who ended the session and why</em>. A timeout is the
 * network failing and retrying is exactly right. A ban is the server refusing you and retrying is
 * both useless and rude. Minecraft does not label these, but it does translate them, and the keys
 * are stable: transport failures live under {@code disconnect.*} and server decisions under
 * {@code multiplayer.disconnect.*}.
 *
 * <p>A reason carrying no vanilla key at all is text the server wrote itself — an operator kick,
 * a plugin's maintenance notice. That is {@link #KICK_CUSTOM}, and it is the case the defaults
 * are deliberately conservative about, because its content is unknowable in advance.
 */
public enum DisconnectKind {

    /** The connection failed. Nothing is wrong with you or the server; retrying is the fix. */
    NETWORK("Connection problem"),

    /** The server went away on purpose — a restart or a full lobby. Retrying works, but later. */
    SERVER_CLOSED("Server closed or full"),

    /** The server refused you: banned, not whitelisted, kicked by an operator. Retrying is rude. */
    SERVER_REFUSED("Server refused the connection"),

    /** Something on this end is wrong — auth, version, keys. Retrying cannot fix any of it. */
    CLIENT_PROBLEM("Client or account problem"),

    /** Server-authored text with no vanilla key behind it. Almost always an operator or plugin. */
    KICK_CUSTOM("Kicked with a custom message"),

    /** No reason could be read at all. Treated as a custom kick, since assuming less is safer. */
    UNKNOWN("Unknown");

    private final String label;

    DisconnectKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Retrying could plausibly succeed. Says nothing about whether it is polite — see the config. */
    public boolean isTransient() {
        return this == NETWORK || this == SERVER_CLOSED;
    }

    // Transport-level failures. The session ended below the game protocol.
    private static final Set<String> NETWORK_KEYS = Set.of(
        "disconnect.lost",
        "disconnect.timeout",
        "disconnect.endOfStream",
        "disconnect.genericReason",
        "disconnect.packetError",
        "disconnect.unknownHost",
        "disconnect.closed",
        "connect.failed",
        "connect.failed.transfer",
        "multiplayer.disconnect.generic",
        "multiplayer.status.cannot_connect",
        "multiplayer.status.cannot_resolve"
    );

    // The server ended the session for its own reasons, not yours. Coming back later works.
    private static final Set<String> SERVER_CLOSED_KEYS = Set.of(
        "multiplayer.disconnect.server_shutdown",
        "multiplayer.disconnect.server_full"
    );

    // The server decided you specifically should not be here.
    private static final Set<String> SERVER_REFUSED_KEYS = Set.of(
        "multiplayer.disconnect.banned",
        "multiplayer.disconnect.banned.reason",
        "multiplayer.disconnect.banned.expiration",
        "multiplayer.disconnect.banned_ip.reason",
        "multiplayer.disconnect.banned_ip.expiration",
        "multiplayer.disconnect.ip_banned",
        "multiplayer.disconnect.not_whitelisted",
        "multiplayer.disconnect.kicked",
        "multiplayer.disconnect.duplicate_login",
        "multiplayer.disconnect.name_taken",
        "multiplayer.disconnect.flying",
        "multiplayer.disconnect.idling",
        "multiplayer.disconnect.illegal_characters",
        "multiplayer.disconnect.code_of_conduct",
        "multiplayer.disconnect.transfers_disabled",
        "disconnect.spam",
        "disconnect.exceeded_packet_rate"
    );

    // Reconnecting changes nothing: the client, the account or the protocol is the problem.
    private static final Set<String> CLIENT_PROBLEM_KEYS = Set.of(
        "multiplayer.disconnect.outdated_client",
        "multiplayer.disconnect.outdated_server",
        "multiplayer.disconnect.incompatible",
        "multiplayer.disconnect.unverified_username",
        "multiplayer.disconnect.authservers_down",
        "multiplayer.disconnect.expired_public_key",
        "multiplayer.disconnect.invalid_public_key_signature",
        "multiplayer.disconnect.invalid_public_key_signature.new",
        "multiplayer.disconnect.missing_tags",
        "multiplayer.disconnect.chat_validation_failed",
        "multiplayer.disconnect.unsigned_chat",
        "multiplayer.disconnect.out_of_order_chat",
        "multiplayer.disconnect.bad_chat_index",
        "multiplayer.disconnect.too_many_pending_chats",
        "multiplayer.disconnect.invalid_packet",
        "multiplayer.disconnect.invalid_player_data",
        "multiplayer.disconnect.invalid_player_movement",
        "multiplayer.disconnect.invalid_vehicle_movement",
        "multiplayer.disconnect.invalid_entity_attacked",
        "multiplayer.disconnect.unexpected_query_response",
        "multiplayer.disconnect.configuration_error",
        "multiplayer.disconnect.slow_login",
        "disconnect.loginFailedInfo",
        "disconnect.loginFailedInfo.insufficientPrivileges",
        "disconnect.loginFailedInfo.invalidSession",
        "disconnect.loginFailedInfo.serversUnavailable",
        "disconnect.loginFailedInfo.userBanned"
    );

    /**
     * Classifies a disconnect from every translation key found in the reason.
     *
     * <p>Takes the whole set rather than just the outermost key because vanilla nests them —
     * a ban reason is {@code multiplayer.disconnect.banned.reason} wrapping the operator's text,
     * and a server that appends a translated footer to a literal kick would otherwise read as a
     * plain custom kick.
     *
     * <p>Where a reason carries keys from several groups, the most restrictive wins. Being wrong
     * towards "do not reconnect" costs a manual click; being wrong the other way hammers a server
     * that already said no.
     */
    public static DisconnectKind classify(Set<String> translationKeys) {
        if (translationKeys.isEmpty()) return KICK_CUSTOM;

        if (containsAny(translationKeys, SERVER_REFUSED_KEYS)) return SERVER_REFUSED;
        if (containsAny(translationKeys, CLIENT_PROBLEM_KEYS)) return CLIENT_PROBLEM;
        if (containsAny(translationKeys, SERVER_CLOSED_KEYS))  return SERVER_CLOSED;
        if (containsAny(translationKeys, NETWORK_KEYS))        return NETWORK;

        // A key we do not recognise. Could be a modded server or a newer Minecraft; either way
        // it is not something this mod was taught to retry.
        return KICK_CUSTOM;
    }

    private static boolean containsAny(Set<String> keys, Set<String> group) {
        for (String key : keys) {
            if (group.contains(key)) return true;
        }
        return false;
    }
}
