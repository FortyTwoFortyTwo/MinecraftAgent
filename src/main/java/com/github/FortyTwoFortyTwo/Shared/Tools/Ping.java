package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.Serializable;
import java.util.Map;

public class Ping implements MinecraftTool {

    @Override
    public String getDescription() {
        return "Checks if Minecraft Tools is available on this server.";
    }

    @Override
    public String getPath() {
        return "/" + getName().toLowerCase();
    }

    @Override
    public boolean isAuthorized(HttpExchange exchange, String secret) {
        return true;
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        return Map.of("status", "ok");
    }
}
