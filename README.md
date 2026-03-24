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
      ]
    }
  }
}
```

The JAR exposes the available Minecraft tools to the AI agent.
When the agent calls a tool, McpBridge sends an HTTP request to **MinecraftAgent**'s bound port, which executes the action in the Minecraft server and returns the result.

### `/agent` via [Anthropic API](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/agent/AnthropicClient.java)

The `/agent` command lets you send prompts to the AI agent directly from within Minecraft.
It communicates with the Anthropic API (`https://api.anthropic.com/v1/messages`), passing the available tools and receiving actions to execute in-game.

All text responses from the agent are printed in chat.
The final response is formatted in Minecraft style (colours, bold, strikethrough, etc.), while intermediate messages during processing are shown in plain white text due to limitations on Anthropic.

### `/claude` via [Claude Code](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/agent/ClaudeCode.java)

The `/claude` command works similarly to `/agent`, but instead of calling the Anthropic API over HTTP, it runs the `claude` CLI directly.
It uses `--output-format text` to get plain text output without a GUI.

This mode does not have access to Minecraft-specific tools.
It only uses `--allowedTools edit_file,read_file,list_files` for file editing purposes.

An earlier version of this had directly read through Claude Code GUI and automatically submitted input to retrieve results.
While it did work, its very hacky and really shouldn't do that way anyway.

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
