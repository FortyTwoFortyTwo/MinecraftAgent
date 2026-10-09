package com.github.FortyTwoFortyTwo.MinecraftAgent.types;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AgentProgress;
import org.bukkit.command.CommandSender;

import java.util.List;

/** One way of running a prompt with /agent <type> */
public interface AgentType {

    String SYSTEM_PROMPT =
            "You are an agent in a Minecraft server with operator-level control. Act with your tools rather than describing what you'd do, and don't make backups first.\n" +
            "For anything involving the world, call GetMemories early, and Memorise anything worth coming back to.\n" +
            "Viewing a file disables ExecuteCode and RunConsoleCommand for the rest of the run, so make those changes first.";

    /** Name typed after /agent */
    String name();

    /** Arguments shown in the usage message after /agent <type> */
    String usage();

    /** Runs the type, with args being everything after the type name. Must always end with progress.finish(), even if it never starts. */
    void run(CommandSender sender, String[] args, AgentProgress progress);

    /** The prompt itself out of the args given to run, shown to players in chat */
    default String prompt(String[] args) {
        return String.join(" ", args);
    }

    default List<String> tabComplete(String[] args) {
        return List.of();
    }
}
