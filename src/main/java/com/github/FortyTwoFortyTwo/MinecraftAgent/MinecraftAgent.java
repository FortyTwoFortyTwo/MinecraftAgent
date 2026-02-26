package com.github.FortyTwoFortyTwo.MinecraftAgent;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class MinecraftAgent extends JavaPlugin {

    private BridgeHttpServer bridgeServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int port = getConfig().getInt("bridge.port", 25580);
        String secret = getConfig().getString("bridge.secret", "change-me");

        bridgeServer = new BridgeHttpServer(this, port, secret);

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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("mcpbridge")) return false;
        if (!sender.hasPermission("mcpbridge.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6Usage: /mcpbridge <reload|status>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                reloadConfig();
                sender.sendMessage("§aConfig reloaded. Restart the plugin to apply port/secret changes.");
                break;
            case "status":
                sender.sendMessage("§aMCP Bridge is running on port §f" + getConfig().getInt("bridge.port", 25580));
                break;
            default:
                sender.sendMessage("§cUnknown subcommand. Use: reload, status");
        }
        return true;
    }
}