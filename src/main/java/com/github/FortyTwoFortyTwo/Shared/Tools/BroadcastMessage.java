package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

import java.io.Serializable;
import java.util.Map;

public class BroadcastMessage implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Broadcasts a message to all players currently online on the Minecraft server. " +
                "This is the only way players can see anything you say; use it to answer questions and report what you did. " +
                "Players cannot reply, so don't ask them questions. " +
                "The message is MiniMessage formatted: use colours and styling to make it easy to read, e.g. highlight " +
                "player names, items, numbers and coordinates, use <green> for success and <red> for problems. " +
                "Supported tags: colours (<red>, <gold>, <aqua>, <#ff8800>, ...), <bold>, <italic>, <underlined>, " +
                "<strikethrough>, <obfuscated>, <gradient:#ff0000:#0000ff>, <rainbow>, <reset> and <newline>. " +
                "Close tags with </tag>, and escape a literal < as \\<.";
    }

    @Override
    public boolean isBlockedForUntrusted() {
        return true;
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("message", stringSchema()));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String message = input.has("message") ? input.get("message").getAsString() : "";

        if (message.isEmpty())
            return Map.of("error", "Missing 'message' field");

        runTask(() -> {
            Bukkit.broadcast(Component.empty().append(MinecraftTools.PREFIX).append(MinecraftTools.MINI_MESSAGE.deserialize(message)));
        });

        return Map.of("success", true, "message", message);
    }
}
