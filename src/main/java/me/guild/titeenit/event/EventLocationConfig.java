package me.guild.titeenit.event;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;

/**
 * Persists per-guild, per-stage spawn locations and the shared bus location to config.yml.
 *
 * Config structure:
 *   event.bus.{world,x,y,z,yaw,pitch}
 *   event.stages.<STAGE>.locations.<GUILD>.{world,x,y,z,yaw,pitch}
 *   event.stages.LAHTI.meta.hillTopY
 */
public class EventLocationConfig {

    private final TiteeniPlugin plugin;

    public EventLocationConfig(TiteeniPlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Bus location
    // -------------------------------------------------------------------------

    public void setBusLocation(Location loc) {
        setLocation("event.bus", loc);
    }

    public Optional<Location> getBusLocation() {
        return loadLocation("event.bus");
    }

    // -------------------------------------------------------------------------
    // Per-stage, per-guild spawn locations
    // -------------------------------------------------------------------------

    public void setStageLocation(EventStage stage, Guild guild, Location loc) {
        setLocation("event.stages." + stage.name() + ".locations." + guild.name(), loc);
    }

    public Optional<Location> getStageLocation(EventStage stage, Guild guild) {
        return loadLocation("event.stages." + stage.name() + ".locations." + guild.name());
    }

    // -------------------------------------------------------------------------
    // Stage-specific metadata
    // -------------------------------------------------------------------------

    public void setKothHillY(double y) {
        plugin.getConfig().set("event.stages.LAHTI.meta.hillTopY", y);
        plugin.saveConfig();
    }

    public double getKothHillY() {
        return plugin.getConfig().getDouble("event.stages.LAHTI.meta.hillTopY", 64.0);
    }

    public void setTurkuGateZ(double z) {
        plugin.getConfig().set("event.stages.TURKU.meta.gateZ", z);
        plugin.saveConfig();
    }

    /** Returns the Z coordinate that the boat must pass before dismounting is allowed.
     *  Returns {@link Double#NaN} if not configured (gate not enforced). */
    public double getTurkuGateZ() {
        if (!plugin.getConfig().contains("event.stages.TURKU.meta.gateZ")) return Double.NaN;
        return plugin.getConfig().getDouble("event.stages.TURKU.meta.gateZ");
    }

    // -------------------------------------------------------------------------
    // Location serialisation helpers
    // -------------------------------------------------------------------------

    private void setLocation(String path, Location loc) {
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x", loc.getX());
        plugin.getConfig().set(path + ".y", loc.getY());
        plugin.getConfig().set(path + ".z", loc.getZ());
        plugin.getConfig().set(path + ".yaw", (double) loc.getYaw());
        plugin.getConfig().set(path + ".pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }

    private Optional<Location> loadLocation(String path) {
        String worldName = plugin.getConfig().getString(path + ".world");
        if (worldName == null) return Optional.empty();
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return Optional.empty();
        double x = plugin.getConfig().getDouble(path + ".x");
        double y = plugin.getConfig().getDouble(path + ".y");
        double z = plugin.getConfig().getDouble(path + ".z");
        float yaw = (float) plugin.getConfig().getDouble(path + ".yaw");
        float pitch = (float) plugin.getConfig().getDouble(path + ".pitch");
        return Optional.of(new Location(world, x, y, z, yaw, pitch));
    }
}
