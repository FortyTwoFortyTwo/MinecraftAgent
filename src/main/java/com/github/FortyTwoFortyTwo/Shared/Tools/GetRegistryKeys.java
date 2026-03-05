package Tools;

import com.google.gson.JsonObject;
import org.bukkit.Registry;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GetRegistryKeys implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Gets a list of all available registry names to use.";
    }

    public Map<String, Serializable> execute(JsonObject input) {
        List<String> names = new ArrayList<>();
        for (Field field : Registry.class.getFields()) {
            if (Registry.class.isAssignableFrom(field.getType())) {
                names.add(field.getName()); // e.g. "MATERIAL", "ENTITY_TYPE"
            }
        }

        return Map.of("list", (Serializable) names);
    }
}
