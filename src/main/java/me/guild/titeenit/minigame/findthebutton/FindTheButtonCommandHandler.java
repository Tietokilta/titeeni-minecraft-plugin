package me.guild.titeenit.minigame.findthebutton;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.command.MinigameCommandHandler;
import me.guild.titeenit.manager.MinigameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class FindTheButtonCommandHandler implements MinigameCommandHandler {

    private final TiteeniPlugin plugin;
    private final MinigameManager manager;

    public FindTheButtonCommandHandler(TiteeniPlugin plugin, MinigameManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public List<String> getGameNames() { return List.of("findthebutton", "ftb"); }

    @Override
    public void handleStart(CommandSender sender, List<Player> online, String[] args) {
        manager.startGame(new FindTheButtonGame(plugin, online));
        sender.sendMessage(Component.text(
                "Starting Find the Button with " + online.size() + " players!", NamedTextColor.GREEN));
    }

    @Override public List<String> getSubCommandNames() { return List.of(); }
    @Override public void handleSubCommand(CommandSender sender, String[] args) {}
    @Override public List<String> tabComplete(CommandSender sender, String[] args) { return List.of(); }
    @Override public List<String> getHelpLines() { return List.of(); }
}
