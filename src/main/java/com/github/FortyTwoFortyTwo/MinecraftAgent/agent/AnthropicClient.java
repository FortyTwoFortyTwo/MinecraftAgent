package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
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
    private int totalTokensUsed = 0;

    public AnthropicClient(FileConfiguration config) {
        this.model = config.getString("anthropic.model");
        this.maxTokens = config.getInt("anthropic.max-tokens");
        this.apiKey = config.getString("anthropic.secret");
    }

    public void sendMessage(CommandSender sender, String userMessage) {
        totalTokensUsed = 0;    // TODO multiple messages at once
        List<JsonObject> messages = new ArrayList<>();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        Bukkit.getScheduler().runTaskLater(MinecraftTools.plugin, () -> {
            try {
                doRequest(sender, messages);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 1L);
    }

    private void doRequest(CommandSender sender, List<JsonObject> messages) throws IOException {
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
                .timeout(java.time.Duration.ofSeconds(60))
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

        if (response.get("type").getAsString().equals("error")) {
            sender.sendMessage(response.get("error").getAsJsonObject().get("message").getAsString());
            return;
        }

        // Accumulate tokens used in this API call
        JsonObject usage = response.getAsJsonObject("usage");

        int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
        int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
        int tokensUsed = inputTokens + outputTokens;
        totalTokensUsed += tokensUsed;

        String stopReason = response.get("stop_reason").getAsString();
        JsonArray contentArray = response.getAsJsonArray("content");

        System.out.println("Accumulated tokens: " + totalTokensUsed + " (input: " + inputTokens + ", output: " + outputTokens + ")");

        // Add Claude's response to message history
        JsonObject assistantMsg = new JsonObject();
        assistantMsg.addProperty("role", "assistant");
        assistantMsg.add("content", contentArray);
        messages.add(assistantMsg);

        if (stopReason.equals("tool_use") || stopReason.equals("end_turn")) {
            // Handle all tool calls and collect results
            JsonArray toolResults = new com.google.gson.JsonArray();

            for (var element : contentArray) {
                JsonObject block = element.getAsJsonObject();
                String type = block.get("type").getAsString();

                if (type.equals("text")) {
                    sender.sendMessage(block.get("text").getAsString());
                } else if (type.equals("tool_use")) {
                    String toolName = block.get("name").getAsString();
                    String toolUseId = block.get("id").getAsString();
                    JsonObject input = block.getAsJsonObject("input");
                    input.addProperty("sender", sender.getName());

                    // Call the actual tool on the Bukkit bridge
                    JsonElement toolResult = callTool(toolName, input);

                    JsonObject resultBlock = new JsonObject();
                    resultBlock.addProperty("type", "tool_result");
                    resultBlock.addProperty("tool_use_id", toolUseId);
                    resultBlock.addProperty("content", MinecraftTools.GSON.toJson(toolResult));
                    toolResults.add(resultBlock);
                }
            }

            if (stopReason.equals("end_turn")) {
                // Log total tokens used and exit out
                sender.sendMessage("§7[Tokens used: " + totalTokensUsed + "]");
                return;
            }

            // Add tool results as a user message and loop again
            JsonObject toolResultMsg = new JsonObject();
            toolResultMsg.addProperty("role", "user");
            toolResultMsg.add("content", toolResults);
            messages.add(toolResultMsg);
        }

        Bukkit.getScheduler().runTaskLater(MinecraftTools.plugin, () -> {
            try {
                doRequest(sender, messages);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, 1L);
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
