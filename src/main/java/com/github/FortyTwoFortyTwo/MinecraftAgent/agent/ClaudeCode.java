package agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.util.*;

public class ClaudeCode {

    private final String projectPath;

    private Process process;

    public ClaudeCode(String projectPath) {
        this.projectPath = projectPath;
    }

    public void start(String prompt) throws IOException, InterruptedException {
        FileConfiguration config = MinecraftTools.plugin.getConfig();

        String[] cmd = new String[] {
                "claude",
                "--print",                  // headless mode: output result to stdout
                "--allowedTools", "edit_file,read_file,list_files",  // allow file editing
                "--output-format", "text",  // plain text output (or "json" for structured)
                "--model", config.getString("anthropic.model"),
                "--dangerously-skip-permissions",    // dangerous!
                "-p", prompt
        };

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(projectPath));
        pb.redirectErrorStream(true);  // merge stderr into stdout
        pb.environment().put("ANTHROPIC_API_KEY", config.getString("anthropic.secret"));

        process = pb.start();

        // DEBUG: print exit code after a short wait
        new Thread(() -> {
            try {
                int code = process.waitFor();
                System.out.println("[Claude] process exited with code: " + code);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "claude-exit-watcher").start();

        // Stream output in background thread
        new Thread(this::readOutput, "claude-output-reader").start();
    }

    public void readOutput() {
        System.out.println("[Claude] output reader started");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[Claude] " + line);
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
}
