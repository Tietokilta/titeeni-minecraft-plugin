# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

Requires JDK 21 and Maven. Use the Nix dev shell if they aren't available on PATH:

```sh
nix develop          # enter the shell once
mvn package -q       # build → target/TiteeniPlugin-1.0.0.jar
```

There are no tests. The only validation is a successful compile.

## Architecture

Paper plugin (API `1.21.11-R0.1-SNAPSHOT`) for a 10-player event. One command, one manager, pluggable minigames.

```
TiteeniPlugin           — onEnable wires MinigameManager + TiteeniCommand
TiteeniCommand          — /titeeni start|stop|score|sethider, TabCompleter
MinigameManager         — owns currentGame, forwards PlayerQuitEvent, exposes onGameSelfTerminated()
Minigame (abstract)     — lifecycle contract: start/stop/onPlayerQuit/getDisplayName/getScores
minigame/jallupollo/    — Jallupöllö (hide-and-seek variant), the only current game
```

**MinigameManager** registers itself as a Bukkit listener and routes `PlayerQuitEvent` to the active game. Game-specific listeners are registered/unregistered inside the game's own `start()`/`stop()`.

**Minigame base class** holds `plugin`, `players` (mutable defensive copy), `state`, and provides helpers: `getOnlinePlayers()`, `broadcast()`, `broadcastActionBar()`, `broadcastScores()`.

### Jallupöllö internals

- One player is the hider (pöllö), the rest are seekers.
- Hider receives a **Jaloviina** (custom POTION, PDC-tagged with `jallupollo_potion`). Holding right-click "drinks" it — `PlayerItemConsumeEvent` is cancelled so it never actually completes.
- A 5-tick repeating task calls `tick()`. `isHandRaised()` on the hider detects active drinking. Every 20 ticks (1 s) of drinking: award `POINTS_PER_SECOND`, play a positional sound to each seeker.
- **Sound**: sent individually per seeker via `player.playSound()` at the hider's location so 3-D direction is preserved. Source volume is back-calculated from Minecraft's attenuation formula so seekers hear full volume ≤ 32 blocks, linear fade to 20 % at 200 blocks, never silent beyond that.
- On stop-drinking: snapshot hider's location → update seeker compasses.
- Seeker hits hider → `onHiderHit()` → nearby seekers get Blindness+Slowness IV for 10 s → role swap via `chooseNewHider()`.
- All participant damage (PvP and environmental) is cancelled except the designed mechanic above.
- Items: Jaloviina can't be dropped or moved into external containers; compass is PDC-tagged with `jallupollo_compass`.

### Adding a new minigame

1. Create a package under `minigame/`, extend `Minigame`, implement the five abstract methods.
2. Add a `case` in `TiteeniCommand.handleStart()` that constructs the game and calls `manager.startGame(game)`.
3. Add the name string to `TiteeniCommand.GAME_TYPES` for tab completion.

## Command reference (in-game)

```
/titeeni start <game>       — start a minigame (currently: jallupollo)
/titeeni stop               — stop current game
/titeeni score              — show live scores
/titeeni sethider <player>  — manually reassign the hider (Jallupöllö only)
```

Permission: `titeeni.admin` (default: op).
