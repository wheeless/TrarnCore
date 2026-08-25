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
        /**
         * Draw each player's skin face as a HUD icon above their marker.
         *
         * <p>Separate from {@link #showPlayers} because the two are independently useful: the
         * health cross and name tag are the parts that tell you something, and the face is
         * decoration that adds a screen-space icon per player.
         */
        public boolean showPlayerHeads = true;
        /**
         * Hide the head icon for players closer than this, in blocks.
         *
         * <p>Inside this range you can see the actual player, so the icon is covering them up
         * rather than helping. Measured in three dimensions, unlike the render-distance cull
         * below — somebody ten blocks away but two hundred down a ravine is not "probably
         * visible", and should keep their icon.
         */
        public int playerHeadHideWithin = 64;
        /** Icon size in pixels at {@link #playerHeadHideWithin} — the closest one ever drawn. */
        public int playerHeadMaxSize = 16;
        /** Icon size in pixels once distance has shrunk it as far as it goes. */
        public int playerHeadMinSize = 6;
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
