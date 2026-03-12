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
 *   9  Emerald
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

    private final Map<UUID, Integer> scores = new HashMap<>();

    public TradeUpGame(TiteeniPlugin plugin, List<Player> players) {
        super(plugin, players);
        for (Player p : players) scores.put(p.getUniqueId(), 0);
    }

    @Override
    public String getDisplayName() { return "Trade Up"; }

    @Override
    public void start() {
        state = MinigameState.RUNNING;
        saveAndClearInventories();

        for (Player p : getOnlinePlayers()) {
            p.getInventory().addItem(new ItemStack(Material.BOOK, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 2, false, false));
        }

        broadcast(Component.text(
                "Trade Up has started! Trade your book up the chain as far as you can!",
                NamedTextColor.GOLD));
    }

    @Override
    public void stop() {
        if (state == MinigameState.ENDED) return;
        state = MinigameState.ENDED;

        // Compute scores from current inventory before restoring
        for (Player p : getOnlinePlayers()) {
            int index = highestChainIndex(p);
            scores.put(p.getUniqueId(), Math.max(index, 0));
            String itemName = index >= 0 ? CHAIN.get(index).displayName() : "nothing";
            p.sendMessage(Component.text(
                    "You finished with: " + itemName, NamedTextColor.AQUA));
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

    // -------------------------------------------------------------------------
    // Scoring
    // -------------------------------------------------------------------------

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
