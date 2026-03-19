package Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jgit.ignore.FastIgnoreRule;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class ListWorkingDirectories implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Lists all available working directories and their top-level contents. Optional path to search for, e.g. 'config.json' or 'src/Main.java'";
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("path", stringSchema()));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String subPath = input.has("path") ? input.get("path").getAsString() : "";

        StringBuilder sb = new StringBuilder();

        for (String dir : MinecraftTools.plugin.getConfig().getStringList("ide.paths")) {
            Path path = Path.of(dir);
            Path target = !subPath.isEmpty() ? path.resolve(subPath) : path;
            Map<Path, List<FastIgnoreRule>> gitignoreMap = loadAllGitignores(path);

            sb.append("📁 ").append(dir).append("\n");
            if (target.toFile().exists()) {
                listContents(target, path, gitignoreMap, sb, "  ", 2);
            } else {
                sb.append("  (path not found)\n");
            }
        }

        return Map.of("success", true, "output", sb.toString());
    }

    // Load all .gitignore files recursively from the working directory
    private Map<Path, List<FastIgnoreRule>> loadAllGitignores(Path workingDir) {
        Map<Path, List<FastIgnoreRule>> gitignoreMap = new LinkedHashMap<>();

        try {
            Files.walkFileTree(workingDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().equals(".gitignore")) {
                        List<FastIgnoreRule> rules = parseGitignore(file);
                        if (!rules.isEmpty()) {
                            gitignoreMap.put(file.getParent(), rules);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // Always skip .git directory
                    if (dir.getFileName() != null && dir.getFileName().toString().equals(".git")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Proceed with whatever we collected
        }

        return gitignoreMap;
    }

    private List<FastIgnoreRule> parseGitignore(Path gitignorePath) {
        List<FastIgnoreRule> rules = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(gitignorePath);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                rules.add(new FastIgnoreRule(line));
            }
        } catch (IOException e) {
            // Skip unreadable .gitignore
        }
        return rules;
    }

    // Checks all .gitignore files that are ancestors of the given path
    private boolean isIgnored(Path path, Path workingDir, Map<Path, List<FastIgnoreRule>> gitignoreMap) {
        Path absolute = path.toAbsolutePath().normalize();
        Path workingDirAbsolute = workingDir.toAbsolutePath().normalize();

        // Always ignore .git
        if (absolute.getFileName() != null && absolute.getFileName().toString().equals(".git"))
            return true;

        // Must be under the working directory at all
        if (!absolute.startsWith(workingDirAbsolute))
            return false;

        boolean isDirectory = absolute.toFile().isDirectory();
        boolean ignored = false;

        // Walk gitignore entries from root down to the file's directory,
        // so deeper rules can override shallower ones (last match wins)
        for (Map.Entry<Path, List<FastIgnoreRule>> entry : gitignoreMap.entrySet()) {
            Path gitignoreDir = entry.getKey().toAbsolutePath().normalize();

            // Only apply if this .gitignore is an ancestor of (or same dir as) the path
            if (!absolute.startsWith(gitignoreDir))
                continue;

            // Relative path from the .gitignore's directory to the file
            Path relative = gitignoreDir.relativize(absolute);
            String relativePosix = relative.toString().replace("\\", "/");

            for (FastIgnoreRule rule : entry.getValue())
                if (rule.isMatch(relativePosix, isDirectory))
                    ignored = rule.getResult(); // false = negation = un-ignore
        }

        return ignored;
    }

    private void listContents(Path path, Path workingDir, Map<Path, List<FastIgnoreRule>> gitignoreMap,
                              StringBuilder sb, String indent, int depth) {
        if (depth == 0 || !path.toFile().isDirectory())
            return;

        File[] files = path.toFile().listFiles();
        if (files == null) return;
        Arrays.sort(files);

        for (File f : files) {
            Path filePath = f.toPath();
            if (isIgnored(filePath, workingDir, gitignoreMap))
                continue;

            sb.append(indent)
                    .append(f.isDirectory() ? "📁 " : "📄 ")
                    .append(f.getName()).append("\n");

            if (f.isDirectory()) {
                listContents(filePath, workingDir, gitignoreMap, sb, indent + "  ", depth - 1);
            }
        }
    }
}
