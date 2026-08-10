# SimDistance

A client-side Fabric mod that draws a **tall, translucent red wall on the chunk borders at the edge
of your simulation-distance region**, so you can see at a glance which chunks around you are
actually being ticked.

Simulation distance is invisible in vanilla, and it is not the same as render distance — chunks you
can *see* are frequently not chunks that are *running*. That gap is why a mob farm stops producing
when you wander a little too far, why a crop field stops growing, and why a redstone contraption
quietly stalls. SimDistance draws the line so you can stay on the right side of it.

## What it draws

- A hollow box snapped to chunk borders, `radius` chunks out from the chunk you are standing in.
  It re-centers in 16-block steps as you cross chunk lines.
- `radius` defaults to the **live simulation distance reported by the server**
  (`ClientWorld.getSimulationDistance()`), which is synced in singleplayer too. You can override it
  with a fixed chunk radius.
- The wall spans the full world height by default, or a fixed band around you.
- Optionally, a per-chunk floor grid inside the region, at your feet level.

## Controls

- **G** — toggle the border on/off (rebindable in Options → Controls → *SimDistance*). The
  confirmation goes to your chat log, locally — see [below](#feedback-is-local-never-sent).
- **ModMenu → SimDistance → Settings** — everything in the table below. Requires Cloth Config.

## Settings

| Setting | Default | Description |
| --- | --- | --- |
| Enabled | on | Master on/off. Also bound to the hotkey. |
| Use Server Simulation Distance | on | Track the live value the server reports. Turn off to use a fixed radius. |
| Manual Chunk Radius | `8` | Used when the above is off, or as a fallback if the server reports nothing. |
| Wall Colour | red | Red/green/blue channel sliders. |
| Wall Opacity | `22%` | How solid the translucent wall is. `0` leaves only the edge lines. |
| Full World Height | on | Span bedrock to build limit. Off uses a fixed band around you. |
| Vertical Radius | `48` | Half-height of that band, when full height is off. |
| Draw Edge Lines | on | Crisp outline along the top/bottom edges and vertical corners. |
| Show Chunk Grid | off | Per-chunk grid on the floor within the region. |

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 25 | Yes | |
| [ModMenu](https://modrinth.com/mod/modmenu) | Recommended | Needed to reach the config screen |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Recommended | Needed for the config screen UI |

[TrarnCore](../TrarnCore) is bundled inside the jar — **do not install it separately**.

## Feedback is local, never sent

The toggle confirmation goes to your chat log via `ChatHud.addMessage`, which appends straight to
the client's own message list. No packet is involved, so nothing reaches the server or other
players — it is not the same thing as sending a chat message. (`ChatHud` contains no networking
code at all; sending chat goes through `ClientPlayNetworkHandler.sendChatMessage`, which this mod
never calls.)

Chat rather than the action bar on purpose: the action bar is a single slot that servers,
scoreboards and other mods all write to, so anything put there tends to be overwritten a tick
later — or to overwrite something you wanted to read. Chat is a log, so several sources coexist.

## Build

> On a **fresh clone**, build [TrarnCore](../TrarnCore) once first — Loom reads the library jar at
> configuration time, so it must exist before this mod can even be configured:
> `(cd ../TrarnCore && ./gradlew build)`. After that, no ordering is needed.

```bash
./gradlew build
```

The jar lands in `build/libs/`, and is also copied to [`../ModBuilds/`](../ModBuilds) alongside the
other mods' output for easy installing. Requires JDK 25.

Minecraft/Loader/Fabric versions come from [`../versions.properties`](../versions.properties),
shared with the sibling mods, so a version bump is one file for all of them.
[TrarnCore](../TrarnCore) is built from source automatically via a Gradle composite build — there is
no publish step.

## Notes

- Client-side only. Nothing is installed or changed server-side; the mod only draws what the server
  already tells your client.
- The border reflects **simulation** distance, not render distance. Where the two differ is exactly
  what this mod exists to show you.

## Related mods

[ClaimViz](../ClaimViz) · [EasyPortalLinker](../EasyPortalLinker) · [ContainerUtil](../ContainerUtil)
· [RSwitch](../RSwitch) — same Minecraft/Fabric target, shared [TrarnCore](../TrarnCore)
plumbing.

## Roadmap

- Optional red boxes around blocks/entities that fall outside the active simulation region — the
  things that behave oddly once you are far enough away.
