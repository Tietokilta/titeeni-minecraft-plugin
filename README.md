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
| `/titeeni setplot <model\|build>` | Set a model or building plot (Copy the Build only) |


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

### Copy the Build

Players race to replicate a pre-built 5×5×5 model structure in their own designated build area. Complete as many models as possible in 5 minutes.

- Each player receives exactly the blocks needed to match the current model
- Breaking a block in your area returns it to your inventory instead of dropping it
- When your build matches the model you score 1 point, your area clears, and you receive blocks for the next model
- Models cycle — if there are multiple registered models each completion advances to the next
- You cannot place or break blocks in the model area

**Setup (one-time, per server world):**

1. Build the first 5×5×5 model structure. Stand at its **north-west-bottom corner** and run:
   ```
   /titeeni setplot model
   ```
   Repeat for each additional model (players cycle through them in order).

2. Mark each player's build area. Stand at the **north-west-bottom corner** of an empty 5×5×5 space per player and run:
   ```
   /titeeni setplot build
   ```
   You need at least as many build plots as players joining the game.

   The game automatically places a **preview plot** 10 blocks behind each build area (along the +Z axis: 5 blocks for the plot itself + 5 block gap). The preview shows a live copy of the model the player is currently trying to replicate, updating whenever they complete a build. Make sure that 5×5×5 space is clear of anything you want to keep.

   Plot locations are saved in `plugins/TiteeniPlugin/config.yml` and persist across restarts.

**Start:** `/titeeni start copythebuild` (alias: `ctb`)
