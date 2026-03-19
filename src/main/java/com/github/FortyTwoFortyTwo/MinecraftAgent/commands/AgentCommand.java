package com.github.FortyTwoFortyTwo.MinecraftAgent.commands;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AnthropicClient;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.jetbrains.annotations.NotNull;

public class AgentCommand extends BukkitCommand {

    private final AnthropicClient anthropic;

    public AgentCommand(AnthropicClient anthropic) {
        super("agent");
        this.anthropic = anthropic;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("minecraft.command.op")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        String message = String.join(" ", args);
        anthropic.sendMessage(sender, message,
                "You are an AI agent embedded in a Minecraft server with full operator-level control.\n" +
                "Use your available tools proactively to fulfil requests rather than just describing what you would do.");
        return true;
    }
}
