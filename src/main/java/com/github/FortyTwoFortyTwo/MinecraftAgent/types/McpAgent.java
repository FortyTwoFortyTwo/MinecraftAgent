package com.github.FortyTwoFortyTwo.MinecraftAgent.types;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AgentProgress;
import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.ClaudeCode;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import org.bukkit.command.CommandSender;

/** Same as api, but runs through Claude Code connected to a Minecraft tools MCP server, so it can be billed to a Claude subscription */
public class McpAgent implements AgentType {

    /** Claude Code's text output is only logged to the console, so players only see what's sent through tools */
    private static final String MCP_SYSTEM_PROMPT = SYSTEM_PROMPT + "\n" +
            "Players cannot see your text replies; they are only written to the server console. " +
            "To tell players anything (answers, progress, results), you must call the BroadcastMessage tool.\n" +
            "Nobody can reply to you during this run, so never ask questions or wait for confirmation. " +
            "Make reasonable assumptions and act on them, mentioning any assumptions in your broadcast.\n" +
            "Keep your final text reply to a one-line summary for the server log.";

    @Override
    public String name() {
        return "mcp";
    }

    @Override
    public String usage() {
        return "<prompt>";
    }

    @Override
    public void run(CommandSender sender, String[] args, AgentProgress progress) {
        // The Minecraft tools and web access, the same set api gets, with none of Claude Code's other built-in tools
        new ClaudeCode(MinecraftTools.plugin.getDataFolder().getAbsolutePath(), sender)
                .builtInTools("WebSearch,WebFetch")
                .systemPrompt(MCP_SYSTEM_PROMPT)
                .minecraftTools()
                .runAsync(String.join(" ", args), progress);
    }
}
