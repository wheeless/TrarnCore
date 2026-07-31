# Plans

Ideas being tracked for this repo. Brief on purpose — these are prompts for future work, not
specs. Anything that graduates into real design work gets its own file here (see
[update-checker.md](update-checker.md)).

Individual mods may also carry their own `plans/` folder for features specific to them —
[ClaimViz](../ClaimViz/plans) does.

---

## New mods

The through-line for everything here: **surface information the game hides, render it in the
world, stay client-side only.** No server component, works on vanilla servers.

All of these get cheaper now that [TrarnCore](../TrarnCore) exists — the render primitives, config
handling, chat feedback and ModMenu glue are already written.

### VillagerUtil
Remember every villager's trades and let you search them. Same architecture as
[ContainerUtil](../ContainerUtil) almost exactly: capture on trade-screen open, index, search UI,
track-and-navigate to the result. Search "mending" and get walked to the librarian who sells it,
with price and whether the trade is locked in. Ignore wandering traders.

*Biggest quality-of-life win of the list, and most of the hard parts (index, search grammar,
staleness, tracking) are solved patterns to copy rather than invent.*

### DespawnSphere
Draw the 128-block despawn sphere and the 24-block no-spawn radius around the player. The literal
sibling to [SimDistance](../SimDistance) — same "invisible mechanic made visible" idea. Together
they answer "where do I stand for this farm to actually run."

*Nearly free with `Shapes` already written.*

### SpawnLight
Mark every block where hostile mobs can spawn. Light data is client-side, so it is a scan-and-render
loop reusing ContainerUtil's chunk-sweep pattern. The classic base-proofing tool.

### RedstoneScope
Render signal strength on redstone dust and power state on components. All in the blockstate and
already synced to the client. Debugging redstone by counting blocks is miserable.

### CropWatch
Highlight crops by growth stage so a whole farm's readiness reads at a glance. Blockstate `age` is
client-side. Complements ContainerUtil's base-management angle — one says what is in storage, the
other what is ready to harvest.

### SoundScope
Render a fading marker where a sound originated. Client sound events carry positions. For "what was
that and where" — locating the cave a skeleton is in, or the mob ticking inside a wall.

### BeaconRange
Visualise a beacon's pyramid requirement and effect radius, and a conduit's range. Small, and
almost entirely `Shapes` calls.

---

## Cross-cutting

### [Update checker](update-checker.md)
Tell the player when a newer release of an installed mod exists, reading GitHub Releases. Interim
measure until these are on Modrinth, where launchers handle updates natively. **Has its own file.**

---

## Deliberately not doing

- **Auto-updating in place.** Downloading and swapping a loaded jar is the standard way these
  things break installs — see [update-checker.md](update-checker.md) for the reasoning.
- **Absorbing features into TrarnCore.** It stays plumbing. Domain logic belongs to the mod that
  owns it, or it stops being a library and becomes something all the mods are hostage to.
