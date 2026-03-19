package commands;

import agent.ClaudeCode;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class IDECommand extends BukkitCommand {

    private List<String> paths;

    public IDECommand(FileConfiguration config) {
        super("ide");

        paths = config.getStringList("ide.paths");
    }

    @Override
    public @NotNull List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1)
            return paths;

        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("minecraft.command.op")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {

            ClaudeCode claude = new ClaudeCode(args[0]);

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
