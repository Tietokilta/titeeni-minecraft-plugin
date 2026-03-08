package me.guild.titeenit.minigame.copythebuild;

import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.minigame.Minigame;
import me.guild.titeenit.minigame.MinigameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CopyTheBuildGame extends Minigame {

    private final List<Location> modelPlots;
    private final List<Location> buildPlots;

    private final Map<UUID, Location> playerBuildCorners = new HashMap<>();
    private final Map<UUID, Location> playerPreviewCorners = new HashMap<>();
    private final Map<UUID, Integer> scores = new HashMap<>();
    private final Map<UUID, Integer> playerModelIndex = new HashMap<>();
    private final Map<Location, BlockState> savedBuildStates = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedInventories = new HashMap<>();

    private int gameSecondsLeft;
    private BukkitTask gameTimerTask;
    private BukkitTask checkTask;

    private final CopyTheBuildListener listener;

    public CopyTheBuildGame(TiteeniPlugin plugin, List<Player> players,
                             List<Location> modelPlots, List<Location> buildPlots) {
        super(plugin, players);
        this.modelPlots = modelPlots;
        this.buildPlots = buildPlots;
        this.listener = new CopyTheBuildListener(plugin, this);
        this.gameSecondsLeft = CopyTheBuildConfig.GAME_DURATION_SECONDS;
        for (Player p : players) {
            scores.put(p.getUniqueId(), 0);
            playerModelIndex.put(p.getUniqueId(), 0);
        }
    }

    @Override
    public String getDisplayName() { return "Copy the Build"; }

    @Override
    public void start() {
        state = MinigameState.RUNNING;

        List<Player> online = getOnlinePlayers();

        // Save inventories
        for (Player p : online) {
            savedInventories.put(p.getUniqueId(), p.getInventory().getContents().clone());
        }

        // Assign build corners (zip online players with build plots) and derive preview corners
        for (int i = 0; i < online.size(); i++) {
            UUID uid = online.get(i).getUniqueId();
            Location buildCorner = buildPlots.get(i);
            playerBuildCorners.put(uid, buildCorner);
            playerPreviewCorners.put(uid, buildCorner.clone().add(0, 0, CopyTheBuildConfig.PREVIEW_OFFSET_Z));
        }

        // Save all build and preview plot block states so we can restore on stop
        for (Location corner : playerBuildCorners.values()) {
            saveAreaBlocks(corner, savedBuildStates);
        }
        for (Location corner : playerPreviewCorners.values()) {
            saveAreaBlocks(corner, savedBuildStates);
        }

        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        gameTimerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            gameSecondsLeft--;
            if (gameSecondsLeft == 60 || gameSecondsLeft == 30 || gameSecondsLeft == 10) {
                broadcast(Component.text(gameSecondsLeft + " seconds remaining!", NamedTextColor.YELLOW));
            }
            if (gameSecondsLeft <= 0) {
                plugin.getMinigameManager().onGameSelfTerminated();
                stop();
            }
        }, 20L, 20L);

        // Clear build areas, populate previews, and hand out first model
        for (Player p : online) {
            clearArea(playerBuildCorners.get(p.getUniqueId()));
            copyModelToPreview(p);
            givePlayerModel(p);
        }

        checkTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::checkBuilds,
                CopyTheBuildConfig.CHECK_INTERVAL_TICKS, CopyTheBuildConfig.CHECK_INTERVAL_TICKS);

        broadcast(Component.text("Copy the Build has started! You have "
                + CopyTheBuildConfig.GAME_DURATION_SECONDS / 60
                + " minutes — copy the model structure to score!", NamedTextColor.GOLD));
    }

    @Override
    public void stop() {
        if (state == MinigameState.ENDED) return;
        state = MinigameState.ENDED;

        if (gameTimerTask != null) { gameTimerTask.cancel(); gameTimerTask = null; }
        if (checkTask != null)     { checkTask.cancel();     checkTask = null;     }

        org.bukkit.event.HandlerList.unregisterAll(listener);

        // Restore all build plot blocks
        for (BlockState saved : savedBuildStates.values()) {
            saved.update(true, false);
        }
        savedBuildStates.clear();

        // Restore inventories for online players
        for (Player p : getOnlinePlayers()) {
            ItemStack[] saved = savedInventories.get(p.getUniqueId());
            if (saved != null) {
                p.getInventory().setContents(saved);
            }
        }

        broadcastScores();
        broadcast(Component.text("Copy the Build has ended!", NamedTextColor.GOLD));
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

    // =========================================================================
    // Game logic
    // =========================================================================

    private void givePlayerModel(Player p) {
        if (state != MinigameState.RUNNING) return;
        Map<Material, Integer> blocks = readModelBlocks(currentModelCorner(p));
        giveBlocksTo(p, blocks);
    }

    private void checkBuilds() {
        if (state != MinigameState.RUNNING) return;
        for (Player p : getOnlinePlayers()) {
            UUID uid = p.getUniqueId();
            Location buildCorner = playerBuildCorners.get(uid);
            if (buildCorner == null) continue;
            if (!buildsMatch(currentModelCorner(p), buildCorner)) continue;

            playerModelIndex.merge(uid, 1, Integer::sum);
            scores.merge(uid, CopyTheBuildConfig.POINTS_PER_BUILD, Integer::sum);
            broadcast(Component.text(p.getName(), NamedTextColor.AQUA)
                    .append(Component.text(" completed a build! (", NamedTextColor.GREEN))
                    .append(Component.text(scores.get(uid) + " pts", NamedTextColor.YELLOW))
                    .append(Component.text(")", NamedTextColor.GREEN)));
            clearArea(buildCorner);
            copyModelToPreview(p);
            givePlayerModel(p);
        }
    }

    private Location currentModelCorner(Player p) {
        int index = playerModelIndex.getOrDefault(p.getUniqueId(), 0);
        return modelPlots.get(index % modelPlots.size());
    }

    private void copyModelToPreview(Player p) {
        Location previewCorner = playerPreviewCorners.get(p.getUniqueId());
        if (previewCorner == null) return;
        Location modelCorner = currentModelCorner(p);
        int size = CopyTheBuildConfig.PLOT_SIZE;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    BlockData data = modelCorner.getWorld().getBlockAt(
                            modelCorner.getBlockX() + dx,
                            modelCorner.getBlockY() + dy,
                            modelCorner.getBlockZ() + dz
                    ).getBlockData();
                    previewCorner.getWorld().getBlockAt(
                            previewCorner.getBlockX() + dx,
                            previewCorner.getBlockY() + dy,
                            previewCorner.getBlockZ() + dz
                    ).setBlockData(data, false);
                }
            }
        }
    }

    // =========================================================================
    // Block helpers
    // =========================================================================

    private Map<Material, Integer> readModelBlocks(Location corner) {
        Map<Material, Integer> counts = new HashMap<>();
        int size = CopyTheBuildConfig.PLOT_SIZE;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    Material mat = corner.getWorld().getBlockAt(
                            corner.getBlockX() + dx,
                            corner.getBlockY() + dy,
                            corner.getBlockZ() + dz
                    ).getType();
                    if (mat != Material.AIR) {
                        counts.merge(mat, 1, Integer::sum);
                    }
                }
            }
        }
        return counts;
    }

    private void giveBlocksTo(Player p, Map<Material, Integer> blocks) {
        p.getInventory().clear();
        for (Map.Entry<Material, Integer> entry : blocks.entrySet()) {
            Material mat = entry.getKey();
            int remaining = entry.getValue();
            while (remaining > 0) {
                int stackSize = Math.min(remaining, mat.getMaxStackSize());
                Map<Integer, ItemStack> leftovers = p.getInventory().addItem(new ItemStack(mat, stackSize));
                for (ItemStack leftover : leftovers.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), leftover);
                }
                remaining -= stackSize;
            }
        }
    }

    private boolean buildsMatch(Location modelCorner, Location buildCorner) {
        int size = CopyTheBuildConfig.PLOT_SIZE;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    Material model = modelCorner.getWorld().getBlockAt(
                            modelCorner.getBlockX() + dx,
                            modelCorner.getBlockY() + dy,
                            modelCorner.getBlockZ() + dz
                    ).getType();
                    Material build = buildCorner.getWorld().getBlockAt(
                            buildCorner.getBlockX() + dx,
                            buildCorner.getBlockY() + dy,
                            buildCorner.getBlockZ() + dz
                    ).getType();
                    if (model != build) return false;
                }
            }
        }
        return true;
    }

    private void saveAreaBlocks(Location corner, Map<Location, BlockState> target) {
        int size = CopyTheBuildConfig.PLOT_SIZE;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    Block block = corner.getWorld().getBlockAt(
                            corner.getBlockX() + dx,
                            corner.getBlockY() + dy,
                            corner.getBlockZ() + dz
                    );
                    target.put(block.getLocation(), block.getState());
                }
            }
        }
    }

    private void clearArea(Location corner) {
        int size = CopyTheBuildConfig.PLOT_SIZE;
        for (int dx = 0; dx < size; dx++) {
            for (int dy = 0; dy < size; dy++) {
                for (int dz = 0; dz < size; dz++) {
                    corner.getWorld().getBlockAt(
                            corner.getBlockX() + dx,
                            corner.getBlockY() + dy,
                            corner.getBlockZ() + dz
                    ).setType(Material.AIR, false);
                }
            }
        }
    }

    // =========================================================================
    // Area checks (used by listener)
    // =========================================================================

    public boolean isInPlayerBuildArea(Player p, Location loc) {
        Location corner = playerBuildCorners.get(p.getUniqueId());
        return corner != null && isInArea(loc, corner);
    }

    public boolean isInModelArea(Location loc) {
        for (Location corner : modelPlots) {
            if (isInArea(loc, corner)) return true;
        }
        return false;
    }

    public boolean isInAnyPreviewArea(Location loc) {
        for (Location corner : playerPreviewCorners.values()) {
            if (isInArea(loc, corner)) return true;
        }
        return false;
    }

    private boolean isInArea(Location loc, Location corner) {
        if (!loc.getWorld().equals(corner.getWorld())) return false;
        int size = CopyTheBuildConfig.PLOT_SIZE;
        int dx = loc.getBlockX() - corner.getBlockX();
        int dy = loc.getBlockY() - corner.getBlockY();
        int dz = loc.getBlockZ() - corner.getBlockZ();
        return dx >= 0 && dx < size && dy >= 0 && dy < size && dz >= 0 && dz < size;
    }
}
