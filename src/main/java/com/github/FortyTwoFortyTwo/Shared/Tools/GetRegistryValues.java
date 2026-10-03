package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Keyed;
import org.bukkit.Registry;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class GetRegistryValues implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Gets a list of all values from a Registry key name.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(
                Map.of("registry", stringSchema("Key name of the registry, e.g. MATERIAL, ENTITY_TYPE")),
                Map.of("filter", stringSchema("Optional filter by regex, do not attempt to get massive list of it")));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String name = input.has("registry") ? input.get("registry").getAsString() : "";
        if (name.isEmpty())
            return Map.of("error", "Missing 'registry' field");

        String filter = input.has("filter") ? input.get("filter").getAsString() : "";

        Registry<?> registry;
        try {
            Field field = Registry.class.getField(name.toUpperCase());
            registry = (Registry<?>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
            // Field doesn't exist or isn't a Registry
            return Map.of("error", "Invalid Registry name: " + name);
        }

        Pattern pattern = !filter.isEmpty() ? Pattern.compile(filter) : null;

        List<String> list = new ArrayList<>();
        for (Keyed entry : registry) {
            if (pattern == null || pattern.matcher(entry.getKey().toString()).find())
                list.add(entry.getKey().toString());
        }

        if (list.size() > 200)
            return Map.of("success", true, "output", list.size() + " results found, use filter regex to narrow down for a more detailed list.");

        return Map.of("success", true, "values", (Serializable) list);
    }
}
