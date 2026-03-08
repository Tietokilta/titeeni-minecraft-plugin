package me.guild.titeenit.minigame.copythebuild;

import me.guild.titeenit.TiteeniPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class CopyTheBuildListener implements Listener {

    private final TiteeniPlugin plugin;
    private final CopyTheBuildGame game;

    public CopyTheBuildListener(TiteeniPlugin plugin, CopyTheBuildGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player)) return;

        Location loc = event.getBlock().getLocation();
        if (game.isInModelArea(loc) || game.isInAnyPreviewArea(loc)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You cannot place blocks here!", NamedTextColor.RED));
            return;
        }
        if (!game.isInPlayerBuildArea(player, loc)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You can only place blocks in your own build area!", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!game.isParticipant(player)) return;

        Location loc = event.getBlock().getLocation();
        if (game.isInModelArea(loc) || game.isInAnyPreviewArea(loc)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You cannot break blocks here!", NamedTextColor.RED));
            return;
        }
        if (!game.isInPlayerBuildArea(player, loc)) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You can only break blocks in your own build area!", NamedTextColor.RED));
            return;
        }
        // In own build area: suppress drop and return the block to inventory
        event.setDropItems(false);
        ItemStack drop = new ItemStack(event.getBlock().getType(), 1);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(drop);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
