package com.github.FortyTwoFortyTwo.MinecraftAgent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AnthropicClient;
import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.BridgeHttpServer;
import com.github.FortyTwoFortyTwo.MinecraftAgent.commands.AgentCommand;
import commands.ClaudeCommand;
import appender.ErrorCatcherAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.util.Base64;

public class MinecraftAgent extends JavaPlugin {

    private BridgeHttpServer bridgeServer;

    private ErrorCatcherAppender errorAppender;

    @Override
    public void onEnable() {
        MinecraftTools.plugin = this;

        saveDefaultConfig();

        AnthropicClient anthropic = new AnthropicClient(getConfig());

        // Catch any errors
        errorAppender = new ErrorCatcherAppender(anthropic, getConfig());
        errorAppender.start();

        // Attach to the root logger so ALL plugins are covered
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Logger rootLogger = ctx.getRootLogger();
        rootLogger.addAppender(errorAppender);


        // Register commands
        CommandMap commandMap = Bukkit.getServer().getCommandMap();
        commandMap.register("agent", new AgentCommand(anthropic));
        commandMap.register("agent", new ClaudeCommand(getConfig()));

        if (getConfig().getBoolean("bridge.enabled", true)) {
            startBridge();
        } else {
            getLogger().info("MCP Bridge HTTP server is disabled in config.yml.");
        }
    }

    private void startBridge() {
        int port = getConfig().getInt("bridge.port", 25580);
        String secret = getConfig().getString("bridge.secret", "");

        // Never run the bridge with an empty or publicly known secret
        if (secret.isBlank() || secret.equals("super-secret-password")) {
            byte[] bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            getConfig().set("bridge.secret", secret);
            saveConfig();
            getLogger().warning("Generated a new random bridge.secret in config.yml, set MC_BRIDGE_SECRET to it for McpBridge.");
        }

        bridgeServer = new BridgeHttpServer(port, secret);

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

        if (errorAppender != null) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.getRootLogger().removeAppender(errorAppender);
            errorAppender.stop();
        }
    }
}