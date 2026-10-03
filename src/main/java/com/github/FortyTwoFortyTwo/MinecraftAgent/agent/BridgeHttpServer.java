package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

/** Serves each Minecraft tool on its own path for McpBridge to forward calls to */
public class BridgeHttpServer {

    private final int port;
    private final String secret;
    private HttpServer server;

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

    private void handle(HttpExchange exchange) throws IOException {
        if (!MinecraftTools.isLocalHost(exchange, port)) {
            MinecraftTools.sendJson(exchange, 403, Map.of("error", "Invalid Host header"));
            return;
        }

        String path = exchange.getRequestURI().getPath();
        Optional<MinecraftTool> found = MinecraftTools.list.stream().filter(tool -> tool.getPath().equals(path)).findFirst();
        if (found.isEmpty()) {
            MinecraftTools.sendJson(exchange, 404, Map.of("error", "Unknown path " + path));
            return;
        }

        MinecraftTool tool = found.get();
        if (!tool.isAuthorized(exchange, secret)) {
            MinecraftTools.sendJson(exchange, 403, Map.of("error", "Unauthorized"));
            return;
        }

        JsonObject arguments = readBody(exchange).getAsJsonObject("arguments");
        Map<String, Serializable> result = tool.safeExecute(arguments != null ? arguments : new JsonObject());
        MinecraftTools.sendJson(exchange, result.containsKey("error") ? 400 : 200, result);
    }

    private static JsonObject readBody(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.isEmpty()) return new JsonObject();
        return MinecraftTools.GSON.fromJson(body, JsonObject.class);
    }
}
