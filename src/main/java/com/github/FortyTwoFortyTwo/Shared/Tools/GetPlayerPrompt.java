package Tools;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.Serializable;
import java.util.Map;

public class GetPlayerPrompt implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Returns info about the player who's sending the prompt.";
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String playerName = input.get("sender").getAsString();  // provided by AnthropicClient

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) return Map.of("error", "Player not found or offline");

        var loc = player.getLocation();
        return Map.of(
                "player", playerName,
                "uuid", player.getUniqueId().toString(),
                "world", loc.getWorld().getName(),
                "x", loc.getX(),
                "y", loc.getY(),
                "z", loc.getZ(),
                "pitch", loc.getPitch(),
                "yaw", loc.getYaw()
        );
    }
}
