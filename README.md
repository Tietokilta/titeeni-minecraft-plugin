# TiteeniPlugin

Paper plugin for the Titeenien Taisto Minecraft event. Manages an 8-stage overarching event where 10 players, each representing a Finnish CS guild, compete for points across multiple minigames.

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

The event follows eight Finnish university cities in order. Players travel between stages on a shared bus. An admin manually advances the event; minigame stages start automatically on advancement.

| # | City | Activity | Scoring |
|---|---|---|---|
| 1 | Turku | Ice boating race | Timed race |
| 2 | Tampere | Parkour | Timed race |
| 3 | Vaasa | Trade Up item chain | Items traded |
| 4 | Oulu | Shoot targets & defeat Toripoliisi | Targets hit |
| 5 | Jyväskylä | Find the Button | Buttons found |
| 6 | Lappeenranta | Copy the Build | Builds completed |
| 7 | Lahti | King of the Hill | Points scored on hill |
| 8 | Otaniemi | Jallupöllö | Points scored as hider |

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

Tied players share the average of the points for the ranks they span (rounded up).

Stage results and the running total are sent to the designated admin player (set with `/titeeni setadmin`) and to the server console. Use `/titeeni totalscore` to show accumulated points at any time.

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
| `SOSA` | SoSA |
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

### 2. Set the designated admin

The admin player receives per-stage scoring results and the final leaderboard in chat.

```
/titeeni setadmin PlayerName
```

### 3. Set the bus location

Stand at the spot where players wait between stages and run:

```
/titeeni setbus
```

### 4. Set spawn locations per stage and guild

For each stage and each guild, stand at that guild's starting position for that stage and run:

```
/titeeni setlocation <STAGE> <GUILD>
```

If `<GUILD>` is omitted, your current position is set for all guilds at once.

Stage names (case-insensitive): `TURKU`, `TAMPERE`, `VAASA`, `OULU`, `JYVASKYLA`, `LAPPEENRANTA`, `LAHTI`, `OTANIEMI`

Example:
```
/titeeni setlocation LAHTI TIK
/titeeni setlocation LAHTI TITE
...
```

Use `/titeeni goto <stage> <guild>` to teleport to a saved spawn and verify placement.

### 5. Set the Turku boat gate

Stand at the finish-line gate (the Z coordinate where players may leave their boat) and run:

```
/titeeni setturkugate
```

Players cannot board the bus from a boat that is still behind this Z coordinate.

### 6. Configure King of the Hill (Lahti)

```
/titeeni setkoth <y>
```

Sets the Y level that counts as "on the hill" for scoring.

### 7. Configure Oulu shooting gallery

For each guild, stand at the location where zombies should spawn for that guild's player and run:

```
/titeeni setzombiespawn <GUILD>
```

Repeat for all guilds. Zombie spawns are saved to config.

### 8. Configure Copy the Build (Lappeenranta)

Register model structures — stand at the north-west-bottom corner of each pre-built 5×5×15 model and run:

```
/titeeni setplot model
```

Register each guild's build area — stand at the north-west-bottom corner of their empty 5×5×15 plot and run:

```
/titeeni setplot build <GUILD>
```

The game automatically places a **preview plot** next to each build area (+X: 5 blocks for the plot + 3 block gap). Make sure that space is clear.

### 9. Set up the bus pressure plate

Place a pressure plate at the bus entrance connected to a command block that runs:

```
/titeeni board @p
```

`@p` resolves to the player who stepped on the plate.

## Running the event

```
/titeeni advance         ← teleports all assigned players to the first event (TURKU), boat race begins
```

Players complete the current activity, then walk onto the pressure plate to board the bus.

```
/titeeni advance         ← teleports all players to the next event, also boards them on the bus if missing
```

The minigame runs until it ends naturally (timer expires or all players finish) or you advance to the next one.

To jump to a specific stage (e.g. for testing):
```
/titeeni advance tampere
```

## Commands

All commands require op (permission `titeeni.admin`). Chat is disabled for all non-op players during the event.

All configuration changes are saved to `TiteeniPlugin/config.yml` and persist over resets.

### Event management

