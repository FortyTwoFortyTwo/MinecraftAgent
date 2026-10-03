package com.github.FortyTwoFortyTwo.Shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** The directories listed in config that file tools are confined to */
public class WorkingDirectories {

    public static List<Path> list() {
        return MinecraftTools.plugin.getConfig().getStringList("directories").stream().map(Path::of).toList();
    }

    /** Resolves a path given by the agent, only allowing it inside a working directory, and never in this plugin's own data folder (holds API keys) */
    public static Path resolve(String rawPath) throws IOException {
        Path path = realPath(Path.of(rawPath));

        if (path.startsWith(realPath(MinecraftTools.plugin.getDataFolder().toPath())))
            throw new SecurityException("Access to " + path + " is not allowed.");

        for (Path dir : list()) {
            if (contains(dir, path))
                return path;
        }

        throw new SecurityException(path + " is outside of the working directories. Call ListWorkingDirectories to see what's available.");
    }

    /** Whether path is dir or inside it, after resolving any ".." and symlinks */
    public static boolean contains(Path dir, Path path) throws IOException {
        return realPath(path).startsWith(realPath(dir));
    }

    /** Resolves symlinks through the deepest existing ancestor, so files that don't exist yet are still checked */
    private static Path realPath(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();

        Path existing = path;
        while (existing != null && !Files.exists(existing))
            existing = existing.getParent();

        if (existing == null)
            return path;

        return existing.toRealPath().resolve(existing.relativize(path)).normalize();
    }
}
