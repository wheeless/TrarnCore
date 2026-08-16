package net.autorelog.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.autorelog.rule.Rule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cloth Config screen for AutoRelog.
 * Only classloaded when cloth-config is present — ModMenuIntegration guards the load.
 */
public class AutoRelogConfigScreen {

    public static Screen build(Screen parent) {
        AutoRelogConfig config = ConfigManager.get();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("AutoRelog Settings"))
            .setSavingRunnable(ConfigManager::save);

        ConfigEntryBuilder entry = builder.entryBuilder();

        general(builder, entry, config);
        whenToReconnect(builder, entry, config);
        timing(builder, entry, config);
        rules(builder, entry, config);
        syntax(builder, entry);

        return builder.build();
    }

    // ── General ───────────────────────────────────────────────────────────────

    private static void general(ConfigBuilder builder, ConfigEntryBuilder entry, AutoRelogConfig config) {
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Enabled"), config.enabled)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Master on/off. When disabled nothing is evaluated and "
                + "the disconnect screen is left exactly as vanilla draws it."))
            .setSaveConsumer(value -> config.enabled = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Show Countdown"), config.showCountdown)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("Add a countdown button and a Cancel button to the disconnect screen."),
                Component.literal("Turn off for a silent reconnect with no UI. The countdown still runs."))
            .setSaveConsumer(value -> config.showCountdown = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Cancel On Any Key"), config.cancelOnKeyPress)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("Any key press cancels a pending reconnect, not just the button."),
                Component.literal("You came back to the keyboard; whatever you press should stop it."))
            .setSaveConsumer(value -> config.cancelOnKeyPress = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Announce In Chat"), config.announceInChat)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("Say what was decided, and why, in local chat."),
                Component.literal("Local only — nothing is sent to the server."))
            .setSaveConsumer(value -> config.announceInChat = value)
            .build());

        general.addEntry(entry
            .startBooleanToggle(Component.literal("Verbose Logging"), config.verboseLogging)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Log every disconnect with its kind, translation keys and the rule that matched."),
                Component.literal("Turn on while writing rules — the keys it prints are what a "
                    + "\"key\" rule matches against."))
            .setSaveConsumer(value -> config.verboseLogging = value)
            .build());
    }

    // ── When to reconnect ─────────────────────────────────────────────────────

    private static void whenToReconnect(ConfigBuilder builder, ConfigEntryBuilder entry, AutoRelogConfig config) {
        ConfigCategory when = builder.getOrCreateCategory(Component.literal("When"));

        when.addEntry(entry
            .startTextDescription(Component.literal(
                "These decide what happens when no rule matches. Rules always win over the toggles below.")
                .withStyle(ChatFormatting.GRAY))
            .build());

        when.addEntry(entry
            .startBooleanToggle(Component.literal("Reconnect On Kick"), config.reconnectOnKick)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Reconnect when an operator or plugin kicked you with a custom message."),
                Component.literal("Off by default on purpose. A kick is somebody deciding you should"),
                Component.literal("leave, and walking straight back in is what gets reconnect mods banned."),
                Component.literal("Prefer a rule matching the specific message you want to retry.")
                    .withStyle(ChatFormatting.YELLOW))
            .setSaveConsumer(value -> config.reconnectOnKick = value)
            .build());

        when.addEntry(entry
            .startBooleanToggle(Component.literal("Reconnect When Refused"), config.reconnectWhenRefused)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Reconnect after a ban, an IP ban, or not being whitelisted."),
                Component.literal("Retrying cannot succeed, and knocking repeatedly is how an")
                    .withStyle(ChatFormatting.RED),
                Component.literal("account ban becomes an IP ban. There is no good reason to enable this.")
                    .withStyle(ChatFormatting.RED))
            .setSaveConsumer(value -> config.reconnectWhenRefused = value)
            .build());

        when.addEntry(entry
            .startBooleanToggle(Component.literal("Reconnect On Server Closed"), config.reconnectOnServerClosed)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("Reconnect after \"Server closed\" or a full server."),
                Component.literal("The restart case, and the reason this mod is worth having."),
                Component.literal("Uses its own longer delay — see the Timing tab."))
            .setSaveConsumer(value -> config.reconnectOnServerClosed = value)
            .build());

        when.addEntry(entry
            .startBooleanToggle(Component.literal("Reconnect On Client Problem"), config.reconnectOnClientProblem)
            .setDefaultValue(false)
            .setTooltip(
                Component.literal("Reconnect after a version mismatch, an expired session or auth being down."),
                Component.literal("Off, because retrying fixes none of those and the loop would just"),
                Component.literal("run out the attempt budget while the real message sat unread."))
            .setSaveConsumer(value -> config.reconnectOnClientProblem = value)
            .build());

        when.addEntry(entry
            .startBooleanToggle(Component.literal("Retry Failed Connections"), config.reconnectOnFailedConnect)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("Retry when the connection never established, as opposed to a session dropping."),
                Component.literal("This is what keeps trying while a server is still booting."))
            .setSaveConsumer(value -> config.reconnectOnFailedConnect = value)
            .build());
    }

    // ── Timing ────────────────────────────────────────────────────────────────

    private static void timing(ConfigBuilder builder, ConfigEntryBuilder entry, AutoRelogConfig config) {
        ConfigCategory timing = builder.getOrCreateCategory(Component.literal("Timing"));

        timing.addEntry(entry
            .startIntField(Component.literal("Delay (seconds)"), config.delaySeconds)
            .setDefaultValue(5)
            .setMin(0).setMax(3600)
            .setTooltip(Component.literal("Wait before the first reconnect attempt."))
            .setSaveConsumer(value -> config.delaySeconds = value)
            .build());

        timing.addEntry(entry
            .startIntField(Component.literal("Server Closed Delay (seconds)"), config.serverClosedDelaySeconds)
            .setDefaultValue(60)
            .setMin(0).setMax(3600)
            .setTooltip(
                Component.literal("Wait used when the server closed rather than the connection failing."),
                Component.literal("A restart takes longer than a network blip, and reconnecting into"),
                Component.literal("a server that is still loading chunks just fails again."))
            .setSaveConsumer(value -> config.serverClosedDelaySeconds = value)
            .build());

        timing.addEntry(entry
            .startDoubleField(Component.literal("Backoff Multiplier"), config.backoffMultiplier)
            .setDefaultValue(1.8)
            .setMin(1.0).setMax(10.0)
            .setTooltip(
                Component.literal("Each failed attempt multiplies the wait by this. 1.0 disables backoff."),
                Component.literal("The first wait is always exactly the delay above — only repeats grow."))
            .setSaveConsumer(value -> config.backoffMultiplier = value)
            .build());

        timing.addEntry(entry
            .startIntField(Component.literal("Max Delay (seconds)"), config.maxDelaySeconds)
            .setDefaultValue(300)
            .setMin(1).setMax(7200)
            .setTooltip(Component.literal("Ceiling for the backed-off wait, so an overnight retry loop "
                + "does not drift into hours between attempts."))
            .setSaveConsumer(value -> config.maxDelaySeconds = value)
            .build());

        timing.addEntry(entry
            .startIntSlider(Component.literal("Jitter"), config.jitterPercent, 0, 100)
            .setDefaultValue(20)
            .setTextGetter(value -> Component.literal(value == 0 ? "off" : value + "%"))
            .setTooltip(
                Component.literal("Random extra wait, as a percentage of the computed delay."),
                Component.literal("When a server restarts, every client running this mod counts down"),
                Component.literal("from the same instant. Without jitter they all reconnect on the"),
                Component.literal("same tick, against a server that just finished booting."))
            .setSaveConsumer(value -> config.jitterPercent = value)
            .build());

        timing.addEntry(entry
            .startIntField(Component.literal("Max Attempts"), config.maxAttempts)
            .setDefaultValue(10)
            .setMin(0).setMax(1000)
            .setTooltip(
                Component.literal("Give up after this many consecutive attempts. 0 means never give up."),
                Component.literal("A rule can override this for a specific message."))
            .setSaveConsumer(value -> config.maxAttempts = value)
            .build());

        timing.addEntry(entry
            .startIntField(Component.literal("Session Reset (seconds)"), config.sessionResetSeconds)
            .setDefaultValue(60)
            .setMin(0).setMax(3600)
            .setTooltip(
                Component.literal("A session lasting at least this long resets the attempt counter."),
                Component.literal("Without it, ten brief drops over an evening exhaust the budget and"),
                Component.literal("the next one hours later is refused for no visible reason."))
            .setSaveConsumer(value -> config.sessionResetSeconds = value)
            .build());
    }

    // ── Rules ─────────────────────────────────────────────────────────────────

    private static void rules(ConfigBuilder builder, ConfigEntryBuilder entry, AutoRelogConfig config) {
        ConfigCategory rules = builder.getOrCreateCategory(Component.literal("Rules"));

        rules.addEntry(entry
            .startTextDescription(Component.literal(
                "One rule per line, checked top to bottom. The first match decides and the rest are "
                + "skipped. See the Syntax tab for the full format and worked examples.")
                .withStyle(ChatFormatting.GRAY))
            .build());

        List<String> lines = new ArrayList<>();
        for (Rule rule : config.rules) lines.add(rule.serialize());

        rules.addEntry(entry
            .startStrList(Component.literal("Rules"), lines)
            .setDefaultValue(defaultRuleLines())
            .setTooltip(
                Component.literal("action | mode | pattern | delay | attempts"),
                Component.literal("Prefix a line with # to keep it without it taking effect."),
                Component.literal("A line that will not parse comes back disabled rather than lost."))
            .setSaveConsumer(saved -> {
                List<Rule> parsed = new ArrayList<>();
                for (String line : saved) {
                    Rule rule = Rule.parse(line);
                    if (rule != null) parsed.add(rule);
                }
                config.rules = parsed;
            })
            .build());
    }

    private static List<String> defaultRuleLines() {
        List<String> lines = new ArrayList<>();
        for (Rule rule : AutoRelogConfig.defaultRules()) lines.add(rule.serialize());
        return lines;
    }

    // ── Syntax reference ──────────────────────────────────────────────────────

    private static void syntax(ConfigBuilder builder, ConfigEntryBuilder entry) {
        ConfigCategory syntax = builder.getOrCreateCategory(Component.literal("Syntax"));

        text(syntax, entry, "Rule format", ChatFormatting.GOLD);
        text(syntax, entry, "  action | mode | pattern | delay | attempts", ChatFormatting.WHITE);
        text(syntax, entry, "Everything after pattern is optional. Use - to inherit a global setting.",
            ChatFormatting.GRAY);
        blank(syntax, entry);

        text(syntax, entry, "Actions", ChatFormatting.GOLD);
        text(syntax, entry, "  relog    reconnect, whatever the toggles say", ChatFormatting.WHITE);
        text(syntax, entry, "  never    do not reconnect, whatever the toggles say", ChatFormatting.WHITE);
        text(syntax, entry, "  ignore   stop checking rules and let the defaults decide", ChatFormatting.WHITE);
        blank(syntax, entry);

        text(syntax, entry, "Modes", ChatFormatting.GOLD);
        text(syntax, entry, "  contains   pattern appears anywhere in the message", ChatFormatting.WHITE);
        text(syntax, entry, "  equals     the whole message, ignoring outer whitespace", ChatFormatting.WHITE);
        text(syntax, entry, "  starts     message begins with the pattern", ChatFormatting.WHITE);
        text(syntax, entry, "  ends       message ends with the pattern", ChatFormatting.WHITE);
        text(syntax, entry, "  regex      Java regular expression, matched anywhere", ChatFormatting.WHITE);
        text(syntax, entry, "  key        a vanilla translation key instead of the text", ChatFormatting.WHITE);
        text(syntax, entry, "Add ! to a mode for case-sensitive matching: contains!", ChatFormatting.GRAY);
        blank(syntax, entry);

        text(syntax, entry, "Examples", ChatFormatting.GOLD);
        example(syntax, entry, "relog | contains | restarting | 120",
            "Plugin says it is restarting. Wait 2 minutes, then come back.");
        example(syntax, entry, "never | contains | maintenance",
            "Do not retry while the server is closed for maintenance.");
        example(syntax, entry, "relog | key | multiplayer.disconnect.server_shutdown | 90 | 20",
            "Match the vanilla key, so it works in any language. 90s wait, up to 20 tries.");
        example(syntax, entry, "never | contains | idle",
            "An AFK kick means you walked away. Coming back for you defeats the point.");
        example(syntax, entry, "relog | regex | (?i)rebooting|be right back | 60",
            "Two wordings a server might use for the same thing.");
        example(syntax, entry, "ignore | contains | scheduled restart",
            "Carve one message out of a broader rule below it, without reordering.");
        example(syntax, entry, "# never | contains | test",
            "Disabled. Kept in the list, has no effect.");
        blank(syntax, entry);

        text(syntax, entry, "Finding the right pattern", ChatFormatting.GOLD);
        text(syntax, entry, "Turn on Verbose Logging in General. Every disconnect then logs its kind,",
            ChatFormatting.GRAY);
        text(syntax, entry, "its exact text and its translation keys — which is what a key rule matches.",
            ChatFormatting.GRAY);
        blank(syntax, entry);

        text(syntax, entry, "Order matters", ChatFormatting.GOLD);
        text(syntax, entry, "The first matching rule decides. Put specific rules above general ones,",
            ChatFormatting.GRAY);
        text(syntax, entry, "or the general one will answer first and the specific one never runs.",
            ChatFormatting.GRAY);
    }

    private static void text(ConfigCategory category, ConfigEntryBuilder entry, String line, ChatFormatting color) {
        category.addEntry(entry.startTextDescription(Component.literal(line).withStyle(color)).build());
    }

    private static void blank(ConfigCategory category, ConfigEntryBuilder entry) {
        category.addEntry(entry.startTextDescription(Component.literal(" ")).build());
    }

    private static void example(ConfigCategory category, ConfigEntryBuilder entry, String rule, String explanation) {
        text(category, entry, "  " + rule, ChatFormatting.AQUA);
        text(category, entry, "    " + explanation, ChatFormatting.DARK_GRAY);
    }
}
