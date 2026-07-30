package net.claimviz.config;

import java.util.ArrayList;
import java.util.List;

public class ClaimVizConfig {

    /** How far (in blocks) from the player to render claim borders. Configurable via ModMenu. */
    public int claimRenderDistance = 200;

    /** Max SquareMap tiles held in GPU memory. Lower if experiencing memory pressure. */
    public int mapTileBudget = 128;

    public List<ServerConfig> servers = new ArrayList<>();

    public static class ServerConfig {
        /** Matched against the server address string on join (substring match, case-insensitive). */
        public String serverAddress = "";
        /** Base URL of the SquareMap instance, no trailing slash. e.g. https://map.example.net */
        public String squaremapUrl = "";
        public boolean enabled = true;
        public int claimRefreshIntervalSeconds = 120;
        public boolean showClaims = true;
        /** Show the claim owner's name as floating text above the border lines. */
        public boolean showClaimOwnerLabels = true;
        /** Distance in blocks between repeated owner labels along a claim edge. */
        public int claimLabelSpacing = 12;
        public boolean showPlayers = false;
        /** Max distance in blocks at which other players are rendered. */
        public int playerRenderDistance = 500;
        /** Show action bar messages when entering/leaving a claim. */
        public boolean showClaimMessages = true;
        /** Continuously show which claim you are standing in on the action bar. */
        public boolean persistentClaimBar = false;
        /** Add claims as waypoints in Xaero's Minimap (if installed). */
        public boolean xaeroWaypointsEnabled = false;
        /** How often (in seconds) to re-fetch map tiles. 0 disables refresh. */
        public int mapTileRefreshSeconds = 60;
    }
}
