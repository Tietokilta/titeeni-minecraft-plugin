package me.guild.titeenit.minigame.tradeup;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.MinigameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Trade Up (Vaasa): each player starts with one Book and trades up a
 * chain of items using pre-configured command-block villagers. At the
 * end of 5 minutes, the score is the player's position in the chain
 * (0 = Book … 9 = Emerald). Higher is better.
 *
 * Chain:
 *   0  Book
 *   1  Diamond
 *   2  Lantern
 *   3  2× Oak Log
 *   4  Gold Block
 *   5  Iron Sword
 *   6  Cooked Porkchop
 *   7  Fishing Rod
 *   8  Diamond Axe
 *   9  Emerald  ← player is auto-boarded on the bus when holding this
 *
 * Score encoding: position * 1_000_000 + time_bonus
 *   where time_bonus = timerMs − ms_elapsed_when_position_achieved  (≥ 0)
 * This lets chain position dominate while time breaks ties within a position.
 */
public class TradeUpGame extends Minigame {

    private record ChainEntry(Material material, int minAmount, String displayName) {}

    private static final List<ChainEntry> CHAIN = List.of(
            new ChainEntry(Material.BOOK,            1, "Book"),
            new ChainEntry(Material.DIAMOND,         1, "Diamond"),
            new ChainEntry(Material.LANTERN,         1, "Lantern"),
            new ChainEntry(Material.OAK_LOG,         2, "2× Oak Log"),
            new ChainEntry(Material.GOLD_BLOCK,      1, "Gold Block"),
            new ChainEntry(Material.IRON_SWORD,      1, "Iron Sword"),
            new ChainEntry(Material.COOKED_PORKCHOP, 1, "Cooked Porkchop"),
            new ChainEntry(Material.FISHING_ROD,     1, "Fishing Rod"),
            new ChainEntry(Material.DIAMOND_AXE,     1, "Diamond Axe"),
            new ChainEntry(Material.EMERALD,         1, "Emerald")
    );

    private final Map<UUID, Integer> scores             = new HashMap<>();
    private final Map<UUID, Integer> lastKnownPosition  = new HashMap<>();
    private final Map<UUID, Long>    positionAchievedAt = new HashMap<>();
    private long startTimeMs;
    private BukkitTask tickTask;
    private final TradeUpListener listener = new TradeUpListener(this);

    public TradeUpGame(TiteeniPlugin plugin, List<Player> players) {
        super(plugin, players);
        for (Player p : players) scores.put(p.getUniqueId(), 0);
    }

    @Override
    public String getDisplayName() { return "Trade Up"; }

    @Override
    public void start() {
        state = MinigameState.RUNNING;
        startTimeMs = System.currentTimeMillis();
        saveAndClearInventories();

        for (Player p : getOnlinePlayers()) {
            p.getInventory().addItem(new ItemStack(Material.BOOK, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 4, false, false));
        }

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // Check inventories every second: update position tracking + auto-board on emerald
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkInventories, 20L, 20L);

        broadcast(Component.text(
                "Trade Up has started! Trade your book up the chain as far as you can!",
                NamedTextColor.GOLD));
    }

    @Override
    public void stop() {
        if (state == MinigameState.ENDED) return;
        state = MinigameState.ENDED;

        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        org.bukkit.event.HandlerList.unregisterAll(listener);

        long now = System.currentTimeMillis();
        int timerMs = getTimerSeconds() * 1000;

        for (Player p : getOnlinePlayers()) {
            UUID uid = p.getUniqueId();

            // Final inventory scan in case tick hasn't caught the last trade
            int index = highestChainIndex(p);
            if (index > lastKnownPosition.getOrDefault(uid, -1)) {
                lastKnownPosition.put(uid, index);
                positionAchievedAt.put(uid, now);
            }

            int pos = Math.max(lastKnownPosition.getOrDefault(uid, 0), 0);
            long achievedAt = positionAchievedAt.getOrDefault(uid, now);
            int elapsed = (int) Math.min(timerMs, achievedAt - startTimeMs);
            int timeBonus = Math.max(0, timerMs - elapsed);
            scores.put(uid, pos * 1_000_000 + timeBonus);

            p.sendMessage(Component.text(
                    "You finished with: " + CHAIN.get(pos).displayName(), NamedTextColor.AQUA));
            p.removePotionEffect(PotionEffectType.SPEED);
        }

        restoreInventories();
        broadcastScores();
        broadcast(Component.text("Trade Up has ended!", NamedTextColor.GOLD));
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
    public Map<UUID, Integer> getScores() {
        return Collections.unmodifiableMap(scores);
    }

    @Override
    protected void broadcastScores() {
        showTopPerformersTitle();
        broadcastAll(Component.text("=== " + getDisplayName() + " — Final Scores ===", NamedTextColor.GOLD));
        getScores().entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    int pos = entry.getValue() / 1_000_000;
                    String item = (pos >= 0 && pos < CHAIN.size()) ? CHAIN.get(pos).displayName() : "nothing";
                    broadcastAll(Component.text(displayName(entry.getKey()) + ": " + item, NamedTextColor.YELLOW));
                });
    }

    // -------------------------------------------------------------------------
    // Inventory scanning
    // -------------------------------------------------------------------------

    /** Called every second; updates position tracking and auto-boards emerald holders. */
    private void checkInventories() {
        if (state != MinigameState.RUNNING) return;
        long now = System.currentTimeMillis();
        for (Player p : getOnlinePlayers()) {
            int index = highestChainIndex(p);
            if (index < 0) continue;
            UUID uid = p.getUniqueId();
            if (index > lastKnownPosition.getOrDefault(uid, -1)) {
                lastKnownPosition.put(uid, index);
                positionAchievedAt.put(uid, now);
                if (index == CHAIN.size() - 1) {
                    // Player reached the emerald — board them on the bus
                    plugin.getEventManager().boardBus(p);
                }
            }
        }
    }

    /** Returns the highest chain index the player currently holds, or -1 if none. */
    private int highestChainIndex(Player p) {
        for (int i = CHAIN.size() - 1; i >= 0; i--) {
            ChainEntry entry = CHAIN.get(i);
            if (countInInventory(p, entry.material()) >= entry.minAmount()) {
                return i;
            }
        }
        return -1;
    }

    private int countInInventory(Player p, Material material) {
        int total = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }
}
