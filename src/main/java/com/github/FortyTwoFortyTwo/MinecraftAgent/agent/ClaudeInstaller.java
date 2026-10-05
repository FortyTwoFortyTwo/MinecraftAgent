package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installs Claude Code with its official native installer, for hosts without a shell such as Pterodactyl.
 * The native build is a single binary in ~/.local/bin that needs no Node, and updates itself afterwards.
 */
public class ClaudeInstaller {

    private static final int TIMEOUT_SECONDS = 300;

    private static final AtomicBoolean installing = new AtomicBoolean();

    private ClaudeInstaller() {
    }

    /** Where the native installer puts the claude executable, whether or not it's installed */
    public static File nativeExecutable() {
        String name = isWindows() ? "claude.exe" : "claude";
        return new File(System.getProperty("user.home"), ".local" + File.separator + "bin" + File.separator + name);
    }

    /** Runs the installer off the main thread, sending its output to the sender */
    public static void installAsync(CommandSender sender) {
        if (!installing.compareAndSet(false, true)) {
            sender.sendMessage(Component.text("Claude Code is already being installed.", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(MinecraftTools.plugin, () -> {
            try {
                install(sender);
            } catch (Exception e) {
                logger().log(Level.WARNING, "Claude Code install failed", e);
                sender.sendMessage(Component.text("Claude Code install failed: " + e, NamedTextColor.RED));
            } finally {
                installing.set(false);
            }
        });
    }

    private static void install(CommandSender sender) throws IOException, InterruptedException {
        sender.sendMessage(Component.text("Installing Claude Code...", NamedTextColor.YELLOW));

        List<String> cmd = isWindows()
                ? List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "irm https://claude.ai/install.ps1 | iex")
                : List.of("bash", "-c", "curl -fsSL https://claude.ai/install.sh | bash");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);  // merge stderr into stdout
        Process process = pb.start();
        process.getOutputStream().close();

        // Stopping the process closes its output, which ends the read loop below
        Thread watchdog = Thread.ofPlatform().daemon().name("claude-installer-watchdog").start(() -> {
            try {
                if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    process.destroy();
            } catch (InterruptedException ignored) {
            }
        });

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // The installer colours its output for a terminal, which would show up as junk in chat
                line = line.replaceAll("\u001B\\[[;\\d]*m", "");
                if (line.isBlank())
                    continue;

                logger().info("[Claude install] " + line);
                sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
            }
        }

        int exitCode = process.waitFor();
        watchdog.interrupt();

        File executable = nativeExecutable();
        if (exitCode == 0 && executable.isFile()) {
            sender.sendMessage(Component.text("Claude Code installed at " + executable.getAbsolutePath(), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("Claude Code install failed with exit code " + exitCode + ", see the console for its output.", NamedTextColor.RED));
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    private static Logger logger() {
        return MinecraftTools.plugin.getLogger();
    }
}
