package me.guild.titeenit.minigame.jallupollo;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.MinigameState;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Event handlers for Jallupöllö
 *
 * Registered on game start, unregistered on game stop.
 *
 * --- Damage ---
 * All PvP is suppressed except a seeker hitting the hider (which triggers the
 * role-transfer mechanic).  Environmental damage is also cancelled so only the
 * designed game mechanic ends the hider's turn.
 */
public class JallupolloListener implements Listener {

    private final TiteeniPlugin plugin;
    private final JallupolloGame game;

    public JallupolloListener(TiteeniPlugin plugin, JallupolloGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    // -------------------------------------------------------------------------
    // Seeker catches the hider
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged)) return;
        if (!game.isParticipant(damaged)) return;

        event.setCancelled(true);   // suppress all damage for participants

        if (game.getState() != MinigameState.RUNNING) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null || !game.isParticipant(attacker)) return;
        if (!damaged.equals(game.getHider())) return;
        if (attacker.equals(game.getHider())) return;
        if (game.isStunned(attacker)) return;

        game.onHiderHit(attacker);
    }

    /** Cancel environmental damage (fall, fire, drowning…) for all participants. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!game.isParticipant(player)) return;
        if (event instanceof EntityDamageByEntityEvent) return; // handled above
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Prevent the hider from losing the Jaloviina
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (game.isJaloviina(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    /** Cancel the consumption of the Jaloviina so it can be used repeatedly. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (game.isJaloviina(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Block the potion from being moved into an external container or by a stunned player. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!game.isParticipant(player)) return;

        ItemStack cursor  = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (!game.isJaloviina(cursor) && !game.isJaloviina(current)) return;

        // Stunned players cannot touch the potion at all
        if (game.isStunned(player)) {
            event.setCancelled(true);
            return;
        }

        // Allow rearranging within the player's own inventory
        if (event.getClickedInventory() != null
                && (event.getClickedInventory().equals(player.getInventory())
                || event.getClickedInventory().getType() == InventoryType.PLAYER)) {
            return;
        }
        event.setCancelled(true);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Arrow a && a.getShooter() instanceof Player p) return p;
        return null;
    }
}
