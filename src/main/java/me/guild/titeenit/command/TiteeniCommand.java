package me.guild.titeenit.command;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.manager.MinigameManager;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.hidenseek.HideAndSeekGame;
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

    private static final List<String> GAME_TYPES = List.of("hidenseek");
    private static final List<String> SUB_COMMANDS = List.of("start", "stop", "score", "sethider");

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
                handleStart(sender, args[1]);
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
            default -> sendHelp(sender);
        }

        return true;
    }

    // =========================================================================
    // Sub-command handlers
    // =========================================================================

    private void handleStart(CommandSender sender, String gameType) {
        if (manager.getCurrentGame() != null) {
            sender.sendMessage(Component.text(
                    "A game is already running. Use /titeeni stop first.", NamedTextColor.RED
            ));
            return;
        }

        List<Player> online = new ArrayList<>(plugin.getServer().getOnlinePlayers());

        switch (gameType.toLowerCase()) {
            case "hidenseek", "hs" -> {
                if (online.size() < 2) {
                    sender.sendMessage(Component.text("At least 2 players must be online.", NamedTextColor.RED));
                    return;
                }
                manager.startGame(new HideAndSeekGame(plugin, online));
                sender.sendMessage(Component.text(
                        "Starting Hide and Seek with " + online.size() + " players!",
                        NamedTextColor.GREEN
                ));
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
        if (!(manager.getCurrentGame() instanceof HideAndSeekGame game)) {
            sender.sendMessage(Component.text("No Hide and Seek game is currently running.", NamedTextColor.RED));
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

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("=== Titeeni Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/titeeni start <game>       - Start a minigame", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni stop               - Stop the current game", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni score              - Show current scores", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/titeeni sethider <player>  - Manually set the hider (hidenseek)", NamedTextColor.YELLOW));
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
                default -> List.of();
            };
        }

        return List.of();
    }
}
