package com.github.FortyTwoFortyTwo.Shared;

import Tools.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
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
            new GetOnlinePlayers(),
            new GetPlayerLocation(),
            new GetWorldInfo(),
            new Ping(),
            new RunConsoleCommand()
    );

    static public void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] bytes = GSON.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Runs a callable on Bukkit's main thread and blocks until it returns */
    static public <T> T runOnMainThread(Callable<T> callable) {
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, callable).get();
        } catch (Exception e) {
            plugin.getLogger().warning("Error running task on main thread: " + e.getMessage());
            return null;
        }
    }
}