# TrustUI

A social-menu-style screen for managing **GriefPrevention** claim trust. Open it while standing in
your claim to see everyone online with their heads, who already has permissions, and grant or
revoke access with a click instead of typing commands.

Client-side only. Nothing is installed server-side — every action is the same command you could
type by hand.

## How it works

Opening the menu runs `/trustlist` and reads the reply out of chat, which is the only way a client
can learn a server plugin's state. The raw listing is hidden while it does so, and the buttons send
`/accesstrust`, `/containertrust`, `/trust`, `/permissiontrust` and `/untrust`.

That means:

- **No server component**, and nothing a server could tell apart from a human typing the commands.
- **It only works where GriefPrevention does.** No claim, no listing.
- **It is only as current as the last read.** The menu re-runs `/trustlist` after every change, so
  it shows what the server actually did rather than what was assumed — a command refused for
  permissions or a mistyped name shows up as the row simply not changing.

## The screen

```
┌────────────────────────────────────────┐
│ [head] Notch            [Container]  ▸ │
│ [head] Steve                         ▸ │
│ [head] Alex             [Build]      ▾ │
│    ( Access )( Container )( Build )    │
│    ( Permission )      ( Remove )      │
└────────────────────────────────────────┘
```

- Players who already hold trust sort to the top and are drawn in full brightness; everyone else is
  dimmed. A badge shows their highest tier, with `+n` if they hold more than one.
- Clicking a row expands it. Tiers they already hold are drawn in that tier's colour, so the row
  shows current state as well as offering actions.
- **Trusted players who are offline still appear**, marked as such — otherwise there would be no way
  to revoke someone's access while they are away.
- Search filters by name. **R** re-reads the claim, **Esc** closes.

## Controls

- **Open Claim Trust Menu** — unbound by default; bind it in Options → Controls → *TrustUI*. It
  opens a full screen, so any default would collide with something on most setups.
- **ModMenu → TrustUI → Settings** — behaviour and command names. Requires Cloth Config.

## Settings

| Setting | Default | Description |
| --- | --- | --- |
| Hide Trust List From Chat | on | Suppress the raw `/trustlist` output while the menu reads it. |
| Refresh After Changes | on | Re-read the claim after granting or revoking. |
| Show Offline Trusted Players | on | List trusted players who are not online, so their access can be removed. |
| Reply Timeout | `30` ticks | How long to wait for the server. Raise it on a laggy server. |

Under **Commands**, every command name is editable — `trustlist`, the four grant commands, and
`untrust`. Servers alias GriefPrevention's commands or run forks that name them differently, and a
hardcoded `/trust` would fail with no way to fix it short of a rebuild.

## Reading the trust list

GriefPrevention prints this:

```
Explicit permissions here:
>NotReallyTrarn                    <- Manage      (gold)
>TotallyNotTrarn NotReallyTrarn    <- Build       (yellow)
>                                  <- Containers  (green)
>                                  <- Access      (blue)
Manage Build Containers Access
```

**The tier lines carry no labels.** GriefPrevention distinguishes them only by colour, documented
by that legend on the last line — which is useless to parse against, since it prints the same four
words regardless of who holds what. What is reliable is the order: always exactly four `>` lines,
always Manage, Build, Containers, Access. So the tier comes from position.

Consequences:

- Empty tiers still print a bare `>`, so the four lines are always there and positions never shift.
- The legend has no `>` prefix, so filtering on that drops it for free.
- A player can hold several tiers and appear on several lines.
- Outside a claim there are no `>` lines at all, which reads as "no claim here" rather than "a claim
  with nobody trusted".

If your server has customised `messages.yml` enough to break this, the `>` prefix is the only thing
the parser depends on.

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 25 | Yes | |
| A server running [GriefPrevention](https://www.spigotmc.org/resources/griefprevention.1884/) | Yes | Nothing to read otherwise |
| [ModMenu](https://modrinth.com/mod/modmenu) | Recommended | Needed to reach the config screen |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Recommended | Needed for the config screen UI |

[TrarnCore](../TrarnCore) is bundled inside the jar — **do not install it separately**.

## Build

> On a **fresh clone**, build [TrarnCore](../TrarnCore) once first — Loom reads the library jar at
> configuration time, so it must exist before this mod can even be configured:
> `(cd ../TrarnCore && ./gradlew build)`. After that, no ordering is needed.

```bash
./gradlew build
```

The jar lands in `build/libs/`, and is also copied to [`../ModBuilds/`](../ModBuilds) alongside the
other mods' output. Requires JDK 25.

## Roadmap

- Read the claim owner and show it in the header (ClaimViz already knows it from SquareMap).
- Bulk actions — grant a tier to several players at once.
- Subdivision support, which GriefPrevention reports separately.

## Related mods

[ClaimViz](../ClaimViz) · [SimDistance](../SimDistance) · [EasyPortalLinker](../EasyPortalLinker) · [ContainerUtil](../ContainerUtil) · [RSwitch](../RSwitch) · [AutoRelog](../AutoRelog) — same Minecraft/Fabric target,
shared [TrarnCore](../TrarnCore) plumbing.
