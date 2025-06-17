package me.goplex.voteplugin;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class VotePlugin extends JavaPlugin {

    private Logger logger = Bukkit.getLogger();
    private static VoteManager VoteManager;
    private VoteConfig VoteConfig;

    @Override
    public void onEnable() {

        VoteConfig = new VoteConfig();
        me.goplex.voteplugin.VoteConfig.load(this);

        this.VoteManager = new VoteManager(this);

        logger.info("VotePlugin enabled!");
        logger.info("Developed by ItsVollx");
    }

    @Override
    public void onDisable() {
        logger.info("VotePlugin disabled!");
    }


    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        String command = label.toLowerCase();

        // /vote <type> <yes|no>
        if (command.equalsIgnoreCase("vote")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /vote <type> [yes|no]");
                return true;
            }

            String type = args[0];
            String choice = (args.length >= 2) ? args[1] : "yes"; // Default to yes if not specified

            VoteManager.handleVote(player, type, choice);
            return true;
        }


        // /votetimeleft <type>
        if (command.equals("votetimeleft")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /votetimeleft <type>");
                return true;
            }

            String type = args[0];
            VoteManager.sendTimeLeft(player, type);
            return true;
        }

        player.sendMessage(ChatColor.RED + "Unknown command. Try /vote or /votetimeleft.");
        return true;
    }


}
