package me.guild.titeenit.minigame.findthebutton;

import me.guild.titeenit.TiteeniPlugin;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class FindTheButtonListener implements Listener {

    private final TiteeniPlugin plugin;
    private final FindTheButtonGame game;

    public FindTheButtonListener(TiteeniPlugin plugin, FindTheButtonGame game) {
        this.plugin = plugin;
        this.game   = game;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // ignore off-hand duplicate events
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !Tag.BUTTONS.isTagged(block.getType())) return;

        // This event fires before vanilla activates the block, so isPowered() is false
        // on the first click and true on any subsequent clicks while it's still pressed.
        if (block.getBlockData() instanceof Powerable p && p.isPowered()) return;

        Player player = event.getPlayer();
        if (!game.isParticipant(player)) return;

        // Don't cancel — let vanilla run the press animation naturally.
        game.onButtonClicked(player, block);
    }
}
