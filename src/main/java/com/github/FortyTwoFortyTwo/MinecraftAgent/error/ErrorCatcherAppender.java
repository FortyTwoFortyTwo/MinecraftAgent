package error;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import com.github.FortyTwoFortyTwo.MinecraftAgent.agent.AnthropicClient;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ErrorCatcherAppender extends AbstractAppender {

    private final AnthropicClient anthropic;

    public ErrorCatcherAppender(AnthropicClient anthropic) {
        super("ErrorCatcher", null, null, true, Property.EMPTY_ARRAY);
        this.anthropic = anthropic;
    }

    @Override
    public void append(LogEvent event) {
        // Filter for ERROR level and above
        if (!event.getLevel().isMoreSpecificThan(Level.ERROR))
            return;

        String message = event.getMessage().getFormattedMessage();
        Throwable thrown = event.getThrown();
        String trace = getStackTrace(thrown);

        // TODO message everyone
        Player player = Bukkit.getOnlinePlayers().iterator().next();
        player.sendMessage(Component.text("Error detected! sending to Agent...", NamedTextColor.RED));

        anthropic.sendMessage(player, message + "\n" + trace,
                "You are an AI agent, you will be given an error stack trace, do the best of your ability to edit files at working directory to fix given errors.");
    }

    public static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}