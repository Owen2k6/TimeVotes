package me.goplex.voteplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

public class VoteManager {

    private final VotePlugin plugin;

    private final Map<String, Integer> activeVotes = new HashMap<>();
    private final Map<String, String> voteStarters = new HashMap<>();
    private final Map<String, Integer> durations = new HashMap<>();
    private final Map<String, Map<String, Long>> voteCooldowns = new HashMap<>();
    private final Map<String, List<Boolean>> voteChoices = new HashMap<>();
    private final Map<String, Set<String>> voteVoters = new HashMap<>();

    private int voteTaskId = -1;

    public VoteManager(VotePlugin plugin) {
        this.plugin = plugin;
    }

    public void handleVote(Player player, String type, String choice) {
        type = type.toLowerCase();
        choice = choice.toLowerCase();

        if (!VoteConfig.voteTimes.containsKey(type)) {
            player.sendMessage(ChatColor.RED + "Invalid vote type: " + ChatColor.GRAY + type);
            return;
        }

        if (!choice.equals("yes") && !choice.equals("no")) {
            player.sendMessage(ChatColor.RED + "Invalid choice. Use 'yes' or 'no'.");
            return;
        }

        String playerName = player.getName();
        long now = System.currentTimeMillis();

        // Cooldown check (per topic)
        voteCooldowns.putIfAbsent(type, new HashMap<>());
        Map<String, Long> typeCooldownMap = voteCooldowns.get(type);
        if (!activeVotes.containsKey(type)) {
            if (typeCooldownMap.containsKey(type)) {
                long lastVoteEndTime = typeCooldownMap.get(type);
                int cooldownSeconds = VoteConfig.voteCoolDowns.getOrDefault(type, 0);
                if ((now - lastVoteEndTime) < cooldownSeconds * 1000L) {
                    long timeLeft = (cooldownSeconds * 1000L - (now - lastVoteEndTime)) / 1000L;
                    player.sendMessage(ChatColor.RED + "You must wait " + ChatColor.YELLOW + timeLeft + " seconds" + ChatColor.RED + " before starting the vote " + ChatColor.GOLD + type + ChatColor.RED + " again.");
                    return;
                }
            }
        }

        if (activeVotes.containsKey(type)) {
            voteChoices.putIfAbsent(type, new ArrayList<>());
            voteVoters.putIfAbsent(type, new HashSet<>());
            List<Boolean> votes = voteChoices.get(type);
            Set<String> voters = voteVoters.get(type);

            if (voters.contains(playerName)) {
                player.sendMessage(ChatColor.YELLOW + "You have already voted for " + ChatColor.GOLD + type + ChatColor.YELLOW + ".");
                return;
            }

            voters.add(playerName);
            votes.add(choice.equals("yes"));
            player.sendMessage(ChatColor.YELLOW + "Your vote for " + ChatColor.GOLD + type + ChatColor.YELLOW + " has been recorded.");
            return;
        }

        if (!choice.equals("yes") && !choice.equals("")) {
            player.sendMessage(ChatColor.RED + "Only a 'yes' vote can start a vote.");
            return;
        }

        voteStarters.put(type, playerName);
        activeVotes.put(type, VoteConfig.voteTimes.get(type));
        durations.put(type, VoteConfig.voteDurations.get(type));
        voteChoices.put(type, new ArrayList<>(Collections.singletonList(true)));
        voteVoters.put(type, new HashSet<>(Collections.singleton(playerName)));

        Bukkit.broadcastMessage(ChatColor.GREEN + "Vote Started " + ChatColor.GRAY + "| " + ChatColor.GOLD + type + ChatColor.YELLOW + " vote has started.");
        startVoteTimer();
    }

    private void startVoteTimer() {
        if (voteTaskId != -1) return;

        voteTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            Iterator<Map.Entry<String, Integer>> iterator = activeVotes.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, Integer> entry = iterator.next();
                String topic = entry.getKey();
                int time = entry.getValue() - 1;

                if (time <= 0) {
                    endVote(topic);
                    iterator.remove();
                } else {
                    activeVotes.put(topic, time);
                }
            }

