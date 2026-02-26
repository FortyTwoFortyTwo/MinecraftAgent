package Tools;

import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;

import java.io.Serializable;
import java.util.Map;

public class BroadcastMessage implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Broadcasts a message to all players currently online on the Minecraft server.";
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("message", stringSchema()));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String message = input.has("message") ? input.get("message").getAsString() : "";

        if (message.isEmpty())
            return Map.of("error", "Missing 'message' field");

        Bukkit.broadcastMessage("§6[AI] §f" + message);

        return Map.of("success", true, "message", message);
    }
}
