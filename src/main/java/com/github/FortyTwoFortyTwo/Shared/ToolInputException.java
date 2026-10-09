package com.github.FortyTwoFortyTwo.Shared;

/**
 * Something the model got wrong in a tool's input, which MinecraftTool.safeExecute reports back to it as an "error" with just this message,
 * unlike any other exception, which is reported as an unexpected failure. The message is shown to the model as is.
 */
public class ToolInputException extends RuntimeException {

    public ToolInputException(String message) {
        super(message);
    }
}
