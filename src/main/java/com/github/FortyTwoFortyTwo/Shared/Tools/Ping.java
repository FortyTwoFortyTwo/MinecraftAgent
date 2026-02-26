package Tools;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.Serializable;
import java.util.Map;

public class Ping implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Checks if Minecraft Tools is available on this server.";
    }


    public String getPath() {
        return "/" + getName().toLowerCase();
    }

    public boolean isAuthorized(HttpExchange exchange, String secret) {
        return true;
    }

    public Map<String, Serializable> execute(JsonObject input) {
        return Map.of("status", "ok");
    }
}
