package commands;

import agent.ClaudeCode;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.jetbrains.annotations.NotNull;

/** Like /agent, but runs through Claude Code so it's billed to a Claude subscription instead of the API */
public class ClaudeAgentCommand extends BukkitCommand {

    private static final String SYSTEM_PROMPT =
            "You are an AI agent embedded in a Minecraft server with full operator-level control.\n" +
            "Use your available tools proactively to fulfil requests rather than just describing what you would do.";

    public ClaudeAgentCommand() {
        super("claudeagent");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("minecraft.command.op")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§cUsage: /claudeagent <prompt>");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {

            // Only the Minecraft tools, the same set /agent gets, with none of Claude Code's built-in tools
            ClaudeCode claude = new ClaudeCode(MinecraftTools.plugin.getDataFolder().getAbsolutePath(), sender, "", SYSTEM_PROMPT, true);

            String message = String.join(" ", args);

            // Start Claude Code
            try {
                claude.start(message);

                // Keep running for 300 seconds then stop
                Thread.sleep(300_000);
                claude.stop();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return true;
    }
}
