package agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ClaudeCode {

    private final String projectPath;

    private PtyProcess ptyProcess;

    public ClaudeCode(String projectPath) {
        this.projectPath = projectPath;
    }

    public void start() throws IOException {
        FileConfiguration config = MinecraftTools.plugin.getConfig();

        String[] cmd = new String[] {
                "claude",
                "--model", config.getString("anthropic.model"),
                "--dangerously-skip-permissions"    // dangerous!
        };

        System.out.println(String.join(" ", cmd));

        ptyProcess = new PtyProcessBuilder()
                .setCommand(cmd)
                .setDirectory(projectPath)
                .setEnvironment(buildEnv())
                .setConsole(false)
                .setInitialColumns(220)
                .setInitialRows(50)
                .start();

        // Stream output in background thread
        new Thread(this::readOutput, "claude-output-reader").start();

        System.out.println("[INFO] Claude Code started in: " + projectPath);
    }

    public void sendCommand(String command) throws IOException, InterruptedException {
        if (ptyProcess == null || !ptyProcess.isAlive()) {
            throw new IllegalStateException("Claude process is not running.");
        }

        OutputStream os = ptyProcess.getOutputStream();
        os.write(command.getBytes(StandardCharsets.UTF_8));
        os.write('\r'); // PTY expects carriage return
        os.flush();

        System.out.println("[SENT] " + command);
        Thread.sleep(200); // small delay between commands
    }

    public void sendCommand(int... b) throws IOException, InterruptedException {
        OutputStream os = ptyProcess.getOutputStream();
        for (int w : b) {
            os.write(w);
            os.flush();
            Thread.sleep(200);
        }
    }

    public void prompt(String request) throws IOException, InterruptedException {
        sendCommand(request);
    }

    public void stop() {
        if (ptyProcess != null && ptyProcess.isAlive()) {
            ptyProcess.destroy();
            System.out.println("[INFO] Claude Code stopped.");
        }
    }

    private void readOutput() {
        try (InputStream is = ptyProcess.getInputStream()) {

            byte[] buf = new byte[1024];
            int len;
            StringBuilder buffer = new StringBuilder();

            while ((len = is.read(buf)) != -1) {
                String raw = new String(buf, 0, len, StandardCharsets.UTF_8);
                String clean = raw.replaceAll("\u001B\\[[;\\d]*[A-Za-z]", "");
                buffer.append(clean);

                if (!clean.replace("?", "").trim().isBlank())
                    System.out.println("[CLAUDE]\n" + clean);

                // Wait for the FULL prompt to render before responding
                // "Enter to confirm" appears at the END of the trust menu and custom API key detected
                if (buffer.toString().contains("Enter to confirm")) {
                    Thread.sleep(800); // give it time to finish rendering

                    if (buffer.toString().contains("1. Yes"))
                        sendCommand('1', '\r');
                    else if (buffer.toString().contains("2. Yes"))
                        sendCommand('2', '\r');
                    else
                        sendCommand('\r', '\r');

                    System.out.println("\n[INFO] Sent Enter for trust prompt");
                    buffer.setLength(0);
                }
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("[INFO] Stream closed: " + e.getMessage());
        }
    }

    private Map<String, String> buildEnv() {
        FileConfiguration config = MinecraftTools.plugin.getConfig();

        // Merge current env + API key
        java.util.Map<String, String> env = new java.util.HashMap<>(System.getenv());
        env.put("ANTHROPIC_API_KEY", config.getString("anthropic.secret"));
        env.put("TERM", "xterm-256color"); // Required for PTY
        return env;
    }
}