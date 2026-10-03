package com.github.FortyTwoFortyTwo.MinecraftAgent.vote;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.IntFunction;

/** Boss bar shown to everyone that counts down the time left in the current prompt vote phase */
public class VoteBossBar {

    private final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);

    private BukkitTask task;
    private IntFunction<Component> title;
    private int total;
    private int remaining;

    /** Starts counting down from the given seconds, replacing any countdown already running. The title is given the seconds left. */
    public void start(int seconds, BossBar.Color color, IntFunction<Component> title) {
        if (task != null)
            task.cancel();

        this.title = title;
        total = Math.max(seconds, 1);
        remaining = seconds;
        bar.color(color);
        update();

        task = Bukkit.getScheduler().runTaskTimer(MinecraftTools.plugin, () -> {
            remaining = Math.max(remaining - 1, 0);
            update();
        }, 20L, 20L);
    }

    /** Refreshes the title and progress, e.g. after a submission or vote changes what the title shows */
    public void update() {
        if (title == null)
            return;

        bar.name(title.apply(remaining));
        bar.progress((float) remaining / total);

        // Shown again every update so players who joined mid-countdown see it too
        Bukkit.getServer().showBossBar(bar);
    }

    public void stop() {
        if (task != null)
            task.cancel();

        task = null;
        title = null;
        Bukkit.getServer().hideBossBar(bar);
    }
}
