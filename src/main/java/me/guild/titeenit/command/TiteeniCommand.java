package me.guild.titeenit.command;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.manager.MinigameManager;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.copythebuild.CopyTheBuildGame;
import me.guild.titeenit.minigame.findthebutton.FindTheButtonGame;
import me.guild.titeenit.minigame.jallupollo.JallupolloGame;
import me.guild.titeenit.minigame.kingofthehill.KingOfTheHillGame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /titeeni command handler.
 *
 * Sub-commands:
 *   start <game>       – start a named minigame with all online players
 *   stop               – stop the current minigame
 *   score              – show current scores
 *   sethider <player>  – manually reassign the hider (hide-and-seek only)
 *
 * Adding a new minigame:
 *   1. Create a class extending Minigame.
 *   2. Add a case in handleStart() that constructs and passes it to manager.startGame().
 *   3. Add its name to GAME_TYPES for tab completion.
 */
public class TiteeniCommand implements CommandExecutor, TabCompleter {

    private static final List<String> GAME_TYPES = List.of("jallupollo", "findthebutton", "kingofthehill", "copythebuild");
    private static final List<String> SUB_COMMANDS = List.of("start", "stop", "score", "sethider", "setplot");
    private static final List<String> PLOT_TYPES = List.of("model", "build");

    private final TiteeniPlugin plugin;
    private final MinigameManager manager;

