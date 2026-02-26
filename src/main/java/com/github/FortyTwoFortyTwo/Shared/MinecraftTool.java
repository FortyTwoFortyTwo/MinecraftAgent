package com.github.FortyTwoFortyTwo.Shared;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

public interface MinecraftTool {

    String getDescription();

    Map<String, Serializable> execute(JsonObject input);

    default String getName() {
        return getClass().getSimpleName();
    }

    default String getPath() {
        return "/tools/" + getName().toLowerCase();
    }

    default McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of());
    }

    default McpSchema.JsonSchema stringSchema() {
        return new McpSchema.JsonSchema("string", null, null, null, null, null);
    }

    default McpSchema.JsonSchema objectSchema(Map<String, Object> properties) {
        return new McpSchema.JsonSchema("object", properties, new ArrayList<>(properties.keySet()), false, null, null);
    }

    default boolean isAuthorized(HttpExchange exchange, String secret) {
        String header = exchange.getRequestHeaders().getFirst("X-MCP-Secret");
        return secret.equals(header);
    }

    default void throwError(String message) {
        try {
            MinecraftTools.sendJson(MinecraftTools.exchange, 400, Map.of("error", message));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
