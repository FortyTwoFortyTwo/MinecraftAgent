package Tools;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.io.Serializable;
import java.util.Map;

public class GetWorldInfo implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Runs a command on the Minecraft server console with operator-level privileges. Use with caution.";
    }

    public Map<String, Serializable> execute(JsonObject input) {
        World world = Bukkit.getWorlds().get(0);
        return Map.of(
                "name", world.getName(),
                "time", world.getTime(),
                "isDay", world.getTime() < 13000,
                "isStorming", world.isThundering(),
                "isRaining", world.hasStorm(),
                "playerCount", world.getPlayers().size(),
                "seed", world.getSeed()
        );
    }
}
