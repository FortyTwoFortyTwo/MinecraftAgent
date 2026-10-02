package com.github.FortyTwoFortyTwo.MinecraftAgent.types;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.ClaudeCode;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.StringUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Claude Code editing files on the host, with no Minecraft tools */
public class CodeAgent implements AgentType {

    private final List<String> paths;

    public CodeAgent(FileConfiguration config) {
        paths = config.getStringList("directories");
    }

    @Override
    public String name() {
        return "code";
    }

    @Override
    public String usage() {
        return "<directory> <prompt>";
    }

    @Override
    public List<String> tabComplete(String[] args) {
        if (args.length == 1)
            return StringUtil.copyPartialMatches(args[0], paths, new ArrayList<>());

        return List.of();
    }

    @Override
    public void run(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /agent code " + usage());
            return;
        }

        // Only allow directories listed in config
        Path directory = Path.of(args[0]).toAbsolutePath().normalize();
        if (paths.stream().noneMatch(path -> Path.of(path).toAbsolutePath().normalize().equals(directory))) {
            sender.sendMessage("§cDirectory must be one of: " + String.join(", ", paths));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {

            ClaudeCode claude = new ClaudeCode(directory.toString(), sender, "Read,Edit,Write,Glob,Grep", null, false);

            String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            try {
                claude.run(message);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
