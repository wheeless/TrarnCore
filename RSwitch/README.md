# RSwitch

A tiny client-side Fabric mod: press **R** to swap the item in your hand with the inventory slot
**directly above it**. If that slot is empty the swap still happens, which makes it an instant way
to clear your hand without opening anything.

Client-side only, and it works on vanilla servers — see [below](#behaviour).

## Behaviour

Whichever hotbar slot you have selected, the slot swapped with is the one drawn immediately above
it in your inventory. Hotbar slot 3 pairs with the third slot of the bottom inventory row, and so
on across all nine.

The swap is a single vanilla `SWAP` slot action — exactly what the game does when you press a
number key while hovering a slot in your inventory. Consequences worth knowing:

- **Works on vanilla servers.** No server-side component, and nothing that looks unusual to a
  server; it is a normal inventory click.
- **Empty slots work by default**, because vanilla's own swap already handles them.
- **It is instant**, since the client applies the change locally and sends the packet, the same as
  any inventory interaction.

Nothing happens while a container screen is open, while spectating, or if both slots are empty.

## Controls

- **R** — swap (rebindable in Options → Controls → *RSwitch*). R is unbound in vanilla, so it is
  free on a default setup.
- **ModMenu → RSwitch → Settings** — enable/disable, which row to swap with, and optional sound
  and action-bar feedback. (Requires Cloth Config, like the sibling mods.)

## Settings

| Setting | Default | What it does |
| --- | --- | --- |
| Enabled | on | Master on/off; the hotkey does nothing when off. |
| Rows Above | 1 | Which inventory row to swap with. 1 is directly above the hotbar; 2 and 3 walk further up. |
| Play Sound | off | A soft click confirming the swap. |
| Show Chat Message | off | Names the item you swapped to, or "Hand cleared". Local only — see below. Fires every press, so it gets noisy. |

## Feedback is local, never sent

Messages go through `ChatHud.addMessage`, which appends straight to your own chat log. No packet
is involved, so nothing reaches the server or other players — it is not the same thing as sending
a chat message. (`ChatHud` contains no networking code at all; sending chat goes through
`ClientPlayNetworkHandler.sendChatMessage`, which neither mod calls.)

Chat rather than the action bar on purpose: the action bar is a single slot that servers,
scoreboards and other mods all write to, so anything put there tends to be overwritten a tick
later — or to overwrite something you wanted to read. Chat is a log, so several sources coexist.

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 21 | Yes | |
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
other mods' output for easy installing. Requires JDK 21.

Minecraft/Yarn/Loader/Fabric versions come from [`../versions.properties`](../versions.properties),
shared with the sibling mods, so a version bump is one file for all of them.
[TrarnCore](../TrarnCore) is built from source automatically via a Gradle composite build — there is
no publish step.

## Implementation note

The one non-obvious part is slot numbering. `PlayerScreenHandler` indices are **not**
`PlayerInventory` indices: the handler puts the crafting grid and armour first, so the main
inventory lands at 9–35 and the hotbar at 36–44. The row directly above the hotbar is therefore
handler slots 27–35, not 0–8 or 9–17.

`InventorySwapper` derives that from the vanilla `INVENTORY_END` and `HOTBAR_SIZE` constants
rather than hardcoding it, so a future re-layout of the player screen follows automatically
instead of silently swapping with the wrong row.

## Roadmap

- Optional swap with the offhand instead of an inventory row.
- Cycle through the column with repeated presses, rather than a fixed row.

## Related mods

[ClaimViz](../ClaimViz) · [SimDistance](../SimDistance) · [EasyPortalLinker](../EasyPortalLinker) ·
[ContainerUtil](../ContainerUtil) — same Minecraft/Yarn/Fabric target, shared
[TrarnCore](../TrarnCore) plumbing.
