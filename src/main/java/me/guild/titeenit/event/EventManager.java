package me.guild.titeenit.event;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.manager.MinigameManager;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.copythebuild.CopyTheBuildGame;
import me.guild.titeenit.minigame.findthebutton.FindTheButtonGame;
import me.guild.titeenit.minigame.jallupollo.JallupolloGame;
import me.guild.titeenit.minigame.kingofthehill.KingOfTheHillGame;
import me.guild.titeenit.minigame.timedrace.TimedRaceGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the overarching event: stage progression, bus tracking,
 * ranked score accumulation, and the tab-list scoreboard.
 */
public class EventManager implements Listener {

    /**
     * Points awarded by finishing rank: index 0 = 1st place, index 1 = 2nd, etc.
     * Any rank beyond the last entry receives 1 point.
     */
    private static final int[] RANK_POINTS = {12, 10, 8, 7, 6, 5, 4, 3, 2, 1};

    private final TiteeniPlugin plugin;
    private final MinigameManager minigameManager;
    private final GuildRegistry guildRegistry;
    private final EventLocationConfig locationConfig;

    private EventStage currentStage = null;
    private boolean advancing = false;
    private final Set<UUID> playersOnBus = new HashSet<>();
    private final Map<UUID, Integer> totalScores = new HashMap<>();

    // Tab-list scoreboard
    private final Scoreboard scoreboard;
    private final Objective tabObjective;

    public EventManager(TiteeniPlugin plugin, MinigameManager minigameManager,
                        GuildRegistry guildRegistry, EventLocationConfig locationConfig) {
        this.plugin = plugin;
        this.minigameManager = minigameManager;
        this.guildRegistry = guildRegistry;
        this.locationConfig = locationConfig;

        // Set up the persistent tab-list scoreboard
        scoreboard = plugin.getServer().getScoreboardManager().getNewScoreboard();
        tabObjective = scoreboard.registerNewObjective(
                "event_pts", Criteria.DUMMY,
                Component.text("Event pts", NamedTextColor.GOLD));
        tabObjective.setDisplaySlot(DisplaySlot.PLAYER_LIST);

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // -------------------------------------------------------------------------
    // Bus
    // -------------------------------------------------------------------------

    /**
     * Marks the player as on the bus, teleports them to the bus location,
     * notifies the active game, and broadcasts a status message.
     * Intended to be triggered by a command block on a pressure plate.
     */
    public void boardBus(Player player) {
        if (!guildRegistry.isAssigned(player)) {
            player.sendMessage(Component.text("You are not assigned to a guild.", NamedTextColor.RED));
            return;
        }
        playersOnBus.add(player.getUniqueId());
        locationConfig.getBusLocation().ifPresent(player::teleport);

        // Let the active minigame react (e.g. TimedRaceGame records finish time)
        Minigame currentGame = minigameManager.getCurrentGame();
        if (currentGame != null) {
            currentGame.onPlayerBoarded(player);
        }

        Optional<Guild> guild = guildRegistry.getGuild(player);
        String guildName = guild.map(Guild::getDisplayName).orElse("?");
        player.sendMessage(Component.text(
                "You have boarded the bus! (" + guildName + ")", NamedTextColor.GREEN));

        int boarded = (int) playersOnBus.stream()
                .filter(uid -> plugin.getServer().getPlayer(uid) != null)
                .count();
        int total = guildRegistry.getAssignedOnlinePlayers().size();
        broadcastAll(Component.text(
                player.getName() + " (" + guildName + ") boarded the bus. ("
                        + boarded + "/" + total + " on board)", NamedTextColor.AQUA));
    }

    // -------------------------------------------------------------------------
    // Stage progression
    // -------------------------------------------------------------------------

    /**
     * Advances to the next stage:
     * 1. Scores the current game (ranked event points).
     * 2. Force-teleports any assigned players who hadn't boarded to the bus.
     * 3. Shows an "arriving to X" title + chat message for 5 seconds.
     * 4. After 5 seconds, teleports everyone to their stage spawn and starts the minigame.
     */
    public void advanceStage(CommandSender sender) {
        if (advancing) {
            sender.sendMessage(Component.text("Stage transition already in progress.", NamedTextColor.RED));
            return;
        }

        boolean isFirstStage = (currentStage == null);
        EventStage nextStage;
        if (isFirstStage) {
            nextStage = EventStage.AINOA;
        } else {
            Optional<EventStage> next = currentStage.next();
            if (next.isEmpty()) {
                sender.sendMessage(Component.text(
                        "The event has already completed all stages!", NamedTextColor.RED));
                return;
            }
            nextStage = next.get();
        }

        // Score and stop the current game
        if (minigameManager.getCurrentGame() != null) {
            accumulateRankedScores(minigameManager.getCurrentGame());
            minigameManager.stopGame();
        }

        // For stages 2+: force-teleport anyone who didn't board on their own
        if (!isFirstStage) {
            for (Player p : guildRegistry.getAssignedOnlinePlayers()) {
                if (!playersOnBus.contains(p.getUniqueId())) {
                    playersOnBus.add(p.getUniqueId());
                    locationConfig.getBusLocation().ifPresent(p::teleport);
                    p.sendMessage(Component.text(
                            "You were teleported to the bus.", NamedTextColor.YELLOW));
                }
            }
        }

        currentStage = nextStage;
        advancing = true;

        // Show "arriving" title to all players for the 5-second bus ride
        broadcastAll(Component.text(
                "Arriving to " + currentStage.getDisplayName() + "...", NamedTextColor.AQUA));
        Title arrivalTitle = Title.title(
                Component.text(currentStage.getDisplayName(), NamedTextColor.GOLD),
                Component.text(currentStage.getSubtitle(), NamedTextColor.YELLOW),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofSeconds(9),
                        Duration.ofMillis(500)));
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.showTitle(arrivalTitle);
        }
        sender.sendMessage(Component.text(
                "Arriving at " + currentStage.getDisplayName() + " — teleporting in 5 seconds.",
                NamedTextColor.GREEN));

