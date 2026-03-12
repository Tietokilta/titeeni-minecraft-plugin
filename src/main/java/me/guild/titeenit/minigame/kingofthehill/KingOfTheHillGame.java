package me.guild.titeenit.minigame.kingofthehill;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.MinigameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KingOfTheHillGame extends Minigame {

    private final Map<UUID, Integer> scores = new HashMap<>();
    private final NamespacedKey knockbackKey;
    private final KingOfTheHillListener listener;
    private final double hillTopY;
    public KingOfTheHillGame(TiteeniPlugin plugin, List<Player> players, double hillTopY) {
        super(plugin, players);
        this.knockbackKey = new NamespacedKey(plugin, "koth_knockback");
        this.listener = new KingOfTheHillListener(plugin, this);
        this.hillTopY = hillTopY;
        for (Player p : players) scores.put(p.getUniqueId(), 0);
    }

    @Override
    public String getDisplayName() { return "King of the Hill"; }

    @Override
    public int getTimerSeconds() { return 240; }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override
    public void start() {
        state = MinigameState.RUNNING;
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        for (Player p : getOnlinePlayers()) applyKnockback(p);
        broadcast(Component.text(
                "King of the Hill has started! Climb to the top and hold your position to score!",
                NamedTextColor.GOLD));
    }

    @Override
    public void stop() {
        state = MinigameState.ENDED;
        HandlerList.unregisterAll(listener);
        for (Player p : getOnlinePlayers()) removeKnockback(p);
        broadcastScores();
        broadcast(Component.text("King of the Hill has ended!", NamedTextColor.GOLD));
    }

    @Override
    public void onPlayerQuit(Player player) {
        removeKnockback(player);
        players.remove(player);
        if (getOnlinePlayers().isEmpty()) {
            plugin.getMinigameManager().onGameSelfTerminated();
            stop();
        }
    }

    @Override
    public Map<UUID, Integer> getScores() { return Collections.unmodifiableMap(scores); }

    // =========================================================================
    // Timer tick (scoring + action bar, driven by MinigameManager every second)
    // =========================================================================

    @Override
    public void onTimerTick(int secondsLeft) {
        if (state != MinigameState.RUNNING) return;
        List<Player> online = getOnlinePlayers();
        if (online.isEmpty()) return;

        List<Player> atPeak = online.stream()
                .filter(p -> p.getLocation().getY() >= hillTopY)
                .toList();

        for (Player p : atPeak) {
            scores.merge(p.getUniqueId(), KingOfTheHillConfig.POINTS_PER_SECOND, Integer::sum);
        }

        sendActionBar(online, atPeak, secondsLeft);
    }

    private void sendActionBar(List<Player> online, List<Player> atPeak, int secondsLeft) {
        Set<UUID> atPeakUuids = new HashSet<>();
        for (Player p : atPeak) atPeakUuids.add(p.getUniqueId());

        // Top-N leaderboard from online players, sorted by score descending
        List<Map.Entry<UUID, Integer>> top = scores.entrySet().stream()
                .filter(e -> online.stream().anyMatch(p -> p.getUniqueId().equals(e.getKey())))
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(KingOfTheHillConfig.ACTION_BAR_TOP_ENTRIES)
                .toList();

        // Build leaderboard prefix component
        Component leaderboard = Component.empty();
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<UUID, Integer> entry = top.get(i);
            Player p = plugin.getServer().getPlayer(entry.getKey());
            String name = p != null ? p.getName() : "?";
            boolean isAtPeak = atPeakUuids.contains(entry.getKey());

            if (i > 0) leaderboard = leaderboard.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            leaderboard = leaderboard
                    .append(Component.text((i + 1) + ". ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(name, isAtPeak ? NamedTextColor.GOLD : NamedTextColor.GRAY))
                    .append(Component.text(" " + entry.getValue(), NamedTextColor.WHITE));
        }

        Component separator = Component.text("  |  ", NamedTextColor.DARK_GRAY);

        for (Player viewer : online) {
            int viewerScore = scores.getOrDefault(viewer.getUniqueId(), 0);
            boolean viewerAtPeak = atPeakUuids.contains(viewer.getUniqueId());

            Component viewerPart;
            if (viewerAtPeak) {
                viewerPart = Component.text(
                        "YOU: " + viewerScore + " +" + KingOfTheHillConfig.POINTS_PER_SECOND + "/s",
                        NamedTextColor.GREEN, TextDecoration.BOLD);
            } else {
                double distToHill = hillTopY - viewer.getLocation().getY();
                viewerPart = Component.text(
                        String.format("You: %d  +%.1fY to hill", viewerScore, distToHill),
                        NamedTextColor.YELLOW);
            }

            viewer.sendActionBar(leaderboard.append(separator).append(viewerPart)
                    .append(Component.text("  " + formatRemaining(secondsLeft), NamedTextColor.GRAY)));
        }
    }

    // =========================================================================
    // Knockback helpers
    // =========================================================================

    private void applyKnockback(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.ATTACK_KNOCKBACK);
        if (attr == null) return;
        attr.removeModifier(knockbackKey);   // idempotent cleanup before adding
        attr.addModifier(new AttributeModifier(knockbackKey, KingOfTheHillConfig.KNOCKBACK_AMOUNT, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void removeKnockback(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.ATTACK_KNOCKBACK);
        if (attr != null) attr.removeModifier(knockbackKey);
    }
}
