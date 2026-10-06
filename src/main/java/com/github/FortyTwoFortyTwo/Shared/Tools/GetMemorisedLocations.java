package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MemorisedLocations;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.Serializable;
import java.util.Map;
import java.util.regex.Pattern;

public class GetMemorisedLocations implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Returns every position and area memorised with MemoriseLocation by name, with its world, coordinates and the player who saved it. " +
                "Call this early whenever a prompt involves the world, not only when it names a place, and use them as context, " +
                "e.g. where a player means by home or here, where to put something new, or which areas belong to other players.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(
                Map.of(),
                Map.of("filter", stringSchema("Optional filter on names by regex, e.g. steve or _farm$")));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String filter = input.has("filter") ? input.get("filter").getAsString() : "";
        Pattern pattern = !filter.isEmpty() ? Pattern.compile(filter) : null;

        Map<String, Serializable> locations = MemorisedLocations.list();
        if (pattern != null)
            locations.keySet().removeIf(name -> !pattern.matcher(name).find());

        if (locations.isEmpty())
            return Map.of("success", true, "output", pattern != null ? "No memorised locations match '" + filter + "'." : "No locations memorised yet.");

        return Map.of("success", true, "locations", (Serializable) locations);
    }
}
