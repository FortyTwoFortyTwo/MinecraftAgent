package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs the Claude Code CLI headless for a single prompt, billed to the logged in Claude subscription.
 * By default it has no tools and its output is only logged to the console.
 */
public class ClaudeCode {

    private static final int TIMEOUT_SECONDS = 300;

    private final String workingDirectory;
    private final CommandSender sender;
    private String builtInTools = "";
    private String systemPrompt;
    private boolean minecraftTools;
    private boolean forwardOutput;

    private AgentProgress progress = AgentProgress.NONE;
    // Assistant messages are streamed one content block at a time, so a turn is counted per message id
    private final Set<String> turns = new HashSet<>();
    private int maxTurns;

    private Process process;
    private MinecraftMcpServer mcpServer;
    private File mcpConfigFile;

    public ClaudeCode(String workingDirectory, CommandSender sender) {
        this.workingDirectory = workingDirectory;
        this.sender = sender;
    }

    /** Comma separated Claude Code tools to make available, every other built-in tool doesn't exist */
    public ClaudeCode builtInTools(String builtInTools) {
        this.builtInTools = builtInTools;
        return this;
    }

    /** Appended to Claude Code's system prompt */
    public ClaudeCode systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    /** Runs an MCP server for this run offering the Minecraft tools */
    public ClaudeCode minecraftTools() {
        this.minecraftTools = true;
        return this;
    }

    /** Sends Claude Code's final result to the sender, not just the console */
    public ClaudeCode forwardOutput() {
        this.forwardOutput = true;
        return this;
    }

