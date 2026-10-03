package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.FortyTwoFortyTwo.MinecraftAgent.types.AgentProgress;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    private final int maxTurns;
    private final int maxTotalTokens;
    private final String apiKey;
    private final HttpClient http = HttpClient.newHttpClient();

    public AnthropicClient(FileConfiguration config) {
        this.model = config.getString("anthropic.model");
        this.maxTokens = config.getInt("anthropic.max-tokens");
        this.maxTurns = config.getInt("anthropic.max-turns");
        this.maxTotalTokens = config.getInt("anthropic.max-total-tokens");
        this.apiKey = config.getString("anthropic.secret");
    }

    /** Runs a prompt, only offering and allowing the given tools */
    public void sendMessage(CommandSender sender, String userMessage, String system, List<MinecraftTool> tools) {
        sendMessage(sender, userMessage, system, tools, AgentProgress.NONE);
    }

    /** Same as above, reporting each turn and tool call to progress */
    public void sendMessage(CommandSender sender, String userMessage, String system, List<MinecraftTool> tools, AgentProgress progress) {
        List<JsonObject> messages = new ArrayList<>();

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userMessage);
        messages.add(userMsg);

        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {
            try {
                run(sender, messages, system, tools, progress);
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                progress.finish();
            }
        });
    }

    private void run(CommandSender sender, List<JsonObject> messages, String system, List<MinecraftTool> tools, AgentProgress progress) throws IOException {
        int totalTokensUsed = 0;

        for (int turn = 0; turn < maxTurns; turn++) {
            if (totalTokensUsed >= maxTotalTokens) {
                sender.sendMessage(Component.text("Token budget reached (" + totalTokensUsed + " tokens)", NamedTextColor.RED));
                return;
            }

            progress.step(turn + 1, maxTurns, "Thinking");
            JsonObject response = doRequest(messages, system, tools);

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

                    System.out.println("Execute " + type + " (" + block + ")");

                    if (type.equals("text")) {
                        sender.sendMessage(block.get("text").getAsString());
                    } else if (type.equals("tool_use")) {
                        String toolName = block.get("name").getAsString();
                        String toolUseId = block.get("id").getAsString();
                        JsonObject input = block.getAsJsonObject("input");
                        input.addProperty("sender", sender.getName());
                        progress.step(turn + 1, maxTurns, "Using " + toolName);

                        // Call the actual tool on the Bukkit bridge
                        JsonElement toolResult = callTool(tools, toolName, input);

                        JsonObject resultBlock = new JsonObject();
                        resultBlock.addProperty("type", "tool_result");
                        resultBlock.addProperty("tool_use_id", toolUseId);
                        resultBlock.addProperty("content", MinecraftTools.GSON.toJson(toolResult));
                        toolResults.add(resultBlock);
                    }
                }

                if (stopReason.equals("end_turn")) {
                    // Log total tokens used and exit out
                    sender.sendMessage(Component.text("[Tokens used: " + totalTokensUsed + "]", NamedTextColor.GRAY));
                    return;
                }

                // Add tool results as a user message and loop again
                JsonObject toolResultMsg = new JsonObject();
                toolResultMsg.addProperty("role", "user");
                toolResultMsg.add("content", toolResults);
                messages.add(toolResultMsg);
            } else if (stopReason.equals("max_tokens")) {
                sender.sendMessage(Component.text("Max tokens reached", NamedTextColor.RED));
                return;
            } else {
                System.err.println("Unknown stop_reason " + stopReason);
            }
        }

        sender.sendMessage(Component.text("Turn limit reached (" + maxTurns + " turns, " + totalTokensUsed + " tokens)", NamedTextColor.RED));
    }

    private JsonObject doRequest(List<JsonObject> messages, String system, List<MinecraftTool> tools) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", maxTokens);
        body.add("tools", buildToolDefinitions(tools));
        body.add("messages", MinecraftTools.GSON.toJsonTree(messages));
        body.addProperty("system", system + """
    All of your text responses will be printed directly in Minecraft chat. Keep responses concise and use
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

        try {
            var httpResponse = http.send(request, HttpResponse.BodyHandlers.ofString());
            return MinecraftTools.GSON.fromJson(httpResponse.body(), JsonObject.class);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    /** Calls the appropriate Bukkit bridge endpoint for a given tool, if it's one this run is allowed to use */
    private JsonElement callTool(List<MinecraftTool> tools, String toolName, JsonObject input) {
        for (MinecraftTool tool : tools) {
            if (!tool.getName().equals(toolName))
                continue;

            // Same as MinecraftMcpServer, a failing tool is reported back to the model rather than ending the run
            Map<String, Serializable> result;
            try {
                result = tool.execute(input);
            } catch (Exception e) {
                result = Map.of("error", String.valueOf(e));
            }
            return MinecraftTools.GSON.toJsonTree(result);
        }

        JsonObject element = new JsonObject();
        element.addProperty("error", "Unknown tool name: " + toolName);
        return element;
    }

    private JsonArray buildToolDefinitions(List<MinecraftTool> tools) throws JsonProcessingException {

        JsonArray array = new JsonArray();
        for (MinecraftTool tool : tools) {
            JsonObject object = new JsonObject();
            object.addProperty("name", tool.getName());

            if (tool.getDescription() != null)
                object.addProperty("description", tool.getDescription());

            if (tool.getType() != null) {
                object.addProperty("type", tool.getType());
            } else {
                String json = MAPPER.writeValueAsString(tool.getInputSchema());
                JsonElement schema = MinecraftTools.GSON.fromJson(json, JsonElement.class);
                object.add("input_schema", schema);
            }

            array.add(object);
        }

        return array;
    }
}
