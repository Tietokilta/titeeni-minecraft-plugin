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

### King of the Hill

Players compete to occupy the highest point. PvP is enabled with bare-fist knockback — knock opponents off the hill, then hold the peak to score.

- All players get Knockback I on their fists — no item needed
- Players at or above the specified Y level score 1 point per second
- Fall damage is cancelled so getting knocked off is repositioning, not punishment
- Action bar shows a live top-3 leaderboard and your distance to the hill
- Game ends after 5 minutes

**Start:** `/titeeni start kingofthehill <y>` (minimum 2 players, e.g. `koth 120`)
