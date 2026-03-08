package me.guild.titeenit.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Encapsulates all command logic for a single minigame.
 *
 * Implementations live in their respective minigame package and are registered
 * with TiteeniCommand at startup. TiteeniCommand delegates to these handlers
 * rather than containing game-specific logic itself.
 */
public interface MinigameCommandHandler {

    /**
     * All name aliases this game responds to for '/titeeni start'.
     * The first element is the primary name shown in help/error messages.
     */
    List<String> getGameNames();

    /**
     * Handle '/titeeni start <game> [extra args…]'.
     * The 'already running' check is done by TiteeniCommand before this is called.
     *
     * @param online all currently online players (full list, unfiltered)
     * @param args   full args array — args[0]="start", args[1]=game name, args[2+]=game-specific
     */
    void handleStart(CommandSender sender, List<Player> online, String[] args);

    /**
     * Sub-command names exclusively owned by this game (e.g. ["sethider"]).
     * TiteeniCommand routes any unrecognised sub-command to whichever handler
     * claims it here.
     */
    List<String> getSubCommandNames();

    /**
     * Handle a game-specific sub-command.
     * args[0] is the sub-command name, args[1+] are its arguments.
     */
    void handleSubCommand(CommandSender sender, String[] args);

    /**
     * Tab-completion suggestions.
     * Called when args[0] matches either this handler's sub-command names or "start"
     * with this handler's game name at args[1].
     */
    List<String> tabComplete(CommandSender sender, String[] args);

    /**
     * Lines appended to the output of '/titeeni' with no arguments.
     * Each string is wrapped in yellow Component.text by TiteeniCommand.
     */
    List<String> getHelpLines();
}
