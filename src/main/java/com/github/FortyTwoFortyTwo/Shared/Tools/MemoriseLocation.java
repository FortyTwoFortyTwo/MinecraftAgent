package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MemorisedLocations;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.IOException;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class MemoriseLocation implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Memorises a position, or an area between two corners, under a name so later prompts know about it, " +
                "e.g. when a player asks to remember their house, tells you where their base is, or you build something worth coming back to. " +
                "Saving with an existing name moves it, so do that whenever a memorised place has moved, e.g. a player says their house is somewhere else now, " +
                "or you moved, rebuilt or resized something at a memorised place. " +
                "Get coordinates with GetPlayerPrompt or GetPlayerLocation first if the player means where they're standing.";
    }

    @Override
    public boolean isBlockedForUntrusted() {
        return true;
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(
                Map.of(
                        "name", stringSchema("1 to 32 lowercase letters, digits or underscores, describing the place and whose it is, e.g. steve_house"),
                        "world", stringSchema("World name, e.g. from GetPlayerLocation"),
                        "x", numberSchema("X of the position, or the area's first corner"),
                        "y", numberSchema("Y of the position, or the area's first corner"),
                        "z", numberSchema("Z of the position, or the area's first corner")),
                Map.of(
                        "x2", numberSchema("X of the area's second corner, only for an area"),
                        "y2", numberSchema("Y of the area's second corner, only for an area"),
                        "z2", numberSchema("Z of the area's second corner, only for an area")));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) throws IOException {
        String name = MemorisedLocations.checkName(input.has("name") ? input.get("name").getAsString() : "");

        String worldName = input.has("world") ? input.get("world").getAsString() : "";
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            return Map.of("error", "World '" + worldName + "' not found");

        double[] corner1 = MemorisedLocations.readCorner(input, "");
        if (corner1 == null)
            return Map.of("error", "Missing 'x', 'y' and 'z' fields");

        double[] corner2 = MemorisedLocations.readCorner(input, "2");

        // Provided by whoever runs the agent, never the model, and missing over the HTTP bridge
        String savedBy = input.has("sender") ? input.get("sender").getAsString() : null;

        Map<String, Serializable> replaced = MemorisedLocations.save(name, world.getName(), corner1, corner2, savedBy);

        Map<String, Serializable> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("name", name);
        result.put("type", corner2 == null ? "position" : "area");
        if (replaced != null)
            result.put("replaced", (Serializable) replaced);

        return result;
    }
}
