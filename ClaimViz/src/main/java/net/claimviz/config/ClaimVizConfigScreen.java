package net.claimviz.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

/**
 * Cloth Config screen for ClaimViz.
 * Only classloaded when cloth-config is present — ModMenuIntegration guards the load.
 */
public class ClaimVizConfigScreen {

    public static Screen build(Screen parent) {
        ClaimVizConfig config = ConfigManager.get();
        if (config.servers == null) config.servers = new ArrayList<>();

        // Scratch slot for adding a brand-new server
        ClaimVizConfig.ServerConfig newSlot = new ClaimVizConfig.ServerConfig();

        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("ClaimViz Settings"))
            .setSavingRunnable(() -> {
                // Save consumers have already updated the original ServerConfig objects in-place.
                // Just handle add/remove and persist.
                if (newSlot.serverAddress != null && !newSlot.serverAddress.isBlank()) {
                    config.servers.add(newSlot);
                }
                config.servers.removeIf(s -> s.serverAddress == null || s.serverAddress.isBlank());
                ConfigManager.save();
                // No reloadActiveConfig needed — activeConfig is the original object and
                // was already updated in-place by the save consumers above.
            });

        ConfigEntryBuilder entry = builder.entryBuilder();

        // ── General ──────────────────────────────────────────────────────────
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(entry
            .startIntSlider(Component.literal("Claim Render Distance"), config.claimRenderDistance, 50, 500)
            .setDefaultValue(200)
            .setTooltip(Component.literal("How far from you (in blocks) claim borders are drawn."))
            .setSaveConsumer(val -> config.claimRenderDistance = val)
            .build()
        );
        general.addEntry(entry
            .startIntSlider(Component.literal("Map Tile Budget"), config.mapTileBudget, 16, 512)
            .setDefaultValue(128)
            .setTooltip(Component.literal("Max SquareMap tiles held in GPU memory. Lower this if the game runs out of memory with the map open."))
            .setSaveConsumer(val -> config.mapTileBudget = val)
            .build()
        );

        // ── Servers ───────────────────────────────────────────────────────────
        ConfigCategory serversCategory = builder.getOrCreateCategory(Component.literal("Servers"));

        serversCategory.addEntry(entry.startTextDescription(
            Component.literal("Clear a server address to remove that entry. Addresses are matched by substring.")
        ).build());

        // One sub-section per existing server — use originals directly so activeConfig
        // is updated in-place when save consumers fire; no copyOf or reloadActiveConfig needed.
        for (ClaimVizConfig.ServerConfig s : config.servers) {
            String label = (s.serverAddress == null || s.serverAddress.isBlank())
                ? "(empty)" : s.serverAddress;
            SubCategoryBuilder sub = entry
                .startSubCategory(Component.literal("[Server] " + label))
                .setExpanded(false);
            addServerFields(entry, sub, s);
            serversCategory.addEntry(sub.build());
        }

        // "Add New Server" slot — expanded when no servers exist yet
        SubCategoryBuilder addSub = entry
            .startSubCategory(Component.literal("+ Add New Server"))
            .setExpanded(config.servers.isEmpty());
        addServerFields(entry, addSub, newSlot);
        serversCategory.addEntry(addSub.build());

