package Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.bukkit.Bukkit;

import java.io.Serializable;
import java.util.Map;

public class RunConsoleCommand implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Runs a command on the Minecraft server console with operator-level privileges. Use with caution.";
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("command", stringSchema()));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String command = input.has("command") ? input.get("command").getAsString() : "";

        if (command.isEmpty())
            return Map.of("error", "Missing 'command' field");

        // Strip leading slash if present
        final String cmd = command.startsWith("/") ? command.substring(1) : command;

        Bukkit.getScheduler().runTask(MinecraftTools.plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
        );

        return Map.of("success", true, "command", cmd);
    }
}
