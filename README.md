# TiteeniPlugin

Paper plugin for the Titeenien Taisto Minecraft event. Manages a 9-stage overarching event where 10 players, each representing a Finnish CS guild, compete for points across multiple minigames.

## Requirements

- Paper 1.21.11+
- Java 21
- Maven

## Build

```sh
mvn package -q
```

Output: `target/TiteeniPlugin-1.0.0.jar`

## Event overview

The event follows nine Finnish university cities in order. Players travel between stages on a shared bus. An admin manually advances the event; minigame stages start automatically on advancement.

| # | City | Activity | Scoring |
|---|---|---|---|
| 1 | Ainoa (Tapiola) | Maze escape | Timed race |
| 2 | Turku | Ice boating race | Timed race |
| 3 | Tampere | Parkour | Timed race |
| 4 | Vaasa | Deliver packages | — (no plugin) |
| 5 | Oulu | Kill a wither | — (no plugin) |
| 6 | Jyväskylä | Find the Button | Buttons found |
| 7 | Lappeenranta | Copy the Build | Builds completed |
| 8 | Lahti | King of the Hill | Points scored on hill |
| 9 | Otaniemi | Jallupöllö | Points scored as hider |

### Event scoring

After each stage, guilds are ranked by their raw game score and awarded event points:

| Place | Points |
|---|---|
| 1st | 12 |
| 2nd | 10 |
| 3rd | 8 |
| 4th | 7 |
| 5th | 6 |
| 6th | 5 |
| 7th | 4 |
| 8th | 3 |
| 9th | 2 |
| 10th | 1 |

For **timed races** (Ainoa, Tampere, Turku), the fastest finisher ranks 1st. Boarding the bus stops a player's individual timer. Players who do not finish receive last place.

The running total is always visible in the **tab list** (player list scoreboard) and via `/titeeni totalscore`.

## Guilds

Each of the 10 guild representatives is assigned via `/titeeni assign`. Only assigned players participate in minigames.

| Constant | Display name |
|---|---|
| `TIK` | TiK |
| `TITE` | TiTe |
| `DG` | DG |
| `DIGIT` | Digit |
| `DATE` | DaTe |
| `CLUSTER` | Cluster |
| `SOSA` | SoSa |
| `TUTTI` | Tutti |
| `ALGO` | Algo |
| `OTIT` | OTiT |

## Pre-event setup

All setup commands require op (`titeeni.admin`). Location commands use your current standing position. Everything is saved to `plugins/TiteeniPlugin/config.yml` and persists across restarts.

### 1. Assign guild representatives

```
/titeeni assign TIK   PlayerName
/titeeni assign TITE  PlayerName
...repeat for all 10 guilds...
```

### 2. Set the bus location

Stand at the spot where players wait between stages and run:

```
/titeeni setbus
```

### 3. Set spawn locations per stage and guild

For each stage and each guild, stand at that guild's starting position for that stage and run:

```
/titeeni setlocation <STAGE> <GUILD>
```

Stage names (case-insensitive): `AINOA`, `LAHTI`, `LAPPEENRANTA`, `JYVASKYLA`, `OULU`, `VAASA`, `TAMPERE`, `TURKU`, `OTANIEMI`

Example:
```
/titeeni setlocation LAHTI TIK
/titeeni setlocation LAHTI TITE
...
```

Timed race stages (Ainoa, Tampere, Turku) start an automatic timer when the stage begins. Oulu, Vaasa are no-plugin stages that still use the configured spawn to teleport players from the bus.

### 4. Configure King of the Hill (Lahti)

```
/titeeni setkoth <y>
```

Sets the Y level that counts as "on the hill" for scoring.

### 5. Configure Copy the Build (Lappeenranta)

Register model structures — stand at the north-west-bottom corner of each pre-built 5×5×5 model and run:

```
/titeeni setplot model
```

Register each guild's build area — stand at the north-west-bottom corner of their empty 5×5×5 plot and run:

```
/titeeni setplot build <GUILD>
```

The game automatically places a **preview plot** 10 blocks behind each build area (+Z: 5 blocks for the plot + 5 block gap). Make sure that space is clear.

### 6. Set up the bus pressure plate

Place a pressure plate at the bus entrance connected to a command block that runs:

```
/titeeni board <player>
```

`<player>` should resolve to the player who stepped on the plate (e.g. using a command block with `@p`).

## Running the event

```
/titeeni advance         ← teleports all bus players to AINOA, no minigame starts
```

Players complete the current activity, then walk onto the pressure plate to board the bus.

```
/titeeni advance         ← teleports to LAHTI, King of the Hill starts automatically
```

The minigame runs until it ends naturally (timer expires) or you stop it manually with `/titeeni stop`. Players then board the bus, and you run `/titeeni advance` again for the next stage.

```
/titeeni totalscore      ← show accumulated points including the current game
```

## Commands

All commands require op (permission `titeeni.admin`).

### Event management

| Command | Description |
|---|---|
| `/titeeni assign <guild> <player>` | Assign an online player to a guild |
| `/titeeni setlocation <stage> <guild>` | Save your current position as the guild's spawn for a stage |
| `/titeeni setbus` | Save your current position as the bus location |
| `/titeeni setkoth <y>` | Set the King of the Hill Y level (Lahti) |
| `/titeeni advance` | Advance to the next stage — teleports bus players and starts the minigame |
| `/titeeni board <player>` | Mark a player as on the bus (intended for command block use) |
| `/titeeni totalscore` | Show accumulated event scores across all completed stages |

### Minigame control

| Command | Description |
|---|---|
| `/titeeni start <game>` | Manually start a minigame with guild-assigned players |
| `/titeeni stop` | Stop the current game |
| `/titeeni score` | Show scores for the current game |
| `/titeeni sethider <player>` | Manually set the hider (Jallupöllö only) |
| `/titeeni setplot model` | Register a model plot corner (Copy the Build) |
| `/titeeni setplot build <guild>` | Register a build plot corner for a guild (Copy the Build) |


## Minigames

### Timed Race

Used for Ainoa (maze), Tampere (parkour), and Turku (ice boating). The plugin starts a shared timer when the stage begins. Each player's individual clock stops the moment they board the bus at the finish.

- Your elapsed time is shown in the action bar in real time
- Once you finish, the action bar shows your final time in green
- Players who do not finish (disconnect or quit) receive last place
- The game ends automatically when all participants have finished; otherwise stop it with `/titeeni stop`

These stages start automatically via `/titeeni advance` — no separate `/titeeni start` needed.

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

2. For each player, stand at the **north-west-bottom corner** of their empty 5×5×5 build area and run:
   ```
   /titeeni setplot build <player>
   ```
   Only players with a registered build plot join the game — running this command is how you control who participates. Re-running it for the same player moves their plot rather than creating a duplicate.

   The game automatically places a **preview plot** 10 blocks behind each build area (along the +Z axis: 5 blocks for the plot itself + 5 block gap). The preview shows a live copy of the model the player is currently trying to replicate, updating whenever they complete a build. Make sure that 5×5×5 space is clear of anything you want to keep.

   Plot locations are saved in `plugins/TiteeniPlugin/config.yml` and persist across restarts.

**Start:** `/titeeni start copythebuild` (alias: `ctb`)
