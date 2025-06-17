package me.goplex.voteplugin;

import org.bukkit.Bukkit;
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
            player.sendMessage("§cInvalid vote type: §7" + type);
            return;
        }

        if (!choice.equals("yes") && !choice.equals("no")) {
            player.sendMessage("§cInvalid choice. Use 'yes' or 'no'.");
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
                    player.sendMessage("§cYou must wait §e" + timeLeft + " seconds §cbefore starting the vote §6" + type + "§c again.");
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
                player.sendMessage("§eYou have already voted for §6" + type + "§e.");
                return;
            }

            voters.add(playerName);
            votes.add(choice.equals("yes"));
            player.sendMessage("§aYour vote for §6" + type + " §ahas been recorded.");
            return;
        }

        if (!choice.equals("yes") && !choice.equals("")) {
            player.sendMessage("§cOnly a 'yes' vote can start a vote.");
            return;
        }

        voteStarters.put(type, playerName);
        activeVotes.put(type, VoteConfig.voteTimes.get(type));
        durations.put(type, VoteConfig.voteDurations.get(type));
        voteChoices.put(type, new ArrayList<>(Collections.singletonList(true)));
        voteVoters.put(type, new HashSet<>(Collections.singleton(playerName)));

        Bukkit.broadcastMessage("§aVote Started §7| §6" + type + "§e vote has started.");
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
            Bukkit.broadcastMessage("§7No one voted on §6" + type + "§7.");
            return;
        }

        long yesVotes = votes.stream().filter(v -> v).count();
        long totalVotes = votes.size();
        int requiredPercent = VoteConfig.votePercentages.getOrDefault(type, 50);
        int actualPercent = (int)((yesVotes * 100.0f) / totalVotes);

        Bukkit.broadcastMessage("§cVote Ended §7| §6" + type + " §chas ended.");
        Bukkit.broadcastMessage("§7Votes Cast: §f" + totalVotes);
        Bukkit.broadcastMessage("§7YES: §a" + yesVotes + " §7NO: §c" + (totalVotes - yesVotes));
        Bukkit.broadcastMessage("§7Required: §e" + requiredPercent + "% §7- Reached: §e" + actualPercent + "%");

        voteCooldowns.get(type).put(type, System.currentTimeMillis());

        if (actualPercent >= requiredPercent) {
            Bukkit.broadcastMessage("§aVote for §6" + type + " §ahas PASSED.");
            applyEffect(type, durations.remove(type));
        } else {
            Bukkit.broadcastMessage("§cVote for §6" + type + " §chas FAILED.");
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
            player.sendMessage("§bTime left for §6" + type + " §8: §e" + time + " seconds.");
        } else if (voteCooldowns.containsKey(type) && voteCooldowns.get(type).containsKey("__global__")) {
            long last = voteCooldowns.get(type).get("__global__");
            int cooldownSeconds = VoteConfig.voteCoolDowns.getOrDefault(type, 0);
            long expiresAt = last + cooldownSeconds * 1000L;
            long timeLeft = (expiresAt - now) / 1000L;

            if (timeLeft > 0) {
                player.sendMessage("§7The topic §6" + type + " §7is on cooldown for §e" + timeLeft + "§7 more seconds.");
            } else {
                player.sendMessage("§7There is no active vote for §6" + type + "§7.");
            }
        } else {
            player.sendMessage("§7There is no active vote for §6" + type + "§7.");
        }
    }

    
    
}
