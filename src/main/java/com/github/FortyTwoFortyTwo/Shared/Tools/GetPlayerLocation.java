package Tools;

import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.Map;

public class GetPlayerLocation implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Returns the current world, X, Y, Z coordinates of an online player.";
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("player", stringSchema()));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String playerName = input.has("player") ? input.get("player").getAsString() : "";

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) return Map.of("error", "Player not found or offline");

        var loc = player.getLocation();
        return Map.of(
                "player", playerName,
                "world", loc.getWorld().getName(),
                "x", Math.round(loc.getX()),
                "y", Math.round(loc.getY()),
                "z", Math.round(loc.getZ())
        );
    }
}
