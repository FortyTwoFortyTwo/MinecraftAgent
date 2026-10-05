package com.github.FortyTwoFortyTwo.MinecraftAgent.commands;

import com.github.FortyTwoFortyTwo.MinecraftAgent.MinecraftAgent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Open to every player, to submit prompts for PromptVote */
public class PromptCommand extends BukkitCommand {

    public PromptCommand() {
        super("prompt");
    }

    @Override
    public @NotNull List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        MinecraftAgent.promptVote.submit(player, String.join(" ", args));
        return true;
    }
}
