package me.guild.titeenit.minigame.jallupollo;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.MinigameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Jallupöllö minigame
 *
 * --- Sound ---
 * The reveal sound (ENTITY_GENERIC_DRINK — the looping sip sound) is sent
 * individually to each seeker via player.playSound() positioned at the hider's
 * location (3-D direction preserved).  The source volume is calculated so
 * Minecraft's linear attenuation delivers the desired hear-level at every
 * distance: full up to 32 blocks, fading to 10 % at ~200 blocks, never below
 * 10 % regardless of distance.
 */
public class JallupolloGame extends Minigame {
    private final NamespacedKey potionKey;
    private final NamespacedKey compassKey;
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Set<UUID> stunnedPlayers = new HashSet<>();
    private final Random rng = new Random();

    // ---- hider state --------------------------------------------------------
    private Player hider;
    private boolean isDrinking;
    private int drinkTicks;
    private int stopGraceCounter;
    private Location lastKnownHiderLocation;

    private BukkitTask gameTick;
    private final JallupolloListener listener;

    public JallupolloGame(TiteeniPlugin plugin, List<Player> players) {
        super(plugin, players);
        this.potionKey  = new NamespacedKey(plugin, "jallupollo_potion");
        this.compassKey = new NamespacedKey(plugin, "jallupollo_compass");
        this.listener   = new JallupolloListener(plugin, this);
        for (Player p : players) scores.put(p.getUniqueId(), 0);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    @Override public String getDisplayName() { return "Jallupöllö"; }

    @Override
    public void start() {
        state = MinigameState.RUNNING;
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        chooseNewHider(players.get(rng.nextInt(players.size())));

        gameTick = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::tick, 0L, JallupolloConfig.TICK_INTERVAL_TICKS);

        broadcast(Component.text("Jallupöllö has started!", NamedTextColor.GOLD));
    }

    @Override
    public void stop() {
        state = MinigameState.ENDED;

        if (gameTick != null) { gameTick.cancel(); gameTick = null; }
        org.bukkit.event.HandlerList.unregisterAll(listener);

        // Clear every player's compass so nothing lingers after the game
        stunnedPlayers.clear();
        for (Player p : getOnlinePlayers()) {
            removeCompass(p);
            p.setCompassTarget(p.getWorld().getSpawnLocation());
        }
        if (hider != null && hider.isOnline()) removeJaloviina(hider);

        broadcastScores();
        broadcast(Component.text("Jallupöllö has ended!", NamedTextColor.GOLD));
    }

    @Override
    public void onPlayerQuit(Player player) {
        if (!players.contains(player)) return;

        if (player.equals(hider)) {
            List<Player> remaining = new ArrayList<>(getOnlinePlayers());
            remaining.remove(player);
            if (remaining.isEmpty()) {
                plugin.getMinigameManager().onGameSelfTerminated();
                stop();
                return;
            }
            chooseNewHider(remaining.get(rng.nextInt(remaining.size())));
        }
        players.remove(player);
    }

    @Override
    public Map<UUID, Integer> getScores() { return Collections.unmodifiableMap(scores); }

    // =========================================================================
    // Game tick  (runs every TICK_INTERVAL_TICKS)
    // =========================================================================

    private void tick() {
        if (state != MinigameState.RUNNING || hider == null || !hider.isOnline()) return;

        // With a 3600 s consume time there is no cancel/restart gap.
        // isHandRaised() is simply true while the player holds right-click.
        boolean handRaised = hider.isHandRaised() && isJaloviina(hider.getActiveItem());

        if (handRaised) {
            stopGraceCounter = 0;
            drinkTicks += JallupolloConfig.TICK_INTERVAL_TICKS;

            if (!isDrinking) {
                isDrinking = true;
                onDrinkingStarted();
            }

            // Every 20 ticks = 1 second
            if (drinkTicks % 20 == 0) {
                scores.merge(hider.getUniqueId(), JallupolloConfig.POINTS_PER_SECOND, Integer::sum);
                hider.sendActionBar(Component.text(
                        "+" + JallupolloConfig.POINTS_PER_SECOND + " pt  |  Total: "
                                + scores.get(hider.getUniqueId()), NamedTextColor.GREEN));

                playRevealSound();
            }

        } else if (isDrinking) {
            stopGraceCounter++;
            if (stopGraceCounter >= JallupolloConfig.STOP_DRINK_GRACE_CHECKS) {
                isDrinking = false;
                drinkTicks = 0;
                stopGraceCounter = 0;
                onDrinkingStopped();
            }
        }
    }

