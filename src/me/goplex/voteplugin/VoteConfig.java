package me.goplex.voteplugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class VoteConfig {

    private static File configFile;
    private static FileConfiguration config;

    public static final Map<String, Integer> voteTimes = new HashMap<>();
    public static final Map<String, Integer> voteDurations = new HashMap<>();
    public static final Map<String, Integer> voteCoolDowns = new HashMap<>();
    public static final Map<String, Integer> votePercentages = new HashMap<>();

    static Logger logger = Bukkit.getLogger();
    
    public static void load(Plugin plugin) {
        // Create plugin folder if not exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        configFile = new File(plugin.getDataFolder(), "votes.yml");

        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                config = YamlConfiguration.loadConfiguration(configFile);

                // Default vote types
                config.set("types.day.votetime", 30);
                config.set("types.day.duration", 0);
                config.set("types.day.cooldown", 20);
                config.set("types.day.percentage", 60);
                              
                config.set("types.rain.votetime", 60);
                config.set("types.rain.duration", 20);
                config.set("types.rain.cooldown", 20);
                config.set("types.rain.percentage", 60);
                
                config.save(configFile);
                logger.info("Created default votes.yml");
            } catch (IOException e) {
            	logger.severe("Failed to create votes.yml: " + e.getMessage());
                return;
            }
        } else {
            config = YamlConfiguration.loadConfiguration(configFile);
        }

        loadVoteTypes(plugin);
    }

    private static void loadVoteTypes(Plugin plugin) {
        if (!config.contains("types")) {
            logger.warning("votes.yml does not contain any vote types!");
            return;
        }

        for (String type : config.getConfigurationSection("types").getKeys(false)) {
            int votetime = config.getInt("types." + type + ".votetime", 30);
            int duration = config.getInt("types." + type + ".duration", 0);
            int cooldown = config.getInt("types." + type + ".cooldown", 0);
            int percentage = config.getInt("types." + type + ".percentage", 60);
            
            voteTimes.put(type.toLowerCase(), votetime);
            voteDurations.put(type.toLowerCase(), duration);
            voteCoolDowns.put(type, cooldown);
            votePercentages.put(type.toLowerCase(), percentage);
        }

        logger.info("Loaded " + voteTimes.size() + " vote types from votes.yml");
    }
    
    public static void reload() {
        if (configFile != null) {
            config = YamlConfiguration.loadConfiguration(configFile);
        }
    }

    public static void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static FileConfiguration getRawConfig() {
        return config;
    }
}
