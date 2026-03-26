package com.github.FortyTwoFortyTwo.Shared;

import Tools.*;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Defines all MCP tools exposed to Claude Desktop.
 * Each tool forwards its call to the Bukkit plugin via MinecraftBridgeClient.
 */
public class MinecraftTools {

    static public final Gson GSON = new Gson();
    static public JavaPlugin plugin;

    static public List<com.github.FortyTwoFortyTwo.Shared.MinecraftTool> list = List.of(
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

    static public void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}