    /** Runs the prompt off the main thread, telling progress about each turn and tool call, and when the run ends */
    public void runAsync(String prompt, AgentProgress progress) {
        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {
            try {
                run(prompt, progress);
            } catch (Exception e) {
                logger().log(Level.WARNING, "Claude Code run failed", e);
                sender.sendMessage(Component.text("Claude Code failed: " + e, NamedTextColor.RED));
            }
        });
    }

    /** Runs the prompt, blocking until it ends or is stopped after the timeout */
    public void run(String prompt, AgentProgress progress) throws IOException, InterruptedException {
        this.progress = progress;
        try {
            start(prompt);

            // Stopping the process closes its output, which ends readOutput
            Thread watchdog = Thread.ofPlatform().daemon().name("claude-watchdog").start(() -> {
                try {
                    if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                        stop();
                } catch (InterruptedException ignored) {
                }
            });

            readOutput();
            logger().info("[Claude] process exited with code: " + process.waitFor());
            watchdog.interrupt();
        } finally {
            cleanup();
            progress.finish();
        }
    }

    private void start(String prompt) throws IOException {
        FileConfiguration config = MinecraftTools.plugin.getConfig();
        maxTurns = config.getInt("claude-code.max-turns");

        List<String> cmd = new ArrayList<>(List.of(
                findExecutable(config),
                "--print",                          // headless mode: output result to stdout
                "--output-format", "stream-json",   // one JSON event per line, so each turn and tool call can be followed
                "--verbose",                        // required by stream-json in headless mode
                "--max-turns", String.valueOf(maxTurns),
                "--tools", builtInTools     // every other built-in tool doesn't exist, even if the user's Claude settings allow it
        ));

        // Tools run without asking, anything else is denied in headless mode
        List<String> allowedTools = new ArrayList<>();
        if (!builtInTools.isEmpty())
            allowedTools.add(builtInTools);

        if (minecraftTools) {
            cmd.addAll(List.of(
                    "--mcp-config", startMcpServer().getAbsolutePath(),
                    "--strict-mcp-config"   // ignore any MCP servers from the user's own Claude settings
            ));
            allowedTools.add("mcp__minecraft");
        }

        if (!allowedTools.isEmpty())
            cmd.addAll(List.of("--allowedTools", String.join(",", allowedTools)));

        if (systemPrompt != null)
            cmd.addAll(List.of("--append-system-prompt", systemPrompt));

        // Empty model uses the default for the logged in plan
        String model = config.getString("claude-code.model");
        if (!model.isBlank())
            cmd.addAll(List.of("--model", model));

        cmd.addAll(List.of("-p", prompt));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(workingDirectory));
        pb.redirectErrorStream(true);  // merge stderr into stdout

        // An API key takes priority over subscription login, so never pass one through
        Map<String, String> env = pb.environment();
        env.remove("ANTHROPIC_API_KEY");

        // Token from `claude setup-token`, otherwise falls back to the host's `claude login` session
        String oauthToken = config.getString("claude-code.oauth-token");
        if (!oauthToken.isBlank())
            env.put("CLAUDE_CODE_OAUTH_TOKEN", oauthToken);

        process = pb.start();
        process.getOutputStream().close();  // nothing to send on stdin, otherwise the CLI waits for it
    }

    /**
     * ProcessBuilder only finds .exe files on Windows, but npm installs a claude.cmd shim.
     * Running the shim would pass the prompt through cmd.exe, so resolve the real claude.exe behind it instead.
     */
    private static String findExecutable(FileConfiguration config) {
        String configured = config.getString("claude-code.executable");
        if (!configured.isBlank())
            return configured;

        // The native installer's bin directory is often missing from PATH, such as after /claudeinstall in a container
        File nativeExe = ClaudeInstaller.nativeExecutable();
        if (nativeExe.isFile())
            return nativeExe.getAbsolutePath();

        if (!System.getProperty("os.name").startsWith("Windows"))
            return "claude";

        // getenv(String) ignores case on Windows, where the variable is usually "Path", but the getenv() map doesn't
        String path = System.getenv("PATH");
        if (path == null)
            return "claude";

        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank())
                continue;

            File exe = new File(dir, "claude.exe");
            if (exe.isFile())
                return exe.getAbsolutePath();

            File npmExe = new File(dir, "node_modules/@anthropic-ai/claude-code/bin/claude.exe");
            if (new File(dir, "claude.cmd").isFile() && npmExe.isFile())
                return npmExe.getAbsolutePath();
        }

        return "claude";
    }

    /** Starts an MCP server for this run, and writes an MCP config pointing Claude Code at it */
    private File startMcpServer() throws IOException {
        mcpServer = new MinecraftMcpServer(sender.getName());
        mcpServer.start();

        JsonObject headers = new JsonObject();
        headers.addProperty("X-MCP-Secret", mcpServer.getSecret());

        JsonObject server = new JsonObject();
        server.addProperty("type", "http");
        server.addProperty("url", mcpServer.getUrl());
        server.add("headers", headers);

        JsonObject servers = new JsonObject();
        servers.add("minecraft", server);

        JsonObject root = new JsonObject();
        root.add("mcpServers", servers);

        // Contains the run's secret, so keep it in the plugin folder rather than in a shared temp directory
        mcpConfigFile = new File(MinecraftTools.plugin.getDataFolder(), "claude-mcp-" + UUID.randomUUID() + ".json");
        Files.writeString(mcpConfigFile.toPath(), MinecraftTools.GSON.toJson(root), StandardCharsets.UTF_8);
        return mcpConfigFile;
    }

    /** Follows the process output until it ends */
    private void readOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonObject event = parseEvent(line);

                // Anything that isn't an event, such as an error on stderr, is shown as is
                if (event == null) {
                    logger().info("[Claude] " + line);
                    if (forwardOutput)
                        sender.sendMessage(line);
                } else {
                    handleEvent(event);
                }
            }
        } catch (IOException e) {
            logger().log(Level.WARNING, "[Claude] Failed to read output", e);
        }
    }

    private static JsonObject parseEvent(String line) {
        try {
            JsonElement element = MinecraftTools.GSON.fromJson(line, JsonElement.class);
            return element != null && element.isJsonObject() && element.getAsJsonObject().has("type") ? element.getAsJsonObject() : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    private void handleEvent(JsonObject event) {
        switch (event.get("type").getAsString()) {
            case "assistant" -> {
                JsonObject message = event.getAsJsonObject("message");
                if (message == null)
                    return;

                if (message.has("id"))
                    turns.add(message.get("id").getAsString());

                String status = AgentProgress.thinking();
                for (JsonElement element : message.getAsJsonArray("content")) {
                    JsonObject block = element.getAsJsonObject();
                    switch (block.get("type").getAsString()) {
                        case "text" -> logger().info("[Claude] " + block.get("text").getAsString());
                        case "tool_use" -> {
                            // Minecraft tools are named mcp__minecraft__<tool>, only the tool is worth showing
                            status = "Using " + block.get("name").getAsString().replace("mcp__minecraft__", "");
                            logger().info("[Claude] " + status + " " + block.get("input"));
                        }
                    }
                }

                progress.step(turns.size(), maxTurns, status);
            }
            case "result" -> {
                // Only the final result is forwarded, same as the plain text output format
                String result = event.has("result") ? event.get("result").getAsString() : "Claude Code ended: " + event.get("subtype").getAsString();
                for (String line : result.split("\n")) {
                    logger().info("[Claude] " + line);
                    if (forwardOutput)
                        sender.sendMessage(line);
                }
            }
        }
    }

    private void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            logger().info("[Claude] Stopped after " + TIMEOUT_SECONDS + " seconds.");
        }
    }

    /** Removes everything that only existed for this run */
    private void cleanup() {
        if (mcpServer != null)
            mcpServer.stop();

        if (mcpConfigFile != null)
            mcpConfigFile.delete();
    }

    private static Logger logger() {
        return MinecraftTools.plugin.getLogger();
    }
}
