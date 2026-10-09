package com.github.FortyTwoFortyTwo.Shared.memories;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.ToolInputException;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A position, or an area between two corners, e.g. a player's house */
final class LocationMemory implements MemoryType {

    static final LocationMemory INSTANCE = new LocationMemory();

    private LocationMemory() {
    }

    /** A position, or an area when corner2 isn't null, which must be in the same world */
    static Consumer<ConfigurationSection> of(Location corner1, Location corner2) {
        if (corner1 == null || corner1.getWorld() == null)
            throw new ToolInputException("The location must have a world");

        if (corner2 != null && corner2.getWorld() != corner1.getWorld())
            throw new ToolInputException("Both corners must be in the same world");

        return of(corner1.getWorld(), xyz(corner1), corner2 != null ? xyz(corner2) : null);
    }

    private static Consumer<ConfigurationSection> of(World world, double[] corner1, double[] corner2) {
        return section -> {
            section.set("world", world.getName());
            section.set("pos1", List.of(corner1[0], corner1[1], corner1[2]));
            if (corner2 != null)
                section.set("pos2", List.of(corner2[0], corner2[1], corner2[2]));
        };
    }

    private static double[] xyz(Location location) {
        return new double[]{location.getX(), location.getY(), location.getZ()};
    }

    /** Reads x, y and z with the given suffix, or null if none of them are given */
    private static double[] readCorner(JsonObject input, String suffix) {
        boolean any = input.has("x" + suffix) || input.has("y" + suffix) || input.has("z" + suffix);
        if (!any)
            return null;

        double[] corner = new double[3];
        String[] axes = {"x", "y", "z"};
        for (int i = 0; i < 3; i++) {
            String key = axes[i] + suffix;
            if (!input.has(key))
                throw new ToolInputException("Missing '" + key + "' field");

            corner[i] = input.get(key).getAsDouble();
            if (!Double.isFinite(corner[i]))
                throw new ToolInputException("'" + key + "' must be a finite number");
        }

        return corner;
    }

    @Override
    public String getType() {
        return "location";
    }

    @Override
    public Map<String, Object> getInputSchema(MinecraftTool tool) {
        return tool.nestedObjectSchema(
                "A position, or an area with x2, y2 and z2 as its second corner. Save again under the same name when it moves or is resized.",
                Map.of(
                        "world", tool.stringSchema(),
                        "x", tool.numberSchema(),
                        "y", tool.numberSchema(),
                        "z", tool.numberSchema()),
                Map.of(
                        "x2", tool.numberSchema(),
                        "y2", tool.numberSchema(),
                        "z2", tool.numberSchema()));
    }

    @Override
    public Consumer<ConfigurationSection> fromInput(JsonObject input) {
        String worldName = input.has("world") ? input.get("world").getAsString() : "";
        World world = Bukkit.getWorld(worldName);
        if (world == null)
            throw new ToolInputException("World '" + worldName + "' not found");

        double[] corner1 = readCorner(input, "");
        if (corner1 == null)
            throw new ToolInputException("Missing 'x', 'y' and 'z' fields");

        return of(world, corner1, readCorner(input, "2"));
    }

    @Override
    public void describe(ConfigurationSection section, LinkedHashMap<String, Serializable> memory) {
        memory.put("world", section.getString("world"));

        // An area has both corners, a position only the one
        if (section.contains("pos2")) {
            memory.put("pos1", new ArrayList<>(section.getDoubleList("pos1")));
            memory.put("pos2", new ArrayList<>(section.getDoubleList("pos2")));
        } else {
            memory.put("pos", new ArrayList<>(section.getDoubleList("pos1")));
        }
    }
}
