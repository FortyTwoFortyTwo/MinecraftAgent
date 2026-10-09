package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.memories.Memories;
import com.github.FortyTwoFortyTwo.Shared.memories.MemoryType;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Memorise implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Memorises something worth coming back to for later prompts, as one of " + String.join(", ", Memories.getTypeNames()) + ". " +
                "Reusing a name replaces it.";
    }

    @Override
    public boolean isBlockedForUntrusted() {
        return true;
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        Map<String, Object> types = new HashMap<>();
        for (MemoryType type : Memories.getTypes())
            types.put(type.getType(), type.getInputSchema(this));

        return objectSchema(
                Map.of("name", stringSchema("Up to 32 lowercase letters, digits or underscores, e.g. steve_house")),
                types);
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String name = input.has("name") ? input.get("name").getAsString() : "";

        List<MemoryType> given = Memories.getTypes().stream().filter(type -> input.has(type.getType())).toList();
        if (given.size() != 1 || !input.get(given.getFirst().getType()).isJsonObject())
            return Map.of("error", "Give exactly one of " + String.join(", ", Memories.getTypeNames()) + " as an object");

        MemoryType type = given.getFirst();
        JsonObject typeInput = input.getAsJsonObject(type.getType());

        // Provided by whoever runs the agent, never the model, and missing over the HTTP bridge
        String savedBy = input.has("sender") ? input.get("sender").getAsString() : null;

        // Entities and items can only be looked up on the main thread
        Map<String, Serializable> replaced = runTask(() -> Memories.save(type, name, savedBy, type.fromInput(typeInput)));

        Map<String, Serializable> result = new LinkedHashMap<>();
        result.put("success", true);
        if (replaced != null)
            result.put("replaced", (Serializable) replaced);

        return result;
    }
}
