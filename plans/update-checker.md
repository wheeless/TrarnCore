# Update checker

Tell the player when a newer release of an installed mod exists. Interim measure until these land
on Modrinth, where launchers handle updates natively.

## Recommendation: notify, don't self-update

Xaero's does the same thing — it tells you an update exists and links you to it. That is the right
call here, for a concrete reason rather than caution:

**A running Minecraft holds its mod jars open.** Replacing one in place means deleting a file the
JVM has locked, which fails outright on Windows. The workaround mods use is to download to a
staging directory and swap on next launch via a shutdown hook or helper process — and that is
exactly where self-updaters break installs, leaving a half-swapped `mods/` folder that will not
launch. Not worth owning that failure mode for six personal mods.

A middle option, if the notification alone gets annoying: offer to **download the new jar next to
the old one** and tell the player to delete the old one and restart. The download is safe; only the
swap is dangerous.

## Where it lives

**TrarnCore.** This is exactly shared plumbing — one implementation, and every mod opts in by
declaring its id and current version. Consistent with the library's remit.

## The monorepo makes this cheap

All six mods live in one repo, so **one API call covers all of them**:

```
GET https://api.github.com/repos/wheeless/TrarnCore/releases
```

Unauthenticated, that is 60 requests/hour per IP — a single call on startup is nowhere near it.

### Parse asset names, not tag names

Tempting to read versions out of tag names, but the tag scheme has two shapes
(`containerutil-v0.1.0` and `all-v0.1.0`), and a bundle tag encodes no per-mod version at all.

The **attached asset filenames do**: `containerutil-0.1.0.jar`, `claimviz-0.0.6.jar`. Parsing
`<archives_base_name>-<version>.jar` out of each release's assets works identically for both tag
shapes and needs no knowledge of the tagging convention. Take the highest version seen per mod id.

## Sketch

- `net.trarncore.update.UpdateChecker` — async fetch on client init, off the render thread.
- Each mod registers: `UpdateChecker.watch("containerutil", "0.1.0")`, reading its own version from
  `FabricLoader.getModContainer(id)` rather than a hardcoded constant.
- Compare semver-ish `x.y.z`; ignore anything unparseable rather than guessing.
- Report once, a few seconds after joining a world, via the mod's own `ChatChannel` so the message
  carries the right prefix and colour — and is local-only, like every other message these mods send.
- One config toggle, default on, plus a "never ask again for this version" so it is not repeated
  every launch.
- Fail silently on network error. An offline player does not need a stack trace.

## Swapping to Modrinth later

Keep the source behind a small interface — `ReleaseSource` with a GitHub implementation now, a
Modrinth one later. Modrinth's API is version-aware per project and per Minecraft version, which is
strictly better than parsing filenames, so this whole file becomes one implementation detail behind
that interface rather than something to rip out.

## Open questions

- Should it check per-Minecraft-version? GitHub releases carry no game-version metadata, so a 1.22
  release would notify a 1.21.11 player. Modrinth solves this properly; until then, either encode
  the game version in the release title, or accept the false positive.
- Bundle releases (`all-v*`) attach five jars at once — confirm the parser handles a release with
  multiple mods' assets, which is the normal case for this repo.
