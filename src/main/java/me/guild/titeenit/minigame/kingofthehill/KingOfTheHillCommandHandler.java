package me.guild.titeenit.minigame.kingofthehill;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.command.MinigameCommandHandler;
import me.guild.titeenit.manager.MinigameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class KingOfTheHillCommandHandler implements MinigameCommandHandler {

    private final TiteeniPlugin plugin;
    private final MinigameManager manager;

    public KingOfTheHillCommandHandler(TiteeniPlugin plugin, MinigameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public List<String> getGameNames() { return List.of("kingofthehill", "koth"); }

    @Override
    public void handleStart(CommandSender sender, List<Player> online, String[] args) {
        if (online.size() < 2) {
            sender.sendMessage(Component.text("At least 2 players must be online.", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /titeeni start kingofthehill <y>", NamedTextColor.RED));
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

    @Override public List<String> getSubCommandNames() { return List.of(); }
    @Override public void handleSubCommand(CommandSender sender, String[] args) {}
    @Override public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
    @Override public List<String> getHelpLines() { return List.of(); }
}
