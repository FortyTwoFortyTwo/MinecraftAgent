package Tools;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TextEditor implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getType() {
        return "text_editor_20250728";
    }

    public String getName() {
        return "str_replace_based_edit_tool";
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String command = input.get("command").getAsString();

        String result = switch (command) {
            case "view"        -> handleView(input);
            case "str_replace" -> handleStrReplace(input);
            case "insert"      -> handleInsert(input);
            case "create"      -> handleCreate(input);
            default            -> "ERROR: Unknown command: " + command;
        };

        return Map.of("success", !result.startsWith("ERROR"), "output", result);
    }

    // View a file or directory listing
    private String handleView(JsonObject input) {
        String path = input.get("path").getAsString();
        try {
            java.io.File file = new java.io.File(path);
            if (file.isDirectory()) {
                return String.join("\n", file.list());
            }
            // Optional: respect view_range if provided
            List<Integer> range = (List<Integer>) input.get("view_range");
            String content = java.nio.file.Files.readString(file.toPath());
            if (range != null) {
                String[] lines = content.split("\n");
                int start = range.get(0) - 1; // 1-indexed
                int end   = Math.min(range.get(1), lines.length);
                return String.join("\n", Arrays.copyOfRange(lines, start, end));
            }
            return content;
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // Replace exact string in a file
    private String handleStrReplace(JsonObject input) {
        String path = input.get("path").getAsString();
        String oldStr = input.get("old_str").getAsString();
        String newStr = input.get("new_str").getAsString();
        try {
            java.nio.file.Path p = java.nio.file.Path.of(path);
            String content = java.nio.file.Files.readString(p);
            if (!content.contains(oldStr)) {
                return "ERROR: old_str not found in file. No changes made.";
            }
            // Ensure only one occurrence — str_replace expects uniqueness
            if (content.indexOf(oldStr) != content.lastIndexOf(oldStr)) {
                return "ERROR: old_str appears multiple times. Provide more context to make it unique.";
            }
            java.nio.file.Files.writeString(p, content.replace(oldStr, newStr));
            return "OK: Replacement made successfully.";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // Insert lines after a given line number
    private String handleInsert(JsonObject input) {
        String path = input.get("path").getAsString();
        int insertLine = input.get("insert_line").getAsInt();
        String newStr = input.get("new_str").getAsString();
        try {
            java.nio.file.Path p = java.nio.file.Path.of(path);
            List<String> lines = new java.util.ArrayList<>(
                    java.nio.file.Files.readAllLines(p)
            );
            lines.add(insertLine, newStr); // inserts after the given line
            java.nio.file.Files.write(p, lines);
            return "OK: Lines inserted.";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // Create a new file (or overwrite)
    private String handleCreate(JsonObject input) {
        String path = input.get("path").getAsString();
        String content = input.get("file_text").getAsString();
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(path),
                    content,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
            );
            return "OK: File created.";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
}
