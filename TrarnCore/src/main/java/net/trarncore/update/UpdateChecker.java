package net.trarncore.update;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.trarncore.TrarnCore;
import net.trarncore.chat.ChatChannel;
import net.trarncore.config.ConfigManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells the player when a newer release of an installed mod exists. Notification only — it never
 * downloads or replaces anything.
 *
 * <p><b>Why it does not self-update.</b> A running Minecraft holds its mod jars open, so replacing
 * one in place means deleting a file the JVM has locked, which fails outright on Windows. The usual
 * workaround — stage the download and swap on next launch via a helper process — is exactly where
 * self-updaters break installs and leave a mods folder that will not launch. Pointing at the
 * release page is worth far more than owning that failure mode.
 *
 * <p>Mods opt in from their client initialiser:
 * <pre>{@code
 * UpdateChecker.watch(MOD_ID, CHAT);
 * }</pre>
 *
 * <p>The lookup runs once per launch on a background thread, and every registered mod is answered
 * by that single request — see {@link GitHubReleaseSource}. Results are reported shortly after you
 * join a world, so the message is not buried in the startup log spam.
 */
public final class UpdateChecker {

    /** Ticks after joining a world before reporting, so the message lands in a settled chat. */
    private static final int REPORT_DELAY_TICKS = 100; // ~5 seconds

    private record Watched(String modId, ModVersion installed, ChatChannel chat) {
    }

    private static final Map<String, Watched> WATCHED = new LinkedHashMap<>();
    private static final Map<String, ReleaseSource.Available> RESULTS = new ConcurrentHashMap<>();

    private static volatile boolean lookupStarted = false;
    private static volatile boolean lookupFinished = false;
    private static volatile boolean reported = false;

    private static int ticksInWorld = -1;

    private UpdateChecker() {
    }

    /**
     * Registers a mod to be checked. Safe to call before the config is loaded; the lookup does not
     * start until {@link #start()} runs.
     *
     * @param modId as declared in {@code fabric.mod.json}
     * @param chat  the mod's own channel, so the notice carries its prefix and colour
     */
    public static synchronized void watch(String modId, ChatChannel chat) {
        String installedText = FabricLoader.getInstance().getModContainer(modId)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse(null);

        if (installedText == null) {
            TrarnCore.LOGGER.debug("[update] '{}' is not loaded; not watching it", modId);
            return;
        }

        ModVersion installed = ModVersion.parse(installedText);
        if (installed == null) {
            // A version we cannot compare is one we must not guess about.
            TrarnCore.LOGGER.debug("[update] '{}' has unparseable version '{}'; not watching it",
                modId, installedText);
            return;
        }
        WATCHED.put(modId, new Watched(modId, installed, chat));
    }

    /** Called once by TrarnCore's initialiser, after every mod has had a chance to register. */
    public static void start() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ticksInWorld = 0);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ticksInWorld = -1);
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        if (WATCHED.isEmpty() || reported) return;
        if (!ConfigManager.get().checkForUpdates) return;

        // Kick the lookup off the first time we are actually in a world: no point spending a
        // request during a session where the player never loads one.
        if (ticksInWorld < 0) return;
        if (!lookupStarted) {
            lookupStarted = true;
            startLookup();
        }

        ticksInWorld++;
        if (ticksInWorld < REPORT_DELAY_TICKS || !lookupFinished) return;

        reported = true;
        report();
    }

    private static void startLookup() {
        Thread thread = new Thread(() -> {
            try {
                ReleaseSource source = new GitHubReleaseSource(ConfigManager.get().updateRepository);
                RESULTS.putAll(source.latestVersions());
            } catch (Exception e) {
                TrarnCore.LOGGER.debug("[update] lookup failed", e);
            } finally {
                lookupFinished = true;
            }
        }, "trarncore-update-check");
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, e) -> {
            TrarnCore.LOGGER.debug("[update] {} died", t.getName(), e);
            lookupFinished = true;
        });
        thread.start();
    }

    private static void report() {
        Map<String, String> alreadyTold = ConfigManager.get().lastNotifiedVersion;
        List<String> announced = new ArrayList<>();

        for (Watched watched : WATCHED.values()) {
            ReleaseSource.Available available = RESULTS.get(watched.modId());
            if (available == null) continue;
            if (!available.version().isNewerThan(watched.installed())) continue;

            // Mention a given version once, then stay quiet until something newer lands.
            String latest = available.version().toString();
            if (latest.equals(alreadyTold.get(watched.modId()))) continue;

            watched.chat().send(
                Component.literal("Update available: ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(watched.installed() + " → " + latest)
                        .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("  [open release]")
                        .withStyle(style -> style
                            .withColor(ChatFormatting.AQUA)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(available.releaseUrl()))))));

            alreadyTold.put(watched.modId(), latest);
            announced.add(watched.modId());
        }

        if (!announced.isEmpty()) {
            ConfigManager.save();
            TrarnCore.LOGGER.info("[update] newer releases available for {}", announced);
        }
    }
}
