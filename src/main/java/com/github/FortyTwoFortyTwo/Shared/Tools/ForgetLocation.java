package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MemorisedLocations;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;

public class ForgetLocation implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Removes a position or area memorised with MemoriseLocation.";
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
        String name = MemorisedLocations.checkName(input.has("name") ? input.get("name").getAsString() : "");

        if (!MemorisedLocations.forget(name))
            return Map.of("error", "No location named '" + name + "' is memorised. Call GetMemorisedLocations to see what's available.");

        return Map.of("success", true);
    }
}
