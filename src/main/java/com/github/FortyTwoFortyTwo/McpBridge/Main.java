package com.github.FortyTwoFortyTwo.McpBridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.FortyTwoFortyTwo.Shared.MinecraftBridgeClient;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.*;

public class Main {

    public static void main(String[] args) {
        // Read config from environment variables so you don't hardcode secrets
        // Set these in claude_desktop_config.json under "env"
        String bridgeUrl = System.getenv().getOrDefault("MC_BRIDGE_URL", "http://127.0.0.1:25580");
        String secret    = System.getenv().getOrDefault("MC_BRIDGE_SECRET", "super-secret-password");

        MinecraftBridgeClient client = new MinecraftBridgeClient(bridgeUrl, secret);

        var jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        var transportProvider = new StdioServerTransportProvider(jsonMapper);

        McpServer.sync(transportProvider)
                .serverInfo("minecraft-mcp", "1.0.0")
                .capabilities(ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(
                        MinecraftTools.getOnlinePlayers(client),
                        MinecraftTools.broadcastMessage(client),
                        MinecraftTools.getPlayerLocation(client),
                        MinecraftTools.runConsoleCommand(client)
                )
                .build();

        // McpServer.sync() blocks on stdio until the stream closes
    }
}