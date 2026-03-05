package Tools;

import com.google.gson.JsonObject;
import io.modelcontextprotocol.spec.McpSchema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.LoggerContext;
import org.bukkit.Bukkit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunConsoleCommand implements com.github.FortyTwoFortyTwo.Shared.MinecraftTool {

    public String getDescription() {
        return "Runs a command on the Minecraft server console with operator-level privileges. Use with caution.";
    }

    public McpSchema.JsonSchema getInputSchema() {
        return objectSchema(Map.of("command", stringSchema()));
    }

    public Map<String, Serializable> execute(JsonObject input) {
        String command = input.has("command") ? input.get("command").getAsString() : "";

        if (command.isEmpty())
            return Map.of("error", "Missing 'command' field");

        // Strip leading slash if present
        final String cmd = command.startsWith("/") ? command.substring(1) : command;

        List<String> output = new ArrayList<>();

        // Capture whatever logs comes out from dispatch for AI to analyze result
        AbstractAppender captureAppender = new AbstractAppender("CaptureAppender", null, null, true, null) {
            @Override
            public void append(LogEvent event) {
                output.add(event.getMessage().getFormattedMessage());
            }
        };
        captureAppender.start();

        // Attach to Log4j root logger
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.getRootLogger().addAppender(captureAppender, null, null);
        ctx.updateLoggers(config);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        // Remove appender
        config.getRootLogger().removeAppender("CaptureAppender");
        ctx.updateLoggers(config);

        return Map.of("success", true, "output", (Serializable) output);
    }
}
