package com.github.FortyTwoFortyTwo.MinecraftAgent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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

        // Register tool endpoints
        server.createContext("/tools/get_online_players", this::handleGetOnlinePlayers);
        server.createContext("/tools/broadcast_message", this::handleBroadcastMessage);
        server.createContext("/tools/get_player_location", this::handleGetPlayerLocation);
        server.createContext("/tools/run_console_command", this::handleRunConsoleCommand);
        server.createContext("/tools/get_world_info", this::handleGetWorldInfo);
        server.createContext("/ping", exchange -> sendJson(exchange, 200, Map.of("status", "ok")));

        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // -------------------------------------------------------------------------
    // Auth helper — checks the X-MCP-Secret header on every request
    // -------------------------------------------------------------------------

    private boolean isAuthorized(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("X-MCP-Secret");
        return secret.equals(header);
    }

    // -------------------------------------------------------------------------
    // Tool handlers
    // -------------------------------------------------------------------------

    /** Returns a comma-separated list of online player names */
    private void handleGetOnlinePlayers(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) { sendJson(exchange, 403, Map.of("error", "Unauthorized")); return; }

        String players = runOnMainThread(() ->
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.joining(", "))
        );

        sendJson(exchange, 200, Map.of(
                "players", players == null ? "" : players,
                "count", Bukkit.getOnlinePlayers().size()
        ));
    }

    /** Broadcasts a message to all players */
    private void handleBroadcastMessage(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) { sendJson(exchange, 403, Map.of("error", "Unauthorized")); return; }

        JsonObject body = readBody(exchange);
        String message = body.has("message") ? body.get("message").getAsString() : "";

        if (message.isEmpty()) {
            sendJson(exchange, 400, Map.of("error", "Missing 'message' field"));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.broadcastMessage("§6[AI] §f" + message)
        );

        sendJson(exchange, 200, Map.of("success", true, "message", message));
    }

    /** Returns the location of a specific player */
    private void handleGetPlayerLocation(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) { sendJson(exchange, 403, Map.of("error", "Unauthorized")); return; }

        JsonObject body = readBody(exchange);
        String playerName = body.has("player") ? body.get("player").getAsString() : "";

        Map<String, Object> result = runOnMainThread(() -> {
            Player player = Bukkit.getPlayerExact(playerName);
            if (player == null) return Map.of("error", "Player not found or offline");

            var loc = player.getLocation();
            return Map.of(
                    "player", playerName,
                    "world", loc.getWorld().getName(),
                    "x", Math.round(loc.getX()),
                    "y", Math.round(loc.getY()),
                    "z", Math.round(loc.getZ())
            );
        });

        sendJson(exchange, 200, result);
    }

    /** Runs a command as console (op-level) */
    private void handleRunConsoleCommand(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) { sendJson(exchange, 403, Map.of("error", "Unauthorized")); return; }

        JsonObject body = readBody(exchange);
        String command = body.has("command") ? body.get("command").getAsString() : "";

        if (command.isEmpty()) {
            sendJson(exchange, 400, Map.of("error", "Missing 'command' field"));
            return;
        }

        // Strip leading slash if present
        final String cmd = command.startsWith("/") ? command.substring(1) : command;

        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
        );

        sendJson(exchange, 200, Map.of("success", true, "command", cmd));
    }

    /** Returns info about the default world */
    private void handleGetWorldInfo(HttpExchange exchange) throws IOException {
        if (!isAuthorized(exchange)) { sendJson(exchange, 403, Map.of("error", "Unauthorized")); return; }

        Map<String, Object> info = runOnMainThread(() -> {
            var world = Bukkit.getWorlds().get(0);
            return Map.of(
                    "name", world.getName(),
                    "time", world.getTime(),
                    "isDay", world.getTime() < 13000,
                    "isStorming", world.isThundering(),
                    "isRaining", world.hasStorm(),
                    "playerCount", world.getPlayers().size(),
                    "seed", world.getSeed()
            );
        });

        sendJson(exchange, 200, info);
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

    private void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] bytes = gson.toJson(data).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}