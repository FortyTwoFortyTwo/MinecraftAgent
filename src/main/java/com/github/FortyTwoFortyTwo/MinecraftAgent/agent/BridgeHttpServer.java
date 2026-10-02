package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public class BridgeHttpServer {

    private final int port;
    private final String secret;
    private HttpServer server;
    private final Gson gson = new Gson();

    public BridgeHttpServer(int port, String secret) {
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

    private void handle(HttpExchange exchange) throws IOException {

        // Reject requests addressed to any other host name, blocking DNS rebinding from browser pages
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || !(host.equalsIgnoreCase("127.0.0.1:" + port) || host.equalsIgnoreCase("localhost:" + port))) {
            MinecraftTools.sendJson(exchange, 403, Map.of("error", "Invalid Host header"));
            return;
        }

        String path = exchange.getRequestURI().getPath();

        for (MinecraftTool tool : MinecraftTools.list) {
            if (!tool.getPath().equals(path))
                continue;

            if (!tool.isAuthorized(exchange, secret)) {
                MinecraftTools.sendJson(exchange, 403, Map.of("error", "Unauthorized"));
                return;
            }

            // Same as MinecraftMcpServer, a failing tool is reported back rather than dropping the connection
            Map<String, Serializable> result;
            try {
                result = tool.execute(readBody(exchange).getAsJsonObject("arguments"));
            } catch (Exception e) {
                result = Map.of("error", String.valueOf(e));
            }

            if (result.containsKey("error"))
                MinecraftTools.sendJson(exchange, 400, result);
            else
                MinecraftTools.sendJson(exchange, 200, result);

            return;
        }

        MinecraftTools.sendJson(exchange, 404, Map.of("error", "Unknown path " + path));
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private JsonObject readBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isEmpty()) return new JsonObject();
        return gson.fromJson(body, JsonObject.class);
    }
}