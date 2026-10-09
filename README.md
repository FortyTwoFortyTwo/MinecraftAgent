# MinecraftAgent

An experimental playground for exploring AI integration in Minecraft. It provides a flexible set of tools that an AI agent can use to interact with a Minecraft server in various ways.

## Repository Structure

The project is split into three modules, located under [src/main/java/com/github/FortyTwoFortyTwo](https://github.com/FortyTwoFortyTwo/MinecraftAgent/tree/main/src/main/java/com/github/FortyTwoFortyTwo):

- **McpBridge**: A Model Context Protocol (MCP) server that relays messages between the Minecraft server and an external AI agent (e.g. Claude Desktop).
- **MinecraftAgent**: A Bukkit plugin that runs inside the Minecraft server and handles all in-game actions.
- **Shared**: Code shared by both McpBridge and MinecraftAgent - primarily the definitions of tools available to the AI agent.

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
The agent formats them with MiniMessage tags (colours, bold, strikethrough, etc.), limited to styling so a response can't add click events.

### `/agent code` via [Claude Code](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/MinecraftAgent/agent/ClaudeCode.java)

The `/agent code <directory> <prompt>` command works similarly to `/agent api`, but instead of calling the Anthropic API over HTTP, it runs the `claude` CLI directly.
It runs headless with `--output-format stream-json`, following each turn and tool call as it happens, and only the final result is sent to chat.

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
A round also starts `prompt-vote.start-after-finish-seconds` after a winning prompt finishes, unless a round or another winning prompt is still running.
Players are picked from a shuffled list, so everyone gets a turn before anyone is picked again, and the most recent winners, `winner-cooldown-percent` of online players, can't be picked again.
Everyone on the server then votes by clicking a submission in chat. A tie is broken at random, and the round is skipped if nobody votes.
The winning prompt runs as `/agent <prompt-vote.type>` on behalf of the player who submitted it, and the agent's replies are broadcast to the whole server.

The prompt gets every tool the chosen type has, so any player picked to submit can effectively run operator commands if their prompt wins the vote.
The [tool limits](#tools) still apply, which keep the agent inside the game.
Operators can use `/admin denyprompt <player>` to stop a submitted prompt from being chosen, which also clears any votes for it.

### Claude subscription

`/agent mcp` and `/agent code` go through Claude Code, so they can run on a Claude Pro/Max subscription instead of API credits.
Run `claude setup-token` on the server host and put the token in `claude-code.oauth-token`, or leave it empty to use the host's `claude login` session.
The token doesn't have to come from the server host, so on hosts without a shell or browser (e.g. Pterodactyl) run it on your own machine instead.

If Claude Code isn't installed on the server host, `/admin claudeinstall` runs the official native installer (`curl -fsSL https://claude.ai/install.sh | bash`, or `install.ps1` on Windows) and streams its output to chat.
It installs a single binary into `~/.local/bin` with no Node needed, which is used automatically when `claude-code.executable` is empty.
On Pterodactyl the home directory is `/home/container`, so the install persists across restarts. The image needs `curl` and `bash`, and outbound internet access.
`ANTHROPIC_API_KEY` is never passed through, since it would take priority over the subscription.

## Tools

[src/main/java/com/github/FortyTwoFortyTwo/Shared/Tools](https://github.com/FortyTwoFortyTwo/MinecraftAgent/tree/main/src/main/java/com/github/FortyTwoFortyTwo/Shared/Tools) contains the tools available to the AI agent. Some noteworthy ones are described below:

Every prompt is treated as untrusted, as players can submit them through a [prompt vote](#prompt-vote), and anything the agent reads from the world could carry instructions:
- `ExecuteCode` only allows the Bukkit, Paper and Adventure APIs and core `java.lang`/`java.util` classes, checked when the code compiles, so nothing reaches files, the network, processes, reflection or plugin configs (this one holds API keys).
- Logged output and error messages from `ExecuteCode` and `RunConsoleCommand`, and the parts of command feedback players could have written, come back as tags like `<text1>`, as they could hold text players wrote, so instructions hidden in signs, books, item names or chat can't steer the agent.
  The agent can't read them, but `BroadcastMessage` (and `/agent api`'s replies) swap them back for the text, inserted as plain text so it can't add its own formatting or click events.
- Files the agent views with `TextEditor` have to be readable to edit them, so viewing one disables `ExecuteCode` and `RunConsoleCommand` for the rest of the run instead.

These apply to `/agent api` and `/agent mcp`. Over the HTTP bridge the `ExecuteCode` limits still apply, but output isn't hidden and file views don't disable anything, as each tool call arrives on its own with no run to track.

`/agent code` uses Claude Code's own file tools in its directory, which these limits don't cover.

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

Command feedback is collected as components, so the agent can read the game's own wording while text players could have written is hidden as `<text1>` tags it can only show to players, e.g. `Gave 1 [Diamond] to <text1>`.
Translations the server knows and literal text without letters, like numbers and coordinates, are shown; any other literal text, unknown translation keys, selectors and NBT are hidden.
A message that would need more than 10 tags, like `/data get`'s NBT, is hidden as a whole.
A command with vanilla's name runs vanilla's, e.g. `kick` runs `minecraft:kick` even if a plugin took over `kick`, unless the agent prefixes the plugin's namespace.
Plugin commands run as the real console instead, as some refuse any other sender, so their feedback is only logged and hidden as a whole.
The agent is always told whether the command exists.

This tool comes with a risk of abuse as it grants access to operator-level commands.
Commands listed in `run-console-command.blocked-commands`, or needing a permission in `run-console-command.blocked-permissions`, are refused, including when run through `/execute ... run`.
`ExecuteCode` can still dispatch commands itself, so these lists don't restrict it.

### ExecuteCode

Allows the agent to write and immediately execute Java code within the Minecraft server, giving it the ability to perform almost any operation supported by the available packages.

Compile errors are sent back to the agent, which can then attempt to fix the code and retry.
For runtime exceptions it only gets the exception type, as the message and any logged output are hidden.

To read results, the code returns values with [`Output.put(name, value)`](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/Shared/Output.java).
Values that can't hold text players wrote, such as numbers, booleans, enums like `Material`, registry keys, UUIDs and locations, are shown to the agent as is, so it can reason about them.
Strings and anything else are hidden as their own `<text1>` tag, so only that value is hidden, not the whole result.

This tool is significantly more powerful than `RunConsoleCommand`, as it lets the agent do anything in the game the API allows.
It can't reach the host though, as only classes allowed by [UntrustedCode](https://github.com/FortyTwoFortyTwo/MinecraftAgent/blob/main/src/main/java/com/github/FortyTwoFortyTwo/Shared/UntrustedCode.java) can be used.

### MemoriseLocation, GetMemorisedLocations and ForgetLocation

Lets the agent remember a position, or an area between two corners, under a name such as `steve_house`, so later prompts know about it.
The agent is told to call `GetMemorisedLocations` whenever a prompt involves the world, not only when it names a place, and use them as context.
For example, it can work out where "home" is, find somewhere to put a new build, or avoid building over another player's area.

They're saved in `plugins/MinecraftAgent/locations.yml` with the world, coordinates and the player whose prompt saved it, up to 200 locations.
When a place moves, e.g. a player says their house is somewhere else now or the agent moves or resizes a build there, the agent saves it again under the same name, and is shown the old location it replaced.

Names are limited to 32 lowercase letters, digits and underscores rather than free text, as a prompt could otherwise leave instructions for every later run to read.
Prompts built from server errors can only list them, not save or remove any.

### TextEditor

This tool differs from the others in how it is defined for the agent.
It uses type `text_editor_20250728` and name `str_replace_based_edit_tool`, which causes Anthropic to automatically provide the tool's input schema and description to the agent.
However, the actual file editing logic still have to be coded manually in Java, as the built-in implementation is only available in JavaScript/TypeScript.

This tool is typically used alongside `ListWorkingDirectories` and `ResolvePath` to locate the correct file before making edits.

## Automatic Stack Trace Reporting

When a plugin error occurs in the Minecraft server, the Bukkit plugin captures the full stack trace and forwards it to the agent.
The agent can then attempt to identify the cause of the error and fix it using the available file editing tools.
