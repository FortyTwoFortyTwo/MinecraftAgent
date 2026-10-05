package com.github.FortyTwoFortyTwo.Shared;

import com.github.FortyTwoFortyTwo.Shared.Tools.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every tool offered to the agent, whether through the Anthropic API, Claude Code's MCP server or McpBridge,
 * plus what those share for serving them over HTTP.
 */
public class MinecraftTools {

    public static final Gson GSON = new Gson();
    /** Set by the plugin on enable, null in McpBridge which only forwards calls */
    public static JavaPlugin plugin;

    public static final Component PREFIX = Component.text("[Agent] ", NamedTextColor.GOLD);

    /** Only styling tags, so the agent's messages can't add click events that run commands as whichever player clicks it */
    public static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset(),
                    StandardTags.newline()))
            .build();

    /** Placeholders like <text1> for text players could have written that was hidden from the agent, inserted unparsed so it can't add formatting or click events */
    public static TagResolver playerTexts(Map<String, String> texts) {
        return TagResolver.resolver(texts.entrySet().stream().map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue())).toList());
    }

    public static final List<MinecraftTool> list = List.of(
            new BroadcastMessage(),
            new ExecuteCode(),
            new GetOnlinePlayers(),
            new GetPlayerLocation(),
            new GetPlayerPrompt(),
            new GetRegistryKeys(),
            new GetRegistryValues(),
            new GetWorldInfo(),
            new ListWorkingDirectories(),
            new Ping(),
            new ResolvePath(),
            new RunConsoleCommand(),
            new TextEditor()
    );

    public static Optional<MinecraftTool> find(String name) {
        return list.stream().filter(tool -> tool.getName().equals(name)).findFirst();
    }

    public static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Whether the request was addressed to localhost, blocking DNS rebinding from browser pages */
    public static boolean isLocalHost(HttpExchange exchange, int port) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        return host != null && (host.equalsIgnoreCase("127.0.0.1:" + port) || host.equalsIgnoreCase("localhost:" + port));
    }

    /** Whether the request has the given secret in its X-MCP-Secret header */
    public static boolean hasSecret(HttpExchange exchange, String secret) {
        String header = exchange.getRequestHeaders().getFirst("X-MCP-Secret");
        if (header == null)
            return false;

        // Constant-time comparison so the secret can't be guessed through response timing
        return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }

    public static void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
