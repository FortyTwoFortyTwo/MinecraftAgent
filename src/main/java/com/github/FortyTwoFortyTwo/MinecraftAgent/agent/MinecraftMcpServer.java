package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP server over HTTP (Streamable HTTP, plain JSON responses) offering the Minecraft tools to a single Claude Code run.
 * Binds a free localhost port with its own random secret, and every tool call is made on behalf of one sender.
 */
public class MinecraftMcpServer {

    private static final String PATH = "/mcp";

    private final String sender;
    private final String secret;
    private final Gson gson = new Gson();

    private HttpServer server;
    private ExecutorService executor;

    public MinecraftMcpServer(String sender) {
        this.sender = sender;

        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        this.secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void start() throws IOException {
        // Bind only to localhost, on whichever port is free
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newFixedThreadPool(2);
        server.setExecutor(executor);
        server.createContext(PATH, this::handle);
        server.start();
    }

    public void stop() {
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
    }

    public String getUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
    }

    /** Sent by the client in the X-MCP-Secret header */
    public String getSecret() {
        return secret;
    }

    private void handle(HttpExchange exchange) throws IOException {

        // Reject requests addressed to any other host name, blocking DNS rebinding from browser pages
        int port = server.getAddress().getPort();
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || !(host.equalsIgnoreCase("127.0.0.1:" + port) || host.equalsIgnoreCase("localhost:" + port))) {
            MinecraftTools.sendJson(exchange, 403, Map.of("error", "Invalid Host header"));
            return;
        }

        // Every tool shares the same secret check
        if (!MinecraftTools.list.getFirst().isAuthorized(exchange, secret)) {
            MinecraftTools.sendJson(exchange, 403, Map.of("error", "Unauthorized"));
            return;
        }

        // Only plain request/response, no server-sent event stream
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.getResponseHeaders().set("Allow", "POST");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        JsonElement body;
        try {
            body = gson.fromJson(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonElement.class);
        } catch (JsonParseException e) {
            body = null;
        }

        if (body == null || !body.isJsonObject()) {
            MinecraftTools.sendJson(exchange, 400, rpcError(JsonNull.INSTANCE, -32700, "Parse error"));
            return;
        }

        // Notifications get no response
        JsonObject response = handleRpc(body.getAsJsonObject());
        if (response == null) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }

        MinecraftTools.sendJson(exchange, 200, response);
    }

    /** Handles one JSON-RPC message, returning null for notifications */
    private JsonObject handleRpc(JsonObject request) {
        if (!request.has("id") || !request.has("method"))
            return null;

        JsonElement id = request.get("id");
        JsonObject params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params")
                : new JsonObject();

        JsonObject result = new JsonObject();
        switch (request.get("method").getAsString()) {
            case "initialize" -> {
                // Agree to whichever protocol version the client asks for, only basic tool calls are used
                result.add("protocolVersion", params.has("protocolVersion") ? params.get("protocolVersion") : new JsonPrimitive("2025-06-18"));

                JsonObject capabilities = new JsonObject();
                capabilities.add("tools", new JsonObject());
                result.add("capabilities", capabilities);

                JsonObject serverInfo = new JsonObject();
                serverInfo.addProperty("name", "minecraft-mcp");
                serverInfo.addProperty("version", "1.0.0");
                result.add("serverInfo", serverInfo);
            }
            case "ping" -> {
            }
            case "tools/list" -> {
                JsonArray tools = new JsonArray();
                for (MinecraftTool tool : MinecraftTools.list) {
                    JsonObject definition = new JsonObject();
                    definition.addProperty("name", tool.getName());
                    definition.addProperty("description", tool.getDescription());
                    definition.add("inputSchema", gson.toJsonTree(tool.getInputSchema()));
                    tools.add(definition);
                }
                result.add("tools", tools);
            }
            case "tools/call" -> {
                String name = params.has("name") ? params.get("name").getAsString() : "";
                MinecraftTool tool = MinecraftTools.list.stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
                if (tool == null)
                    return rpcError(id, -32602, "Unknown tool " + name);

                JsonObject arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                        ? params.getAsJsonObject("arguments")
                        : new JsonObject();

                // Same as AnthropicClient, never trust a sender the model made up
                arguments.addProperty("sender", sender);

                Map<String, Serializable> output;
                try {
                    output = tool.execute(arguments);
                } catch (Exception e) {
                    output = Map.of("error", String.valueOf(e));
                }

                JsonObject content = new JsonObject();
                content.addProperty("type", "text");
                content.addProperty("text", gson.toJson(output));

                JsonArray contents = new JsonArray();
                contents.add(content);

                result.add("content", contents);
                result.addProperty("isError", output == null || output.containsKey("error"));
            }
            default -> {
                return rpcError(id, -32601, "Method not found");
            }
        }

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        return response;
    }

    private static JsonObject rpcError(JsonElement id, int code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);

        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("error", error);
        return response;
    }
}
