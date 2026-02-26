package me.guild.titeenit.manager;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.Minigame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Owns the currently active minigame and forwards server events to it.
 *
 * Only one game can be active at a time. Calling startGame() while a game
 * is already running will stop the old game first.
 */
public class MinigameManager implements Listener {

    private final TiteeniPlugin plugin;
    private Minigame currentGame;

    public MinigameManager(TiteeniPlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void startGame(Minigame game) {
        if (currentGame != null) {
            currentGame.stop();
        }
        currentGame = game;
        game.start();
    }

    public void stopGame() {
        if (currentGame != null) {
            currentGame.stop();
            currentGame = null;
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (currentGame != null) {
            currentGame.onPlayerQuit(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        // Handle player join
    }

    public Minigame getCurrentGame() {
        return currentGame;
    }

    /** Called by a game when it ends itself (e.g. last player quits). */
    public void onGameSelfTerminated() {
        currentGame = null;
    }
}
