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
            "start", "stop", "score",
            "assign", "setlocation", "setbus", "setkoth", "advance", "board", "totalscore"
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
                new CopyTheBuildCommandHandler(plugin, manager, guildRegistry)
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
            case "start"      -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text(
                            "Usage: /titeeni start <" + primaryGameNamesJoined() + ">",
                            NamedTextColor.RED));
                    return true;
                }
                handleStart(sender, args);
            }
            case "stop"       -> handleStop(sender);
            case "score"      -> handleScore(sender);
            case "assign"     -> handleAssign(sender, args);
            case "setlocation"-> handleSetLocation(sender, args);
            case "setbus"     -> handleSetBus(sender);
            case "setkoth"    -> handleSetKoth(sender, args);
            case "advance"    -> eventManager.advanceStage(sender);
            case "board"      -> handleBoard(sender, args);
            case "totalscore" -> handleTotalScore(sender);
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
    // Minigame sub-command handlers
    // =========================================================================

    private void handleStart(CommandSender sender, String[] args) {
        if (manager.getCurrentGame() != null) {
            sender.sendMessage(Component.text(
                    "A game is already running. Use /titeeni stop first.", NamedTextColor.RED));
            return;
        }

        List<Player> guildPlayers = guildRegistry.getAssignedOnlinePlayers();
        String gameType = args[1].toLowerCase();

        for (MinigameCommandHandler handler : handlers) {
            if (handler.getGameNames().contains(gameType)) {
                handler.handleStart(sender, guildPlayers, args);
                return;
            }
        }

        sender.sendMessage(Component.text(
                "Unknown game type: " + args[1] + ". Available: " + primaryGameNamesJoined(),
                NamedTextColor.RED));
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

    private void handleSetLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /titeeni setlocation <stage> <guild>", NamedTextColor.RED));
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
        locationConfig.setStageLocation(stage, guild, admin.getLocation());
        sender.sendMessage(Component.text(
                "Set " + stage.getDisplayName() + " spawn for " + guild.getDisplayName()
                        + " to your current location.", NamedTextColor.GREEN));
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

    private void handleBoard(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /titeeni board <player>", NamedTextColor.RED));
            return;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        eventManager.boardBus(target);
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
        sender.sendMessage(Component.text("/titeeni start <game>                   - Start a minigame", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni stop                           - Stop the current game", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni score                          - Show current game scores", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni assign <guild> <player>        - Assign player to guild", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setlocation <stage> <guild>    - Set guild spawn for stage", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setbus                         - Set bus location", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni setkoth <y>                    - Set KOTH hill Y level", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni advance                        - Advance to next stage", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni board <player>                 - Board a player on the bus", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni totalscore                     - Show accumulated event scores", NamedTextColor.YELLOW));
        for (MinigameCommandHandler handler : handlers) {
            for (String line : handler.getHelpLines()) {
                sender.sendMessage(Component.text(line, NamedTextColor.YELLOW));
            }
        }
        sender.sendMessage(Component.text("Available games: " + primaryGameNamesJoined(), NamedTextColor.GRAY));
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

        if (sub.equals("start")) {
            if (args.length == 2) {
                return handlers.stream()
                        .flatMap(h -> h.getGameNames().stream())
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (args.length >= 3) {
                String gameType = args[1].toLowerCase();
                for (MinigameCommandHandler handler : handlers) {
                    if (handler.getGameNames().contains(gameType)) {
                        return handler.tabComplete(sender, args);
                    }
                }
            }
            return List.of();
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

    private String primaryGameNamesJoined() {
        return handlers.stream()
                .map(h -> h.getGameNames().get(0))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
