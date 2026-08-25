# BlowByBlow

A scrolling combat log. What hit you, for how much, with what — and the same in reverse for
everything you hit.

```
Zombie hit you for 1.5♥ with an Iron Sword
You hit Zombie for ~3.5♥ with a Netherite Axe
You took 1♥ from the fall
Skeleton killed you
```

Client-side only. Nothing is installed server-side, and nothing is sent anywhere — it reads what
the server already tells your client.

## Where it draws

Both, either, or neither:

- **A HUD panel** — a scrolling feed that fades out when the fight stops. On by default.
- **Local chat** — the same lines in your chat log, so you can scroll back after the fact. Off by
  default: a fight is a line per hit, and chat keeps history, so leaving it on buries anything the
  server actually said to you.

The panel is placed by **dragging it**, not by typing coordinates. Bind *Move Combat Feed* in
Options → Controls, or use the button in the settings screen. Panels snap to screen edges and
centres, Alt disables snapping, and arrow keys nudge a pixel at a time.

Dropping a panel re-pins it to whichever anchor it landed nearest, so the placement holds when you
resize the window or change GUI scale. A position stored as raw pixels does not.

## Floating numbers

Damage pops off whatever was hit and drifts upward, easing out so it leaps from the hit rather than
sliding. Only your own hits and what lands on you — bystander numbers popping off every mob in a
farm is the fastest way to make this unusable.

## What it can and cannot know

**Attribution is not guesswork.** The server sends your client a damage event for every entity you
can see, and Minecraft records it on the entity, so *who* hit *what* and with which damage type
comes straight from the game.

**The number is not in that packet.** Amounts come from watching health change, and that splits
into two cases:

| | |
| --- | --- |
| **Damage to you** | **Exact.** Your health is sent to you precisely. |
| **Damage you deal** | **A floor.** Read from the target's synced health. |

The second case is blind to overkill: a mob on 2 hearts hit for 10 reports 2, because 2 is all the
health there was to lose. Those amounts are prefixed with `~` so a floor is never mistaken for a
measurement.

**This is why it is a log and not a damage meter.** Every line is an observation, and an
observation can be honest about its own limits. A DPS total would accumulate that error across
hundreds of hits and then state the result as a fact.

## Settings

**ModMenu → BlowByBlow → Settings.** Five tabs: General, Appearance, What To Log, Floating Numbers,
and an Accuracy tab explaining the above. Requires Cloth Config.

| Setting | Default | Notes |
| --- | --- | --- |
| Max lines | 8 | Held in the panel at once |
| Hold / fade | 8s / 1s | Fully visible, then fading |
| Newest at bottom | on | Match this to where you put the panel |
| Track radius | 32 blocks | How far out other entities are watched |
| Show hearts | on | `3♥` rather than the 6 half-heart points the game counts in |
| Name weapons | on | |
| Show healing | off | Natural regeneration makes this chatty when you are simply well fed |
| Show nearby fights | off | Near a mob farm this is thousands of lines you did not ask for |

## Controls

Both unbound by default — a combat log is not a thing you toggle mid-fight, and every free key near
the movement keys is taken on a normal setup.

- **Toggle Combat Feed**
- **Move Combat Feed** — opens the drag-to-place screen

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 25 | Yes | |
| [ModMenu](https://modrinth.com/mod/modmenu) | Recommended | Needed to reach the config screen |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Recommended | Needed for the config screen UI |

[TrarnCore](../TrarnCore) is bundled inside the jar — **do not install it separately**.

## How it works

No mixins. A client tick handler polls health and reads `getLastDamageSource()` off the entity,
which is where Minecraft stores the damage event the server sent. That does the same job as hooking
the packet and survives Minecraft moving the packet around.

The scrolling feed itself, the anchor maths and the drag-to-place screen live in
[TrarnCore](../TrarnCore) under `hud/` — none of it is combat-specific, and the next mod that wants
a HUD panel gets it for free. What stays here is the damage interpretation and the sentences.

## Build

> On a **fresh clone**, build [TrarnCore](../TrarnCore) once first — Loom reads the library jar at
> configuration time, so it must exist before this mod can even be configured:
> `(cd ../TrarnCore && ./gradlew build)`. After that, no ordering is needed.

```bash
./gradlew build
```

The jar lands in `build/libs/`, and is also copied to [`../ModBuilds/`](../ModBuilds). Requires JDK 25.

## Notes / limitations

- Absorption hearts are not health, so damage soaked by them is invisible to the amount reading.
- An entity that leaves tracking range mid-fight stops being watched; its next observed health
  change is treated as a fresh start rather than a huge hit.
- Two hits landing between health syncs can coalesce into one line with the combined amount.

## Related mods

[ClaimViz](../ClaimViz) · [SimDistance](../SimDistance) · [EasyPortalLinker](../EasyPortalLinker) ·
[ContainerUtil](../ContainerUtil) · [RSwitch](../RSwitch) · [TrustUI](../TrustUI) ·
[AutoRelog](../AutoRelog) — same Minecraft/Fabric target, shared [TrarnCore](../TrarnCore) plumbing.
