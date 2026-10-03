# MinecraftAgent

An experimental playground for exploring AI integration in Minecraft. It provides a flexible set of tools that an AI agent can use to interact with a Minecraft server in various ways.

## Repository Structure

The project is split into three modules, located under [src/main/java/com/github/FortyTwoFortyTwo](https://github.com/FortyTwoFortyTwo/MinecraftAgent/tree/main/src/main/java/com/github/FortyTwoFortyTwo):

- **McpBridge**: A Model Context Protocol (MCP) server that relays messages between the Minecraft server and an external AI agent (e.g. Claude Desktop).
- **MinecraftAgent**: A Bukkit plugin that runs inside the Minecraft server and handles all in-game actions.
- **Shared**: Code shared by both McpBridge and MinecraftAgent — primarily the definitions of tools available to the AI agent.

## Agent Prompts

[This directory](https://github.com/FortyTwoFortyTwo/MinecraftAgent/tree/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/agent) provides several ways to send prompts to an AI agent:

### Model Context Protocol via [HTTP Bridge](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/agent/BridgeHttpServer.java)

This is where **McpBridge** comes in. It exposes Minecraft tools to any MCP-compatible AI agent.

For example, to use it with Claude Desktop, add the following to `%APPDATA%\Claude\claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "minecraft": {
      "command": "java",
      "args": [
        "-jar",
        "<path to this repo>\\build\\libs\\McpBridge-1.0.0.jar"
      ],
      "env": {
        "MC_BRIDGE_SECRET": "<bridge.secret from plugins\\MinecraftAgent\\config.yml>"
      }
    }
  }
}
```

The plugin generates a random `bridge.secret` on first start if none is set, and McpBridge refuses to start without `MC_BRIDGE_SECRET`.

The JAR exposes the available Minecraft tools to the AI agent.
When the agent calls a tool, McpBridge sends an HTTP request to **MinecraftAgent**'s bound port, which executes the action in the Minecraft server and returns the result.

### `/agent api` via [Anthropic API](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/agent/AnthropicClient.java)

The `/agent api <prompt>` command lets you send prompts to the AI agent directly from within Minecraft.
It communicates with the Anthropic API (`https://api.anthropic.com/v1/messages`), passing the available tools and receiving actions to execute in-game.

All text responses from the agent are printed in chat.
The final response is formatted in Minecraft style (colours, bold, strikethrough, etc.), while intermediate messages during processing are shown in plain white text due to limitations on Anthropic.

### `/agent code` via [Claude Code](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/agent/ClaudeCode.java)

The `/agent code <directory> <prompt>` command works similarly to `/agent api`, but instead of calling the Anthropic API over HTTP, it runs the `claude` CLI directly.
It uses `--output-format text` to get plain text output without a GUI.

This mode does not have access to Minecraft-specific tools.
It only has `--tools Read,Edit,Write,Glob,Grep` for file editing purposes, so Bash, web access and every other built-in tool don't exist for it.
The directory must be one of the `directories` listed in config.

An earlier version of this had directly read through Claude Code GUI and automatically submitted input to retrieve results.
While it did work, its very hacky and really shouldn't do that way anyway.

### `/agent mcp` via Claude Code

The `/agent mcp <prompt>` command is the Claude Code equivalent of `/agent api`.
Each run starts its own [MCP server over HTTP](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/agent/MinecraftMcpServer.java) on a free localhost port with a random secret, and stops it when the run ends.
Claude Code connects to it directly (`--mcp-config` with `"type": "http"`) and runs the agent loop over the Minecraft tools, so neither McpBridge nor the `bridge` config is involved.
It has none of Claude Code's built-in tools (`--tools ""`), only the same Minecraft tools `/agent api` gets.
Tool calls are always made on behalf of the player who ran the command.

### [Prompt Vote](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/vote/PromptVote.java)

When `prompt-vote.enabled` is set, every `prompt-vote.interval-seconds` a few random players are asked to submit a prompt with `/prompt <prompt>`.
Players are picked from a shuffled list, so everyone gets a turn before anyone is picked again, and a player whose prompt wins can't be picked again for `winner-cooldown-seconds`.
Everyone on the server then votes by clicking the `[Click to vote]` button next to a submission in chat. A tie is broken at random, and the round is skipped if nobody votes.
The winning prompt runs as `/agent <prompt-vote.type>` on behalf of the player who submitted it, and the agent's replies are broadcast to the whole server.

The prompt gets every tool the chosen type has, so any player picked to submit can effectively run operator commands if their prompt wins the vote.

### Claude subscription

`/agent mcp` and `/agent code` go through Claude Code, so they can run on a Claude Pro/Max subscription instead of API credits.
Run `claude setup-token` on the server host and put the token in `claude-code.oauth-token`, or leave it empty to use the host's `claude login` session.
`ANTHROPIC_API_KEY` is never passed through, since it would take priority over the subscription.

## Tools

[src/main/java/com/github/FortyTwoFortyTwo/Shared/Tools](https://github.com/FortyTwoFortyTwo/MinecraftAgent/tree/main/src/main/java/com/github/FortyTwoFortyTwo/Shared/Tools) contains the tools available to the AI agent. Some noteworthy ones are described below:

### GetPlayerPrompt

Used when a prompt contains phrases like "give me..." or "make me...".
This tool returns the name of the player who sent the prompt, so the agent knows which Minecraft player to target.

### GetRegistryKeys and GetRegistryValues

Bukkit's registry provides a schema of all valid values for various game data.
While the AI generally has good knowledge of Minecraft content, it can sometimes rely on outdated information.
For example, it may not know about copper tools, which were recently added to the game.
These tools allow the agent to query the registry directly to get up-to-date values.

### RunConsoleCommand

A simple but powerful tool that lets the AI run any Minecraft console command.
The agent is generally good at knowing the correct command syntax.

Any log output produced during command execution is captured and returned to the agent, allowing it to analyse the outcome.
If a command contains a syntax error, the agent can read the error message and attempt to correct and retry the command.

This tool comes with a risk of abuse as it grants access to all operator-level commands without restriction.

### ExecuteCode

Allows the agent to write and immediately execute Java code within the Minecraft server, giving it the ability to perform almost any operation supported by the available packages.

Compile errors and runtime exceptions are captured and sent back to the agent, which can then attempt to fix the code and retry.

This tool is significantly more powerful than `RunConsoleCommand`, as it lets the agent execute code with unrestricted access to the server with no guardrails...

### TextEditor

This tool differs from the others in how it is defined for the agent.
It uses type `text_editor_20250728` and name `str_replace_based_edit_tool`, which causes Anthropic to automatically provide the tool's input schema and description to the agent.
However, the actual file editing logic still have to be coded manually in Java, as the built-in implementation is only available in JavaScript/TypeScript.

This tool is typically used alongside `ListWorkingDirectories` and `ResolvePath` to locate the correct file before making edits.

## Automatic Stack Trace Reporting

When a plugin error occurs in the Minecraft server, the Bukkit plugin captures the full stack trace and forwards it to the agent.
The agent can then attempt to identify the cause of the error and fix it using the available file editing tools.
