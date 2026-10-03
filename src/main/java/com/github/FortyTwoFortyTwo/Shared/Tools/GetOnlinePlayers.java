package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.Map;
import java.util.stream.Collectors;

public class GetOnlinePlayers implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Returns the names and count of all currently online players.";
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {

        String players = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.joining(", "));

        return Map.of(
                "players", players,
                "count", Bukkit.getOnlinePlayers().size()
        );
    }
}
