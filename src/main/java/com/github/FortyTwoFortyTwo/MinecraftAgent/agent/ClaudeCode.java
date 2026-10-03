package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.google.gson.JsonObject;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Runs the Claude Code CLI headless, billed to the logged in Claude subscription */
public class ClaudeCode {

    private final String workingDirectory;
    private final CommandSender sender;
    private final String builtInTools;
    private final String systemPrompt;
    private final boolean minecraftTools;
    private final boolean forwardOutput;

    private Process process;
    private MinecraftMcpServer mcpServer;
    private File mcpConfigFile;

    /**
     * @param builtInTools   comma separated Claude Code tools to make available, or "" for none
     * @param systemPrompt   appended to Claude Code's system prompt, or null
     * @param minecraftTools whether to run an MCP server for this run offering the Minecraft tools
     * @param forwardOutput  whether Claude Code's text output is sent to the sender, otherwise it's only logged to the console
     */
    public ClaudeCode(String workingDirectory, CommandSender sender, String builtInTools, String systemPrompt, boolean minecraftTools, boolean forwardOutput) {
        this.workingDirectory = workingDirectory;
        this.sender = sender;
        this.builtInTools = builtInTools;
        this.systemPrompt = systemPrompt;
        this.minecraftTools = minecraftTools;
        this.forwardOutput = forwardOutput;
    }

    /** Runs the prompt, blocking until it's stopped after 300 seconds, so call off the main thread */
    public void run(String prompt) throws IOException, InterruptedException {
        start(prompt);

        // Keep running for 300 seconds then stop
        Thread.sleep(300_000);
        stop();
    }

    public void start(String prompt) throws IOException {
        FileConfiguration config = MinecraftTools.plugin.getConfig();

        List<String> cmd = new ArrayList<>(List.of(
                findExecutable(config),
                "--print",                  // headless mode: output result to stdout
                "--output-format", "text",  // plain text output (or "json" for structured)
                "--max-turns", String.valueOf(config.getInt("claude-code.max-turns")),
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

        try {
            process = pb.start();
        } catch (IOException e) {
            cleanup();
            throw e;
        }
        process.getOutputStream().close();  // nothing to send on stdin, otherwise the CLI waits for it

        // DEBUG: print exit code after a short wait
        new Thread(() -> {
            try {
                int code = process.waitFor();
                System.out.println("[Claude] process exited with code: " + code);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            cleanup();
        }, "claude-exit-watcher").start();

        // Stream output in background thread
        new Thread(this::readOutput, "claude-output-reader").start();
    }

    /**
     * ProcessBuilder only finds .exe files on Windows, but npm installs a claude.cmd shim.
     * Running the shim would pass the prompt through cmd.exe, so resolve the real claude.exe behind it instead.
     */
    private static String findExecutable(FileConfiguration config) {
        String configured = config.getString("claude-code.executable");
        if (!configured.isBlank())
            return configured;

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
        try {
            Files.writeString(mcpConfigFile.toPath(), MinecraftTools.GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            cleanup();
            throw e;
        }
        return mcpConfigFile;
    }

    public void readOutput() {
        System.out.println("[Claude] output reader started");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Claude] " + line);
                if (forwardOutput)
                    sender.sendMessage(line);
            }
        } catch (IOException e) {
            System.out.println("[Claude] IOException: " + e);
        }
        System.out.println("[Claude] output reader finished");
    }

    public void stop() {
        if (process != null && process.isAlive()) {
            process.destroy();
            System.out.println("[INFO] Claude Code stopped.");
        }
    }

    /** Removes everything that only existed for this run */
    private void cleanup() {
        if (mcpServer != null)
            mcpServer.stop();

        if (mcpConfigFile != null)
            mcpConfigFile.delete();
    }
}
