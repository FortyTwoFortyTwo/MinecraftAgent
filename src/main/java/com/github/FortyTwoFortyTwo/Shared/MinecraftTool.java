package com.github.FortyTwoFortyTwo.Shared;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
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

    /** Runs the tool, returning an "error" key for anything the model got wrong. Call safeExecute rather than this. */
    Map<String, Serializable> execute(JsonObject input) throws Exception;

    /** Same as execute, but a failing tool is reported back as an error rather than thrown, so the model can retry */
    default Map<String, Serializable> safeExecute(JsonObject input) {
        try {
            return execute(input);
        } catch (Exception e) {
            return Map.of("error", String.valueOf(e));
        }
    }

    default String getName() {
        return getClass().getSimpleName();
    }

    /** Whether this tool is blocked for prompts built from untrusted input, e.g. server error messages */
    default boolean isBlockedForUntrusted() {
        return false;
    }

    /** Whether this tool is disabled for the rest of a run once any tool has returned text players could have written */
    default boolean isPrivileged() {
        return false;
    }

    /**
     * Whether this call returns text players could have written that the agent has to read as is, e.g. a viewed file, which could carry instructions.
     * Text the agent doesn't need to read should be returned as PlayerText instead, which keeps it hidden.
     */
    default boolean returnsPlayerText(JsonObject input) {
        return false;
    }

    default String getPath() {
        return "/tools/" + getName().toLowerCase();
    }

    default McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of());
    }

    /** The input schema as JSON, for tool definitions sent to the model */
    default JsonElement getInputSchemaJson() {
        return MinecraftTools.GSON.toJsonTree(getInputSchema());
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

    default Map<String, Object> numberSchema(String description) {
        return Map.of(
                "type", "number",
                "description", description
        );
    }

    /** Schema where every property is required */
    default McpSchema.JsonSchema objectSchema(Map<String, Object> properties) {
        return objectSchema(properties, Map.of());
    }

    default McpSchema.JsonSchema objectSchema(Map<String, Object> required, Map<String, Object> optional) {
        Map<String, Object> properties = new HashMap<>(required);
        properties.putAll(optional);
        return new McpSchema.JsonSchema("object", properties, new ArrayList<>(required.keySet()), false, null, null);
    }

    default boolean isAuthorized(HttpExchange exchange, String secret) {
        return MinecraftTools.hasSecret(exchange, secret);
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
        Bukkit.getScheduler().runTask(MinecraftTools.plugin, () -> {

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
