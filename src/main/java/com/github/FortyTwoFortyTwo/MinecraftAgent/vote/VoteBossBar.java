package com.github.FortyTwoFortyTwo.MinecraftAgent.vote;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.IntFunction;

/** Counts down the time left in the current prompt vote phase */
public class VoteBossBar extends BroadcastBossBar {

    private BukkitTask task;
    private IntFunction<Component> title;
    private int total;
    private int remaining;

    public VoteBossBar() {
        super(BossBar.Color.YELLOW);
    }

    /** Starts counting down from the given seconds, replacing any countdown already running. The title is given the seconds left. */
    public void start(int seconds, BossBar.Color color, IntFunction<Component> title) {
        if (task != null)
            task.cancel();

        this.title = title;
        total = Math.max(seconds, 1);
        remaining = seconds;
        color(color);
        update();

        task = Bukkit.getScheduler().runTaskTimer(MinecraftTools.plugin, () -> {
            remaining = Math.max(remaining - 1, 0);
            update();
        }, 20L, 20L);
    }

    /** Refreshes the title and progress, e.g. after a submission or vote changes what the title shows */
    public void update() {
        if (title != null)
            show(title.apply(remaining), (float) remaining / total);
    }

    public void stop() {
        if (task != null)
            task.cancel();

        task = null;
        title = null;
        hide();
    }
}
