package me.woktalvv.bountyhunt;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BountyHuntPlugin extends JavaPlugin {

    private final Map<UUID, BountyHunt> activeHunts = new ConcurrentHashMap<>();
    private FileConfiguration config;
    private int prepTime;
    private int baseHuntTime;
    private double distanceMultiplier;
    private boolean deathPenalty;
    private boolean lootReward;
    private String anonymousJoinMsg;
    private String anonymousLeaveMsg;
    private String bossBarTitle;
    private BarColor bossBarColor;
    private BarStyle bossBarStyle;
    private Map<String, String> messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);
        getLogger().info("BountyHunt plugin enabled by Woktalvv!");
    }

    @Override
    public void onDisable() {
        for (BountyHunt hunt : activeHunts.values()) {
            hunt.cancel();
        }
        activeHunts.clear();
        getLogger().info("BountyHunt plugin disabled!");
    }

    private void loadConfig() {
        config = getConfig();
        prepTime = config.getInt("prep-time", 900);
        baseHuntTime = config.getInt("base-hunt-time", 900);
        distanceMultiplier = config.getDouble("distance-multiplier", 0.75);
        deathPenalty = config.getBoolean("death-penalty", true);
        lootReward = config.getBoolean("loot-reward", true);

        anonymousJoinMsg = ChatColor.translateAlternateColorCodes('&',
            config.getString("anonymous-messages.join", "&7A random player joined the game"));
        anonymousLeaveMsg = ChatColor.translateAlternateColorCodes('&',
            config.getString("anonymous-messages.leave", "&7A random player left the game"));

        bossBarTitle = ChatColor.translateAlternateColorCodes('&',
            config.getString("bossbar.title", "&c&lHUNT ACTIVE &7| &fDistance: &c{distance}m &7| &fWorld: &e{dimension}"));
        bossBarColor = BarColor.valueOf(config.getString("bossbar.color", "RED").toUpperCase());
        bossBarStyle = BarStyle.valueOf(config.getString("bossbar.style", "SEGMENTED_10").toUpperCase());

        messages = new HashMap<>();
        if (config.getConfigurationSection("messages") != null) {
            for (String key : config.getConfigurationSection("messages").getKeys(false)) {
                messages.put(key, ChatColor.translateAlternateColorCodes('&',
                    config.getString("messages." + key, "")));
            }
        }
    }

    public BossBar createBossBar(Player hunter, Player target) {
        double distance = hunter.getLocation().distance(target.getLocation());
        String dimension = formatWorldName(target.getWorld());
        
        String title = bossBarTitle
            .replace("{distance}", String.format("%.0f", distance))
            .replace("{dimension}", dimension);
            
        BossBar bar = Bukkit.createBossBar(title, bossBarColor, bossBarStyle);
        bar.addPlayer(hunter);
        bar.setProgress(Math.min(1.0, distance / 15000.0));
        bar.setVisible(true);
        return bar;
    }

    private String formatWorldName(World world) {
        if (world == null) return "Unknown";
        String name = world.getName().toLowerCase();
        if (name.contains("nether")) return "Nether";
        if (name.contains("end")) return "The End";
        if (name.contains("overworld") || name.equals("world")) return "Overworld";
        return world.getName();
    }

    public Map<UUID, BountyHunt> getActiveHunts() {
        return activeHunts;
    }

    public void startHunt(Player hunter) {
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(hunter);

        if (onlinePlayers.size() < 1) {
            hunter.sendMessage(getMessage("not-enough-players"));
            return;
        }

        if (activeHunts.containsKey(hunter.getUniqueId())) {
            hunter.sendMessage(getMessage("already-hunting"));
            return;
        }

        Player target = onlinePlayers.get(new Random().nextInt(onlinePlayers.size()));

        BountyHunt hunt = new BountyHunt(this, hunter, target);
        activeHunts.put(hunter.getUniqueId(), hunt);
        hunt.startPreparationTimer();

        hunter.sendMessage(getMessage("hunt-started"));
        
        String prepTimeMessage = getMessage("hunt-started-target")
            .replace("{hunter}", hunter.getName())
            .replace("{prepTime}", String.valueOf(prepTime / 60));
        target.sendMessage(prepTimeMessage);
    }

    public String getMessage(String key) {
        return messages.getOrDefault(key, "&cMessage not found: " + key);
    }

    public String getAnonymousJoinMsg() {
        return anonymousJoinMsg;
    }

    public String getAnonymousLeaveMsg() {
        return anonymousLeaveMsg;
    }

    public int getPrepTime() {
        return prepTime;
    }

    public int getBaseHuntTime() {
        return baseHuntTime;
    }

    public double getDistanceMultiplier() {
        return distanceMultiplier;
    }

    public boolean isDeathPenalty() {
        return deathPenalty;
    }

    public boolean isLootReward() {
        return lootReward;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command!");
            return true;
        }

        if (!cmd.getName().equalsIgnoreCase("bounty")) {
            return false;
        }

        if (args.length == 0 || !args[0].equalsIgnoreCase("start")) {
            player.sendMessage(ChatColor.RED + "Usage: /bounty start");
            return true;
        }

        startHunt(player);
        return true;
    }
}
