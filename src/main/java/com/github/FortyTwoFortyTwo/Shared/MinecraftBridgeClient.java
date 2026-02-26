package com.github.FortyTwoFortyTwo.Shared;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Lightweight HTTP client that forwards tool calls to the Bukkit bridge plugin.
 */
public class MinecraftBridgeClient {

    private final String baseUrl;
    private final String secret;
    private final HttpClient http;
    private final Gson gson = new Gson();

    public MinecraftBridgeClient(String baseUrl, String secret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.secret = secret;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Sends a GET request to the bridge (no body).
     */
    public JsonObject get(String path) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("X-MCP-Secret", secret)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return gson.fromJson(response.body(), JsonObject.class);
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Bridge unreachable: " + e.getMessage());
            return error;
        }
    }

    /**
     * Sends a POST request with a JSON body to the bridge.
     */
    public JsonObject post(String path, JsonObject body) {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("X-MCP-Secret", secret)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8))
                    .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return gson.fromJson(response.body(), JsonObject.class);
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("error", "Bridge unreachable: " + e.getMessage());
            return error;
        }
    }
}