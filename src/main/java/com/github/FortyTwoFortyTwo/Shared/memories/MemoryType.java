package com.github.FortyTwoFortyTwo.Shared.memories;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** One type of memory, which saves its own fields in a memory's section in memories.yml */
public interface MemoryType {

    /** Name of the type, saved as the memory's "type", shown to the agent, and the key of its object in Memorise's input, e.g. entity */
    String getType();

    /** Schema of this type's object in Memorise's input, built with the tool's schema helpers */
    Map<String, Object> getInputSchema(MinecraftTool tool);

    /** Fills in a memory's section from this type's object in Memorise's input, throwing ToolInputException for anything the model got wrong. Called on the main thread. */
    Consumer<ConfigurationSection> fromInput(JsonObject input);

    /** Adds what the agent is shown about the memory from what's saved. Can be called on any thread. */
    void describe(ConfigurationSection section, LinkedHashMap<String, Serializable> memory);

    /** Same as describe, but can look at the world, returning whether it updated the section so it should be saved. Called on the main thread. */
    default boolean describeLive(ConfigurationSection section, LinkedHashMap<String, Serializable> memory) {
        describe(section, memory);
        return false;
    }
}
