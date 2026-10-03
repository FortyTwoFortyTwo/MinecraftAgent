package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;

import java.awt.*;
import java.io.Serializable;
import java.util.Map;

public class BroadcastMessage implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    /** Only styling tags, so a message can't add click events that run commands as whichever player clicks it */
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset(),
                    StandardTags.newline()))
            .build();

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

    public boolean isBlockedForUntrusted() {
        return true;
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("message", stringSchema()));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String message = input.has("message") ? input.get("message").getAsString() : "";

        if (message.isEmpty())
            return Map.of("error", "Missing 'message' field");

        runTask(() -> {
            Bukkit.broadcast(Component.empty().append(MinecraftTools.PREFIX).append(MINI_MESSAGE.deserialize(message)));
        });

        return Map.of("success", true, "message", message);
    }
}
