package com.github.FortyTwoFortyTwo.MinecraftAgent.types;

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

    /** Runs the type, with args being everything after the type name */
    void run(CommandSender sender, String[] args);

    default List<String> tabComplete(String[] args) {
        return List.of();
    }
}
