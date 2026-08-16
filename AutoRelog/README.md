# AutoRelog

Reconnects you to a server after a drop — but only when reconnecting is the right thing to do.

Most auto-reconnect mods treat every disconnect the same and hammer the server until they get
back in. AutoRelog reads *why* the session ended and decides from that. A timeout is the network
failing and it retries. A restart is worth waiting out. **A kick is somebody deciding you should
leave, so by default it does nothing at all.**

Client-side only. Nothing is installed server-side, and reconnecting is the same action as
clicking the server in your list.

## What it does out of the box

| What happened | Default |
| --- | --- |
| Connection lost, timed out, end of stream | **Reconnect** after 5s |
| Connection attempt failed (server still booting) | **Reconnect** after 5s |
| "Server closed" — a restart, or a full server | **Reconnect** after 60s |
| Kicked by an operator or a plugin | **Stay disconnected** |
| Banned, IP banned, not whitelisted | **Stay disconnected** |
| Wrong version, expired session, auth servers down | **Stay disconnected** |

Every one of those is a toggle, and every one can be overridden per-message by a rule.

### Why kicks are off by default

A kick is a person or a plugin deciding you should not be here right now. Walking straight back
in is the behaviour that gets auto-reconnect mods banned outright on servers, and it is rude even
when it works. If a particular server's "kick" is really a restart notice, write a rule for that
message rather than opening up every kick.

Bans have their own toggle, and leaving it off is the right call: retrying cannot succeed, and
knocking repeatedly on a server that banned you is how an account ban becomes an IP ban.

## Rules

Rules are checked in order against the disconnect message. **The first match decides**, and rules
outrank every toggle above — they are the escape hatch for a server whose wording does not fit the
general classification.

```
action | mode | pattern | delay | attempts
```

Everything after `pattern` is optional; `-` means "inherit the global setting".

### Actions

| | |
| --- | --- |
| `relog` | Reconnect, whatever the toggles say |
| `never` | Do not reconnect, whatever the toggles say |
| `ignore` | Stop checking rules and let the defaults decide |

### Modes

| | |
| --- | --- |
| `contains` | Pattern appears anywhere in the message |
| `equals` | The whole message, ignoring outer whitespace |
| `starts` / `ends` | Message begins or ends with the pattern |
| `regex` | Java regular expression, matched anywhere |
| `key` | A vanilla translation key instead of the text |

Add `!` for case-sensitive matching: `contains!`. Prefix a whole line with `#` to keep it without
it taking effect.

### Examples

```
relog | contains | restarting | 120
```
Plugin says it is restarting. Wait two minutes, then come back.

```
relog | key | multiplayer.disconnect.server_shutdown | 90 | 20
```
Match the vanilla key rather than its text, so it keeps working in any language and survives the
server rewording anything. Ninety seconds, up to twenty tries.

```
never | contains | idle
```
An AFK kick means you walked away. Coming back for you defeats the point.

```
never | regex | (?i)maintenance|whitelist
```
Two words that both mean "not now".

```
ignore | contains | scheduled restart
never  | contains | restart
```
Carve one message out of a broader rule below it without reordering the list — the first line
sends "scheduled restart" to the defaults, and everything else matching "restart" is blocked.

### `key` is the sturdy one

`contains` matches what you see on screen, which depends on your language and on the server not
rewording its messages. `key` matches Minecraft's internal identifier, which does neither.

Turn on **Verbose Logging** and every disconnect logs its kind, its exact text and its translation
keys — the keys it prints are precisely what a `key` rule matches.

## Timing

| Setting | Default | Description |
| --- | --- | --- |
| Delay | `5s` | Before the first attempt |
| Server Closed Delay | `60s` | A restart takes longer than a network blip |
| Backoff Multiplier | `1.8` | Each failure multiplies the wait. `1.0` disables it |
| Max Delay | `300s` | Ceiling, so an overnight loop does not drift into hours |
| Jitter | `20%` | Random extra wait |
| Max Attempts | `10` | `0` means never give up |
| Session Reset | `60s` | A session this long resets the attempt counter |

The first wait is always exactly the configured delay — backoff only grows on repeats.

**Jitter is not decoration.** When a server restarts, every client running this mod counts down
from the same instant. Without jitter they all reconnect on the same tick, against a server that
has just finished booting. Twenty percent spread is enough to smear that out.

**Session Reset** exists because without it, ten brief drops over an evening exhaust the attempt
budget, and the next drop hours later — after a perfectly healthy session — is refused for no
reason a player could guess at.

## On the disconnect screen

A countdown button showing the attempt number, and a Cancel button. Any key press cancels too, on
the theory that a player who came back to the keyboard mid-countdown will hit *something*, and
whatever they hit should stop it.

Cancelling also resets the attempt counter — cancelling is a decision, not a failure.

## Controls

No keybind. Everything happens on the disconnect screen.

**ModMenu → AutoRelog → Settings** — five tabs: General, When, Timing, Rules, and a Syntax
reference with worked examples. Requires Cloth Config.

Rules are stored as objects in `config/autorelog/config.json`, where they are readable and
commentable, and edited in ModMenu as one line each. The JSON is the source of truth; a line that
will not parse comes back disabled with the original text kept rather than being silently dropped.

## Requirements

| Dependency | Required | Notes |
| --- | --- | --- |
| Fabric Loader ≥ 0.15 | Yes | |
| Fabric API | Yes | |
| Java 25 | Yes | |
| [ModMenu](https://modrinth.com/mod/modmenu) | Recommended | Needed to reach the config screen |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | Recommended | Needed for the config screen UI |

[TrarnCore](../TrarnCore) is bundled inside the jar — **do not install it separately**.

## How it reads the reason

`DisconnectedScreen` keeps the reason in a private field with no getter, so the mod declares a
one-line access widener for it. That is the whole of its contact with Minecraft's internals — no
mixins, in keeping with the rest of this repo. If a future Minecraft renames the field the widener
fails at load with a clear message, rather than the mod silently deciding every disconnect is
"unknown".

Classification comes from the reason's translation keys: transport failures live under
`disconnect.*` and server decisions under `multiplayer.disconnect.*`, and both sets are stable.
A reason carrying no vanilla key at all is text the server wrote itself — which is exactly the
case the defaults are conservative about, because its content is unknowable in advance.

Where a reason mixes keys from several groups, the most restrictive wins. Being wrong towards "do
not reconnect" costs a click; being wrong the other way hammers a server that already said no.

## Build

> On a **fresh clone**, build [TrarnCore](../TrarnCore) once first — Loom reads the library jar at
> configuration time, so it must exist before this mod can even be configured:
> `(cd ../TrarnCore && ./gradlew build)`. After that, no ordering is needed.

```bash
./gradlew build
```

The jar lands in `build/libs/`, and is also copied to [`../ModBuilds/`](../ModBuilds) alongside the
other mods' output. Requires JDK 25.

## Notes / limitations

- Singleplayer and LAN worlds are ignored entirely.
- The server to return to is captured on join, because Minecraft clears it while tearing the
  session down — by the time a disconnect screen exists there is nothing left to ask.
- Reconnecting uses vanilla's own join path, so a server sees exactly what it would see if you had
  clicked the entry in your server list.

## Related mods

[ClaimViz](../ClaimViz) · [SimDistance](../SimDistance) · [EasyPortalLinker](../EasyPortalLinker) ·
[ContainerUtil](../ContainerUtil) · [RSwitch](../RSwitch) · [TrustUI](../TrustUI) — same
Minecraft/Fabric target, shared [TrarnCore](../TrarnCore) plumbing.