        // Teleport and start minigame after 5 seconds (100 ticks)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            advancing = false;

            List<UUID> toTeleport = isFirstStage
                    ? guildRegistry.getAssignedOnlinePlayers().stream().map(Player::getUniqueId).toList()
                    : new ArrayList<>(playersOnBus);
            playersOnBus.clear();

            for (UUID uuid : toTeleport) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null) continue;
                guildRegistry.getGuild(player).ifPresent(guild -> {
                    Optional<Location> loc = locationConfig.getStageLocation(currentStage, guild);
                    if (loc.isPresent()) {
                        player.teleport(loc.get());
                    } else {
                        player.sendMessage(Component.text(
                                "No spawn location configured for " + currentStage.getDisplayName()
                                        + " — staying put.", NamedTextColor.YELLOW));
                    }
                });
            }

            if (currentStage.hasMinigame()) {
                launchMinigame(currentStage, sender);
            }
        }, 100L);
    }

    // -------------------------------------------------------------------------
    // Ranked score accumulation
    // -------------------------------------------------------------------------

    /**
     * Ranks players by raw game score, awards event points by rank, and
     * refreshes the tab scoreboard. Ties are broken arbitrarily.
     * Points: 1st=12, 2nd=10, 3rd=8, 4th=7 … 10th=1.
     */
    private void accumulateRankedScores(Minigame game) {
        Map<UUID, Integer> rawScores = game.getScores();
        if (rawScores.isEmpty()) return;

        Comparator<Map.Entry<UUID, Integer>> cmp = game.isLowerScoreBetter()
                ? Map.Entry.comparingByValue()
                : Map.Entry.<UUID, Integer>comparingByValue().reversed();

        List<UUID> ranked = rawScores.entrySet().stream()
                .sorted(cmp)
                .map(Map.Entry::getKey)
                .toList();

        for (int i = 0; i < ranked.size(); i++) {
            int pts = i < RANK_POINTS.length ? RANK_POINTS[i] : 1;
            totalScores.merge(ranked.get(i), pts, Integer::sum);
        }

        updateScoreboard();
    }

    // -------------------------------------------------------------------------
    // Scoreboard
    // -------------------------------------------------------------------------

    private void updateScoreboard() {
        for (Map.Entry<UUID, Integer> entry : totalScores.entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p == null) continue;
            tabObjective.getScore(p.getName()).setScore(entry.getValue());
        }
        // Ensure all online players see this scoreboard
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!p.getScoreboard().equals(scoreboard)) {
                p.setScoreboard(scoreboard);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        p.setScoreboard(scoreboard);
        // Restore their score entry if they already have points
        Integer score = totalScores.get(p.getUniqueId());
        if (score != null) {
            tabObjective.getScore(p.getName()).setScore(score);
        }
    }

    // -------------------------------------------------------------------------
    // Scores (public)
    // -------------------------------------------------------------------------

    public Map<UUID, Integer> getTotalScores() {
        return Map.copyOf(totalScores);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public EventStage getCurrentStage() { return currentStage; }
    public Set<UUID> getPlayersOnBus() { return Set.copyOf(playersOnBus); }

    // -------------------------------------------------------------------------
    // Internal: launch minigame for a given stage
    // -------------------------------------------------------------------------

    private void launchMinigame(EventStage stage, CommandSender sender) {
        List<Player> players = guildRegistry.getAssignedOnlinePlayers();
        if (players.isEmpty()) {
            sender.sendMessage(Component.text(
                    "No assigned players online — minigame not started.", NamedTextColor.YELLOW));
            return;
        }

        switch (stage.getMinigameKey()) {
            case "timedrace" -> {
                minigameManager.startGame(new TimedRaceGame(plugin, players));
                sender.sendMessage(Component.text(
                        "Started Timed Race for " + stage.getDisplayName() + ".",
                        NamedTextColor.GREEN));
            }
            case "kingofthehill" -> {
                double hillTopY = locationConfig.getKothHillY();
                minigameManager.startGame(new KingOfTheHillGame(plugin, players, hillTopY));
                sender.sendMessage(Component.text(
                        "Started King of the Hill (hill Y=" + hillTopY + ")", NamedTextColor.GREEN));
            }
            case "copythebuild" -> {
                List<Location> modelPlots = loadModelPlots();
                Map<UUID, Location> buildPlots = buildGuildPlotMap();
                if (modelPlots.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "No model plots configured — Copy the Build not started.",
                            NamedTextColor.RED));
                    return;
                }
                if (buildPlots.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "No guild build plots configured — Copy the Build not started.",
                            NamedTextColor.RED));
                    return;
                }
                minigameManager.startGame(new CopyTheBuildGame(plugin, players, modelPlots, buildPlots));
                sender.sendMessage(Component.text("Started Copy the Build.", NamedTextColor.GREEN));
            }
            case "findthebutton" -> {
                minigameManager.startGame(new FindTheButtonGame(plugin, players));
                sender.sendMessage(Component.text("Started Find the Button.", NamedTextColor.GREEN));
            }
            case "jallupollo" -> {
                if (players.size() < 2) {
                    sender.sendMessage(Component.text(
                            "Need at least 2 assigned online players for Jallupöllö.",
                            NamedTextColor.RED));
                    return;
                }
                minigameManager.startGame(new JallupolloGame(plugin, players));
                sender.sendMessage(Component.text("Started Jallupöllö.", NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(
                    "Unknown minigame key: " + stage.getMinigameKey(), NamedTextColor.RED));
        }
    }

    private List<Location> loadModelPlots() {
        List<?> raw = plugin.getConfig().getList("copyTheBuild.modelPlots");
        if (raw == null) return List.of();
        List<Location> locations = new ArrayList<>();
        for (Object obj : raw) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            Object worldName = map.get("world");
            Object xObj = map.get("x");
            Object yObj = map.get("y");
            Object zObj = map.get("z");
            if (!(worldName instanceof String) || xObj == null || yObj == null || zObj == null) continue;
            org.bukkit.World world = plugin.getServer().getWorld((String) worldName);
            if (world == null) continue;
            locations.add(new Location(world,
                    ((Number) xObj).doubleValue(),
                    ((Number) yObj).doubleValue(),
                    ((Number) zObj).doubleValue()));
        }
        return locations;
    }

    private Map<UUID, Location> buildGuildPlotMap() {
        Map<UUID, Location> result = new HashMap<>();
        List<?> raw = plugin.getConfig().getList("copyTheBuild.buildPlots");
        if (raw == null) return result;
        for (Object obj : raw) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            Object guildName = map.get("guild");
            Object worldName = map.get("world");
            Object xObj = map.get("x");
            Object yObj = map.get("y");
            Object zObj = map.get("z");
            if (!(guildName instanceof String) || !(worldName instanceof String)
                    || xObj == null || yObj == null || zObj == null) continue;
            Guild.fromString((String) guildName).ifPresent(guild -> {
                guildRegistry.getPlayer(guild).ifPresent(player -> {
                    org.bukkit.World world = plugin.getServer().getWorld((String) worldName);
                    if (world == null) return;
                    result.put(player.getUniqueId(), new Location(world,
                            ((Number) xObj).doubleValue(),
                            ((Number) yObj).doubleValue(),
                            ((Number) zObj).doubleValue()));
                });
            });
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void broadcastAll(Component message) {
        plugin.getServer().broadcast(message);
    }
}
