package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.Map;

public class GetPlayerPrompt implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Returns info about the player who's sending the prompt.";
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String playerName = input.get("sender").getAsString();  // provided by whoever runs the agent, never the model

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) return Map.of("error", "Player not found or offline");

        Map<String, Serializable> result = GetPlayerLocation.describe(player);
        result.put("uuid", player.getUniqueId().toString());
        return result;
    }
}
