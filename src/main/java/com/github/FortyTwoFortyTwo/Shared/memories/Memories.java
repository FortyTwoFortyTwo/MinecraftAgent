package com.github.FortyTwoFortyTwo.Shared.memories;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.github.FortyTwoFortyTwo.Shared.ToolInputException;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Things the agent memorised by name, kept in memories.yml so later prompts can refer to them,
 * e.g. "teleport me to my house" or "where's my dog".
 * Each type of memory is its own MemoryType, which saves its own fields in the memory's section.
 * Names are short ids rather than free text, as a prompt that saves one could otherwise leave instructions for every later run.
 */
public final class Memories {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");
    private static final int MAX_MEMORIES = 200;

    private static final List<MemoryType> TYPES = List.of(LocationMemory.INSTANCE, EntityMemory.INSTANCE, ItemMemory.INSTANCE);

    // Tool calls over MCP can run in parallel
    private static final Object lock = new Object();

    // ExecuteCode's run in progress on this thread, so its code can only save memories while it runs, not from tasks or listeners it registered
    private static final ThreadLocal<CodeRun> codeRun = new ThreadLocal<>();

    private record CodeRun(String sender, LinkedHashMap<String, Serializable> memorised) {}

    private Memories() {
    }

    // The only members ExecuteCode's code can use, so UntrustedCode only has to allow this class

    public static void memorise(String name, Location location) {
        saveFromCode(LocationMemory.INSTANCE, name, LocationMemory.of(location, null));
    }

    public static void memorise(String name, Location corner1, Location corner2) {
        saveFromCode(LocationMemory.INSTANCE, name, LocationMemory.of(corner1, corner2));
    }

    public static void memorise(String name, Entity entity) {
        saveFromCode(EntityMemory.INSTANCE, name, EntityMemory.of(entity));
    }

    public static void memorise(String name, ItemStack item) {
        saveFromCode(ItemMemory.INSTANCE, name, ItemMemory.of(item));
    }

    /** Lets ExecuteCode's code save memories on this thread until endCode(), returning what it memorised by name */
    public static LinkedHashMap<String, Serializable> beginCode(String sender) {
        LinkedHashMap<String, Serializable> memorised = new LinkedHashMap<>();
        codeRun.set(new CodeRun(sender, memorised));
        return memorised;
    }

    public static void endCode() {
        codeRun.remove();
    }

    /** Saves a memory from ExecuteCode's code, as whoever the run is for, adding it to what the run memorised */
    private static void saveFromCode(MemoryType type, String name, Consumer<ConfigurationSection> fill) {
        CodeRun run = codeRun.get();
        if (run == null)
            throw new ToolInputException("Memories can only be saved while ExecuteCode's code runs");

        LinkedHashMap<String, Serializable> replaced;
        try {
            replaced = save(type, name, run.sender(), fill);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        LinkedHashMap<String, Serializable> memory = new LinkedHashMap<>();
        memory.put("type", type.getType());
        if (replaced != null)
            memory.put("replaced", replaced);

        run.memorised().put(name, memory);
    }

    /** Saves a memory of the type with the fields it fills in, returning the memory it replaced with the same name, or null */
    public static LinkedHashMap<String, Serializable> save(MemoryType type, String name, String savedBy, Consumer<ConfigurationSection> fill) throws IOException {
        checkName(name);

        synchronized (lock) {
            YamlConfiguration yaml = load();
            ConfigurationSection previous = yaml.getConfigurationSection(name);
            if (previous == null && yaml.getKeys(false).size() >= MAX_MEMORIES)
                throw new ToolInputException("Already memorised " + MAX_MEMORIES + " things, forget one first.");

            LinkedHashMap<String, Serializable> replaced = null;
            if (previous != null) {
                MemoryType previousType = typeOf(previous);
                replaced = start(previous, previousType);
                previousType.describe(previous, replaced);
            }

            ConfigurationSection section = yaml.createSection(name);
            section.set("type", type.getType());
            if (savedBy != null)
                section.set("saved-by", savedBy);

            fill.accept(section);

            yaml.save(file());
            return replaced;
        }
    }

    /** Whether a memory with the name existed */
    public static boolean forget(String name) throws IOException {
        checkName(name);

        synchronized (lock) {
            YamlConfiguration yaml = load();
            if (!yaml.contains(name))
                return false;

            yaml.set(name, null);
            yaml.save(file());
            return true;
        }
    }

    /**
     * Every memory by name, or only those of the type when it isn't null,
     * with what each type can only tell on the main thread, e.g. where entities are now. Call on the main thread.
     */
    public static LinkedHashMap<String, Serializable> list(String type) throws IOException {
        synchronized (lock) {
            YamlConfiguration yaml = load();

            boolean updated = false;
            LinkedHashMap<String, Serializable> memories = new LinkedHashMap<>();
            for (String name : yaml.getKeys(false)) {
                ConfigurationSection section = yaml.getConfigurationSection(name);
                if (section == null)
                    continue;

                MemoryType memoryType = typeOf(section);
                if (type != null && !memoryType.getType().equals(type))
                    continue;

                LinkedHashMap<String, Serializable> memory = start(section, memoryType);
                updated |= memoryType.describeLive(section, memory);
                memories.put(name, memory);
            }

            if (updated)
                yaml.save(file());

            return memories;
        }
    }

    public static List<MemoryType> getTypes() {
        return TYPES;
    }

    public static List<String> getTypeNames() {
        return TYPES.stream().map(MemoryType::getType).toList();
    }

    /** What every memory is shown with, before its type's own fields */
    private static LinkedHashMap<String, Serializable> start(ConfigurationSection section, MemoryType type) {
        LinkedHashMap<String, Serializable> memory = new LinkedHashMap<>();
        memory.put("type", type.getType());

        // Player names are limited to letters, digits and underscores, so they can't carry instructions
        if (section.contains("saved-by"))
            memory.put("savedBy", section.getString("saved-by"));

        return memory;
    }

    private static MemoryType typeOf(ConfigurationSection section) {
        // Memories used to only be locations, saved without a type
        String type = section.getString("type", LocationMemory.INSTANCE.getType());
        return TYPES.stream()
                .filter(memoryType -> memoryType.getType().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Memory '" + section.getName() + "' has unknown type '" + type + "'"));
    }

    private static YamlConfiguration load() {
        return YamlConfiguration.loadConfiguration(file());
    }

    private static File file() {
        File file = new File(MinecraftTools.plugin.getDataFolder(), "memories.yml");

        // Memories used to only be locations, saved in locations.yml in the same format
        File old = new File(MinecraftTools.plugin.getDataFolder(), "locations.yml");
        if (!file.exists() && old.exists() && !old.renameTo(file))
            return old;

        return file;
    }

    /** Validates a name given by the agent */
    private static void checkName(String name) {
        if (!NAME.matcher(name).matches())
            throw new ToolInputException("Name must be 1 to 32 lowercase letters, digits or underscores");
    }
}
