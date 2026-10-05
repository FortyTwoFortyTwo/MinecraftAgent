package com.github.FortyTwoFortyTwo.MinecraftAgent.agent;

import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Told how far an agent run has got, so it can be shown to players. Called from any thread. */
public interface AgentProgress {

    AgentProgress NONE = new AgentProgress() {
        @Override
        public void step(int turn, int maxTurns, String status) {
        }

        @Override
        public void finish() {
        }
    };

    /** A random status from the config to show while the agent is thinking, e.g. "Thinking" or "Pondering" */
    static String thinking() {
        List<String> statuses = MinecraftTools.plugin.getConfig().getStringList("thinking-statuses");
        return statuses.isEmpty() ? "Thinking" : statuses.get(ThreadLocalRandom.current().nextInt(statuses.size()));
    }

    /** Called every time the agent starts a turn or calls a tool, status being what it's doing */
    void step(int turn, int maxTurns, String status);

    /** Called once the run has ended, for whatever reason */
    void finish();
}
