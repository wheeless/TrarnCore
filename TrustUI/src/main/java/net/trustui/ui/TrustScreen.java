package net.trustui.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.trustui.TrustUI;
import net.trustui.config.ConfigManager;
import net.trustui.trust.TrustCommands;
import net.trustui.trust.TrustLevel;
import net.trustui.trust.TrustListReader;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Trust management for the claim you are standing in, built out of the same widgets as vanilla's
 * Social Interactions screen so it looks like part of the game rather than an overlay.
 *
 * <p>{@link HeaderAndFooterLayout} gives the title/body/footer arrangement and the Done button;
 * {@link TrustListWidget} is a real {@code ContainerObjectSelectionList}, which brings the bordered panel,
 * scrollbar, row highlighting, focus outlines and narration with it.
 *
 * <p>Row heights are fixed there, so a row cannot literally grow when clicked. Expanding instead
 * inserts an actions entry directly beneath the player's row — two entries to the list, one
 * opening row to the player.
 */
public class TrustScreen extends Screen {

    private static final int ROW_HEIGHT = 32;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    private EditBox searchBox;
    private TrustListWidget list;
    private Button refreshButton;

    private final List<PlayerEntry> all = new ArrayList<>();
    private String expanded;

    private boolean loading = true;
    private boolean inClaim = false;

    public TrustScreen() {
        super(Component.literal("Claim Trust"));
    }

    @Override
    protected void init() {
        layout.addTitleHeader(getTitle(), font);

        list = layout.addToContents(new TrustListWidget(minecraft, width, layout.getContentHeight(),
            layout.getHeaderHeight(), ROW_HEIGHT));

        searchBox = new EditBox(font, 0, 0, 200, 18, Component.literal("Search"));
        searchBox.setMaxLength(64);
        searchBox.setHint(Component.literal("Search players").withStyle(ChatFormatting.DARK_GRAY));
        searchBox.setResponder(text -> rebuildRows());
        layout.addToHeader(searchBox);

        refreshButton = layout.addToFooter(Button.builder(Component.literal("Refresh"),
            b -> refresh()).width(80).build());
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(80).build());

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        setInitialFocus(searchBox);

        refresh();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if (list != null) {
            list.updateSize(width, layout);
        }
    }

    /** Asks the server who is trusted here, then rebuilds the list around the answer. */
    private void refresh() {
        loading = true;
        expanded = null;
        if (refreshButton != null) refreshButton.active = false;

        TrustListReader.request(result -> {
            inClaim = result.inClaim();
            buildEntries(result.trusted());
            loading = false;
            if (refreshButton != null) refreshButton.active = true;
        });
    }

    /**
     * Merges the online player list with the names the server reported as trusted.
     *
     * <p>The two only partly overlap: someone can be trusted but offline — still needing a row so
     * their access can be revoked — and most online players hold no trust at all.
     */
    private void buildEntries(Map<String, EnumSet<TrustLevel>> trusted) {
        all.clear();

        Minecraft mc = Minecraft.getInstance();
        String self = mc.player != null ? mc.player.getGameProfile().name() : "";
        Map<String, PlayerEntry> byName = new LinkedHashMap<>();

        if (mc.getConnection() != null) {
            for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
                String name = entry.getProfile().name();
                if (name == null || name.equalsIgnoreCase(self)) continue;
                byName.put(name.toLowerCase(Locale.ROOT),
                    new PlayerEntry(name, entry.getSkin(), true, matchTrust(trusted, name)));
            }
        }

        if (ConfigManager.get().showOfflineTrusted) {
            for (Map.Entry<String, EnumSet<TrustLevel>> entry : trusted.entrySet()) {
                String name = entry.getKey();
                if (name.equalsIgnoreCase(self)) continue;
                String key = name.toLowerCase(Locale.ROOT);
                if (byName.containsKey(key)) continue;
                // No profile to look a skin up from, so fall back to the default.
                byName.put(key, new PlayerEntry(name, DefaultPlayerSkin.getDefaultSkin(), false, entry.getValue()));
            }
        }

        // Trusted players first — they are what you came to manage — then the rest alphabetically.
        List<PlayerEntry> entries = new ArrayList<>(byName.values());
        entries.sort((a, b) -> {
            if (a.hasAnyTrust() != b.hasAnyTrust()) return a.hasAnyTrust() ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        all.addAll(entries);
        rebuildRows();
    }

    private static EnumSet<TrustLevel> matchTrust(Map<String, EnumSet<TrustLevel>> trusted, String name) {
        for (Map.Entry<String, EnumSet<TrustLevel>> entry : trusted.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return EnumSet.noneOf(TrustLevel.class);
    }

    /** Rebuilds the visible rows, inserting an actions row under whichever player is expanded. */
    private void rebuildRows() {
        if (list == null) return;
        list.clear();

        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        for (PlayerEntry player : all) {
            if (!query.isEmpty() && !player.name().toLowerCase(Locale.ROOT).contains(query)) continue;

            boolean isExpanded = player.name().equals(expanded);
            list.add(new TrustListEntry.Player(player, isExpanded, () -> toggle(player.name())));
            if (isExpanded) {
                list.add(new TrustListEntry.Actions(player,
                    level -> grant(player, level),
                    () -> revoke(player)));
            }
        }
    }

    private void toggle(String name) {
        expanded = name.equals(expanded) ? null : name;
        rebuildRows();
    }

    private void grant(PlayerEntry player, TrustLevel level) {
        TrustCommands.grant(level, player.name());
        TrustUI.CHAT.send("Granted " + level.displayName() + " to " + player.name());
        afterChange();
    }

    private void revoke(PlayerEntry player) {
        TrustCommands.revoke(player.name());
        TrustUI.CHAT.send("Removed all trust from " + player.name(), ChatFormatting.GOLD);
        afterChange();
    }

    /**
     * Re-reads the claim after a change, so the menu shows what the server actually did rather
     * than what we assumed — a command refused for permissions or a mistyped name would otherwise
     * be hidden behind an optimistic row update.
     */
    private void afterChange() {
        if (ConfigManager.get().refreshAfterChange) {
            refresh();
        } else {
            expanded = null;
            rebuildRows();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        // Status sits just under the title, where vanilla puts "Server26 - 6 players".
        String status;
        int color = 0xFFA0A0A0;
        if (loading) {
            status = "Reading claim…";
        } else if (!inClaim) {
            status = "Not standing in a claim";
            color = 0xFFFF7043;
        } else {
            long count = all.stream().filter(PlayerEntry::hasAnyTrust).count();
            status = count + (count == 1 ? " player trusted" : " players trusted");
        }
        context.centeredText(font, status, width / 2,
            layout.getHeaderHeight() - 12, color);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        // Only when the search box does not have focus, or typing "r" would refresh instead.
        if (input.key() == GLFW.GLFW_KEY_R && (searchBox == null || !searchBox.isFocused())) {
            refresh();
            return true;
        }
        if (TrustUI.OPEN_MENU != null && TrustUI.OPEN_MENU.matches(input)) {
            onClose();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
