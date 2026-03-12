package me.guild.titeenit.manager;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.Minigame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns the currently active minigame and forwards server events to it.
 *
 * Also manages the central game timer: counts down from game.getTimerSeconds(),
 * announces at 60 / 30 / 10 seconds remaining, then auto-boards any players
 * who have not yet boarded and stops the game when time is up.
 *
 * Only one game can be active at a time. Calling startGame() while a game
 * is already running will stop the old game first.
 */
public class MinigameManager implements Listener {

    private final TiteeniPlugin plugin;
    private Minigame currentGame;
    private BukkitTask gameTimer;

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
        startTimer(game);
    }

    public void stopGame() {
        cancelTimer();
        if (currentGame != null) {
            currentGame.stop();
            currentGame = null;
        }
    }

    /** Called by a game when it ends itself (e.g. all players finished the race). */
    public void onGameSelfTerminated() {
        cancelTimer();
        currentGame = null;
    }

    public Minigame getCurrentGame() { return currentGame; }

    // -------------------------------------------------------------------------
    // Central timer
    // -------------------------------------------------------------------------

    private void startTimer(Minigame game) {
        int[] secondsLeft = {game.getTimerSeconds()};
        gameTimer = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft[0]--;
            game.onTimerTick(secondsLeft[0]);
            if (secondsLeft[0] == 60 || secondsLeft[0] == 30 || secondsLeft[0] == 10) {
                game.broadcast(Component.text(secondsLeft[0] + " seconds remaining!", NamedTextColor.YELLOW));
            }
            if (secondsLeft[0] <= 0) {
                plugin.getEventManager().autoBoardRemaining();
                stopGame();
            }
        }, 20L, 20L);
    }

    private void cancelTimer() {
        if (gameTimer != null) { gameTimer.cancel(); gameTimer = null; }
    }

    // -------------------------------------------------------------------------
    // Event routing
    // -------------------------------------------------------------------------

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
}
