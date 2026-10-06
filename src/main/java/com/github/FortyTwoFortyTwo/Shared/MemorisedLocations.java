package com.github.FortyTwoFortyTwo.Shared;

import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Positions and areas the agent memorised by name, kept in locations.yml so later prompts can refer to them, e.g. "teleport me to my house".
 * Names are short ids rather than free text, as a prompt that saves one could otherwise leave instructions for every later run.
 */
public final class MemorisedLocations {

    public static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");
    public static final int MAX_LOCATIONS = 200;

    // Tool calls over MCP can run in parallel
    private static final Object lock = new Object();

    private MemorisedLocations() {
    }

    /** Saves a position, or an area when corner2 isn't null, returning the location it replaced with the same name, or null */
    public static LinkedHashMap<String, Serializable> save(String name, String world, double[] corner1, double[] corner2, String savedBy) throws IOException {
        synchronized (lock) {
            YamlConfiguration yaml = load();
            ConfigurationSection previous = yaml.getConfigurationSection(name);
            if (previous == null && yaml.getKeys(false).size() >= MAX_LOCATIONS)
                throw new IllegalStateException("Already memorised " + MAX_LOCATIONS + " locations, forget one first.");

            LinkedHashMap<String, Serializable> replaced = previous != null ? describe(previous) : null;

            ConfigurationSection section = yaml.createSection(name);
            section.set("world", world);
            section.set("pos1", List.of(corner1[0], corner1[1], corner1[2]));
            if (corner2 != null)
                section.set("pos2", List.of(corner2[0], corner2[1], corner2[2]));

            if (savedBy != null)
                section.set("saved-by", savedBy);

            yaml.save(file());
            return replaced;
        }
    }

    /** Whether a location with the name existed */
    public static boolean forget(String name) throws IOException {
        synchronized (lock) {
            YamlConfiguration yaml = load();
            if (!yaml.contains(name))
                return false;

            yaml.set(name, null);
            yaml.save(file());
            return true;
        }
    }

    /** Every memorised location by name */
    public static LinkedHashMap<String, Serializable> list() {
        synchronized (lock) {
            YamlConfiguration yaml = load();

            LinkedHashMap<String, Serializable> locations = new LinkedHashMap<>();
            for (String name : yaml.getKeys(false)) {
                ConfigurationSection section = yaml.getConfigurationSection(name);
                if (section != null)
                    locations.put(name, describe(section));
            }

            return locations;
        }
    }

    private static LinkedHashMap<String, Serializable> describe(ConfigurationSection section) {
        LinkedHashMap<String, Serializable> location = new LinkedHashMap<>();
        location.put("world", section.getString("world"));
        if (section.contains("pos2")) {
            location.put("type", "area");
            location.put("pos1", new ArrayList<>(section.getDoubleList("pos1")));
            location.put("pos2", new ArrayList<>(section.getDoubleList("pos2")));
        } else {
            location.put("type", "position");
            location.put("pos", new ArrayList<>(section.getDoubleList("pos1")));
        }

        // Player names are limited to letters, digits and underscores, so they can't carry instructions
        if (section.contains("saved-by"))
            location.put("savedBy", section.getString("saved-by"));

        return location;
    }

    private static YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(file());
    }

    private static File file() {
        return new File(MinecraftTools.plugin.getDataFolder(), "locations.yml");
    }

    /** Validates a name given by the agent */
    public static String checkName(String name) {
        if (!NAME.matcher(name).matches())
            throw new IllegalArgumentException("Name must be 1 to 32 lowercase letters, digits or underscores, e.g. steve_house");

        return name;
    }

    /** Reads x, y and z with the given suffix, or null if none of them are given */
    public static double[] readCorner(JsonObject input, String suffix) {
        boolean any = input.has("x" + suffix) || input.has("y" + suffix) || input.has("z" + suffix);
        if (!any)
            return null;

        double[] corner = new double[3];
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            String key = axes[i] + suffix;
            if (!input.has(key))
                throw new IllegalArgumentException("Missing '" + key + "' field");

            corner[i] = input.get(key).getAsDouble();
            if (!Double.isFinite(corner[i]))
                throw new IllegalArgumentException("'" + key + "' must be a finite number");
        }

        return corner;
    }
}
