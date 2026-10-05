package com.github.FortyTwoFortyTwo.MinecraftAgent.vote;

import com.github.FortyTwoFortyTwo.MinecraftAgent.bossbar.AgentBossBar;
import com.github.FortyTwoFortyTwo.MinecraftAgent.types.AgentType;
import com.github.FortyTwoFortyTwo.Shared.MinecraftTools;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;

/** Every interval, a few players submit prompts and everyone votes on which one gets sent to the agent */
public class PromptVote {

    private enum Phase { IDLE, SUBMIT, VOTE }

    private record Submission(UUID player, Component displayName, String prompt) {}

    // Agent types the winning prompt can run as, by name
    private final Map<String, AgentType> types;

    // Shuffled players who haven't submitted yet this cycle, so everyone gets a turn before anyone repeats
    private final List<UUID> bag = new ArrayList<>();
    // Past winners, most recent first, so the latest few can be kept from being picked again
    private final LinkedList<UUID> winners = new LinkedList<>();

    private final Set<UUID> chosen = new HashSet<>();
    private final Map<UUID, Submission> submissions = new LinkedHashMap<>();
    private final List<Submission> ballot = new ArrayList<>();
    private final Map<UUID, Integer> votes = new HashMap<>();
    // Submitters whose prompt an admin denied during the vote, kept on the ballot so vote indexes stay valid
    private final Set<UUID> denied = new HashSet<>();
    private final Random random = new Random();
    private final VoteBossBar bossBar = new VoteBossBar();
    // One per winning prompt still running, as the next round can finish before the last agent does
    private final Set<AgentBossBar> agentBars = new HashSet<>();

    private Phase phase = Phase.IDLE;
    private BukkitTask timer;
    private BukkitTask phaseTask;

    public PromptVote(Map<String, AgentType> types) {
        this.types = types;
    }

    private static FileConfiguration config() {
        return MinecraftTools.plugin.getConfig();
    }

    /** Schedules the next round, rescheduling every time so interval-seconds can change without a restart */
    public void start() {
        timer = Bukkit.getScheduler().runTaskLater(MinecraftTools.plugin, () -> {
            if (config().getBoolean("prompt-vote.enabled"))
                startRound();

            start();
        }, config().getInt("prompt-vote.interval-seconds") * 20L);
    }

    public void stop() {
        if (timer != null)
            timer.cancel();

        timer = null;
        reset();

        agentBars.forEach(AgentBossBar::hide);
        agentBars.clear();
    }

    /** Starts a round, unless one is already running or there aren't enough players */
    private void startRound() {
        if (phase != Phase.IDLE || Bukkit.getOnlinePlayers().size() < config().getInt("prompt-vote.min-players"))
            return;

        List<Player> picked = pickSubmitters();
        if (picked.isEmpty())
            return;

        phase = Phase.SUBMIT;
        picked.forEach(player -> chosen.add(player.getUniqueId()));

        int submitSeconds = config().getInt("prompt-vote.submit-seconds");
        Component names = Component.join(JoinConfiguration.commas(true), picked.stream().map(Player::displayName).toList());
        broadcast(Component.text("Waiting on ", NamedTextColor.YELLOW)
                .append(names)
                .append(Component.text(" to submit a prompt for the agent (" + submitSeconds + "s)...")));

        for (Player player : picked) {
            player.sendMessage(MinecraftTools.PREFIX
                    .append(Component.text("You've been picked to submit a prompt! ", NamedTextColor.GREEN))
                    .append(Component.text("[Click to submit]", NamedTextColor.AQUA, TextDecoration.BOLD)
                            .hoverEvent(HoverEvent.showText(Component.text("Type your prompt for the agent")))
                            .clickEvent(ClickEvent.suggestCommand("/prompt "))));
        }

        bossBar.start(submitSeconds, BossBar.Color.YELLOW, seconds -> Component.text(
                "Waiting for prompts (" + submissions.size() + "/" + chosen.size() + ") - " + seconds + "s", NamedTextColor.YELLOW));

        phaseTask = Bukkit.getScheduler().runTaskLater(MinecraftTools.plugin, this::endSubmit, submitSeconds * 20L);
    }

