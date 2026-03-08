package me.guild.titeenit.minigame.kingofthehill;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.MinigameState;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Event handlers for King of the Hill.
 *
 * PvP between participants is allowed (knockback only — no death, since damage
 * is real but fall damage is cancelled, so getting knocked off just costs height).
 * All non-PvP damage (fall, fire, drowning, etc.) is cancelled to keep knockback
 * fun rather than fatal.
 */
public class KingOfTheHillListener implements Listener {

    private final TiteeniPlugin plugin;
    private final KingOfTheHillGame game;

    public KingOfTheHillListener(TiteeniPlugin plugin, KingOfTheHillGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    /** Allow PvP between participants; cancel any hit where the damaged entity is a non-participant. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player damaged)) return;
        if (!game.isParticipant(damaged)) return;

        if (game.getState() != MinigameState.RUNNING) {
            event.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(event);
        if (attacker != null && game.isParticipant(attacker)) {
            // Participant-on-participant hit: allow PvP
            return;
        }

        // Non-participant source → cancel
        event.setCancelled(true);
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
    // Helper
    // -------------------------------------------------------------------------

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Arrow a && a.getShooter() instanceof Player p) return p;
        return null;
    }
}
