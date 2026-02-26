package me.guild.titeenit.minigame.findthebutton;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.MinigameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;

public class FindTheButtonGame extends Minigame {

    private final Map<UUID, Integer> scores = new HashMap<>();
    /** All buttons that have been clicked — restored when the game ends. */
    private final Map<Location, BlockState> removedButtons = new HashMap<>();

    private final FindTheButtonListener listener;
    private BukkitTask timerTask;
    private int secondsLeft;

    public FindTheButtonGame(TiteeniPlugin plugin, List<Player> players) {
        super(plugin, players);
        this.listener = new FindTheButtonListener(plugin, this);
        for (Player p : players) scores.put(p.getUniqueId(), 0);
        this.secondsLeft = FindTheButtonConfig.GAME_DURATION_SECONDS;
    }

    @Override
    public String getDisplayName() { return "Find the Button"; }

    @Override
    public void start() {
        state = MinigameState.RUNNING;
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        timerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            secondsLeft--;
            if (secondsLeft == 60 || secondsLeft == 30 || secondsLeft == 10) {
                broadcast(Component.text(secondsLeft + " seconds remaining!", NamedTextColor.YELLOW));
            }
            if (secondsLeft <= 0) {
                plugin.getMinigameManager().onGameSelfTerminated();
                stop();
            }
        }, 20L, 20L);

        broadcast(Component.text("Find the Button has started! You have "
                + FindTheButtonConfig.GAME_DURATION_SECONDS / 60 + " minutes — find and click buttons to score!",
                NamedTextColor.GOLD));
    }

    @Override
    public void stop() {
        state = MinigameState.ENDED;
        if (timerTask != null) { timerTask.cancel(); timerTask = null; }
        org.bukkit.event.HandlerList.unregisterAll(listener);
        restoreButtons();
        broadcastScores();
        broadcast(Component.text("Find the Button has ended!", NamedTextColor.GOLD));
    }

    @Override
    public void onPlayerQuit(Player player) {
        players.remove(player);
        if (getOnlinePlayers().isEmpty()) {
            plugin.getMinigameManager().onGameSelfTerminated();
            stop();
        }
    }

    @Override
    public Map<UUID, Integer> getScores() { return Collections.unmodifiableMap(scores); }

    // =========================================================================
    // Called by listener
    // =========================================================================

    public void onButtonClicked(Player player, org.bukkit.block.Block block) {
        if (state != MinigameState.RUNNING) return;

        Location loc = block.getLocation();
        if (removedButtons.containsKey(loc)) return; // already pending removal

        // Save the unpowered state now (event fires before vanilla activates the block)
        removedButtons.put(loc, block.getState());

        // Remove after 15 ticks — vanilla presses for 20+ ticks so the button
        // disappears while still visually down, before the auto-unpress fires.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (state != MinigameState.RUNNING) return; // game ended; restoreButtons() handles it
            block.setType(Material.AIR);
            scores.merge(player.getUniqueId(), FindTheButtonConfig.POINTS_PER_BUTTON, Integer::sum);
            broadcast(Component.text(player.getName(), NamedTextColor.AQUA)
                    .append(Component.text(" found a button! (", NamedTextColor.GREEN))
                    .append(Component.text(scores.get(player.getUniqueId()) + " pts", NamedTextColor.YELLOW))
                    .append(Component.text(")", NamedTextColor.GREEN)));
        }, 15L);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void restoreButtons() {
        for (BlockState saved : removedButtons.values()) {
            saved.update(true, false);
        }
        removedButtons.clear();
    }
}
