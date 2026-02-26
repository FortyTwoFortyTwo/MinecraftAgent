package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AnthropicClient {
    private final ObjectMapper MAPPER = new ObjectMapper();

    private final String model;
    private final int maxTokens;
    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();

    public AnthropicClient(FileConfiguration config) {
        this.model = config.getString("anthropic.model");
        this.maxTokens = config.getInt("anthropic.max-tokens");
        this.apiKey = config.getString("anthropic.secret");
    }

    public String sendMessage(CommandSender sender, String userMessage) throws IOException {
        List<JsonObject> messages = new ArrayList<>();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        // Agentic loop — keep going until Claude gives a text response with no tool calls
        while (true) {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("max_tokens", maxTokens);
            body.add("tools", buildToolDefinitions());
            body.add("messages", MinecraftTools.GSON.toJsonTree(messages));
            body.addProperty("system", """
    You are an AI agent embedded in a Minecraft server with full operator-level control.
    Use your available tools proactively to fulfil requests rather than just describing what you would do.
    Your response will be printed directly in Minecraft chat. Keep responses concise and use
    Minecraft's legacy formatting codes (e.g. §a for green, §b for aqua, §l for bold) to make
    your output readable. Do not use markdown — it will not render in game.
    """);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(MinecraftTools.GSON.toJson(body)))
                    .build();

            JsonObject response;
            try {
                var httpResponse = http.send(request, HttpResponse.BodyHandlers.ofString());
                response = MinecraftTools.GSON.fromJson(httpResponse.body(), JsonObject.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted", e);
            }

            if (response.get("type").getAsString().equals("error"))
                return response.get("error").toString();

            String stopReason = response.get("stop_reason").getAsString();
            JsonArray contentArray = response.getAsJsonArray("content");

            // Add Claude's response to message history
            JsonObject assistantMsg = new JsonObject();
            assistantMsg.addProperty("role", "assistant");
            assistantMsg.add("content", contentArray);
            messages.add(assistantMsg);

            if (stopReason.equals("end_turn")) {
                // Final text response — find and return the text block
                for (var element : contentArray) {
                    JsonObject block = element.getAsJsonObject();
                    if (block.get("type").getAsString().equals("text")) {
                        return block.get("text").getAsString();
                    }
                }
            }

            if (stopReason.equals("tool_use")) {
                // Handle all tool calls and collect results
                JsonArray toolResults = new com.google.gson.JsonArray();

                for (var element : contentArray) {
                    JsonObject block = element.getAsJsonObject();
                    if (!block.get("type").getAsString().equals("tool_use")) continue;

                    String toolName = block.get("name").getAsString();
                    String toolUseId = block.get("id").getAsString();
                    JsonObject input = block.getAsJsonObject("input");
                    input.addProperty("sender", sender.getName());

                    // Call the actual tool on the Bukkit bridge
                    JsonElement toolResult = callTool(toolName, input);
                    String stringResult = MinecraftTools.GSON.toJson(toolResult);

                    JsonObject resultBlock = new JsonObject();
                    resultBlock.addProperty("type", "tool_result");
                    resultBlock.addProperty("tool_use_id", toolUseId);
                    resultBlock.addProperty("content", stringResult);
                    toolResults.add(resultBlock);
                }

                // Add tool results as a user message and loop again
                JsonObject toolResultMsg = new JsonObject();
                toolResultMsg.addProperty("role", "user");
                toolResultMsg.add("content", toolResults);
                messages.add(toolResultMsg);
            }
        }
    }

    /** Calls the appropriate Bukkit bridge endpoint for a given tool */
    private JsonElement callTool(String toolName, JsonObject input) {
        for (MinecraftTool tool : MinecraftTools.list) {
            if (!tool.getName().equals(toolName))
                continue;

            Map<String, Serializable> result = tool.execute(input);
            return MinecraftTools.GSON.toJsonTree(result);
        }

        JsonObject element = new JsonObject();
        element.addProperty("error", "Unknown tool name: " + toolName);
        return element;
    }

    private JsonArray buildToolDefinitions() throws JsonProcessingException {

        JsonArray array = new JsonArray();
        for (MinecraftTool tool : MinecraftTools.list) {
            JsonObject object = new JsonObject();
            object.addProperty("name", tool.getName());
            object.addProperty("description", tool.getDescription());

            String json = MAPPER.writeValueAsString(tool.getInputSchema());
            JsonElement schema = MinecraftTools.GSON.fromJson(json, JsonElement.class);
            object.add("input_schema", schema);

            array.add(object);
        }

        return array;
    }
}