| Command | Description |
|---|---|
| `/titeeni assign <guild> <player>` | Assign an online player to a guild |
| `/titeeni unassign <player>` | Remove a player from all guilds |
| `/titeeni setadmin <player>` | Designate who receives scoring messages in chat |
| `/titeeni setlocation <stage> [guild]` | Save your position as the guild's spawn for a stage (all guilds if omitted) |
| `/titeeni setbus` | Save your current position as the bus location |
| `/titeeni goto <stage> <guild>` | Teleport to a configured stage spawn |
| `/titeeni advance [stage]` | Advance to the next (or a specific) stage — teleports bus players and starts the minigame |
| `/titeeni end` | End the event early and reset state |
| `/titeeni board [player]` | Mark a player as on the bus; omit player to board all remaining |
| `/titeeni totalscore` | Show accumulated event scores across all completed stages |

### Minigame control

| Command | Description |
|---|---|
| `/titeeni score` | Show scores for the current game |
| `/titeeni stop` | Stop the current game |
| `/titeeni sethider <player>` | Manually set the hider (Jallupöllö only) |
| `/titeeni setkoth <y>` | Set the King of the Hill Y level (Lahti) |
| `/titeeni setturkugate` | Save your current Z as the Turku boat gate (players cannot leave their boat before this Z) |
| `/titeeni setzombiespawn <guild>` | Save your position as the zombie spawn for a guild (Oulu) |
| `/titeeni setplot model` | Register a model plot corner (Copy the Build) |
| `/titeeni setplot build <guild>` | Register a build plot corner for a guild (Copy the Build) |


## Minigames

### Timed Race

Used for Turku (ice boating) and Tampere (parkour). The plugin starts a shared timer when the stage begins. Each player's individual clock stops the moment they board the bus at the finish.

- Your elapsed time is shown in the action bar in real time
- Once you finish, the action bar shows your final time in green
- Players who do not finish receive last place

**Tampere DNF ranking:** players who do not finish before the timer are ranked by the Y level of their last reached checkpoint — the further through the parkour, the better the rank. This correctly separates players who made different amounts of progress.

### Trade Up (Vaasa)

Players race to trade a single **Book** up a chain of items as far as possible, using pre-configured villagers on the map. Higher position in the chain = better rank; time of achievement breaks ties within the same position.

Chain: Book → Diamond → Lantern → 2× Oak Log → Gold Block → Iron Sword → Cooked Porkchop → Fishing Rod → Diamond Axe → **Emerald**

- Each player starts with one Book and Speed V
- Reaching the Emerald automatically boards you on the bus
- Item drops are disabled
- Game ends after 5 minutes or when all players finish

### Shooting Gallery (Oulu)

Players fight waves of Toripoliisi zombies in a 4-minute shooting gallery. Each player receives a Bow (Infinity) and iron armour.

- Shoot the target blocks on the map to score points and spawn zombies
- Every hit explodes a small sphere of blocks (restored at game end) and spawns zombies at your guild's zombie spawn point
- Zombie count per hit scales with your score (1 + score/10), so the arena gets harder as you improve
- Score is the number of target blocks you hit

Setup: register a zombie spawn for each guild with `/titeeni setzombiespawn <guild>`.

### Find the Button

Players explore the map and click hidden buttons to score points. First to find the most buttons in 5 minutes wins.

- Right-click a button to claim it — the button disappears and you score 1 point
- Buttons are restored when the game ends

### Copy the Build

Players race to replicate a pre-built 5×5×15 model structure in their own designated build area. Complete as many models as possible in 8 minutes.

- Each player receives exactly the blocks needed to match the current model
- Breaking a block in your area returns it to your inventory instead of dropping it
- When your build matches the model you score 1 point, your area clears, and you receive blocks for the next model
- Models cycle — if there are multiple registered models each completion advances to the next
- You cannot place or break blocks outside the building area

### King of the Hill

Players compete to occupy the highest point. PvP is enabled with bare-fist knockback — knock opponents off the hill, then hold the peak to score.

- All players get Knockback I on their fists — no item needed
- Players at or above the specified Y level score 1 point per second
- Fall damage is cancelled so getting knocked off is repositioning, not punishment
- Action bar shows a live top-3 leaderboard and your distance to the hill
- Game ends after 3 minutes


### Jallupöllö

Hide-and-seek variant. One player is the **pöllö** (hider), the rest are seekers.

- The hider receives a **Jaloviina** bottle — hold right-click to drink and score 1 point per second
- Drinking broadcasts a positional sound to all seekers and gives the drinker a glow effect
- A seeker who hits the hider becomes the new pöllö, and they gain speed 10 for 10 seconds
- A pöllö who is not drinking gets increasingly slower every 10 seconds they do not drink
