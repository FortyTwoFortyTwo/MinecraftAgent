package com.github.FortyTwoFortyTwo.MinecraftAgent.bossbar;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;

/** Boss bar shown to every player on the server */
public class BroadcastBossBar {

    private final BossBar bar;

    public BroadcastBossBar(BossBar.Color color) {
        bar = BossBar.bossBar(Component.empty(), 0f, color, BossBar.Overlay.PROGRESS);
    }

    /** Sets what the bar shows, and shows it again so players who joined since it was last shown see it too */
    protected void show(Component name, float progress) {
        bar.name(name);
        bar.progress(Math.clamp(progress, 0f, 1f));
        Bukkit.getServer().showBossBar(bar);
    }

    protected void color(BossBar.Color color) {
        bar.color(color);
    }

    public void hide() {
        Bukkit.getServer().hideBossBar(bar);
    }
}
