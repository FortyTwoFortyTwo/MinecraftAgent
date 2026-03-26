package Tools;

import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class GetRegistryValues implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Gets a list of all values from a Registry key name.";
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of(
                "registry", stringSchema("Key name of the registry, e.g. MATERIAL, ENTITY_TYPE"),
                "filter", stringSchema("Optional filter by regex, do not attempt to get massive list of it")));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String name = input.get("registry").getAsString();
        String filter = input.has("filter") ? input.get("filter").getAsString() : null;

        Registry<?> registry;

        try {
            Field field = Registry.class.getField(name.toUpperCase());
            registry = (Registry<?>) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // Field doesn't exist or isn't a Registry
            return Map.of("error", "Invalid Registry name: " + name);
        }

        Pattern pattern = filter != null ? Pattern.compile(filter) : null;

        List<String> list = new ArrayList<>();
        for (Keyed entry : registry) {
            if (pattern == null || pattern.matcher(entry.getKey().toString()).find())
                list.add(entry.getKey().toString());
        }

        return Map.of("values", (Serializable) list);
    }
}
