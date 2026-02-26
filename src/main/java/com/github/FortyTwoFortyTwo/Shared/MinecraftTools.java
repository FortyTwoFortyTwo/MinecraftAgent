package com.github.FortyTwoFortyTwo.Shared;

import com.google.gson.JsonObject;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.List;
import java.util.Map;

/**
 * Defines all MCP tools exposed to Claude Desktop.
 * Each tool forwards its call to the Bukkit plugin via MinecraftBridgeClient.
 */
public class MinecraftTools {

    // -------------------------------------------------------------------------
    // get_online_players
    // -------------------------------------------------------------------------
    public static McpServerFeatures.SyncToolSpecification getOnlinePlayers(MinecraftBridgeClient client) {
        var schema = new JsonSchema("object", Map.of(), List.of(), false, null, null);

        // Build the Tool
        Tool tool = Tool.builder()
                .name("get_online_players")
                .description("Returns the names and count of all currently online players.")
                .inputSchema(schema)   // plain JSON string
                .build();

        return McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler(
                (exchange, args) -> {
                    JsonObject result = client.get("/tools/get_online_players");
                    return toToolResult(result);
                }
        ).build();
    }

    // -------------------------------------------------------------------------
    // broadcast_message
    // -------------------------------------------------------------------------
    public static McpServerFeatures.SyncToolSpecification broadcastMessage(MinecraftBridgeClient client) {
        var messageProperty = new McpSchema.JsonSchema("string", null, null, null, null, null);
        var schema = new McpSchema.JsonSchema(
                "object",
                Map.of("message", messageProperty),
                List.of("message"),  // required
                false, null, null
        );

        Tool tool = Tool.builder()
                .name("broadcast_message")
                .description("Broadcasts a message to all players currently online on the Minecraft server.")
                .inputSchema(schema)   // plain JSON string
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, args) -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("message", (String) args.get("message"));
                    JsonObject result = client.post("/tools/broadcast_message", body);
                    return toToolResult(result);
                }
        );
    }

    // -------------------------------------------------------------------------
    // get_player_location
    // -------------------------------------------------------------------------
    public static McpServerFeatures.SyncToolSpecification getPlayerLocation(MinecraftBridgeClient client) {
        var playerProperty = new McpSchema.JsonSchema("string", null, null, null, null, null);
        var schema = new McpSchema.JsonSchema(
                "object",
                Map.of("player", playerProperty),
                List.of("player"),
                false, null, null
        );

        Tool tool = Tool.builder()
                .name("get_player_location")
                .description("Returns the current world, X, Y, Z coordinates of an online player.")
                .inputSchema(schema)   // plain JSON string
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, args) -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("player", (String) args.get("player"));
                    JsonObject result = client.post("/tools/get_player_location", body);
                    return toToolResult(result);
                }
        );
    }

    // -------------------------------------------------------------------------
    // run_console_command
    // -------------------------------------------------------------------------
    public static McpServerFeatures.SyncToolSpecification runConsoleCommand(MinecraftBridgeClient client) {
        var commandProperty = new McpSchema.JsonSchema("string", null, null, null, null, null);
        var schema = new McpSchema.JsonSchema(
                "object",
                Map.of("command", commandProperty),
                List.of("command"),
                false, null, null
        );

        Tool tool = Tool.builder()
                .name("run_console_command")
                .description("Runs a command on the Minecraft server console with operator-level privileges. Use with caution.")
                .inputSchema(schema)   // plain JSON string
                .build();

        return new McpServerFeatures.SyncToolSpecification(
                tool,
                (exchange, args) -> {
                    JsonObject body = new JsonObject();
                    body.addProperty("command", (String) args.get("command"));
                    JsonObject result = client.post("/tools/run_console_command", body);
                    return toToolResult(result);
                }
        );
    }

    // -------------------------------------------------------------------------
    // Utility: convert a JsonObject response into an MCP CallToolResult
    // -------------------------------------------------------------------------
    private static CallToolResult toToolResult(JsonObject json) {
        boolean isError = json.has("error");
        String text = json.toString();
        return new CallToolResult(List.of(new TextContent(text)), isError);
    }
}