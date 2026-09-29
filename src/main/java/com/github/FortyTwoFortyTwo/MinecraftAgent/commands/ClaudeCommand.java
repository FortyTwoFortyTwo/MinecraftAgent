package commands;

import agent.ClaudeCode;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class ClaudeCommand extends BukkitCommand {

    private List<String> paths;

    public ClaudeCommand(FileConfiguration config) {
        super("claude");

        paths = config.getStringList("directories");
    }

    @Override
    public @NotNull List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1)
            return paths;

        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        // Claude Code edits files on the host, so ops don't get this by default, it must be granted explicitly
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("minecraftagent.claude")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /claude <directory> <prompt>");
            return true;
        }

        // Only allow directories listed in config
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        if (paths.stream().noneMatch(path -> Path.of(path).toAbsolutePath().normalize().equals(directory))) {
            sender.sendMessage("§cDirectory must be one of: " + String.join(", ", paths));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {

            ClaudeCode claude = new ClaudeCode(directory.toString());

            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            // Start Claude Code
            try {
                claude.start(message);
                Thread.sleep(5_000); // Wait for Claude to initialize

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