    private List<Player> pickSubmitters() {
        Set<UUID> onCooldown = winnersOnCooldown();
        List<Player> eligible = Bukkit.getOnlinePlayers().stream()
                .filter(player -> !onCooldown.contains(player.getUniqueId()))
                .map(player -> (Player) player)
                .toList();

        int submitters = config().getInt("prompt-vote.submitters");
        List<Player> picked = new ArrayList<>();
        takeFromBag(eligible, picked, submitters);

        if (picked.size() < submitters) {
            // Everyone eligible has had a turn, start a new shuffled cycle
            List<UUID> refill = new ArrayList<>(eligible.stream()
                    .filter(player -> !picked.contains(player))
                    .map(Player::getUniqueId)
                    .toList());

            Collections.shuffle(refill, random);
            bag.addAll(refill);
            takeFromBag(eligible, picked, submitters);
        }

        return picked;
    }

    /** The most recent online winners, as many as winner-cooldown-percent of online players, always leaving at least one player eligible */
    private Set<UUID> winnersOnCooldown() {
        int online = Bukkit.getOnlinePlayers().size();
        int count = Math.min((int) (online * config().getDouble("prompt-vote.winner-cooldown-percent")), online - 1);

        Set<UUID> onCooldown = new HashSet<>();
        for (UUID winner : winners) {
            if (onCooldown.size() >= count)
                break;

            if (Bukkit.getPlayer(winner) != null)
                onCooldown.add(winner);
        }

        return onCooldown;
    }

    private void takeFromBag(List<Player> eligible, List<Player> picked, int submitters) {
        Iterator<UUID> iterator = bag.iterator();
        while (iterator.hasNext() && picked.size() < submitters) {
            Player player = Bukkit.getPlayer(iterator.next());
            if (player == null || !eligible.contains(player))
                continue;

            picked.add(player);
            iterator.remove();
        }
    }

    public void submit(Player player, String prompt) {
        if (phase != Phase.SUBMIT || !chosen.contains(player.getUniqueId())) {
            player.sendMessage(Component.text("You weren't picked to submit a prompt this round.", NamedTextColor.RED));
            return;
        }

        if (prompt.isBlank()) {
            player.sendMessage(Component.text("Usage: /prompt <prompt>", NamedTextColor.RED));
            return;
        }

        int maxPromptLength = config().getInt("prompt-vote.max-prompt-length");
        if (prompt.length() > maxPromptLength) {
            player.sendMessage(Component.text("Prompt is too long, max " + maxPromptLength + " characters.", NamedTextColor.RED));
            return;
        }

        boolean replaced = submissions.put(player.getUniqueId(), new Submission(player.getUniqueId(), player.displayName(), prompt)) != null;
        player.sendMessage(Component.text(replaced ? "Prompt updated." : "Prompt submitted.", NamedTextColor.GREEN));
        bossBar.update();

        // No need to wait out the timer once everyone has submitted
        if (submissions.size() == chosen.size()) {
            phaseTask.cancel();
            endSubmit();
        }
    }

    /** Names of players whose prompt can still be denied this round */
    public List<String> deniable() {
        return pending().stream()
                .map(submission -> Bukkit.getOfflinePlayer(submission.player()).getName())
                .filter(Objects::nonNull)
                .toList();
    }

