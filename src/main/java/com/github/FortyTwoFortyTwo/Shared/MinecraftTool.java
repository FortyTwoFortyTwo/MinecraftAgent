package com.github.FortyTwoFortyTwo.Shared;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public interface MinecraftTool {

    default String getType() {
        return null;
    }

    default String getDescription() {
        return null;
    }

    default Map<String, Serializable> execute(JsonObject input) {
        return null;
    }

    default String getName() {
        return getClass().getSimpleName();
    }

    /** Whether this tool is blocked for prompts built from untrusted input, e.g. server error messages */
    default boolean isBlockedForUntrusted() {
        return false;
    }

    default String getPath() {
        return "/tools/" + getName().toLowerCase();
    }

    default McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of());
    }

    default Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    default Map<String, Object> stringSchema(String description) {
        return Map.of(
                "type", "string",
                "description", description
        );
    }

    default McpSchema.JsonSchema objectSchema(Map<String, Object> properties) {
        return new McpSchema.JsonSchema("object", properties, new ArrayList<>(properties.keySet()), false, null, null);
    }

    default boolean isAuthorized(HttpExchange exchange, String secret) {
        String header = exchange.getRequestHeaders().getFirst("X-MCP-Secret");
        if (header == null)
            return false;

        // Constant-time comparison so the secret can't be guessed through response timing
        return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }

    default void runTask(Runnable task) {
        runTask(() -> {
            task.run();
            return null; // Callable<Void> must return null
        });
    }

    default <T> T runTask(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();

        // Schedule a sync task
        Bukkit.getScheduler().runTask(com.github.FortyTwoFortyTwo.Shared.MinecraftTools.plugin, () -> {

            try {
                // Call the task. then signal that the sync is done
                future.complete(task.call());
            } catch (Throwable e) {
                // Always complete the future, otherwise the async thread waits forever
                future.completeExceptionally(e);
            }
        });

        // Block the async thread until the sync task completes
        try {
            return future.get(); // waits here until future.complete() is called
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
