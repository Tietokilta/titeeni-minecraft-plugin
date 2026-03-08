package me.guild.titeenit.event;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.manager.MinigameManager;
import me.guild.titeenit.minigame.copythebuild.CopyTheBuildGame;
import me.guild.titeenit.minigame.findthebutton.FindTheButtonGame;
import me.guild.titeenit.minigame.jallupollo.JallupolloGame;
import me.guild.titeenit.minigame.kingofthehill.KingOfTheHillGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the overarching event: stage progression, bus tracking, and cross-game score accumulation.
 */
public class EventManager {

    private final TiteeniPlugin plugin;
    private final MinigameManager minigameManager;
    private final GuildRegistry guildRegistry;
    private final EventLocationConfig locationConfig;

    private EventStage currentStage = null;
    private final Set<UUID> playersOnBus = new HashSet<>();
    private final Map<UUID, Integer> totalScores = new HashMap<>();

    public EventManager(TiteeniPlugin plugin, MinigameManager minigameManager,
                        GuildRegistry guildRegistry, EventLocationConfig locationConfig) {
        this.plugin = plugin;
        this.minigameManager = minigameManager;
        this.guildRegistry = guildRegistry;
        this.locationConfig = locationConfig;
    }

    // -------------------------------------------------------------------------
    // Bus
    // -------------------------------------------------------------------------

    /**
     * Marks the player as on the bus, teleports them to the bus location if configured,
     * and sends a confirmation message. Intended to be triggered by a command block.
     */
    public void boardBus(Player player) {
        if (!guildRegistry.isAssigned(player)) {
            player.sendMessage(Component.text("You are not assigned to a guild.", NamedTextColor.RED));
            return;
        }
        playersOnBus.add(player.getUniqueId());
        locationConfig.getBusLocation().ifPresent(player::teleport);

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
     * Advances to the next stage: accumulates scores, stops the current game,
     * teleports bus players, and auto-starts the next stage's minigame if applicable.
     */
    public void advanceStage(CommandSender sender) {
        // Determine next stage
        EventStage nextStage;
        if (currentStage == null) {
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

        // Accumulate scores from running game before stopping it
        if (minigameManager.getCurrentGame() != null) {
            minigameManager.getCurrentGame().getScores()
                    .forEach((uuid, pts) -> totalScores.merge(uuid, pts, Integer::sum));
            minigameManager.stopGame();
        }

        currentStage = nextStage;

        // Teleport bus players to their guild's configured start location
        List<UUID> boarded = new ArrayList<>(playersOnBus);
        playersOnBus.clear();
        for (UUID uuid : boarded) {
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

        // Announce the new stage
        broadcastAll(Component.text("══════════════════════════════", NamedTextColor.GOLD));
        broadcastAll(Component.text("  Next stop: " + currentStage.getDisplayName() + "!",
                NamedTextColor.GOLD));
        broadcastAll(Component.text("══════════════════════════════", NamedTextColor.GOLD));

        sender.sendMessage(Component.text(
                "Advanced to stage: " + currentStage.getDisplayName(), NamedTextColor.GREEN));

        // Auto-start minigame if this stage has one
        if (currentStage.hasMinigame()) {
            launchMinigame(currentStage, sender);
        }
    }

    // -------------------------------------------------------------------------
    // Scores
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
