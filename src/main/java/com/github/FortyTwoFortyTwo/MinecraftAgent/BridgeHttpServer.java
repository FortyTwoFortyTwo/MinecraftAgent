package com.github.FortyTwoFortyTwo.MinecraftAgent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public class BridgeHttpServer {

    private final JavaPlugin plugin;
    private final int port;
    private final String secret;
    private HttpServer server;
    private final Gson gson = new Gson();

    public BridgeHttpServer(JavaPlugin plugin, int port, String secret) {
        this.plugin = plugin;
        this.port = port;
        this.secret = secret;
    }

    public void start() throws IOException {
        // Bind only to localhost — never expose this to the internet
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));

        for (MinecraftTool tool : MinecraftTools.list) {
            server.createContext(tool.getPath(), this::handle);
        }

        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // -------------------------------------------------------------------------
    // Tool handler
    // -------------------------------------------------------------------------

    private void handle(HttpExchange exchange) {
        runOnMainThread(() -> {
            String path = exchange.getRequestURI().getPath();

            for (MinecraftTool tool : MinecraftTools.list) {
                if (!tool.getPath().equals(path))
                    continue;

                if (!tool.isAuthorized(exchange, secret)) {
                    MinecraftTools.sendJson(exchange, 403, Map.of("error", "Unauthorized"));
                    return null;
                }

                Map<String, Serializable> result = tool.execute(readBody(exchange));
                if (result != null) // if null, assuming that throwError has been used
                    MinecraftTools.sendJson(exchange, 200, result);

                return null;
            }

            MinecraftTools.sendJson(exchange, 404, Map.of("error", "Unknown path " + path));
            return null;
        });
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /** Runs a callable on Bukkit's main thread and blocks until it returns */
    private <T> T runOnMainThread(Callable<T> callable) {
        try {
            return Bukkit.getScheduler().callSyncMethod(plugin, callable).get();
        } catch (Exception e) {
            plugin.getLogger().warning("Error running task on main thread: " + e.getMessage());
            return null;
        }
    }

    private JsonObject readBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isEmpty()) return new JsonObject();
        return gson.fromJson(body, JsonObject.class);
    }
}