            if (activeVotes.isEmpty()) {
                Bukkit.getScheduler().cancelTask(voteTaskId);
                voteTaskId = -1;
            }
        }, 20L, 20L);
    }

    private void endVote(String type) {
        type = type.toLowerCase();
        String starter = voteStarters.remove(type);
        List<Boolean> votes = voteChoices.remove(type);
        voteVoters.remove(type);

        if (votes == null || votes.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.GRAY + "No one voted on " + ChatColor.GOLD + type + ChatColor.GRAY + ".");
            return;
        }

        long yesVotes = votes.stream().filter(v -> v).count();
        long totalVotes = votes.size();
        int requiredPercent = VoteConfig.votePercentages.getOrDefault(type, 50);
        int actualPercent = (int) ((yesVotes * 100.0f) / totalVotes);

        Bukkit.broadcastMessage(ChatColor.RED + "Vote Ended" + ChatColor.GRAY + " | " + ChatColor.RED + type + ChatColor.RED + " has ended.");
        Bukkit.broadcastMessage(ChatColor.GRAY + "Votes Cast: " + ChatColor.WHITE + totalVotes);
        Bukkit.broadcastMessage(ChatColor.GRAY + "YES: " + ChatColor.GREEN + yesVotes + ChatColor.GRAY + " NO: " + ChatColor.RED + (totalVotes - yesVotes));
        Bukkit.broadcastMessage(ChatColor.GRAY + "Required: " + ChatColor.YELLOW + requiredPercent + "%" + ChatColor.GRAY + "- Reached: " + ChatColor.YELLOW + actualPercent + "%");

        voteCooldowns.get(type).put(type, System.currentTimeMillis());

        if (actualPercent >= requiredPercent) {
            Bukkit.broadcastMessage(ChatColor.GREEN + "Vote for " + ChatColor.GOLD + type + ChatColor.GREEN + " has PASSED.");
            applyEffect(type, durations.remove(type));
        } else {
            Bukkit.broadcastMessage(ChatColor.RED + "Vote for " + ChatColor.GOLD + type + ChatColor.RED + " has FAILED.");
        }
    }

    private void applyEffect(final String type, final int duration) {
        switch (type.toLowerCase()) {
            case "day":
                Bukkit.getWorlds().get(0).setTime(1000);
                break;

            case "rain":
                Bukkit.getWorlds().get(0).setStorm(true);
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    Bukkit.getWorlds().get(0).setStorm(false);
                }, duration * 20L);
                break;
        }
    }

    public void sendTimeLeft(Player player, String type) {
        type = type.toLowerCase();
        long now = System.currentTimeMillis();

        if (activeVotes.containsKey(type)) {
            int time = activeVotes.get(type);
            player.sendMessage(ChatColor.BLUE + "Time left for " + ChatColor.GOLD + type + ChatColor.DARK_GRAY + " : " + ChatColor.YELLOW + time + " seconds.");
        } else if (voteCooldowns.containsKey(type) && voteCooldowns.get(type).containsKey("__global__")) {
            long last = voteCooldowns.get(type).get("__global__");
            int cooldownSeconds = VoteConfig.voteCoolDowns.getOrDefault(type, 0);
            long expiresAt = last + cooldownSeconds * 1000L;
            long timeLeft = (expiresAt - now) / 1000L;

            if (timeLeft > 0) {
                player.sendMessage(ChatColor.GRAY + "The topic " + ChatColor.GOLD + type + ChatColor.GRAY + " is on cooldown for " + ChatColor.YELLOW + timeLeft + ChatColor.GRAY + " more seconds.");
            } else {
                player.sendMessage(ChatColor.GRAY + "There is no active vote for " + ChatColor.GOLD + type + ChatColor.GRAY + ".");
            }
        } else {
            player.sendMessage(ChatColor.GRAY + "There is no active vote for " + ChatColor.GOLD + type + ChatColor.GRAY + ".");
        }
    }


}
