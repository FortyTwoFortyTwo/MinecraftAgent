package Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
            Path target = subPath != null ? path.resolve(subPath) : path;
            sb.append("📁 ").append(dir).append("\n");
            if (target.toFile().exists()) {
                listContents(target, sb, "  ", 2); // max 2 levels deep
            } else {
                sb.append("  (path not found)\n");
            }
        }

        return Map.of("success", true, "output", sb.toString());
    }

    private void listContents(Path path, StringBuilder sb, String indent, int depth) {
        if (depth == 0 || !path.toFile().isDirectory()) return;
        File[] files = path.toFile().listFiles();
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            sb.append(indent)
                    .append(f.isDirectory() ? "📁 " : "📄 ")
                    .append(f.getName()).append("\n");
            if (f.isDirectory()) {
                listContents(f.toPath(), sb, indent + "  ", depth - 1);
            }
        }
    }
}
