package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.memories.Memories;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

public class ForgetMemory implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Removes a memory.";
    }

    @Override
    public boolean isBlockedForUntrusted() {
        return true;
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("name", stringSchema()));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) throws IOException {
        String name = input.has("name") ? input.get("name").getAsString() : "";

        if (!Memories.forget(name))
            return Map.of("error", "Nothing named '" + name + "' is memorised");

        return Map.of("success", true);
    }
}
