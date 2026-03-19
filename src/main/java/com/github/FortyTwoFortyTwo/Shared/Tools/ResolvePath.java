package Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ResolvePath implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Searches across all working directories for a file or directory, matching the given name or relative path.";
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("name", stringSchema()));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String name = input.get("name").getAsString();
        List<String> matches = new ArrayList<>();

        for (String dir : MinecraftTools.plugin.getConfig().getStringList("ide.paths"))
            searchRecursively(Path.of(dir), name, matches);

        if (matches.isEmpty())
            return Map.of("success", false, "output", "ERROR: No file matching '" + name + "' found in any working directory. Call list_working_directories to see what's available.");

        return Map.of("success", true, "output", String.join("\n", matches));
    }

    private void searchRecursively(Path dir, String name, List<String> matches) {
        // Check direct match first (e.g. "src/Main.java" relative to this working dir)
        Path candidate = dir.resolve(name);
        if (candidate.toFile().exists()) {
            matches.add(candidate.toAbsolutePath().normalize().toString());
            return; // don't recurse further if already matched at this level
        }

        // Walk the directory tree looking for filename matches
        File[] children = dir.toFile().listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                searchRecursively(child.toPath(), name, matches);
            } else if (child.getName().equals(name)) {
                String abs = child.toPath().toAbsolutePath().normalize().toString();
                if (!matches.contains(abs)) {
                    matches.add(abs);
                }
            }
        }
    }
}
