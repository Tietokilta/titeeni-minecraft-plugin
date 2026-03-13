package me.guild.titeenit.minigame.shootinggallery;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.event.EventStage;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.MinigameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShootingGalleryGame extends Minigame {

    private final Map<UUID, Integer>       scores          = new HashMap<>();
    private final Map<Location, BlockState> removedBlocks  = new HashMap<>();
    private final List<UUID>               spawnedEntities = new ArrayList<>();
    private final Set<Location>            hitTargets      = new HashSet<>();
    private final Map<Guild, Location>     zombieSpawns;

    private final ShootingGalleryListener listener;

    public ShootingGalleryGame(TiteeniPlugin plugin, List<Player> players, Map<Guild, Location> zombieSpawns) {
        super(plugin, players);
        this.zombieSpawns = zombieSpawns;
        this.listener = new ShootingGalleryListener(plugin, this);
        for (Player p : players) scores.put(p.getUniqueId(), 0);
    }

    @Override
    public String getDisplayName() { return "Toripoliisi battle"; }
    @Override public int getTimerSeconds() { return 240; }

    @Override
    public void start() {
        state = MinigameState.RUNNING;
        saveAndClearInventories();

        var locationConfig = plugin.getEventLocationConfig();
        ItemStack bow = new ItemStack(Material.BOW);
        bow.addEnchantment(Enchantment.INFINITY, 1);
        ItemStack arrow = new ItemStack(Material.ARROW, 1);
        for (Player p : getOnlinePlayers()) {
            plugin.getGuildRegistry().getGuild(p).ifPresent(guild ->
                    locationConfig.getStageLocation(EventStage.OULU, guild).ifPresent(loc ->
                            p.setRespawnLocation(loc, true)));
            p.getInventory().addItem(bow.clone());
            p.getInventory().addItem(arrow.clone());
            p.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
            p.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            p.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            p.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        }

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        broadcast(Component.text(
                "Defeat Toripoliisi to score!",
                NamedTextColor.GOLD));
    }

    @Override
    public void stop() {
        state = MinigameState.ENDED;
        org.bukkit.event.HandlerList.unregisterAll(listener);

        for (BlockState saved : removedBlocks.values()) {
            saved.update(true, false);
        }
        removedBlocks.clear();

        for (UUID uid : spawnedEntities) {
            Entity entity = plugin.getServer().getEntity(uid);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        spawnedEntities.clear();

        restoreInventories();
        broadcastScores();
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

    public void onTargetHit(Player shooter, Block targetBlock) {
        if (!hitTargets.add(targetBlock.getLocation())) return; // dedup

        Location center = targetBlock.getLocation().toCenterLocation();

        // Sphere block removal
        int radius = ShootingGalleryConfig.EXPLOSION_RADIUS;
        int cx = targetBlock.getX();
        int cy = targetBlock.getY();
        int cz = targetBlock.getZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    Block b = targetBlock.getWorld().getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.isEmpty() || b.isLiquid()) continue;
                    removedBlocks.putIfAbsent(b.getLocation(), b.getState());
                    b.setType(Material.AIR);
                }
            }
        }

        targetBlock.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
        targetBlock.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);

        int newScore = scores.merge(shooter.getUniqueId(), ShootingGalleryConfig.POINTS_PER_TARGET, Integer::sum);
        int zombieCount = 1 + (newScore / 10);

        plugin.getGuildRegistry().getGuild(shooter.getUniqueId()).ifPresent(guild -> {
            Location spawnLoc = zombieSpawns.get(guild);
            if (spawnLoc != null) {
                for (int i = 0; i < zombieCount; i++) {
                    Entity zombie = spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ZOMBIE);
                    if (zombie instanceof Zombie z) {
                        z.setAdult();
                        z.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                        z.getEquipment().setHelmetDropChance(0f);
                        AttributeInstance range = z.getAttribute(Attribute.FOLLOW_RANGE);
                        if (range != null) range.setBaseValue(256.0);
                    }
                    spawnedEntities.add(zombie.getUniqueId());
                }
            }
        });
        if (newScore % 10 == 0) {
            broadcast(Component.text(shooter.getName(), NamedTextColor.AQUA)
                .append(Component.text(" blew up a target! (" + newScore + " destroyed so far)", NamedTextColor.GREEN)));
        }
    }
}
