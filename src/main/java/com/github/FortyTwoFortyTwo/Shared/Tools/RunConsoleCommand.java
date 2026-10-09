package com.github.FortyTwoFortyTwo.Shared.Tools;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.github.FortyTwoFortyTwo.Shared.MixedText;
import com.github.FortyTwoFortyTwo.Shared.PlayerText;
import com.github.FortyTwoFortyTwo.Shared.appender.CaptureLogsAppender;
import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RunConsoleCommand implements MinecraftTool {

    private static final String VANILLA = "minecraft:";

    @Override
    public String getDescription() {
        return "Runs a command on the Minecraft server console with operator-level privileges. " +
                "Vanilla commands are run over a plugin's with the same name; prefix the plugin's namespace to run its instead, e.g. essentials:kick.";
    }

    @Override
    public boolean isBlockedForUntrusted() {
        return true;
    }

    @Override
    public boolean isPrivileged() {
        return true;
    }

    @Override
    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("command", stringSchema()));
    }

    @Override
    public Map<String, Serializable> execute(JsonObject input) {
        String command = input.has("command") ? input.get("command").getAsString() : "";

        if (command.isEmpty())
            return Map.of("error", "Missing 'command' field");

        // Strip leading slash if present
        final String cmd = command.startsWith("/") ? command.substring(1) : command;

        return runTask(() -> {
            String line = preferVanilla(cmd);
            String blocked = findBlocked(line);
            if (blocked != null)
                return Map.of("success", false, "error", "Command is blocked: " + blocked);

            // Feedback can include text from the world, e.g. a player's name or /data get on a sign or book,
            // so vanilla's is kept as components to only hide those parts. Plugin commands can refuse anything but the real console,
            // so they keep it, and their feedback is only logged, which stays hidden as a whole.
            boolean vanilla = line.toLowerCase().startsWith(VANILLA);
            List<Component> feedback = new ArrayList<>();
            CommandSender sender = vanilla ? Bukkit.createCommandSender(feedback::add) : Bukkit.getConsoleSender();
            CaptureLogsAppender capture = new CaptureLogsAppender();
            Supplier<Serializable> output = () -> vanilla ? MixedText.of(feedback) : PlayerText.of(capture.getOutput());

            try {
                if (!Bukkit.dispatchCommand(sender, line))
                    return Map.of("success", false, "error", "Unknown command: " + line.split(" ")[0]);

                return Map.of("success", true, "output", output.get());
            } catch (CommandException e) {
                // Thrown by the command's own code, the real error is the cause
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return Map.of("success", false, "error", cause.getClass().getName(), "errorMessage", PlayerText.of(cause.getMessage()),
                        "output", output.get());
            } finally {
                capture.end();
            }
        });
    }

    /**
     * Runs vanilla's command when there's one by that name, even if a plugin took over its label, as that's the syntax the agent knows.
     * Namespaced labels are kept as is, so a plugin's command can still be picked, e.g. essentials:kick.
     */
    private static String preferVanilla(String cmd) {
        String line = cmd.trim();
        String label = line.split("\\s+")[0];
        if (label.contains(":"))
            return line;

        String vanilla = VANILLA + label.toLowerCase();
        return Bukkit.getCommandMap().getKnownCommands().containsKey(vanilla) ? vanilla + line.substring(label.length()) : line;
    }

    /**
     * The first command in the line that's blocked in config, or null if none are.
     * Anything after a "run" is checked too, so /execute can't be used to get around it.
     */
    private static String findBlocked(String cmd) {
        ConfigurationSection config = MinecraftTools.plugin.getConfig().getConfigurationSection("run-console-command");
        if (config == null)
            return null;

        Set<String> commands = config.getStringList("blocked-commands").stream().map(String::toLowerCase).collect(Collectors.toSet());
        List<String> permissions = config.getStringList("blocked-permissions").stream().map(String::toLowerCase).toList();

        String[] args = cmd.trim().split("\\s+");
        for (int i = 0; i < args.length; i++) {
            if (i > 0 && !args[i - 1].equalsIgnoreCase("run"))
                continue;

            String label = args[i].toLowerCase();
            if (label.startsWith("/"))
                label = label.substring(1);

            if (isBlocked(label, commands, permissions))
                return label;
        }

        return null;
    }

    private static boolean isBlocked(String label, Set<String> commands, List<String> permissions) {
        // Namespaced labels like minecraft:op are blocked the same as op
        if (commands.contains(label) || commands.contains(label.substring(label.indexOf(':') + 1)))
            return true;

        Command command = Bukkit.getCommandMap().getCommand(label);
        if (command == null)
            return false;

        // Aliases resolve to the same command, so block by its name and every alias
        if (commands.contains(command.getName().toLowerCase()) || command.getAliases().stream().anyMatch(alias -> commands.contains(alias.toLowerCase())))
            return true;

        String permission = command.getPermission();
        if (permission == null)
            return false;

        // A command can need any one of several permissions separated by ;
        for (String node : permission.toLowerCase().split(";")) {
            for (String blocked : permissions) {
                if (blocked.endsWith("*") ? node.startsWith(blocked.substring(0, blocked.length() - 1)) : node.equals(blocked))
                    return true;
            }
        }

        return false;
    }
}
