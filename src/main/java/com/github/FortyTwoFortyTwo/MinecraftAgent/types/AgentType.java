package com.github.FortyTwoFortyTwo.MinecraftAgent.types;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AgentProgress;
import org.bukkit.command.CommandSender;

import java.util.List;

/** One way of running a prompt with /agent <type> */
public interface AgentType {

    String SYSTEM_PROMPT =
            "You are an AI agent embedded in a Minecraft server with full operator-level control.\n" +
            "Use your available tools proactively to fulfil requests rather than just describing what you would do.";

    /** Name typed after /agent */
    String name();

    /** Arguments shown in the usage message after /agent <type> */
    String usage();

    /** Runs the type, with args being everything after the type name. Must always end with progress.finish(), even if it never starts. */
    void run(CommandSender sender, String[] args, AgentProgress progress);

    default List<String> tabComplete(String[] args) {
        return List.of();
    }
}