        return builder.build();
    }

    private static void addServerFields(ConfigEntryBuilder entry,
                                        SubCategoryBuilder sub,
                                        ClaimVizConfig.ServerConfig slot) {
        sub.add(entry
            .startStrField(Component.literal("Server Address"), slot.serverAddress)
            .setTooltip(Component.literal("Substring of the server IP matched on join (e.g. play.example.com). Clear to remove this entry."))
            .setSaveConsumer(val -> slot.serverAddress = val)
            .build()
        );
        sub.add(entry
            .startStrField(Component.literal("SquareMap URL"), slot.squaremapUrl)
            .setTooltip(Component.literal("Base URL of SquareMap web map, no trailing slash (e.g. https://map.example.com)"))
            .setSaveConsumer(val -> slot.squaremapUrl = val)
            .build()
        );
        sub.add(entry
            .startBooleanToggle(Component.literal("Enabled"), slot.enabled)
            .setTooltip(Component.literal("Uncheck to temporarily disable this server without removing it."))
            .setSaveConsumer(val -> slot.enabled = val)
            .build()
        );
        sub.add(entry
            .startIntField(Component.literal("Claim Refresh (seconds)"), slot.claimRefreshIntervalSeconds)
            .setDefaultValue(120)
            .setMin(10)
            .setMax(3600)
            .setTooltip(Component.literal("How often to re-fetch claim data from SquareMap (default: 120 s = 2 min)."))
            .setSaveConsumer(val -> slot.claimRefreshIntervalSeconds = val)
            .build()
        );
        sub.add(entry
            .startBooleanToggle(Component.literal("Show Claims"), slot.showClaims)
            .setTooltip(Component.literal("Render colored claim borders in the world."))
            .setSaveConsumer(val -> slot.showClaims = val)
            .build()
        );
        sub.add(entry
            .startBooleanToggle(Component.literal("Claim Owner Labels"), slot.showClaimOwnerLabels)
            .setDefaultValue(true)
            .setTooltip(Component.literal("Show the owner's name floating above the claim border lines."))
            .setSaveConsumer(val -> slot.showClaimOwnerLabels = val)
            .build()
        );
        sub.add(entry
            .startIntSlider(Component.literal("Label Spacing (blocks)"), slot.claimLabelSpacing, 4, 128)
            .setDefaultValue(12)
            .setTooltip(Component.literal("How many blocks apart owner labels repeat along each claim edge."))
            .setSaveConsumer(val -> slot.claimLabelSpacing = val)
            .build()
        );
        sub.add(entry
            .startBooleanToggle(Component.literal("Show Players"), slot.showPlayers)
            .setTooltip(Component.literal("Show other players' positions, name tags, and health markers."))
            .setSaveConsumer(val -> slot.showPlayers = val)
            .build()
        );
        sub.add(entry
            .startBooleanToggle(Component.literal("Show Player Heads"), slot.showPlayerHeads)
            .setDefaultValue(true)
            .setTooltip(
                Component.literal("Draw each player's skin face as an icon above their marker."),
                Component.literal("Turn off to keep just the health cross and name tag."))
            .setSaveConsumer(val -> slot.showPlayerHeads = val)
            .build()
        );
        sub.add(entry
            .startIntSlider(Component.literal("Player Render Distance (blocks)"), slot.playerRenderDistance, 50, 25000)
            .setDefaultValue(500)
            .setTooltip(Component.literal("Maximum distance at which other players' heads and markers are rendered."))
            .setSaveConsumer(val -> slot.playerRenderDistance = val)
            .build()
        );
        sub.add(entry
            .startBooleanToggle(Component.literal("Claim Enter/Leave Messages"), slot.showClaimMessages)
            .setTooltip(Component.literal("Show an action bar message when you enter or leave a claim."))
            .setSaveConsumer(val -> slot.showClaimMessages = val)
            .build()
        );
        sub.add(entry
            .startBooleanToggle(Component.literal("Persistent Claim Bar"), slot.persistentClaimBar)
            .setDefaultValue(false)
            .setTooltip(Component.literal("Continuously display which claim you are standing in on the action bar. Defaults to off."))
            .setSaveConsumer(val -> slot.persistentClaimBar = val)
            .build()
        );
        sub.add(entry
            .startBooleanToggle(Component.literal("Xaero Waypoints"), slot.xaeroWaypointsEnabled)
            .setTooltip(Component.literal("Add claim markers as waypoints in Xaero's Minimap / World Map (requires Xaero's installed)."))
            .setSaveConsumer(val -> slot.xaeroWaypointsEnabled = val)
            .build()
        );
        sub.add(entry
            .startIntSlider(Component.literal("Map Tile Refresh (seconds)"), slot.mapTileRefreshSeconds, 0, 300)
            .setDefaultValue(60)
            .setTooltip(Component.literal("How often the in-game map re-fetches tiles from SquareMap. Set to 0 to disable auto-refresh."))
            .setSaveConsumer(val -> slot.mapTileRefreshSeconds = val)
            .build()
        );
    }

}