    /** Stops the named player's prompt from being chosen this round */
    public void deny(CommandSender sender, String name) {
        if (phase == Phase.IDLE) {
            sender.sendMessage(Component.text("No prompt vote is running.", NamedTextColor.RED));
            return;
        }

        Submission submission = pending().stream()
                .filter(pending -> name.equalsIgnoreCase(Bukkit.getOfflinePlayer(pending.player()).getName()))
                .findFirst()
                .orElse(null);

        if (submission == null) {
            sender.sendMessage(Component.text("No pending prompt from " + name + ".", NamedTextColor.RED));
            return;
        }

        Player player = Bukkit.getPlayer(submission.player());
        if (player != null)
            player.sendMessage(Component.text("Your prompt was denied by an admin.", NamedTextColor.RED));

        sender.sendMessage(Component.text("Denied ", NamedTextColor.GREEN)
                .append(submission.displayName())
                .append(Component.text("'s prompt: "))
                .append(quoted(submission.prompt())));

        if (phase == Phase.SUBMIT) {
            // Prompts aren't public yet, so quietly drop it and don't let them submit another
            submissions.remove(submission.player());
            chosen.remove(submission.player());
            bossBar.update();

            if (submissions.size() == chosen.size()) {
                phaseTask.cancel();
                endSubmit();
            }

            return;
        }

        denied.add(submission.player());
        int index = ballot.indexOf(submission);
        votes.values().removeIf(vote -> vote == index);
        bossBar.update();

        broadcast(Component.text("", NamedTextColor.RED)
                .append(submission.displayName())
                .append(Component.text("'s prompt was denied by an admin, votes for it have been cleared.")));

        if (pending().isEmpty()) {
            broadcast(Component.text("Every prompt was denied, skipping this round.", NamedTextColor.RED));
            reset();
        }
    }

    /** Submissions still in the running this round */
    private List<Submission> pending() {
        return switch (phase) {
            case IDLE -> List.of();
            case SUBMIT -> List.copyOf(submissions.values());
            case VOTE -> ballot.stream().filter(submission -> !denied.contains(submission.player())).toList();
        };
    }

    private void vote(Player player, Submission submission) {
        // Buttons from an earlier round stay clickable in chat, so make sure this one is still on the ballot
        if (phase != Phase.VOTE || !ballot.contains(submission)) {
            player.sendMessage(Component.text("This vote has already ended.", NamedTextColor.RED));
            return;
        }

        if (denied.contains(submission.player())) {
            player.sendMessage(Component.text("This prompt was denied by an admin.", NamedTextColor.RED));
            return;
        }

        votes.put(player.getUniqueId(), ballot.indexOf(submission));
        player.sendMessage(Component.text("Voted for ", NamedTextColor.GREEN)
                .append(submission.displayName())
                .append(Component.text("'s prompt.")));
        bossBar.update();
    }

    private void endSubmit() {
        if (submissions.isEmpty()) {
            broadcast(Component.text("Nobody submitted a prompt, skipping this round.", NamedTextColor.RED));
            reset();
            return;
        }

        ballot.addAll(submissions.values());

        phase = Phase.VOTE;
        int voteSeconds = config().getInt("prompt-vote.vote-seconds");
        broadcast(Component.text("Click a prompt below to vote for which one gets sent to the agent (" + voteSeconds + "s):", NamedTextColor.YELLOW));

        for (Submission submission : ballot) {
            // The whole line is clickable, children inherit the hover and click events
            Bukkit.broadcast(Component.text("- ", NamedTextColor.GRAY)
                    .append(submission.displayName())
                    .append(Component.text(": "))
                    .append(quoted(submission.prompt()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to vote for ", NamedTextColor.GREEN)
                            .append(submission.displayName())
                            .append(Component.text("'s prompt"))))
                    .clickEvent(ClickEvent.callback(audience -> {
                        if (audience instanceof Player player)
                            vote(player, submission);
                    })));
        }

        bossBar.start(voteSeconds, BossBar.Color.GREEN, seconds -> Component.text(
                "Vote for a prompt in chat (" + votes.size() + " vote" + (votes.size() == 1 ? "" : "s") + ") - " + seconds + "s", NamedTextColor.GREEN));

        phaseTask = Bukkit.getScheduler().runTaskLater(MinecraftTools.plugin, this::endVote, voteSeconds * 20L);
    }

