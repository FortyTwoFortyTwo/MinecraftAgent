package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.memories.Memories;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class GetMemories implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Lists memories by name and who saved them: locations (pos, or pos1 and pos2 for an area), " +
                "entities (where they are, or were last seen if unloaded) and items. " +
                "Use them as context, e.g. where home is, or whose area or pet something is.";
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(
                Map.of(),
                Map.of(
                        "filter", stringSchema("Regex on names"),
                        "type", stringSchema(String.join(", ", Memories.getTypeNames()))));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String filter = input.has("filter") ? input.get("filter").getAsString() : "";
        Pattern pattern = !filter.isEmpty() ? Pattern.compile(filter) : null;
        String type = input.has("type") ? input.get("type").getAsString() : "";
        if (!type.isEmpty() && !Memories.getTypeNames().contains(type))
            return Map.of("error", "Type must be one of " + String.join(", ", Memories.getTypeNames()));

        // Entities can only be looked up on the main thread
        LinkedHashMap<String, Serializable> memories = runTask(() -> Memories.list(!type.isEmpty() ? type : null));
        if (pattern != null)
            memories.keySet().removeIf(name -> !pattern.matcher(name).find());

        if (memories.isEmpty())
            return Map.of("success", true, "output", "Nothing memorised" + (pattern != null || !type.isEmpty() ? " matches." : " yet."));

        return Map.of("success", true, "memories", memories);
    }
}
