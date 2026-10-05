package com.github.FortyTwoFortyTwo.MinecraftAgent.commands;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.ClaudeInstaller;
import com.github.FortyTwoFortyTwo.MinecraftAgent.MinecraftAgent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/** Server admin utilities, as /admin <subcommand> */
public class AdminCommand extends BukkitCommand {

    /** tabComplete is null when the subcommand has nothing to suggest */
    private record Subcommand(String name, String usage, String description,
                              BiConsumer<CommandSender, String[]> action, BiFunction<CommandSender, String[], List<String>> tabComplete) {

        Subcommand(String name, String usage, String description, BiConsumer<CommandSender, String[]> action) {
            this(name, usage, description, action, null);
        }
    }

    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();

    public AdminCommand() {
        super("admin");

        add(new Subcommand("claudeinstall", "", "Installs Claude Code on the server host, for /agent mcp and /agent code",
                this::claudeinstall));

        add(new Subcommand("denyprompt", "<player>", "Stops a player's prompt in the current prompt vote from being chosen",
                this::denyprompt, this::denypromptTabComplete));
    }

    private void add(Subcommand subcommand) {
        subcommands.put(subcommand.name(), subcommand);
    }

    @Override
    public @NotNull List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 0 || !sender.hasPermission("minecraft.command.op"))
            return List.of();

        if (args.length == 1)
            return StringUtil.copyPartialMatches(args[0], subcommands.keySet(), new ArrayList<>());

        Subcommand subcommand = subcommands.get(args[0].toLowerCase());
        if (subcommand == null || subcommand.tabComplete() == null)
            return List.of();

        return subcommand.tabComplete().apply(sender, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("minecraft.command.op")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        Subcommand subcommand = args.length < 1 ? null : subcommands.get(args[0].toLowerCase());
        if (subcommand == null) {
            sender.sendMessage(Component.text("Usage: /admin <subcommand>", NamedTextColor.RED));
            for (Subcommand usage : subcommands.values())
                sender.sendMessage(Component.text("/admin " + usage.name() + (usage.usage().isEmpty() ? "" : " " + usage.usage()), NamedTextColor.YELLOW)
                        .append(Component.text(" - " + usage.description(), NamedTextColor.GRAY)));

            return true;
        }

        subcommand.action().accept(sender, Arrays.copyOfRange(args, 1, args.length));
        return true;
    }

    public void claudeinstall(CommandSender sender, String[] args) {
        ClaudeInstaller.installAsync(sender);
    }

    public void denyprompt(CommandSender sender, String[] args) {
        if (args.length < 1)
            sender.sendMessage(Component.text("Usage: /admin denyprompt <player>", NamedTextColor.RED));
        else
            MinecraftAgent.promptVote.deny(sender, args[0]);
    }

    public List<String> denypromptTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1)
            return StringUtil.copyPartialMatches(args[0], MinecraftAgent.promptVote.deniable(), new ArrayList<>());

        return List.of();
    }
}
