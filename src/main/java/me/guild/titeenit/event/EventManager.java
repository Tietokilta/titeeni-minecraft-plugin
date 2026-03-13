package me.guild.titeenit.event;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.manager.MinigameManager;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.copythebuild.CopyTheBuildGame;
import me.guild.titeenit.minigame.findthebutton.FindTheButtonGame;
import me.guild.titeenit.minigame.jallupollo.JallupolloGame;
import me.guild.titeenit.minigame.kingofthehill.KingOfTheHillGame;
import me.guild.titeenit.minigame.timedrace.TampereRaceGame;
import me.guild.titeenit.minigame.tradeup.TradeUpGame;
import me.guild.titeenit.minigame.shootinggallery.ShootingGalleryCommandHandler;
import me.guild.titeenit.minigame.shootinggallery.ShootingGalleryGame;
import me.guild.titeenit.minigame.timedrace.TimedRaceGame;
import me.guild.titeenit.minigame.timedrace.TurkuRaceGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
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
    /** UUID of the admin who receives overall event scoring messages. Null if not set. */
    private UUID adminUuid = null;

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

        // Load persisted admin UUID
        String savedAdmin = plugin.getConfig().getString("event.adminUuid");
        if (savedAdmin != null) {
            try { adminUuid = UUID.fromString(savedAdmin); } catch (IllegalArgumentException ignored) {}
        }

        // Keep all assigned players permanently fed — refresh every 2 s, no particles
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            PotionEffect saturation = new PotionEffect(PotionEffectType.SATURATION, 9999, 0, false, false);
            for (Player p : guildRegistry.getAssignedOnlinePlayers()) {
                p.addPotionEffect(saturation);
            }
        }, 20L, 40L);
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
    /** Advance to the next sequential stage. */
    public void advanceStage(CommandSender sender) {
        advanceStage(sender, null);
    }

    /**
     * Advance to a specific stage, or to the next sequential stage when
     * {@code targetStage} is {@code null}. When the last stage is already
     * complete and no target is given, shows the final leaderboard and resets.
     */
    public void advanceStage(CommandSender sender, EventStage targetStage) {
        if (advancing) {
            sender.sendMessage(Component.text("Stage transition already in progress.", NamedTextColor.RED));
            return;
        }

        boolean isFirstStage = (currentStage == null);
        EventStage nextStage;

        if (targetStage != null) {
            nextStage = targetStage;
        } else if (isFirstStage) {
            nextStage = EventStage.values()[0];
        } else {
            Optional<EventStage> next = currentStage.next();
            if (next.isEmpty()) {
                sender.sendMessage(Component.text(
                        "The last stage is still running. It will end automatically.",
                        NamedTextColor.YELLOW));
                return;
            }
            nextStage = next.get();
        }

        // If the admin advances before the timer runs out, score and stop the game now.
        // Normally the game is already scored and stopped when the timer expires.
        if (minigameManager.getCurrentGame() != null) {
            Minigame current = minigameManager.getCurrentGame();
            minigameManager.stopGame();
            accumulateRankedScores(current);
        }

        autoBoardRemaining();

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
     * Called by MinigameManager when a game ends naturally (timer expired or
     * self-terminated). Scores the game and, if this was the last stage, shows
     * the final leaderboard and resets so the event can be run again.
     */
    public void onMinigameEnded(Minigame game) {
        accumulateRankedScores(game);
        if (currentStage != null && currentStage.next().isEmpty()) {
            broadcastFinalLeaderboard();
            resetState();
        }
    }

    /**
     * Ranks players by raw game score, awards event points by rank, and
     * refreshes the tab scoreboard.
     * Tied players share the average of the points for the ranks they span,
     * rounded up. Points: 1st=12, 2nd=10, 3rd=8, 4th=7 … 10th=1.
     */
    private void accumulateRankedScores(Minigame game) {
        Map<UUID, Integer> rawScores = game.getScores();
        if (rawScores.isEmpty()) return;

        Comparator<Map.Entry<UUID, Integer>> cmp = game.isLowerScoreBetter()
                ? Map.Entry.comparingByValue()
                : Map.Entry.<UUID, Integer>comparingByValue().reversed();

        List<Map.Entry<UUID, Integer>> sorted = rawScores.entrySet().stream()
                .sorted(cmp)
                .toList();

        // Compute points per player, averaging within tie groups (ceiling)
        Map<UUID, Integer> awardedPts = new HashMap<>();
        int i = 0;
        while (i < sorted.size()) {
            int j = i + 1;
            while (j < sorted.size()
                    && sorted.get(j).getValue().equals(sorted.get(i).getValue())) j++;
            int sum = 0;
            for (int k = i; k < j; k++) sum += k < RANK_POINTS.length ? RANK_POINTS[k] : 1;
            int pts = (sum + (j - i) - 1) / (j - i); // ceiling division
            for (int k = i; k < j; k++) awardedPts.put(sorted.get(k).getKey(), pts);
            i = j;
        }

        for (Map.Entry<UUID, Integer> e : awardedPts.entrySet()) {
            totalScores.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        updateScoreboard();

        String[] placeLabels = {"1st","2nd","3rd","4th","5th","6th","7th","8th","9th","10th"};
        sendToAdmin(Component.text("=== " + game.getDisplayName() + " — Stage Results ===", NamedTextColor.GOLD));
        i = 0;
        while (i < sorted.size()) {
            int j = i + 1;
            while (j < sorted.size()
                    && sorted.get(j).getValue().equals(sorted.get(i).getValue())) j++;
            boolean tied = j - i > 1;
            String placeBase = i < placeLabels.length ? placeLabels[i] : (i + 1) + "th";
            String place = tied ? "T-" + placeBase : placeBase;
            int pts = awardedPts.get(sorted.get(i).getKey());
            for (int k = i; k < j; k++) {
                UUID uid = sorted.get(k).getKey();
                int total = totalScores.getOrDefault(uid, 0);
                sendToAdmin(Component.text(
                        place + " " + resolveDisplayName(uid) + ": +" + pts + " pts  (total: " + total + ")",
                        NamedTextColor.YELLOW));
            }
            i = j;
        }
    }

    private String resolveDisplayName(UUID uuid) {
        Optional<Guild> guild = guildRegistry.getGuild(uuid);
        if (guild.isPresent()) return guild.get().getDisplayName();
        Player p = plugin.getServer().getPlayer(uuid);
        if (p != null) return p.getName();
        var offline = plugin.getServer().getOfflinePlayer(uuid);
        return offline.getName() != null ? offline.getName() : uuid.toString();
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
    public void onChat(AsyncChatEvent event) {
        event.setCancelled(true);
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

    /**
     * Ends the event immediately without showing a leaderboard. The current game
     * (if any) is stopped and all state is cleared so the next
     * {@code /titeeni advance} starts from the first stage.
     */
    public void endEvent(CommandSender sender) {
        if (advancing) {
            sender.sendMessage(Component.text(
                    "Cannot end during a stage transition. Wait and try again.",
                    NamedTextColor.RED));
            return;
        }
        if (minigameManager.getCurrentGame() != null) {
            minigameManager.stopGame();
        }
        resetState();
        sender.sendMessage(Component.text(
                "Event ended. Use /titeeni advance to start from the first stage.",
                NamedTextColor.GREEN));
    }

    private void resetState() {
        currentStage = null;
        advancing = false;
        playersOnBus.clear();
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
        totalScores.clear();
    }

    public Map<UUID, Integer> getTotalScores() {
        return Map.copyOf(totalScores);
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public EventStage getCurrentStage() { return currentStage; }
    public Set<UUID> getPlayersOnBus() { return Set.copyOf(playersOnBus); }

    /**
     * Boards every assigned online player who has not yet boarded.
     * Called automatically when a minigame's timer expires so no one is left
     * behind waiting at the end of a stage.
     */
    public void autoBoardRemaining() {
        for (Player p : guildRegistry.getAssignedOnlinePlayers()) {
            if (!playersOnBus.contains(p.getUniqueId())) {
                boardBus(p);
            }
        }
    }

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
            }
            case "tampererace" -> {
                minigameManager.startGame(new TampereRaceGame(plugin, players));
            }
            case "tradeup" -> {
                minigameManager.startGame(new TradeUpGame(plugin, players));
            }
            case "boatrace" -> {
                double gateZ = locationConfig.getTurkuGateZ();
                minigameManager.startGame(new TurkuRaceGame(plugin, players, gateZ));
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
            case "shootinggallery" -> {
                Map<Guild, Location> zombieSpawns = ShootingGalleryCommandHandler.loadZombieSpawns(plugin);
                minigameManager.startGame(new ShootingGalleryGame(plugin, players, zombieSpawns));
                sender.sendMessage(Component.text("Started Shooting Gallery.", NamedTextColor.GREEN));
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

    private void broadcastFinalLeaderboard() {
        // Build sorted leaderboard (highest score first)
        List<Map.Entry<UUID, Integer>> sorted = totalScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .toList();

        sendToAdmin(Component.text("═══════════ FINAL RESULTS ═══════════", NamedTextColor.GOLD));
        for (int i = 0; i < sorted.size(); i++) {
            UUID uid = sorted.get(i).getKey();
            int pts = sorted.get(i).getValue();
            String playerName = plugin.getServer().getOfflinePlayer(uid).getName();
            if (playerName == null) playerName = uid.toString();
            String guildName = guildRegistry.getGuild(uid)
                    .map(Guild::getDisplayName).orElse("?");
            String place = (i + 1) + ". ";
            NamedTextColor color = switch (i) {
                case 0 -> NamedTextColor.GOLD;
                case 1 -> NamedTextColor.GRAY;
                case 2 -> NamedTextColor.DARK_RED;
                default -> NamedTextColor.WHITE;
            };
            sendToAdmin(Component.text(
                    place + playerName + " (" + guildName + ") — " + pts + " pts", color));
        }
        sendToAdmin(Component.text("═════════════════════════════════════", NamedTextColor.GOLD));
    }

    public void setAdmin(UUID uuid) {
        this.adminUuid = uuid;
        plugin.getConfig().set("event.adminUuid", uuid.toString());
        plugin.saveConfig();
    }

    /** Sends a message only to the configured admin player (if online). */
    private void sendToAdmin(Component message) {
        if (adminUuid == null) return;
        Player admin = plugin.getServer().getPlayer(adminUuid);
        if (admin != null) admin.sendMessage(message);
    }

    private void broadcastAll(Component message) {
        plugin.getServer().broadcast(message);
    }
}
