package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.PlayerText;
import com.github.FortyTwoFortyTwo.Shared.appender.CaptureLogsAppender;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandException;

import java.io.Serializable;
import java.util.Map;

public class RunConsoleCommand implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Runs a command on the Minecraft server console with operator-level privileges. Use with caution.";
    }

    @Override
    public boolean isBlockedForUntrusted() {
        return true;
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("command", stringSchema()));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String command = input.has("command") ? input.get("command").getAsString() : "";

        if (command.isEmpty())
            return Map.of("error", "Missing 'command' field");

        // Strip leading slash if present
        final String cmd = command.startsWith("/") ? command.substring(1) : command;

        return runTask(() -> {
            // Capture logs for AI to analyze. Command feedback can include text from the world, e.g. /data get on a sign or book.
            CaptureLogsAppender capture = new CaptureLogsAppender();

            try {
                // The feedback is hidden from the agent, so at least tell it when the command doesn't exist
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd))
                    return Map.of("success", false, "error", "Unknown command: " + cmd.split(" ")[0]);

                return Map.of("success", true, "output", PlayerText.of(capture.getOutput()));
            } catch (CommandException e) {
                // Thrown by the command's own code, the real error is the cause
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return Map.of("success", false, "error", cause.getClass().getName(),
                        "errorMessage", PlayerText.of(cause.getMessage()), "output", PlayerText.of(capture.getOutput()));
            } finally {
                capture.end();
            }
        });
    }
}
