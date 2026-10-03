package com.github.FortyTwoFortyTwo.MinecraftAgent.vote;

import com.github.FortyTwoFortyTwo.MinecraftAgent.types.AgentProgress;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

import java.util.function.Consumer;

/** Shown while the winning prompt runs, filling up as the agent uses up its turns */
public class AgentBossBar extends BroadcastBossBar implements AgentProgress {

    private final Component displayName;
    private final Consumer<AgentBossBar> onFinish;

    /** onFinish is given this bar on the main thread once the run has ended */
    public AgentBossBar(Component displayName, Consumer<AgentBossBar> onFinish) {
        super(BossBar.Color.BLUE);
        this.displayName = displayName;
        this.onFinish = onFinish;
    }

    public void start() {
        show(title("Starting"), 0f);
    }

    @Override
    public void step(int turn, int maxTurns, String status) {
        runSync(() -> show(title(status + " (turn " + turn + "/" + maxTurns + ")"), (float) turn / Math.max(maxTurns, 1)));
    }

    @Override
    public void finish() {
        runSync(() -> {
            hide();
            onFinish.accept(this);
        });
    }

    private Component title(String status) {
        return Component.text("Running ", NamedTextColor.AQUA)
                .append(displayName)
                .append(Component.text("'s prompt - " + status));
    }

    /** Agents report from their own threads, so move onto the main thread, unless the plugin has already been disabled */
    private static void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread())
            runnable.run();
        else if (MinecraftTools.plugin.isEnabled())
            Bukkit.getScheduler().runTask(MinecraftTools.plugin, runnable);
    }
}
