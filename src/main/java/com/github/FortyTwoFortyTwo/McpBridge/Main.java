package com.github.FortyTwoFortyTwo.McpBridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.FortyTwoFortyTwo.Shared.MinecraftBridgeClient;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;

import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // Read config from environment variables so you don't hardcode secrets
        // Set these in claude_desktop_config.json under "env"
        String bridgeUrl = System.getenv().getOrDefault("MC_BRIDGE_URL", "http://127.0.0.1:25580");
        String secret    = System.getenv().getOrDefault("MC_BRIDGE_SECRET", "super-secret-password");

        MinecraftBridgeClient client = new MinecraftBridgeClient(bridgeUrl, secret);

        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        var transportProvider = new StdioServerTransportProvider(jsonMapper);

        List<McpServerFeatures.SyncToolSpecification> tools = new ArrayList<>();

        for (com.github.FortyTwoFortyTwo.Shared.MinecraftTool minecraftTool : MinecraftTools.list) {
            // Build the Tool
            Tool tool = Tool.builder()
                    .name(minecraftTool.getName())
                    .description(minecraftTool.getDescription())
                    .inputSchema(minecraftTool.getInputSchema())
                    .build();

            tools.add(McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler(
                    (exchange, request) -> {
                        JsonObject jsonObject = MinecraftTools.GSON.toJsonTree(request).getAsJsonObject();
                        JsonObject result = client.post(minecraftTool.getPath(), jsonObject);
                        return toToolResult(result);
                    }
            ).build());
        }

        McpServer.sync(transportProvider)
                .serverInfo("minecraft-mcp", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(tools)
                .build();

        // McpServer.sync() blocks on stdio until the stream closes
    }

    private static CallToolResult toToolResult(JsonObject json) {
        boolean isError = json.has("error");
        String text = json.toString();

        return CallToolResult.builder().addTextContent(text).isError(isError).build();
    }
}