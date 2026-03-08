package me.guild.titeenit.minigame.jallupollo;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.command.MinigameCommandHandler;
import me.guild.titeenit.manager.MinigameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class JallupolloCommandHandler implements MinigameCommandHandler {

    private final TiteeniPlugin plugin;
    private final MinigameManager manager;

    public JallupolloCommandHandler(TiteeniPlugin plugin, MinigameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public List<String> getGameNames() { return List.of("jallupollo", "jp"); }

    @Override
    public void handleStart(CommandSender sender, List<Player> online, String[] args) {
        if (online.size() < 2) {
            sender.sendMessage(Component.text("At least 2 players must be online.", NamedTextColor.RED));
            return;
        }
        manager.startGame(new JallupolloGame(plugin, online));
        sender.sendMessage(Component.text(
                "Starting Jallupöllö with " + online.size() + " players!", NamedTextColor.GREEN));
    }

    @Override
    public List<String> getSubCommandNames() { return List.of("sethider"); }

    @Override
    public void handleSubCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /titeeni sethider <player>", NamedTextColor.RED));
            return;
        }
        if (!(manager.getCurrentGame() instanceof JallupolloGame game)) {
            sender.sendMessage(Component.text("No Jallupöllö game is currently running.", NamedTextColor.RED));
            return;
        }
        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        if (!game.isParticipant(target)) {
            sender.sendMessage(Component.text(args[1] + " is not in the game.", NamedTextColor.RED));
            return;
        }
        game.chooseNewHider(target);
        sender.sendMessage(Component.text("Set " + target.getName() + " as the hider.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        // sethider <player>
        if (args[0].equalsIgnoreCase("sethider") && args.length == 2) {
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    @Override
    public List<String> getHelpLines() {
        return List.of("/titeeni sethider <player>  - Manually set the hider (jallupöllö)");
    }
}
