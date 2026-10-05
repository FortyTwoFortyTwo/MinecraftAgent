package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.WorkingDirectories;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Anthropic's text editor tool, with the schema and description provided by Anthropic and only the editing done here */
public class TextEditor implements MinecraftTool {

    @Override
    public String getType() {
        return "text_editor_20250728";
    }

    @Override
    public String getName() {
        return "str_replace_based_edit_tool";
    }

    @Override
    public boolean returnsPlayerText(JsonObject input) {
        // Files can hold anything players wrote, e.g. logs with chat in them
        return input.has("command") && input.get("command").getAsString().equals("view");
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String command = input.get("command").getAsString();

        try {
            String output = switch (command) {
                case "view"        -> view(input);
                case "str_replace" -> strReplace(input);
                case "insert"      -> insert(input);
                case "create"      -> create(input);
                default            -> throw new IllegalArgumentException("Unknown command: " + command);
            };

            return Map.of("success", true, "output", output);
        } catch (Exception e) {
            return Map.of("success", false, "error", Objects.requireNonNullElse(e.getMessage(), e.toString()));
        }
    }

    // View a file or directory listing
    private String view(JsonObject input) throws IOException {
        Path path = WorkingDirectories.resolve(input.get("path").getAsString());
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                return String.join("\n", children.map(child -> child.getFileName().toString()).sorted().toList());
            }
        }

        String content = Files.readString(path);

        JsonArray range = input.getAsJsonArray("view_range");
        if (range == null)
            return content;

        // 1-indexed and inclusive, with -1 as the end meaning the end of the file
        String[] lines = content.split("\n");
        int start = Math.max(range.get(0).getAsInt() - 1, 0);
        int end = range.get(1).getAsInt() == -1 ? lines.length : Math.min(range.get(1).getAsInt(), lines.length);
        if (start > end)
            throw new IllegalArgumentException("view_range starts past the end of the file, which has " + lines.length + " lines.");

        return String.join("\n", Arrays.copyOfRange(lines, start, end));
    }

    // Replace exact string in a file
    private String strReplace(JsonObject input) throws IOException {
        Path path = WorkingDirectories.resolve(input.get("path").getAsString());
        String oldStr = input.get("old_str").getAsString();
        String newStr = input.has("new_str") ? input.get("new_str").getAsString() : "";

        String content = Files.readString(path);
        int index = content.indexOf(oldStr);
        if (index == -1)
            throw new IllegalArgumentException("old_str not found in file. No changes made.");

        // str_replace expects uniqueness
        if (index != content.lastIndexOf(oldStr))
            throw new IllegalArgumentException("old_str appears multiple times. Provide more context to make it unique.");

        Files.writeString(path, content.replace(oldStr, newStr));
        return "Replacement made successfully.";
    }

    // Insert lines after a given line number, 0 being the start of the file
    private String insert(JsonObject input) throws IOException {
        Path path = WorkingDirectories.resolve(input.get("path").getAsString());
        int insertLine = input.get("insert_line").getAsInt();
        String newStr = input.get("new_str").getAsString();

        List<String> lines = new ArrayList<>(Files.readAllLines(path));
        lines.add(insertLine, newStr);
        Files.write(path, lines);
        return "Lines inserted.";
    }

    // Create a new file (or overwrite)
    private String create(JsonObject input) throws IOException {
        Path path = WorkingDirectories.resolve(input.get("path").getAsString());
        String content = input.get("file_text").getAsString();

        Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return "File created.";
    }
}
