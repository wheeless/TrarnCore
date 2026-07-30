package net.claimviz.integration;

import net.claimviz.ClaimViz;
import net.claimviz.data.ClaimRect;
import net.claimviz.data.PlayerData;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Soft-dependency bridge to Xaero's Minimap.
 *
 * No Xaero imports — everything is accessed via reflection so this class loads
 * cleanly when Xaero is absent. Minecraft's own types (RegistryKey, etc.) are
 * used directly since they are always on the classpath.
 */
public class XaeroIntegration {

	private static final boolean XAERO_PRESENT = FabricLoader.getInstance().isModLoaded("xaerominimap");

	// ── Player radar reflection handles ──────────────────────────────────────
	private static Method getSessionMethod;
	private static Method getProcessorMethod;
	private static Method getTrackerManagerMethod;
	private static Method managerUpdate;
	private static Method managerRemove;
	private static Method managerReset;
	private static boolean reflectionFailed;

	// ── Waypoint reflection handles ──────────────────────────────────────────
	private static Method getMinimapSessionMethod; // MinimapProcessor.getSession() → MinimapSession
	private static Method getWorldManagerMethod;
	private static Method getCurrentWorldMethod;
	private static Method getCurrentWaypointSetMethod;
	private static Method waypointSetAddMethod;
	private static Method waypointSetRemoveMethod;
	private static Constructor<?> waypointConstructor;
	private static Class<?> waypointClass;

	// WaypointColor ordinals: 10=GREEN, 11=AQUA, 13=PURPLE, 14=YELLOW, 15=WHITE
	private static final int COLOR_OWN = 13; // PURPLE
	private static final int COLOR_ADMIN = 11; // AQUA
	private static final int COLOR_OTHER = 10; // GREEN

	private static final List<Object> trackedWaypoints = new ArrayList<>();

	static {
		if (XAERO_PRESENT) {
			ClaimViz.LOGGER.info("ClaimViz: Xaero's Minimap detected — player radar integration enabled");
		}
	}

	// ── Player radar ─────────────────────────────────────────────────────────

	/**
	 * Pushes all fetched players into Xaero's synced-player tracker so they
	 * appear as dots on the minimap. Called every second from PlayerFetcher.
	 */
	public static void syncPlayerPositions(List<PlayerData> players) {
		if (!XAERO_PRESENT)
			return;
		try {
			Object manager = getManager();
			if (manager == null)
				return;
			ensureMethods(manager);

			managerReset.invoke(manager);
			for (PlayerData pd : players) {
				UUID uuid = parseUuid(pd.uuid());
				if (uuid == null)
					continue;
				RegistryKey<World> dimKey = dimKey(pd.world());
				managerUpdate.invoke(manager, uuid, pd.x(), pd.y(), pd.z(), dimKey);
			}
		} catch (Exception e) {
			ClaimViz.LOGGER.warn("[ClaimViz] Xaero player sync failed", e);
		}
	}

	/** Called on disconnect — clears all player dots from the minimap. */
	public static void clearTrackedPlayers() {
		if (!XAERO_PRESENT)
			return;
		try {
			Object manager = getManager();
			if (manager == null)
				return;
			ensureMethods(manager);
			managerReset.invoke(manager);
		} catch (Exception e) {
			ClaimViz.LOGGER.warn("[ClaimViz] Xaero clear tracked players failed", e);
		}
	}

	// ── Claim waypoints ───────────────────────────────────────────────────────