    public TiteeniCommand(TiteeniPlugin plugin, MinigameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // =========================================================================
    // CommandExecutor
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("titeeni.admin")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text(
                            "Usage: /titeeni start <" + String.join("|", GAME_TYPES) + ">",
                            NamedTextColor.RED
                    ));
                    return true;
                }
                handleStart(sender, args);
            }
            case "stop"     -> handleStop(sender);
            case "score"    -> handleScore(sender);
            case "sethider" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /titeeni sethider <player>", NamedTextColor.RED));
                    return true;
                }
                handleSetHider(sender, args[1]);
            }
            case "setplot" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text(
                            "Usage: /titeeni setplot model | setplot build <player>",
                            NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                    return true;
                }
                handleSetPlot(player, args);
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    // =========================================================================
    // Sub-command handlers
    // =========================================================================

    private void handleStart(CommandSender sender, String[] args) {
        if (manager.getCurrentGame() != null) {
            sender.sendMessage(Component.text(
                    "A game is already running. Use /titeeni stop first.", NamedTextColor.RED
            ));
            return;
        }

        List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());

        String gameType = args[1].toLowerCase();
        switch (gameType.toLowerCase()) {
            case "jallupollo", "jp" -> {
                if (online.size() < 2) {
                    sender.sendMessage(Component.text("At least 2 players must be online.", NamedTextColor.RED));
                    return;
                }
                manager.startGame(new JallupolloGame(plugin, online));
                sender.sendMessage(Component.text(
                        "Starting Jallupöllö with " + online.size() + " players!",
                        NamedTextColor.GREEN
                ));
            }
            case "findthebutton", "ftb" -> {
                manager.startGame(new FindTheButtonGame(plugin, online));
                sender.sendMessage(Component.text(
                        "Starting Find the Button with " + online.size() + " players!",
                        NamedTextColor.GREEN
                ));
            }
            case "kingofthehill", "koth" -> {
                if (online.size() < 2) {
                    sender.sendMessage(Component.text("At least 2 players must be online.", NamedTextColor.RED));
                    return;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /titeeni start kingofthehill <y>", NamedTextColor.RED));
                    return;
                }
                double hillTopY;
                try {
                    hillTopY = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid Y level: " + args[2], NamedTextColor.RED));
                    return;
                }
                manager.startGame(new KingOfTheHillGame(plugin, online, hillTopY));
                sender.sendMessage(Component.text(
                        "Starting King of the Hill with " + online.size() + " players! Hill top: Y=" + hillTopY,
                        NamedTextColor.GREEN));
            }
            case "copythebuild", "ctb" -> {
                List<Location> modelPlots = loadPlotLocations("copyTheBuild.modelPlots");
                Map<String, Location> buildAssignments = loadBuildAssignments();
                if (modelPlots.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "No model plots registered. Use /titeeni setplot model first.",
                            NamedTextColor.RED));
                    return;
                }
                if (buildAssignments.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "No build plots registered. Use /titeeni setplot build <player> first.",
                            NamedTextColor.RED));
                    return;
                }
                // Include only online players who have an assigned build plot
                List<Player> participants = new ArrayList<>();
                List<Location> orderedPlots = new ArrayList<>();
                for (Player p : online) {
                    Location corner = buildAssignments.get(p.getName());
                    if (corner != null) {
                        participants.add(p);
                        orderedPlots.add(corner);
                    }
                }
                if (participants.isEmpty()) {
                    sender.sendMessage(Component.text(
                            "None of the online players have a build plot assigned.",
                            NamedTextColor.RED));
                    return;
                }
                manager.startGame(new CopyTheBuildGame(plugin, participants, modelPlots, orderedPlots));
                sender.sendMessage(Component.text(
                        "Starting Copy the Build with " + participants.size() + " players!",
                        NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(
                    "Unknown game type: " + gameType + ".  Available: " + String.join(", ", GAME_TYPES),
                    NamedTextColor.RED
            ));
        }
    }

    private void handleStop(CommandSender sender) {
        if (manager.getCurrentGame() == null) {
            sender.sendMessage(Component.text("No game is currently running.", NamedTextColor.RED));
            return;
        }
        manager.stopGame();
        sender.sendMessage(Component.text("Game stopped.", NamedTextColor.GREEN));
    }

    private void handleScore(CommandSender sender) {
        Minigame game = manager.getCurrentGame();
        if (game == null) {
            sender.sendMessage(Component.text("No game is currently running.", NamedTextColor.RED));
            return;
        }

        Map<UUID, Integer> scores = game.getScores();
        if (scores.isEmpty()) {
            sender.sendMessage(Component.text("No scores to show.", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.text("=== " + game.getDisplayName() + " — Scores ===", NamedTextColor.GOLD));
        scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    Player p = plugin.getServer().getPlayer(entry.getKey());
                    String name = p != null ? p.getName() : entry.getKey().toString();
                    sender.sendMessage(Component.text(name + ": " + entry.getValue() + " points", NamedTextColor.YELLOW));
                });
    }

    private void handleSetHider(CommandSender sender, String playerName) {
        if (!(manager.getCurrentGame() instanceof JallupolloGame game)) {
            sender.sendMessage(Component.text("No Jallupöllö game is currently running.", NamedTextColor.RED));
            return;
        }

        Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + playerName, NamedTextColor.RED));
            return;
        }
        if (!game.isParticipant(target)) {
            sender.sendMessage(Component.text(playerName + " is not in the game.", NamedTextColor.RED));
            return;
        }

        game.chooseNewHider(target);
        sender.sendMessage(Component.text("Set " + target.getName() + " as the hider.", NamedTextColor.GREEN));
    }

    private void handleSetPlot(Player admin, String[] args) {
        switch (args[1].toLowerCase()) {
            case "model" -> {
                Location loc = admin.getLocation().getBlock().getLocation();
                saveNewPlotLocation("copyTheBuild.modelPlots", loc);
                int count = loadPlotLocations("copyTheBuild.modelPlots").size();
                admin.sendMessage(Component.text(
                        "Saved model plot at " + formatLoc(loc) + " (" + count + " total)",
                        NamedTextColor.GREEN));
            }
            case "build" -> {
                if (args.length < 3) {
                    admin.sendMessage(Component.text(
                            "Usage: /titeeni setplot build <player>", NamedTextColor.RED));
                    return;
                }
                String playerName = args[2];
                Location loc = admin.getLocation().getBlock().getLocation();
                saveBuildPlot(playerName, loc);
                admin.sendMessage(Component.text(
                        "Saved build plot for " + playerName + " at " + formatLoc(loc),
                        NamedTextColor.GREEN));
            }
            default -> admin.sendMessage(Component.text(
                    "Unknown plot type: " + args[1] + ". Use: " + String.join(", ", PLOT_TYPES),
                    NamedTextColor.RED));
        }
    }

    /** Returns player name → build corner for all registered build plots. */
    private Map<String, Location> loadBuildAssignments() {
        List<?> raw = plugin.getConfig().getList("copyTheBuild.buildPlots");
        if (raw == null) return new LinkedHashMap<>();
        Map<String, Location> assignments = new LinkedHashMap<>();
        for (Object obj : raw) {
            if (!(obj instanceof Map<?, ?> map)) continue;
            Object playerName = map.get("player");
            Object worldName  = map.get("world");
            Object xObj = map.get("x");
            Object yObj = map.get("y");
            Object zObj = map.get("z");
            if (!(playerName instanceof String) || !(worldName instanceof String)
                    || xObj == null || yObj == null || zObj == null) continue;
            org.bukkit.World world = plugin.getServer().getWorld((String) worldName);
            if (world == null) continue;
            assignments.put((String) playerName, new Location(world,
                    ((Number) xObj).doubleValue(),
                    ((Number) yObj).doubleValue(),
                    ((Number) zObj).doubleValue()));
        }
        return assignments;
    }

    /**
     * Saves a build plot for the given player name. Replaces any existing entry
     * for that player so re-running the command moves their plot rather than duplicating it.
     */
    private void saveBuildPlot(String playerName, Location loc) {
        List<Map<String, Object>> existing = new ArrayList<>();
        List<?> raw = plugin.getConfig().getList("copyTheBuild.buildPlots");
        if (raw != null) {
            for (Object obj : raw) {
                if (!(obj instanceof Map<?, ?> map)) continue;
                if (playerName.equalsIgnoreCase(String.valueOf(map.get("player")))) continue;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("player", map.get("player"));
                entry.put("world",  map.get("world"));
                entry.put("x", map.get("x"));
                entry.put("y", map.get("y"));
                entry.put("z", map.get("z"));
                existing.add(entry);
            }
        }
        Map<String, Object> newEntry = new LinkedHashMap<>();
        newEntry.put("player", playerName);
        newEntry.put("world",  loc.getWorld().getName());
        newEntry.put("x", loc.getBlockX());
        newEntry.put("y", loc.getBlockY());
        newEntry.put("z", loc.getBlockZ());
        existing.add(newEntry);
        plugin.getConfig().set("copyTheBuild.buildPlots", existing);
        plugin.saveConfig();
    }

    private List<Location> loadPlotLocations(String configKey) {
        List<?> raw = plugin.getConfig().getList(configKey);
        if (raw == null) return new ArrayList<>();
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

    private void saveNewPlotLocation(String configKey, Location loc) {
        List<Map<String, Object>> existing = new ArrayList<>();
        List<?> raw = plugin.getConfig().getList(configKey);
        if (raw != null) {
            for (Object obj : raw) {
                if (!(obj instanceof Map<?, ?> map)) continue;
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("world", map.get("world"));
                entry.put("x", map.get("x"));
                entry.put("y", map.get("y"));
                entry.put("z", map.get("z"));
                existing.add(entry);
            }
        }
        Map<String, Object> newEntry = new LinkedHashMap<>();
        newEntry.put("world", loc.getWorld().getName());
        newEntry.put("x", loc.getBlockX());
        newEntry.put("y", loc.getBlockY());
        newEntry.put("z", loc.getBlockZ());
        existing.add(newEntry);
        plugin.getConfig().set(configKey, existing);
        plugin.saveConfig();
    }

    private String formatLoc(Location loc) {
        return loc.getWorld().getName() + " "
                + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Titeeni Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/titeeni start <game>       - Start a minigame", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni stop               - Stop the current game", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni score              - Show current scores", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni sethider <player>  - Manually set the hider (jallupöllö)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setplot model      - Register a model plot (copythebuild)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setplot build <p>  - Register a build plot for player p (copythebuild)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Available games: " + String.join(", ", GAME_TYPES), NamedTextColor.GRAY));
    }

    // =========================================================================
    // TabCompleter
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("titeeni.admin")) return List.of();

        if (args.length == 1) {
            return SUB_COMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "start" -> GAME_TYPES.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .toList();
                case "sethider" -> plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
                case "setplot" -> PLOT_TYPES.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .toList();
                default -> List.of();
            };
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("setplot")
                && args[1].equalsIgnoreCase("build")) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}
