package me.guild.titeenit.command;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.event.EventLocationConfig;
import me.guild.titeenit.event.EventManager;
import me.guild.titeenit.event.EventStage;
import me.guild.titeenit.event.GuildRegistry;
import me.guild.titeenit.manager.MinigameManager;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.copythebuild.CopyTheBuildCommandHandler;
import me.guild.titeenit.minigame.findthebutton.FindTheButtonCommandHandler;
import me.guild.titeenit.minigame.jallupollo.JallupolloCommandHandler;
import me.guild.titeenit.minigame.kingofthehill.KingOfTheHillCommandHandler;
import me.guild.titeenit.minigame.shootinggallery.ShootingGalleryCommandHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * /titeeni command handler — routes to per-minigame MinigameCommandHandler instances
 * and handles overarching event management commands.
 */
public class TiteeniCommand implements CommandExecutor, TabCompleter {

    private static final List<String> BASE_SUB_COMMANDS = List.of(
            "score",
            "assign", "unassign", "setlocation", "setbus", "setkoth", "setturkugate", "advance", "end", "board", "totalscore",
            "goto", "setadmin"
    );

    private static final List<String> GUILD_NAMES = Arrays.stream(Guild.values())
            .map(Guild::name).map(String::toLowerCase).toList();

    private static final List<String> STAGE_NAMES = Arrays.stream(EventStage.values())
            .map(EventStage::name).map(String::toLowerCase).toList();

    private final TiteeniPlugin plugin;
    private final MinigameManager manager;
    private final GuildRegistry guildRegistry;
    private final EventLocationConfig locationConfig;
    private final EventManager eventManager;
    private final List<MinigameCommandHandler> handlers;

    public TiteeniCommand(TiteeniPlugin plugin, MinigameManager manager,
                          GuildRegistry guildRegistry, EventLocationConfig locationConfig,
                          EventManager eventManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.guildRegistry = guildRegistry;
        this.locationConfig = locationConfig;
        this.eventManager = eventManager;
        this.handlers = List.of(
                new JallupolloCommandHandler(plugin, manager),
                new FindTheButtonCommandHandler(plugin, manager),
                new KingOfTheHillCommandHandler(plugin, manager),
                new CopyTheBuildCommandHandler(plugin, manager, guildRegistry),
                new ShootingGalleryCommandHandler(plugin, manager)
        );
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
            case "score"         -> handleScore(sender);
            case "assign"        -> handleAssign(sender, args);
            case "unassign"      -> handleUnassign(sender, args);
            case "setlocation"   -> handleSetLocation(sender, args);
            case "setbus"        -> handleSetBus(sender);
            case "setkoth"       -> handleSetKoth(sender, args);
            case "setturkugate"  -> handleSetTurkuGate(sender);
            case "advance"       -> handleAdvance(sender, args);
            case "end"           -> eventManager.endEvent(sender);
            case "board"         -> handleBoard(sender, args);
            case "totalscore"    -> handleTotalScore(sender);
            case "goto"          -> handleGoto(sender, args);
            case "setadmin"      -> handleSetAdmin(sender, args);
            default -> {
                String sub = args[0].toLowerCase();
                for (MinigameCommandHandler handler : handlers) {
                    if (handler.getSubCommandNames().contains(sub)) {
                        handler.handleSubCommand(sender, args);
                        return true;
                    }
                }
                sendHelp(sender);
            }
        }

