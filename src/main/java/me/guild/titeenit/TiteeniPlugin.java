package me.guild.titeenit;

import me.guild.titeenit.command.TiteeniCommand;
import me.guild.titeenit.manager.MinigameManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TiteeniPlugin extends JavaPlugin {

    private MinigameManager minigameManager;

    @Override
    public void onEnable() {
        minigameManager = new MinigameManager(this);

        var cmd = getCommand("titeeni");
        var handler = new TiteeniCommand(this, minigameManager);
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);

        getLogger().info("TiteeniPlugin enabled.");
    }

    @Override
    public void onDisable() {
        if (minigameManager.getCurrentGame() != null) {
            minigameManager.getCurrentGame().stop();
        }
        getLogger().info("TiteeniPlugin disabled.");
    }

    public MinigameManager getMinigameManager() {
        return minigameManager;
    }
}
