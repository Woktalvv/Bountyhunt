package me.woktalvv.bountyhunt;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerEventListener implements Listener {

    private final BountyHuntPlugin plugin;

    public PlayerEventListener(BountyHuntPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();

        if (killer != null) {
            BountyHunt hunt = plugin.getActiveHunts().get(killer.getUniqueId());
            if (hunt != null && hunt.isActive() && hunt.getTargetUUID().equals(player.getUniqueId())) {
                hunt.completeHunt(true);
                return;
            }
        }

        BountyHunt huntersHunt = plugin.getActiveHunts().get(player.getUniqueId());
        if (huntersHunt != null && huntersHunt.isActive()) {
            huntersHunt.completeHunt(false);
            return;
        }

        for (BountyHunt hunt : plugin.getActiveHunts().values()) {
            if (hunt.isActive() && hunt.getTargetUUID().equals(player.getUniqueId())) {
                hunt.completeHunt(false);
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean isInHunt = false;
        
        BountyHunt hunt = plugin.getActiveHunts().get(player.getUniqueId());
        if (hunt != null && hunt.isActive()) {
            isInHunt = true;
            if (!hunt.isInHuntPhase()) {
                hunt.cancel();
                plugin.getActiveHunts().remove(player.getUniqueId());
                Player target = Bukkit.getPlayer(hunt.getTargetUUID());
                if (target != null && target.isOnline()) {
                    target.sendMessage(plugin.getMessage("hunt-cancelled"));
                }
            }
        }

        for (BountyHunt activeHunt : plugin.getActiveHunts().values()) {
            if (activeHunt.isActive() && activeHunt.getTargetUUID().equals(player.getUniqueId())) {
                isInHunt = true;
                break;
            }
        }

        // Replace real name with anonymous message
        if (isInHunt) {
            event.setQuitMessage(plugin.getAnonymousLeaveMsg());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean isInHunt = false;
        
        BountyHunt hunt = plugin.getActiveHunts().get(player.getUniqueId());
        if (hunt != null && hunt.isActive()) {
            isInHunt = true;
        }

        for (BountyHunt activeHunt : plugin.getActiveHunts().values()) {
            if (activeHunt.isActive() && activeHunt.getTargetUUID().equals(player.getUniqueId())) {
                isInHunt = true;
                break;
            }
        }

        // Replace real name with anonymous message
        if (isInHunt) {
            event.setJoinMessage(plugin.getAnonymousJoinMsg());
        }
    }
}
