package me.guild.titeenit.minigame.shootinggallery;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.MinigameState;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

public class ShootingGalleryListener implements Listener {

    private final TiteeniPlugin plugin;
    private final ShootingGalleryGame game;

    public ShootingGalleryListener(TiteeniPlugin plugin, ShootingGalleryGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        Block hitBlock = event.getHitBlock();
        if (hitBlock == null || hitBlock.getType() != Material.TARGET) return;
        if (game.getState() != MinigameState.RUNNING) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;
        if (!game.isParticipant(shooter)) return;

        event.setCancelled(true);
        game.onTargetHit(shooter, hitBlock);
    }
}
