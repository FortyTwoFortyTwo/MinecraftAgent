package com.github.FortyTwoFortyTwo.MinecraftAgent.types;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.ClaudeCode;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/** Same as api, but runs through Claude Code so it's billed to a Claude subscription instead of the API */
public class SubscriptionAgent implements AgentType {

    @Override
    public String name() {
        return "subscription";
    }

    @Override
    public String usage() {
        return "<prompt>";
    }

    @Override
    public void run(CommandSender sender, String[] args) {
        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {

            // Only the Minecraft tools, the same set api gets, with none of Claude Code's built-in tools
            ClaudeCode claude = new ClaudeCode(MinecraftTools.plugin.getDataFolder().getAbsolutePath(), sender, "", SYSTEM_PROMPT, true);

            String message = String.join(" ", args);

            try {
                claude.run(message);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
