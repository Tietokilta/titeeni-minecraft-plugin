package me.guild.titeenit.minigame.copythebuild;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.command.MinigameCommandHandler;
import me.guild.titeenit.manager.MinigameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CopyTheBuildCommandHandler implements MinigameCommandHandler {

    private static final List<String> PLOT_TYPES = List.of("model", "build");

    private final TiteeniPlugin plugin;
    private final MinigameManager manager;

    public CopyTheBuildCommandHandler(TiteeniPlugin plugin, MinigameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public List<String> getGameNames() { return List.of("copythebuild", "ctb"); }

    @Override
    public void handleStart(CommandSender sender, List<Player> online, String[] args) {
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

    @Override
    public List<String> getSubCommandNames() { return List.of("setplot"); }

    @Override
    public void handleSubCommand(CommandSender sender, String[] args) {
        // args[0] == "setplot"
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /titeeni setplot model | setplot build <player>",
                    NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }

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
                Location loc = admin.getLocation().getBlock().getLocation();
                saveBuildPlot(args[2], loc);
                admin.sendMessage(Component.text(
                        "Saved build plot for " + args[2] + " at " + formatLoc(loc),
                        NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text(
                    "Unknown plot type: " + args[1] + ". Use: " + String.join(", ", PLOT_TYPES),
                    NamedTextColor.RED));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // setplot <type>
        if (args[0].equalsIgnoreCase("setplot")) {
            if (args.length == 2) {
                return PLOT_TYPES.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .toList();
            }
            // setplot build <player>
            if (args.length == 3 && args[1].equalsIgnoreCase("build")) {
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
        }
        return List.of();
    }

    @Override
    public List<String> getHelpLines() {
        return List.of(
                "/titeeni setplot model      - Register a model plot corner",
                "/titeeni setplot build <p>  - Register a build plot corner for player p"
        );
    }

    // =========================================================================
    // Config helpers
    // =========================================================================

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
}