        return true;
    }

    // =========================================================================
    // Event/minigame sub-command handlers
    // =========================================================================

    private void handleAdvance(CommandSender sender, String[] args) {
        if (args.length < 2) {
            eventManager.advanceStage(sender);
            return;
        }
        EventStage target = EventStage.fromString(args[1]).orElse(null);
        if (target == null) {
            sender.sendMessage(Component.text(
                    "Unknown stage: " + args[1] + ". Available: " + STAGE_NAMES, NamedTextColor.RED));
            return;
        }
        eventManager.advanceStage(sender, target);
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

    // =========================================================================
    // Event management sub-command handlers
    // =========================================================================

    private void handleAssign(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /titeeni assign <guild> <player>", NamedTextColor.RED));
            return;
        }
        Guild guild = Guild.fromString(args[1]).orElse(null);
        if (guild == null) {
            sender.sendMessage(Component.text(
                    "Unknown guild: " + args[1] + ". Available: " + GUILD_NAMES, NamedTextColor.RED));
            return;
        }
        Player target = plugin.getServer().getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
            return;
        }
        guildRegistry.assign(guild, target);
        sender.sendMessage(Component.text(
                "Assigned " + target.getName() + " to " + guild.getDisplayName() + ".",
                NamedTextColor.GREEN));
    }

    private void handleUnassign(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /titeeni unassign <player>", NamedTextColor.RED));
            return;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        UUID uuid = target != null ? target.getUniqueId() : null;
        if (uuid == null) {
            // Accept offline players by name via OfflinePlayer
            uuid = plugin.getServer().getOfflinePlayer(args[1]).getUniqueId();
        }
        List<Guild> removed = guildRegistry.unassignPlayer(uuid);
        if (removed.isEmpty()) {
            sender.sendMessage(Component.text(args[1] + " was not assigned to any guild.", NamedTextColor.YELLOW));
        } else {
            String guilds = removed.stream().map(Guild::getDisplayName).reduce((a, b) -> a + ", " + b).orElse("");
            sender.sendMessage(Component.text(
                    "Unassigned " + args[1] + " from: " + guilds + ".", NamedTextColor.GREEN));
        }
    }

    private void handleSetLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /titeeni setlocation <stage> [guild]", NamedTextColor.RED));
            return;
        }
        EventStage stage = EventStage.fromString(args[1]).orElse(null);
        if (stage == null) {
            sender.sendMessage(Component.text(
                    "Unknown stage: " + args[1] + ". Available: " + STAGE_NAMES, NamedTextColor.RED));
            return;
        }
        if (args.length >= 3) {
            Guild guild = Guild.fromString(args[2]).orElse(null);
            if (guild == null) {
                sender.sendMessage(Component.text(
                        "Unknown guild: " + args[2] + ". Available: " + GUILD_NAMES, NamedTextColor.RED));
                return;
            }
            locationConfig.setStageLocation(stage, guild, admin.getLocation());
            sender.sendMessage(Component.text(
                    "Set " + stage.getDisplayName() + " spawn for " + guild.getDisplayName()
                            + " to your current location.", NamedTextColor.GREEN));
        } else {
            for (Guild guild : Guild.values()) {
                locationConfig.setStageLocation(stage, guild, admin.getLocation());
            }
            sender.sendMessage(Component.text(
                    "Set " + stage.getDisplayName() + " spawn for all guilds to your current location.",
                    NamedTextColor.GREEN));
        }
    }

    private void handleSetBus(CommandSender sender) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        locationConfig.setBusLocation(admin.getLocation());
        sender.sendMessage(Component.text("Bus location set to your current position.", NamedTextColor.GREEN));
    }

    private void handleSetKoth(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /titeeni setkoth <y>", NamedTextColor.RED));
            return;
        }
        double y;
        try {
            y = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid Y level: " + args[1], NamedTextColor.RED));
            return;
        }
        locationConfig.setKothHillY(y);
        sender.sendMessage(Component.text(
                "King of the Hill Y level set to " + y + ".", NamedTextColor.GREEN));
    }

    private void handleSetTurkuGate(CommandSender sender) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        double z = admin.getLocation().getZ();
        locationConfig.setTurkuGateZ(z);
        sender.sendMessage(Component.text(
                "Turku gate Z set to " + z + ". Boats cannot be left before this Z coordinate.",
                NamedTextColor.GREEN));
    }

    private void handleBoard(CommandSender sender, String[] args) {
        if (args.length < 2) {
            eventManager.autoBoardRemaining();
            return;
        }
        Player target;
        if (args[1].startsWith("@")) {
            try {
                var entities = plugin.getServer().selectEntities(sender, args[1]);
                target = entities.stream()
                        .filter(e -> e instanceof Player)
                        .map(e -> (Player) e)
                        .findFirst()
                        .orElse(null);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text("Invalid selector: " + args[1], NamedTextColor.RED));
                return;
            }
        } else {
            target = plugin.getServer().getPlayer(args[1]);
        }
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        eventManager.boardBus(target);
    }

    private void handleGoto(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /titeeni goto <stage> <guild>", NamedTextColor.RED));
            return;
        }
        EventStage stage = EventStage.fromString(args[1]).orElse(null);
        if (stage == null) {
            sender.sendMessage(Component.text(
                    "Unknown stage: " + args[1] + ". Available: " + STAGE_NAMES, NamedTextColor.RED));
            return;
        }
        Guild guild = Guild.fromString(args[2]).orElse(null);
        if (guild == null) {
            sender.sendMessage(Component.text(
                    "Unknown guild: " + args[2] + ". Available: " + GUILD_NAMES, NamedTextColor.RED));
            return;
        }
        locationConfig.getStageLocation(stage, guild).ifPresentOrElse(
                loc -> {
                    admin.teleport(loc);
                    sender.sendMessage(Component.text(
                            "Teleported to " + stage.getDisplayName() + " spawn for " + guild.getDisplayName() + ".",
                            NamedTextColor.GREEN));
                },
                () -> sender.sendMessage(Component.text(
                        "No spawn configured for " + stage.getDisplayName() + " / " + guild.getDisplayName() + ".",
                        NamedTextColor.RED)));
    }

    private void handleSetAdmin(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /titeeni setadmin <player>", NamedTextColor.RED));
            return;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        eventManager.setAdmin(target.getUniqueId());
        sender.sendMessage(Component.text(
                target.getName() + " is now the event admin (receives overall scoring).",
                NamedTextColor.GREEN));
    }

    private void handleTotalScore(CommandSender sender) {
        Map<UUID, Integer> totals = eventManager.getTotalScores();

        // Also include in-progress game scores
        Map<UUID, Integer> display = new HashMap<>(totals);
        if (manager.getCurrentGame() != null) {
            manager.getCurrentGame().getScores()
                    .forEach((uuid, pts) -> display.merge(uuid, pts, Integer::sum));
        }

        if (display.isEmpty()) {
            sender.sendMessage(Component.text("No scores recorded yet.", NamedTextColor.YELLOW));
            return;
        }

        EventStage stage = eventManager.getCurrentStage();
        String stageLabel = stage != null ? stage.getDisplayName() : "not started";
        sender.sendMessage(Component.text(
                "=== Event Total Scores (after " + stageLabel + ") ===", NamedTextColor.GOLD));
        display.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    Player p = plugin.getServer().getPlayer(entry.getKey());
                    String name = p != null ? p.getName() : entry.getKey().toString();
                    String guildLabel = p != null
                            ? guildRegistry.getGuild(p)
                                .map(g -> " (" + g.getDisplayName() + ")")
                                .orElse("")
                            : "";
                    sender.sendMessage(Component.text(
                            name + guildLabel + ": " + entry.getValue() + " pts",
                            NamedTextColor.YELLOW));
                });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Titeeni Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/titeeni advance [stage]                - Advance to next (or specific) stage", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni end                            - End event early, reset for next run", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni score                          - Show current game scores", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni totalscore                     - Show accumulated event scores", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni board <player>                 - Board a player on the bus", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni assign <guild> <player>        - Assign player to guild", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni unassign <player>              - Remove player from all guilds", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setlocation <stage> [guild]    - Set spawn for stage (all guilds if omitted)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setbus                         - Set bus location", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setkoth <y>                    - Set KOTH hill Y level", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setturkugate                   - Set Turku boat gate Z (your current Z)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni goto <stage> <guild>           - Teleport to a guild's spawn in a stage", NamedTextColor.YELLOW));
        for (MinigameCommandHandler handler : handlers) {
            for (String line : handler.getHelpLines()) {
                sender.sendMessage(Component.text(line, NamedTextColor.YELLOW));
            }
        }
    }

    // =========================================================================
    // TabCompleter
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("titeeni.admin")) return List.of();

        if (args.length == 1) {
            List<String> all = Stream.concat(
                    BASE_SUB_COMMANDS.stream(),
                    handlers.stream().flatMap(h -> h.getSubCommandNames().stream())
            ).filter(s -> s.startsWith(args[0].toLowerCase())).toList();
            return all;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("advance") && args.length == 2) {
            return STAGE_NAMES.stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }

        if (sub.equals("assign")) {
            if (args.length == 2) {
                return GUILD_NAMES.stream()
                        .filter(g -> g.startsWith(args[1].toLowerCase())).toList();
            }
            if (args.length == 3) {
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
        }

        if (sub.equals("unassign") && args.length == 2) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (sub.equals("setlocation")) {
            if (args.length == 2) {
                return STAGE_NAMES.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
            }
            if (args.length == 3) {
                return GUILD_NAMES.stream()
                        .filter(g -> g.startsWith(args[2].toLowerCase())).toList();
            }
        }

        if (sub.equals("goto")) {
            if (args.length == 2) {
                return STAGE_NAMES.stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
            }
            if (args.length == 3) {
                return GUILD_NAMES.stream()
                        .filter(g -> g.startsWith(args[2].toLowerCase())).toList();
            }
        }

        if (sub.equals("board")) {
            if (args.length == 2) {
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        // Game-specific sub-command completion
        for (MinigameCommandHandler handler : handlers) {
            if (handler.getSubCommandNames().contains(sub)) {
                return handler.tabComplete(sender, args);
            }
        }

        return List.of();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

}
