package me.guild.titeenit.event;

import me.guild.titeenit.Guild;
import me.guild.titeenit.TiteeniPlugin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks which player represents each guild.
 * Assignments are persisted to config.yml under guildAssignments.<GUILD_NAME>.
 */
public class GuildRegistry {

    private final TiteeniPlugin plugin;
    private final Map<Guild, UUID> assignments = new EnumMap<>(Guild.class);

    public GuildRegistry(TiteeniPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // -------------------------------------------------------------------------
    // Assignment
    // -------------------------------------------------------------------------

    public void assign(Guild guild, Player player) {
        assignments.put(guild, player.getUniqueId());
        plugin.getConfig().set("guildAssignments." + guild.name(), player.getUniqueId().toString());
        plugin.saveConfig();
    }

    public void unassign(Guild guild) {
        assignments.remove(guild);
        plugin.getConfig().set("guildAssignments." + guild.name(), null);
        plugin.saveConfig();
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /** Returns the online player for this guild, if assigned and online. */
    public Optional<Player> getPlayer(Guild guild) {
        UUID uuid = assignments.get(guild);
        if (uuid == null) return Optional.empty();
        Player p = plugin.getServer().getPlayer(uuid);
        return Optional.ofNullable(p);
    }

    /** Returns the guild for a given player, if assigned. */
    public Optional<Guild> getGuild(Player player) {
        UUID uuid = player.getUniqueId();
        for (Map.Entry<Guild, UUID> entry : assignments.entrySet()) {
            if (entry.getValue().equals(uuid)) return Optional.of(entry.getKey());
        }
        return Optional.empty();
    }

    public boolean isAssigned(Player player) {
        return getGuild(player).isPresent();
    }

    /** All assigned players who are currently online. */
    public List<Player> getAssignedOnlinePlayers() {
        List<Player> result = new ArrayList<>();
        for (Guild guild : Guild.values()) {
            getPlayer(guild).ifPresent(result::add);
        }
        return result;
    }

    /** Map of guild → online player for guilds where the player is online. */
    public Map<Guild, Player> getOnlineGuildMap() {
        Map<Guild, Player> map = new EnumMap<>(Guild.class);
        for (Guild guild : Guild.values()) {
            getPlayer(guild).ifPresent(p -> map.put(guild, p));
        }
        return map;
    }

    public Map<Guild, UUID> getAssignments() {
        return Map.copyOf(assignments);
    }

    // -------------------------------------------------------------------------
    // Persistence
    // -------------------------------------------------------------------------

    private void load() {
        for (Guild guild : Guild.values()) {
            String uuidStr = plugin.getConfig().getString("guildAssignments." + guild.name());
            if (uuidStr != null) {
                try {
                    assignments.put(guild, UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }
}
