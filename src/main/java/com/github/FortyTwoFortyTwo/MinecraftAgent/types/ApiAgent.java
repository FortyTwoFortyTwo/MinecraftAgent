package com.github.FortyTwoFortyTwo.MinecraftAgent.types;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AnthropicClient;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import org.bukkit.command.CommandSender;

/** In-game Minecraft tools through the Anthropic API */
public class ApiAgent implements AgentType {

    private final AnthropicClient anthropic;

    public ApiAgent(AnthropicClient anthropic) {
        this.anthropic = anthropic;
    }

    @Override
    public String name() {
        return "api";
    }

    @Override
    public String usage() {
        return "<prompt>";
    }

    @Override
    public void run(CommandSender sender, String[] args) {
        String message = String.join(" ", args);
        anthropic.sendMessage(sender, message, SYSTEM_PROMPT, MinecraftTools.list);
    }
}