    private void endVote() {
        if (votes.isEmpty()) {
            broadcast(Component.text("Nobody voted, skipping this round.", NamedTextColor.RED));
            reset();
            return;
        }

        int[] counts = new int[ballot.size()];
        for (int index : votes.values())
            counts[index]++;

        int max = Arrays.stream(counts).max().orElse(0);
        List<Submission> tied = new ArrayList<>();

        for (int i = 0; i < ballot.size(); i++) {
            if (denied.contains(ballot.get(i).player()))
                continue;

            Bukkit.broadcast(Component.text("", NamedTextColor.GRAY)
                    .append(ballot.get(i).displayName())
                    .append(Component.text(": " + counts[i] + " vote" + (counts[i] == 1 ? "" : "s"))));
            if (counts[i] == max)
                tied.add(ballot.get(i));
        }

        runWinner(tied.get(random.nextInt(tied.size())));
    }

    private void runWinner(Submission winner) {
        winners.remove(winner.player());
        winners.addFirst(winner.player());
        // No more than the max player count can ever be on cooldown
        while (winners.size() > Bukkit.getMaxPlayers())
            winners.removeLast();

        reset();

        // Type name, optionally followed by arguments that go before the prompt, e.g. "code <directory>"
        // No explicit default, so a config.yml saved before this option existed falls back to the bundled one
        String value = config().getString("prompt-vote.type");
        String[] command = (value == null ? "" : value).trim().split("\\s+");
        AgentType type = types.get(command[0].toLowerCase());
        if (type == null) {
            broadcast(Component.text("prompt-vote.type '" + value + "' in config.yml isn't one of: " + String.join(", ", types.keySet()), NamedTextColor.RED));
            return;
        }

        broadcast(Component.text("", NamedTextColor.GREEN)
                .append(winner.displayName())
                .append(Component.text("'s prompt was chosen: "))
                .append(quoted(winner.prompt())));

        Player player = Bukkit.getPlayer(winner.player());
        if (player == null) {
            broadcast(Component.text("", NamedTextColor.RED)
                    .append(winner.displayName())
                    .append(Component.text(" left, skipping their prompt.")));
            return;
        }

        List<String> args = new ArrayList<>(Arrays.asList(command).subList(1, command.length));
        args.addAll(Arrays.asList(winner.prompt().split(" ")));

        AgentBossBar agentBar = new AgentBossBar(winner.displayName(), agentBars::remove);
        agentBars.add(agentBar);
        agentBar.start();

        type.run(broadcastingSender(player), args.toArray(String[]::new), agentBar);
    }

    /** Acts as the given player, except every message sent to it goes to the whole server so everyone sees what their vote did */
    private static CommandSender broadcastingSender(Player player) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
            try {
                if (!method.getName().equals("sendMessage"))
                    return method.invoke(player, args);

                for (Player online : Bukkit.getOnlinePlayers())
                    method.invoke(online, args);

                method.invoke(Bukkit.getConsoleSender(), args);
                return null;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }

    private void reset() {
        if (phaseTask != null)
            phaseTask.cancel();

        phaseTask = null;
        bossBar.stop();
        phase = Phase.IDLE;
        chosen.clear();
        submissions.clear();
        ballot.clear();
        votes.clear();
        denied.clear();
    }

    /** Shows a prompt in italics, wrapped in gray quotes */
    private static Component quoted(String prompt) {
        return Component.text("\"", NamedTextColor.GRAY)
                .append(Component.text(prompt, NamedTextColor.WHITE, TextDecoration.ITALIC))
                .append(Component.text("\""));
    }

    private void broadcast(Component message) {
        Bukkit.broadcast(MinecraftTools.PREFIX.append(message));
    }
}
