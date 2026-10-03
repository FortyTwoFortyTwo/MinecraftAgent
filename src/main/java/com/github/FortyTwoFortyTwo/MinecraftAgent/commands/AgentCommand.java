package com.github.FortyTwoFortyTwo.MinecraftAgent.commands;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AgentProgress;
import com.github.FortyTwoFortyTwo.MinecraftAgent.types.AgentType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AgentCommand extends BukkitCommand {

    private final Map<String, AgentType> types;

    public AgentCommand(Map<String, AgentType> types) {
        super("agent");
        this.types = types;
    }

    @Override
    public @NotNull List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        if (args.length == 1)
            return StringUtil.copyPartialMatches(args[0], types.keySet(), new ArrayList<>());

        AgentType type = types.get(args[0].toLowerCase());
        if (type == null)
            return List.of();

        return type.tabComplete(Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("minecraft.command.op")) {
            sender.sendMessage(Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        AgentType type = args.length < 2 ? null : types.get(args[0].toLowerCase());
        if (type == null) {
            for (AgentType usage : types.values())
                sender.sendMessage(Component.text("Usage: /agent " + usage.name() + " " + usage.usage(), NamedTextColor.RED));

            return true;
        }

        type.run(sender, Arrays.copyOfRange(args, 1, args.length), AgentProgress.NONE);
        return true;
    }
}
