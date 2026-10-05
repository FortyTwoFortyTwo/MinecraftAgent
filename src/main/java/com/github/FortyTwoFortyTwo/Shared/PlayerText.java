package com.github.FortyTwoFortyTwo.Shared;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Text in a tool's output that players could have written, e.g. chat, signs, books or item names, so it could hold instructions.
 * The agent only gets a placeholder for it, which BroadcastMessage can show to players.
 */
public record PlayerText(String text) implements Serializable {

    public static PlayerText of(String text) {
        return new PlayerText(Objects.requireNonNullElse(text, ""));
    }

    /** Captured log lines, one per line */
    public static PlayerText of(List<String> lines) {
        return new PlayerText(String.join("\n", lines));
    }
}
