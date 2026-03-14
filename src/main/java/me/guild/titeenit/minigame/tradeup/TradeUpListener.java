package me.guild.titeenit.minigame.tradeup;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

class TradeUpListener implements Listener {

    private final TradeUpGame game;

    TradeUpListener(TradeUpGame game) {
        this.game = game;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (game.getPlayers().contains(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
