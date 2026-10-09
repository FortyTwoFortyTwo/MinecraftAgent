package com.github.FortyTwoFortyTwo.MinecraftAgent.appender;

import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AnthropicClient;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTool;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.Bukkit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ErrorCatcherAppender extends AbstractAppender {

    // How many distinct errors are remembered to skip repeats, forgetting the oldest after that
    private static final int MAX_PREVIOUS = 256;

    private final AnthropicClient anthropic;
    private final List<MinecraftTool> tools;
    private final Set<String> previous = Collections.newSetFromMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_PREVIOUS;
        }
    });
    private final Deque<Long> recentRuns = new ArrayDeque<>();

    public ErrorCatcherAppender(AnthropicClient anthropic) {
        super("ErrorCatcher", null, null, true, Property.EMPTY_ARRAY);
        this.anthropic = anthropic;
        // Error messages can contain player-controlled text, so only offer tools that are safe for untrusted input
        this.tools = MinecraftTools.list.stream().filter(tool -> !tool.isBlockedForUntrusted()).toList();    }

    @Override
    public synchronized void append(LogEvent event) {
        // Filter for ERROR level and above
        if (!event.getLevel().isMoreSpecificThan(Level.ERROR))
            return;

        Throwable thrown = event.getThrown();
        if (thrown == null)
            return;

        // The exception and where it was thrown is enough to tell if it's a repeat
        StackTraceElement[] trace = thrown.getStackTrace();
        String key = thrown + (trace.length > 0 ? "\n  at " + trace[0] : "");
        if (!previous.add(key))
            return;

        // Limit how many runs errors can trigger, so a flood of distinct errors can't drain the API budget
        long now = System.currentTimeMillis();
        while (!recentRuns.isEmpty() && now - recentRuns.peekFirst() > 3_600_000)
            recentRuns.pollFirst();

        if (recentRuns.size() >= MinecraftTools.plugin.getConfig().getInt("error-catcher.max-runs-per-hour"))
            return;

        recentRuns.addLast(now);

        Bukkit.broadcast(Component.text("Error detected! sending to Agent...", NamedTextColor.RED));

        // Replies go to console only, never to player chat
        anthropic.sendMessage(Bukkit.getConsoleSender(), event.getMessage().getFormattedMessage() + "\n" + getStackTrace(thrown),
                "You are an AI agent, you will be given an error stack trace, do the best of your ability to edit files at working directory to fix given errors.",
                tools);
    }

    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
