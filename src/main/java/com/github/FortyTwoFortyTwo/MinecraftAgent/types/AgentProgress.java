package com.github.FortyTwoFortyTwo.MinecraftAgent.types;

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

    /** Called every time the agent starts a turn or calls a tool, status being what it's doing */
    void step(int turn, int maxTurns, String status);

    /** Called once the run has ended, for whatever reason */
    void finish();
}