    // =========================================================================
    // Drinking transitions
    // =========================================================================

    private void onDrinkingStarted() {
        broadcast(Component.text(hider.getName(), NamedTextColor.RED)
                .append(Component.text(" is drinking! Navigate to them!", NamedTextColor.YELLOW)));
    }

    private void onDrinkingStopped() {
        lastKnownHiderLocation = hider.getLocation().clone();
        for (Player seeker : getSeekers()) {
            if (seeker.isOnline()) seeker.setCompassTarget(lastKnownHiderLocation);
        }

        broadcast(Component.text(hider.getName(), NamedTextColor.RED)
                .append(Component.text(" stopped drinking. Compass updated!", NamedTextColor.GRAY)));
    }

    // =========================================================================
    // Sound
    // =========================================================================

    /**
     * Sends the reveal sound individually to each seeker with a source volume
     * computed so that, after the client's linear attenuation, they hear it at
     * the desired level:
     *   hearLevel = 1 − dist / (16 × sourceVolume)
     *   → sourceVolume = dist / (16 × (1 − desiredLevel))
     *
     * The sound is positioned at the hider's location so each client receives
     * correct 3-D direction cues despite the custom volume.
     */
    private void playRevealSound() {
        if (hider == null || !hider.isOnline()) return;
        Location hiderLoc = hider.getLocation();

        for (Player seeker : getOnlinePlayers()) {
            if (seeker.equals(hider)) continue;
            if (!seeker.getWorld().equals(hiderLoc.getWorld())) continue;

            double dist = seeker.getLocation().distance(hiderLoc);
            float srcVolume = computeSourceVolume((float) dist);
            seeker.playSound(hiderLoc, Sound.ENTITY_GENERIC_DRINK,
                    SoundCategory.PLAYERS, srcVolume, JallupolloConfig.SOUND_PITCH);
        }
    }

    /**
     * Returns the source volume to pass to player.playSound() so that Minecraft's
     * attenuation produces the desired hear-level at the given distance.
     */
    private static float computeSourceVolume(float dist) {
        float desired;
        if (dist <= JallupolloConfig.SOUND_FULL_RANGE) {
            desired = 1.0f;
        } else {
            float t = (dist - JallupolloConfig.SOUND_FULL_RANGE)
                    / (JallupolloConfig.SOUND_FADE_END - JallupolloConfig.SOUND_FULL_RANGE);
            desired = Math.max(JallupolloConfig.SOUND_MIN_VOLUME,
                    1.0f - t * (1.0f - JallupolloConfig.SOUND_MIN_VOLUME));
        }

        if (desired >= 1.0f || dist <= 1f) {
            // Very high source volume → attenuation at short range is negligible
            return 1000f;
        }
        // Solve: desired = 1 - dist / (16 × src)  →  src = dist / (16 × (1 - desired))
        return Math.min(1000f, dist / (16f * (1f - desired)));
    }

    // =========================================================================
    // Public API (called by listener)
    // =========================================================================

