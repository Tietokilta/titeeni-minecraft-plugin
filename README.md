# TiteeniPlugin

Paper plugin for the Titeenien Taisto Minecraft event. Manages minigames for up to 10 players.

## Requirements

- Paper 1.21.11+
- Java 21

## Build

```sh
nix develop --command mvn package -q
```

Output: `target/TiteeniPlugin-1.0.0.jar`

## Commands

All commands require op (permission `titeeni.admin`).

| Command | Description |
|---|---|
| `/titeeni start <game>` | Start a minigame |
| `/titeeni stop` | Stop the current game |
| `/titeeni score` | Show current scores |
| `/titeeni sethider <player>` | Manually set the hider (Jallupöllö only) |

## Minigames

### Jallupöllö

Hide-and-seek variant. One player is the **pöllö** (hider), the rest are seekers.

- The hider receives a **Jaloviina** bottle — hold right-click to drink and score 1 point per second
- Drinking broadcasts a positional sound to all seekers and updates their compass when stopped
- A seeker who hits the hider becomes the new pöllö; nearby seekers are briefly stunned
