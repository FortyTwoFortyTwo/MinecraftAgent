package com.github.FortyTwoFortyTwo.MinecraftAgent.commands;

import com.github.FortyTwoFortyTwo.MinecraftAgent.bossbar.AgentBossBar;
import com.github.FortyTwoFortyTwo.MinecraftAgent.types.AgentType;
import com.github.FortyTwoFortyTwo.MinecraftAgent.vote.PromptVote;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
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

        String[] typeArgs = Arrays.copyOfRange(args, 1, args.length);
        Bukkit.broadcast(MinecraftTools.PREFIX.append(Component.text("", NamedTextColor.GREEN)
                .append(sender instanceof Player player ? player.displayName() : sender.name())
                .append(Component.text(" ran a prompt: "))
                .append(PromptVote.quoted(type.prompt(typeArgs)))));

        AgentBossBar bar = new AgentBossBar(sender.name(), finished -> {});
        bar.start();
        type.run(sender, typeArgs, bar);
        return true;
    }
}