	/** Syncs all claims as waypoints into the current Xaero waypoint set. */
	public static void syncClaimWaypoints(List<ClaimRect> claims) {
		if (!XAERO_PRESENT)
			return;
		ClaimViz.LOGGER.info("ClaimViz: syncClaimWaypoints called with {} claims", claims.size());
		// Dispatch to main thread — Xaero's WaypointSet is not thread-safe; the render
		// thread iterates it to draw the minimap concurrently with our background fetch.
		MinecraftClient.getInstance().execute(() -> {
			try {
				Object waypointSet = getCurrentWaypointSet();
				if (waypointSet == null) {
					ClaimViz.LOGGER.warn("ClaimViz: Xaero waypointSet is null — world not ready yet?");
					return;
				}
				ensureWaypointReflection(waypointSet);

				for (Object wp : trackedWaypoints) {
					waypointSetRemoveMethod.invoke(waypointSet, wp);
				}
				trackedWaypoints.clear();

				String selfName = selfName();

				for (ClaimRect claim : claims) {
					int cx = (int) Math.floor((claim.minX() + claim.maxX()) / 2.0);
					int cz = (int) Math.floor((claim.minZ() + claim.maxZ()) / 2.0);
					String name = waypointName(claim);
					String inits = waypointInitials(claim);
					int color = waypointColor(claim, selfName);
					Object wp = waypointConstructor.newInstance(cx, 64, cz, name, inits, color);
					waypointSetAddMethod.invoke(waypointSet, wp);
					trackedWaypoints.add(wp);
				}

				ClaimViz.LOGGER.info("ClaimViz: synced {} claim waypoints to Xaero", trackedWaypoints.size());
			} catch (Exception e) {
				ClaimViz.LOGGER.warn("ClaimViz: Xaero waypoint sync failed: {} — {}", e.getClass().getSimpleName(),
						e.getMessage());
				e.printStackTrace();
			}
		});
	}

	/**
	 * Removes all ClaimViz-added waypoints and clears player dots on disconnect.
	 */
	public static void clearWaypoints() {
		if (!XAERO_PRESENT)
			return;
		if (!trackedWaypoints.isEmpty()) {
			try {
				Object waypointSet = getCurrentWaypointSet();
				if (waypointSet != null && waypointSetRemoveMethod != null) {
					for (Object wp : trackedWaypoints) {
						waypointSetRemoveMethod.invoke(waypointSet, wp);
					}
				}
			} catch (Exception e) {
				ClaimViz.LOGGER.warn("[ClaimViz] Xaero waypoint clear failed", e);
			}
			trackedWaypoints.clear();
		}
		clearTrackedPlayers();
	}

	// ── Waypoint helpers ──────────────────────────────────────────────────────

	private static Object getCurrentWaypointSet() throws Exception {
		// Chain: XaeroMinimapSession → getMinimapProcessor() → getSession()
		// [MinimapSession]
		// → getWorldManager() → getCurrentWorld() → getCurrentWaypointSet()
		Object xaeroSession = getOrInitSession();
		if (xaeroSession == null)
			return null;

		if (getProcessorMethod == null)
			getProcessorMethod = xaeroSession.getClass().getMethod("getMinimapProcessor");
		Object processor = getProcessorMethod.invoke(xaeroSession);
		if (processor == null)
			return null;

		if (getMinimapSessionMethod == null)
			getMinimapSessionMethod = processor.getClass().getMethod("getSession");
		Object minimapSession = getMinimapSessionMethod.invoke(processor);
		if (minimapSession == null)
			return null;

		if (getWorldManagerMethod == null)
			getWorldManagerMethod = minimapSession.getClass().getMethod("getWorldManager");
		Object worldManager = getWorldManagerMethod.invoke(minimapSession);
		if (worldManager == null)
			return null;

		if (getCurrentWorldMethod == null)
			getCurrentWorldMethod = worldManager.getClass().getMethod("getCurrentWorld");
		Object minimapWorld = getCurrentWorldMethod.invoke(worldManager);
		if (minimapWorld == null)
			return null;

		if (getCurrentWaypointSetMethod == null)
			getCurrentWaypointSetMethod = minimapWorld.getClass().getMethod("getCurrentWaypointSet");
		return getCurrentWaypointSetMethod.invoke(minimapWorld);
	}

