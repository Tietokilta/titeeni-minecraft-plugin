package me.guild.titeenit.minigame.shootinggallery;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import me.guild.titeenit.command.MinigameCommandHandler;
import me.guild.titeenit.manager.MinigameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShootingGalleryCommandHandler implements MinigameCommandHandler {

    private static final List<String> GUILD_NAMES = Arrays.stream(Guild.values())
            .map(Guild::name).map(String::toLowerCase).toList();

    private final TiteeniPlugin plugin;
    private final MinigameManager manager;

    public ShootingGalleryCommandHandler(TiteeniPlugin plugin, MinigameManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @Override
    public List<String> getGameNames() { return List.of("shootinggallery"); }

    @Override
    public void handleStart(CommandSender sender, List<Player> online, String[] args) {
        Map<Guild, Location> zombieSpawns = loadZombieSpawns(plugin);
        manager.startGame(new ShootingGalleryGame(plugin, online, zombieSpawns));
        sender.sendMessage(Component.text("Started Shooting Gallery.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> getSubCommandNames() { return List.of("setzombiespawn"); }

    @Override
    public void handleSubCommand(CommandSender sender, String[] args) {
        if (args[0].equalsIgnoreCase("setzombiespawn")) {
            handleSetZombieSpawn(sender, args);
        }
    }

    private void handleSetZombieSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player admin)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /titeeni setzombiespawn <guild>", NamedTextColor.RED));
            return;
        }
        Guild guild = Guild.fromString(args[1]).orElse(null);
        if (guild == null) {
            sender.sendMessage(Component.text(
                    "Unknown guild: " + args[1] + ". Available: " + GUILD_NAMES, NamedTextColor.RED));
            return;
        }
        Location loc  = admin.getLocation();
        String path   = "shootingGallery.zombieSpawns." + guild.name();
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x",     loc.getX());
        plugin.getConfig().set(path + ".y",     loc.getY());
        plugin.getConfig().set(path + ".z",     loc.getZ());
        plugin.getConfig().set(path + ".yaw",   (double) loc.getYaw());
        plugin.getConfig().set(path + ".pitch", (double) loc.getPitch());
        plugin.saveConfig();
        sender.sendMessage(Component.text(
                "Zombie spawn for " + guild.getDisplayName() + " set at your position.",
                NamedTextColor.GREEN));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args[0].equalsIgnoreCase("setzombiespawn") && args.length == 2) {
            return GUILD_NAMES.stream()
                    .filter(g -> g.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    @Override
    public List<String> getHelpLines() {
        return List.of("/titeeni setzombiespawn <guild>         - Set zombie spawn for Oulu shooting gallery");
    }

    public static Map<Guild, Location> loadZombieSpawns(TiteeniPlugin plugin) {
        Map<Guild, Location> result = new HashMap<>();
        for (Guild guild : Guild.values()) {
            String path      = "shootingGallery.zombieSpawns." + guild.name();
            String worldName = plugin.getConfig().getString(path + ".world");
            if (worldName == null) continue;
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) continue;
            double x    = plugin.getConfig().getDouble(path + ".x");
            double y    = plugin.getConfig().getDouble(path + ".y");
            double z    = plugin.getConfig().getDouble(path + ".z");
            float  yaw  = (float) plugin.getConfig().getDouble(path + ".yaw");
            float  pitch = (float) plugin.getConfig().getDouble(path + ".pitch");
            result.put(guild, new Location(world, x, y, z, yaw, pitch));
        }
        return result;
    }
}
