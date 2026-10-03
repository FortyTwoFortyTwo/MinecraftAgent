package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.Serializable;
import java.util.Map;

public class GetWorldInfo implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Returns the main world's name, time of day, weather, player count and seed.";
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        World world = Bukkit.getWorlds().getFirst();
        return Map.of(
                "name", world.getName(),
                "time", world.getTime(),
                "isDay", world.getTime() < 13000,
                "isStorming", world.isThundering(),
                "isRaining", world.hasStorm(),
                "playerCount", world.getPlayers().size(),
                "seed", world.getSeed()
        );
    }
}
