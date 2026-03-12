package com.github.FortyTwoFortyTwo.MinecraftAgent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AnthropicClient;
import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.BridgeHttpServer;
import com.github.FortyTwoFortyTwo.MinecraftAgent.commands.AgentCommand;
import commands.IDECommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

public class MinecraftAgent extends JavaPlugin {

    private BridgeHttpServer bridgeServer;

    @Override
    public void onEnable() {
        MinecraftTools.plugin = this;

        saveDefaultConfig();

        int port = getConfig().getInt("bridge.port", 25580);
        String secret = getConfig().getString("bridge.secret", "super-secret-password");

        bridgeServer = new BridgeHttpServer(this, port, secret);
        AnthropicClient anthropic = new AnthropicClient(getConfig());

        CommandMap commandMap = Bukkit.getServer().getCommandMap();

        commandMap.register("agent", new AgentCommand(anthropic));
        commandMap.register("ide", new IDECommand(getConfig()));

        try {
            bridgeServer.start();
            getLogger().info("MCP Bridge HTTP server started on localhost:" + port);
        } catch (Exception e) {
            getLogger().severe("Failed to start MCP Bridge server: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (bridgeServer != null) {
            bridgeServer.stop();
            getLogger().info("MCP Bridge HTTP server stopped.");
        }
    }
}