    public void onHiderHit(Player attacker) {
        if (state != MinigameState.RUNNING) return;
        if (!players.contains(attacker) || attacker.equals(hider)) return;

        Player caughtHider = hider;

        for (Player p : getOnlinePlayers()) {
            if (p.equals(attacker)) continue;
            if (p.getLocation().distance(caughtHider.getLocation()) <= JallupolloConfig.NEARBY_EFFECT_RADIUS) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                        JallupolloConfig.DEBUFF_DURATION_TICKS, 0, false, true, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        JallupolloConfig.DEBUFF_DURATION_TICKS,
                        JallupolloConfig.SLOWNESS_AMPLIFIER, false, true, true));
                UUID stunId = p.getUniqueId();
                stunnedPlayers.add(stunId);
                plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> stunnedPlayers.remove(stunId), JallupolloConfig.DEBUFF_DURATION_TICKS);
            }
        }

        broadcast(Component.text(attacker.getName(), NamedTextColor.AQUA)
                .append(Component.text(" caught ", NamedTextColor.GOLD))
                .append(Component.text(caughtHider.getName(), NamedTextColor.RED))
                .append(Component.text("! ", NamedTextColor.GOLD))
                .append(Component.text(attacker.getName(), NamedTextColor.AQUA))
                .append(Component.text(" is now the hider!", NamedTextColor.GOLD)));

        chooseNewHider(attacker);
    }

    public void chooseNewHider(Player newHider) {
        if (hider != null && hider.isOnline()) {
            removeJaloviina(hider);
            hider.sendMessage(Component.text("You are no longer the hider.", NamedTextColor.YELLOW));
        }

        isDrinking = false;
        drinkTicks = 0;
        stopGraceCounter = 0;
        lastKnownHiderLocation = null;

        hider = newHider;
        removeCompass(hider);
        hider.getInventory().addItem(createJaloviina());

        // Remove old compasses and redistribute with the new hider name
        for (Player p : getOnlinePlayers()) removeCompass(p);
        for (Player seeker : getSeekers()) {
            if (seeker.isOnline()) {
                seeker.setCompassTarget(seeker.getWorld().getSpawnLocation());
                seeker.getInventory().addItem(createSeekerCompass(newHider));
            }
        }

        newHider.sendMessage(Component.text(
                "You are the pöllö! Find a spot, then hold right-click on the Jaloviina to score points.",
                NamedTextColor.GREEN));
        newHider.sendMessage(Component.text(
                "Your location is visible to the seekers while you drink — release to hide it!",
                NamedTextColor.YELLOW));
        broadcast(Component.text(newHider.getName(), NamedTextColor.RED)
                .append(Component.text(" is now the pöllö!", NamedTextColor.GOLD)));
    }

    // =========================================================================
    // Potion helpers
    // =========================================================================

    ItemStack createJaloviina() {
        ItemStack potion = new ItemStack(Material.POTION);

        // Override the consume duration so the animation runs continuously
        // without ever "completing" during a normal game session.
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        meta.displayName(Component.text("Jaloviina", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Hold right-click to drink and score points", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Your location is revealed to all seekers while drinking!", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setColor(Color.fromRGB(0x85, 0x46, 0x1E));
        meta.getPersistentDataContainer().set(potionKey, PersistentDataType.BYTE, (byte) 1);
        potion.setItemMeta(meta);
        return potion;
    }

    private void removeJaloviina(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isJaloviina(contents[i])) player.getInventory().setItem(i, null);
        }
    }

    public boolean isJaloviina(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(potionKey, PersistentDataType.BYTE);
    }

    private List<Player> getSeekers() {
        return players.stream().filter(p -> !p.equals(hider)).toList();
    }

    public Player getHider() { return hider; }

    public boolean isStunned(Player player) {
        return stunnedPlayers.contains(player.getUniqueId());
    }

    // =========================================================================
    // Compass helpers
    // =========================================================================

    ItemStack createSeekerCompass(Player hider) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(Component.text("Pöllö tracker", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                Component.text("Currently tracking: ", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(Component.text(hider.getName(), NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false))));
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
        return compass;
    }

    public boolean isHiderCompass(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    private void removeCompass(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isHiderCompass(contents[i])) player.getInventory().setItem(i, null);
        }
    }
}
