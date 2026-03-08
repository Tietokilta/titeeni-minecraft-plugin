package me.guild.titeenit;

import me.guild.titeenit.command.TiteeniCommand;
import me.guild.titeenit.event.EventLocationConfig;
import me.guild.titeenit.event.EventManager;
import me.guild.titeenit.event.GuildRegistry;
import me.guild.titeenit.manager.MinigameManager;
import org.bukkit.plugin.java.JavaPlugin;

public class TiteeniPlugin extends JavaPlugin {

    private MinigameManager minigameManager;
    private GuildRegistry guildRegistry;
    private EventLocationConfig eventLocationConfig;
    private EventManager eventManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        minigameManager = new MinigameManager(this);
        guildRegistry = new GuildRegistry(this);
        eventLocationConfig = new EventLocationConfig(this);
        eventManager = new EventManager(this, minigameManager, guildRegistry, eventLocationConfig);

        var cmd = getCommand("titeeni");
        var handler = new TiteeniCommand(this, minigameManager, guildRegistry,
                eventLocationConfig, eventManager);
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

    public MinigameManager getMinigameManager() { return minigameManager; }
    public GuildRegistry getGuildRegistry() { return guildRegistry; }
    public EventLocationConfig getEventLocationConfig() { return eventLocationConfig; }
    public EventManager getEventManager() { return eventManager; }
}