	private static void ensureWaypointReflection(Object waypointSet) throws Exception {
		if (waypointClass != null)
			return;
		waypointClass = Class.forName("xaero.common.minimap.waypoints.Waypoint");
		waypointConstructor = waypointClass.getConstructor(
				int.class, int.class, int.class, String.class, String.class, int.class);
		waypointSetAddMethod = waypointSet.getClass().getMethod("add", waypointClass);
		waypointSetRemoveMethod = waypointSet.getClass().getMethod("remove", waypointClass);
	}

	private static String waypointName(ClaimRect claim) {
		if ("Administrator".equals(claim.owner()))
			return "Admin Claim";
		return claim.owner() + "'s Claim";
	}

	private static String waypointInitials(ClaimRect claim) {
		if ("Administrator".equals(claim.owner()))
			return "AD";
		String o = claim.owner();
		return o.length() >= 2 ? o.substring(0, 2).toUpperCase() : o.toUpperCase();
	}

	private static int waypointColor(ClaimRect claim, String selfName) {
		if ("Administrator".equals(claim.owner()))
			return COLOR_ADMIN;
		if (selfName != null && selfName.equalsIgnoreCase(claim.owner()))
			return COLOR_OWN;
		return COLOR_OTHER;
	}

	private static String selfName() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null)
			return null;
		return client.player.getGameProfile().name();
	}

	// ── Reflection helpers ────────────────────────────────────────────────────

	/**
	 * Returns the Xaero MinimapSession, initializing the cached method if needed.
	 */
	private static Object getOrInitSession() throws Exception {
		if (getSessionMethod == null) {
			Class<?> sc = Class.forName("xaero.common.XaeroMinimapSession");
			getSessionMethod = sc.getMethod("getCurrentSession");
		}
		return getSessionMethod.invoke(null);
	}

	private static Object getManager() throws Exception {
		if (reflectionFailed)
			return null;
		try {
			Object session = getOrInitSession();
			if (session == null)
				return null;

			if (getProcessorMethod == null)
				getProcessorMethod = session.getClass().getMethod("getMinimapProcessor");
			Object processor = getProcessorMethod.invoke(session);
			if (processor == null)
				return null;

			if (getTrackerManagerMethod == null)
				getTrackerManagerMethod = processor.getClass().getMethod("getSyncedTrackedPlayerManager");
			return getTrackerManagerMethod.invoke(processor);
		} catch (Exception e) {
			ClaimViz.LOGGER.warn("ClaimViz: Xaero reflection setup failed, disabling player radar: {}", e.getMessage());
			reflectionFailed = true;
			return null;
		}
	}

	private static void ensureMethods(Object manager) throws NoSuchMethodException {
		if (managerReset == null)
			managerReset = manager.getClass().getMethod("reset");
		if (managerUpdate == null)
			managerUpdate = manager.getClass().getMethod(
					"update", UUID.class, double.class, double.class, double.class, RegistryKey.class);
		if (managerRemove == null)
			managerRemove = manager.getClass().getMethod("remove", UUID.class);
	}

	/**
	 * Converts SquareMap dim key "minecraft_overworld" → RegistryKey for
	 * minecraft:overworld.
	 */
	private static RegistryKey<World> dimKey(String squaremapDim) {
		int idx = squaremapDim.indexOf('_');
		String id = idx < 0 ? squaremapDim
				: squaremapDim.substring(0, idx) + ":" + squaremapDim.substring(idx + 1);
		return RegistryKey.of(RegistryKeys.WORLD, Identifier.of(id));
	}

	/** Parses a compact UUID string (no dashes) into a UUID. */
	private static UUID parseUuid(String compact) {
		try {
			if (compact.length() == 32) {
				return UUID.fromString(
						compact.substring(0, 8) + "-" +
								compact.substring(8, 12) + "-" +
								compact.substring(12, 16) + "-" +
								compact.substring(16, 20) + "-" +
								compact.substring(20));
			}
			return UUID.fromString(compact);
		} catch (Exception e) {
			return null;
		}
	}
}
