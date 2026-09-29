package appender;

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
import org.bukkit.configuration.file.FileConfiguration;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class ErrorCatcherAppender extends AbstractAppender {

    private final AnthropicClient anthropic;
    private final List<MinecraftTool> tools;
    private final int maxRunsPerHour;
    private final List<String> previous = new ArrayList<>();
    private final Deque<Long> recentRuns = new ArrayDeque<>();

    public ErrorCatcherAppender(AnthropicClient anthropic, FileConfiguration config) {
        super("ErrorCatcher", null, null, true, Property.EMPTY_ARRAY);
        this.anthropic = anthropic;
        // Error messages can contain player-controlled text, so only offer tools that are safe for untrusted input
        this.tools = MinecraftTools.list.stream().filter(tool -> !tool.isBlockedForUntrusted()).toList();
        this.maxRunsPerHour = config.getInt("error-catcher.max-runs-per-hour", 5);
    }

    @Override
    public synchronized void append(LogEvent event) {
        // Filter for ERROR level and above
        if (!event.getLevel().isMoreSpecificThan(Level.ERROR))
            return;

        String message = event.getMessage().getFormattedMessage();
        Throwable thrown = event.getThrown();
        if (thrown == null)
            return;

        // Get just the message and first line of the trace to detect if its a dupe
        StackTraceElement[] trace = thrown.getStackTrace();
        StringBuilder sb = new StringBuilder(thrown.toString()).append("\n");
        for (int i = 0; i < Math.min(1, trace.length); i++)
            sb.append("  at ").append(trace[i]).append("\n");

        String result = sb.toString();
        if (previous.contains(result))
            return;

        previous.add(result);

        // Limit how many runs errors can trigger, so a flood of distinct errors can't drain the API budget
        long now = System.currentTimeMillis();
        while (!recentRuns.isEmpty() && now - recentRuns.peekFirst() > 3_600_000)
            recentRuns.pollFirst();

        if (recentRuns.size() >= maxRunsPerHour)
            return;

        recentRuns.addLast(now);

        Bukkit.broadcast(Component.text("Error detected! sending to Agent...", NamedTextColor.RED));

        // Replies go to console only, never to player chat
        anthropic.sendMessage(Bukkit.getConsoleSender(), message + "\n" + getStackTrace(thrown),
                "You are an AI agent, you will be given an error stack trace, do the best of your ability to edit files at working directory to fix given errors.",
                tools);
    }

    public static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
