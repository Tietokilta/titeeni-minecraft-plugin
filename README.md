# TiteeniPlugin

Paper plugin for the Titeenien Taisto Minecraft event. Manages minigames for up to 10 players.

## Requirements

- Paper 1.21.11+
- Java 21
- Maven

## Build

```sh
mvn package -q
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
| `/titeeni setplot <model\|build>` | Register a plot corner (Copy the Build only) |

## Minigames

### Jallupöllö

Hide-and-seek variant. One player is the **pöllö** (hider), the rest are seekers.

- The hider receives a **Jaloviina** bottle — hold right-click to drink and score 1 point per second
- Drinking broadcasts a positional sound to all seekers and updates their compass when stopped
- A seeker who hits the hider becomes the new pöllö; nearby seekers are briefly stunned

**Start:** `/titeeni start jallupollo` (minimum 2 players)

### Find the Button

Players explore the map and click hidden buttons to score points. First to find the most buttons in 5 minutes wins.

- Right-click a button to claim it — the button disappears and you score 1 point
- Buttons are restored when the game ends

**Start:** `/titeeni start findthebutton`
