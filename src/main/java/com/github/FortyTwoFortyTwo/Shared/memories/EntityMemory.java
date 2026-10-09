package com.github.FortyTwoFortyTwo.Shared.memories;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.ToolInputException;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** An entity by its UUID, e.g. a player's pet, with where it was last seen for when it isn't loaded */
final class EntityMemory implements MemoryType {

    static final EntityMemory INSTANCE = new EntityMemory();

    private EntityMemory() {
    }

    /** The entity, with where it is now as where it was last seen */
    static Consumer<ConfigurationSection> of(Entity entity) {
        if (entity == null)
            throw new ToolInputException("No entity given");

        if (entity instanceof Player)
            throw new ToolInputException("Players can't be memorised, as they're already known by name");

        return section -> {
            section.set("entity", entity.getUniqueId().toString());
            section.set("entity-type", entity.getType().getKey().toString());
            setLastSeen(section, entity.getLocation());
        };
    }

    private static void setLastSeen(ConfigurationSection section, Location location) {
        section.set("world", location.getWorld().getName());
        section.set("pos1", List.of(location.getX(), location.getY(), location.getZ()));
    }

    @Override
    public String getType() {
        return "entity";
    }

    @Override
    public Map<String, Object> getInputSchema(MinecraftTool tool) {
        return tool.nestedObjectSchema(
                "A loaded non-player entity that moves on its own, e.g. a pet.",
                Map.of("uuid", tool.stringSchema()),
                Map.of());
    }

    @Override
    public Consumer<ConfigurationSection> fromInput(JsonObject input) {
        UUID uuid;
        try {
            uuid = UUID.fromString(input.has("uuid") ? input.get("uuid").getAsString() : "");
        } catch (IllegalArgumentException e) {
            throw new ToolInputException("Invalid UUID");
        }

        Entity entity = Bukkit.getEntity(uuid);
        if (entity == null)
            throw new ToolInputException("No loaded entity has UUID " + uuid);

        return of(entity);
    }

    @Override
    public void describe(ConfigurationSection section, LinkedHashMap<String, Serializable> memory) {
        // The entity's custom name isn't kept, as players can set it with a name tag
        memory.put("entityType", section.getString("entity-type"));
        memory.put("uuid", section.getString("entity"));
        memory.put("world", section.getString("world"));
        memory.put("pos", new ArrayList<>(section.getDoubleList("pos1")));
    }

    @Override
    public boolean describeLive(ConfigurationSection section, LinkedHashMap<String, Serializable> memory) {
        // Keep where it was last seen while loaded, for once it's unloaded
        Entity entity = Bukkit.getEntity(UUID.fromString(section.getString("entity")));
        if (entity != null)
            setLastSeen(section, entity.getLocation());

        describe(section, memory);
        memory.put("loaded", entity != null);
        return entity != null;
    }
}
