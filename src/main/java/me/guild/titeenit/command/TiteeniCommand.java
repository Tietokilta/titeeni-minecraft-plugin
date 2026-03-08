package me.guild.titeenit.command;

import me.guild.titeenit.TiteeniPlugin;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * /titeeni command handler — routes to per-minigame MinigameCommandHandler instances.
 *
 * To add a new minigame:
 *   1. Create a package under minigame/, extend Minigame.
 *   2. Create a *CommandHandler class in that package implementing MinigameCommandHandler.
 *   3. Register it in the handlers list in this constructor.
 */
public class TiteeniCommand implements CommandExecutor, TabCompleter {

    private static final List<String> BASE_SUB_COMMANDS = List.of("start", "stop", "score");

    private final TiteeniPlugin plugin;
    private final MinigameManager manager;
    private final List<MinigameCommandHandler> handlers;

    public TiteeniCommand(TiteeniPlugin plugin, MinigameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.handlers = List.of(
                new JallupolloCommandHandler(plugin, manager),
                new FindTheButtonCommandHandler(plugin, manager),
                new KingOfTheHillCommandHandler(plugin, manager),
                new CopyTheBuildCommandHandler(plugin, manager)
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
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text(
                            "Usage: /titeeni start <" + primaryGameNamesJoined() + ">",
                            NamedTextColor.RED));
                    return true;
                }
                handleStart(sender, args);
            }
            case "stop"  -> handleStop(sender);
            case "score" -> handleScore(sender);
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
    // Sub-command handlers
    // =========================================================================

    private void handleStart(CommandSender sender, String[] args) {
        if (manager.getCurrentGame() != null) {
            sender.sendMessage(Component.text(
                    "A game is already running. Use /titeeni stop first.", NamedTextColor.RED));
            return;
        }

        List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());
        String gameType = args[1].toLowerCase();

        for (MinigameCommandHandler handler : handlers) {
            if (handler.getGameNames().contains(gameType)) {
                handler.handleStart(sender, online, args);
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Titeeni Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/titeeni start <game>  - Start a minigame", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni stop          - Stop the current game", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni score         - Show current scores", NamedTextColor.YELLOW));
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
            // Delegate further args to the matching game handler
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
