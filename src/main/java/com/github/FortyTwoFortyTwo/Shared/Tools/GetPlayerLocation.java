package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class GetPlayerLocation implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Returns the current world, X, Y, Z coordinates of an online player.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("player", stringSchema()));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String playerName = input.has("player") ? input.get("player").getAsString() : "";

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) return Map.of("error", "Player not found or offline");

        return describe(player);
    }

    /** The player's name and location, mutable so callers can add more */
    static Map<String, Serializable> describe(Player player) {
        Location loc = player.getLocation();

        Map<String, Serializable> map = new LinkedHashMap<>();
        map.put("player", player.getName());
        map.put("world", loc.getWorld().getName());
        map.put("x", loc.getX());
        map.put("y", loc.getY());
        map.put("z", loc.getZ());
        map.put("pitch", loc.getPitch());
        map.put("yaw", loc.getYaw());
        return map;
    }
}
