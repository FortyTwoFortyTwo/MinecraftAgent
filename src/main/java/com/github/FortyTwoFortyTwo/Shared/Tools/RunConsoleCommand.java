package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
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
            // Capture logs for AI to analyze
            CaptureLogsAppender capture = new CaptureLogsAppender();

            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                return Map.of("success", true, "output", (Serializable) capture.getOutput());
            } catch (CommandException e) {
                // Thrown by the command's own code, the real error is the cause
                return Map.of("success", false, "error", String.valueOf(e.getCause() != null ? e.getCause() : e), "output", (Serializable) capture.getOutput());
            } finally {
                capture.end();
            }
        });
    }
}
