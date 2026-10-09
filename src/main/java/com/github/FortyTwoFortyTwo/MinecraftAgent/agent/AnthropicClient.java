package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AnthropicClient {

    private static final String FORMATTING_PROMPT = """
            All of your text responses will be printed directly in Minecraft chat. Keep responses concise and format them
            with MiniMessage tags to make your output readable, e.g. <green>success</green>, <red>problems</red>,
            <gold>, <aqua>, <bold>, <italic>, <newline>. Escape a literal < as \\<. Do not use markdown, it will not render in game.
            """;

    private final HttpClient http = HttpClient.newHttpClient();

    private static FileConfiguration config() {
        return MinecraftTools.plugin.getConfig();
    }

    /** Runs a prompt, only offering and allowing the given tools, plus web search and fetch */
    public void sendMessage(CommandSender sender, String userMessage, String system, List<MinecraftTool> tools) {
        sendMessage(sender, userMessage, system, tools, AgentProgress.NONE);
    }

    /** Same as above, reporting each turn and tool call to progress */
    public void sendMessage(CommandSender sender, String userMessage, String system, List<MinecraftTool> tools, AgentProgress progress) {
        List<JsonObject> messages = new ArrayList<>();
        messages.add(message("user", userMessage));
        ToolGuard guard = new ToolGuard(sender.getName());

        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {
            try {
                run(sender, messages, system, tools, guard, progress);
            } catch (Exception e) {
                logger().log(Level.WARNING, "Anthropic API run failed", e);
                sender.sendMessage(Component.text("Agent request failed: " + e, NamedTextColor.RED));
            } finally {
                progress.finish();
            }
        });
    }

    private void run(CommandSender sender, List<JsonObject> messages, String system, List<MinecraftTool> tools, ToolGuard guard, AgentProgress progress) throws IOException {
        int maxTurns = config().getInt("anthropic.max-turns");
        int maxTotalTokens = config().getInt("anthropic.max-total-tokens");
        int totalTokensUsed = 0;

        for (int turn = 0; turn < maxTurns; turn++) {
            if (totalTokensUsed >= maxTotalTokens) {
                sender.sendMessage(Component.text("Token budget reached (" + totalTokensUsed + " tokens)", NamedTextColor.RED));
                return;
            }

            progress.step(turn + 1, maxTurns, AgentProgress.thinking());
            JsonObject response = doRequest(messages, system, tools);

            if (response.get("type").getAsString().equals("error")) {
                sender.sendMessage(Component.text(response.getAsJsonObject("error").get("message").getAsString(), NamedTextColor.RED));
                return;
            }

            // Accumulate tokens used in this API call
            JsonObject usage = response.getAsJsonObject("usage");
            int inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").getAsInt() : 0;
            int outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").getAsInt() : 0;
            totalTokensUsed += inputTokens + outputTokens;
            logger().fine("Accumulated tokens: " + totalTokensUsed + " (input: " + inputTokens + ", output: " + outputTokens + ")");

            String stopReason = response.get("stop_reason").getAsString();
            JsonArray contentArray = response.getAsJsonArray("content");

            // Add Claude's response to message history
            messages.add(message("assistant", contentArray));

            // Show any text, and handle all tool calls collecting their results
            JsonArray toolResults = new JsonArray();
            for (JsonElement element : contentArray) {
                JsonObject block = element.getAsJsonObject();
                switch (block.get("type").getAsString()) {
                    case "text" -> sender.sendMessage(MinecraftTools.MINI_MESSAGE.deserialize(block.get("text").getAsString(), MinecraftTools.playerTexts(guard.texts())));
                    case "tool_use" -> {
                        String toolName = block.get("name").getAsString();
                        JsonObject input = block.getAsJsonObject("input");
                        progress.step(turn + 1, maxTurns, "Using " + toolName);
                        logger().info("Using " + toolName + " " + input);

                        JsonObject resultBlock = new JsonObject();
                        resultBlock.addProperty("type", "tool_result");
                        resultBlock.addProperty("tool_use_id", block.get("id").getAsString());
                        resultBlock.addProperty("content", MinecraftTools.GSON.toJson(callTool(tools, guard, toolName, input)));
                        toolResults.add(resultBlock);
                    }
                }
            }

            switch (stopReason) {
                case "tool_use" -> messages.add(message("user", toolResults));
                // A long running server tool like web search was paused, sending the conversation back as is continues it
                case "pause_turn" -> {}
                case "end_turn" -> {
                    sender.sendMessage(Component.text("[Tokens used: " + totalTokensUsed + "]", NamedTextColor.GRAY));
                    return;
                }
                case "max_tokens" -> {
                    sender.sendMessage(Component.text("Max tokens reached", NamedTextColor.RED));
                    return;
                }
                default -> {
                    // e.g. refusal, sending the conversation back as is would only get the same answer
                    sender.sendMessage(Component.text("Agent stopped: " + stopReason, NamedTextColor.RED));
                    return;
                }
            }
        }

        sender.sendMessage(Component.text("Turn limit reached (" + maxTurns + " turns, " + totalTokensUsed + " tokens)", NamedTextColor.RED));
    }

    private JsonObject doRequest(List<JsonObject> messages, String system, List<MinecraftTool> tools) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", config().getString("anthropic.model"));
        body.addProperty("max_tokens", config().getInt("anthropic.max-tokens"));
        body.add("tools", buildToolDefinitions(tools));
        body.add("messages", MinecraftTools.GSON.toJsonTree(messages));
        body.addProperty("system", system + "\n" + FORMATTING_PROMPT);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("x-api-key", config().getString("anthropic.secret"))
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(MinecraftTools.GSON.toJson(body)))
                .build();

        HttpResponse<String> httpResponse;
        try {
            httpResponse = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }

        // Anything in front of the API, e.g. a proxy or an outage page, may not answer in JSON
        JsonElement json;
        try {
            json = MinecraftTools.GSON.fromJson(httpResponse.body(), JsonElement.class);
        } catch (RuntimeException e) {
            json = null;
        }

        if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("type"))
            throw new IOException("Unexpected response (HTTP " + httpResponse.statusCode() + ")");

        return json.getAsJsonObject();
    }

    /** Calls the given tool, if it's one this run is allowed to use */
    private static Map<String, Serializable> callTool(List<MinecraftTool> tools, ToolGuard guard, String toolName, JsonObject input) {
        return MinecraftTools.find(toolName)
                .filter(tools::contains)
                .map(tool -> guard.call(tool, input))
                .orElse(Map.of("error", "Unknown tool name: " + toolName));
    }

    private static JsonArray buildToolDefinitions(List<MinecraftTool> tools) {
        JsonArray array = new JsonArray();
        for (MinecraftTool tool : tools) {
            JsonObject object = new JsonObject();
            object.addProperty("name", tool.getName());

            if (tool.getDescription() != null)
                object.addProperty("description", tool.getDescription());

            // Anthropic-defined tools come with their own schema
            if (tool.getType() != null)
                object.addProperty("type", tool.getType());
            else
                object.add("input_schema", tool.getInputSchemaJson());

            array.add(object);
        }

        // Server tools run on Anthropic's side, their results come back in the same response rather than as tool_use
        array.add(serverTool("web_search_20260209", "web_search"));
        array.add(serverTool("web_fetch_20260209", "web_fetch"));

        return array;
    }

    private static JsonObject serverTool(String type, String name) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        object.addProperty("name", name);
        return object;
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private static JsonObject message(String role, JsonArray content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.add("content", content);
        return message;
    }

    private static Logger logger() {
        return MinecraftTools.plugin.getLogger();
    }
}
