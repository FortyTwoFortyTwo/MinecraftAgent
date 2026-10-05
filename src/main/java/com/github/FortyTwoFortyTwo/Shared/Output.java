package com.github.FortyTwoFortyTwo.Shared;

import org.bukkit.Keyed;
import org.bukkit.Location;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Lets ExecuteCode's code return values to the agent with Output.put(name, value).
 * Values that can't hold text players wrote, e.g. numbers, enums like Material, keys and locations, are shown to the agent as is,
 * anything else is PlayerText, which the agent only gets a placeholder for.
 */
public final class Output {

    // ExecuteCode runs the code on the main thread, so this only collects from the run in progress
    private static final ThreadLocal<LinkedHashMap<String, Serializable>> current = new ThreadLocal<>();

    private Output() {
    }

    /** Returns a value to the agent under the given name */
    public static void put(String name, Object value) {
        LinkedHashMap<String, Serializable> values = current.get();
        if (values != null)
            values.put(name, toSafe(value));
    }

    /** Starts collecting values on this thread, until end() */
    public static LinkedHashMap<String, Serializable> begin() {
        LinkedHashMap<String, Serializable> values = new LinkedHashMap<>();
        current.set(values);
        return values;
    }

    public static void end() {
        current.remove();
    }

    private static Serializable toSafe(Object value) {
        return switch (value) {
            case null -> "null";
            case Number number -> number;
            case Boolean bool -> bool;
            // Constants defined in code, e.g. Material.SAND
            case Enum<?> constant -> constant.name();
            // Registry keys, e.g. minecraft:plains, which players can't make up
            case Keyed keyed -> keyed.getKey().toString();
            case UUID uuid -> uuid.toString();
            case Location location -> location(location);
            case Collection<?> collection -> new ArrayList<>(collection.stream().map(Output::toSafe).toList());
            case Object[] array -> new ArrayList<>(Arrays.stream(array).map(Output::toSafe).toList());
            default -> PlayerText.of(String.valueOf(value));
        };
    }

    private static LinkedHashMap<String, Serializable> location(Location location) {
        LinkedHashMap<String, Serializable> map = new LinkedHashMap<>();
        if (location.getWorld() != null)
            map.put("world", location.getWorld().getKey().toString());

        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        return map;
    }
}
