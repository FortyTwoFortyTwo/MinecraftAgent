package com.github.FortyTwoFortyTwo.Shared.appender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;

import java.util.ArrayList;
import java.util.List;

/** Collects everything logged until end() is called, which must always happen, e.g. in a finally block */
public class CaptureLogsAppender extends AbstractAppender {

    private static final String NAME = "CaptureAppender";

    private final List<String> output = new ArrayList<>();

    @SuppressWarnings("this-escape")
    public CaptureLogsAppender() {
        super(NAME, null, null, true, null);

        start();

        // Attach to Log4j root logger
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.getRootLogger().addAppender(this, null, null);
        ctx.updateLoggers(config);
    }

    @Override
    public void append(LogEvent event) {
        // Capture whatever logs comes out from dispatch for AI to analyze result
        output.add(event.getMessage().getFormattedMessage());
    }

    public void end() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        config.getRootLogger().removeAppender(NAME);
        ctx.updateLoggers(config);
        stop();
    }

    public List<String> getOutput() {
        return output;
    }
}
