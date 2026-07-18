package me.woktalvv.bountyhunt;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class BountyHunt {

    private final BountyHuntPlugin plugin;
    private final Player hunter;
    private final Player target;
    private final UUID hunterUUID;
    private final UUID targetUUID;
    private int prepTimeRemaining;
    private int huntTimeRemaining;
    private boolean isActive;
    private boolean inHuntPhase;
    private boolean wasPaused;
    private BukkitRunnable prepTask;
    private BukkitRunnable huntTask;
    private org.bukkit.boss.BossBar bossBar;

    public BountyHunt(BountyHuntPlugin plugin, Player hunter, Player target) {
        this.plugin = plugin;
        this.hunter = hunter;
        this.target = target;
        this.hunterUUID = hunter.getUniqueId();
        this.targetUUID = target.getUniqueId();
        this.prepTimeRemaining = plugin.getPrepTime();
        this.isActive = true;
        this.inHuntPhase = false;
        this.wasPaused = false;
    }

    public Player getHunter() {
        return hunter;
    }

    public Player getTarget() {
        return target;
    }

    public UUID getHunterUUID() {
        return hunterUUID;
    }

    public UUID getTargetUUID() {
        return targetUUID;
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isInHuntPhase() {
        return inHuntPhase;
    }

    public void startPreparationTimer() {
        prepTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive) {
                    cancel();
                    return;
                }

                Player currentHunter = Bukkit.getPlayer(hunterUUID);
                Player currentTarget = Bukkit.getPlayer(targetUUID);

                if (currentHunter == null || currentTarget == null || !currentHunter.isOnline() || !currentTarget.isOnline()) {
                    if (!wasPaused) {
                        wasPaused = true;
                    }
                    return;
                }

                if (wasPaused) {
                    wasPaused = false;
                }

                prepTimeRemaining--;

                if (prepTimeRemaining <= 0) {
                    startHuntTimer();
                    cancel();
                }
            }
        };
        prepTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void startHuntTimer() {
        if (!isActive) return;

        Player currentHunter = Bukkit.getPlayer(hunterUUID);
        Player currentTarget = Bukkit.getPlayer(targetUUID);

        if (currentHunter == null || currentTarget == null || !currentHunter.isOnline() || !currentTarget.isOnline()) {
            wasPaused = true;
            return;
        }

        inHuntPhase = true;
        wasPaused = false;
        
        double distance = currentHunter.getLocation().distance(currentTarget.getLocation());
        int calculatedTime = (int) (distance * plugin.getDistanceMultiplier());
        huntTimeRemaining = plugin.getBaseHuntTime() + calculatedTime;
        
        int minutes = huntTimeRemaining / 60;
        int seconds = huntTimeRemaining % 60;
        currentHunter.sendMessage(plugin.getMessage("hunt-time-up")
            .replace("{huntTime}", String.format("%d:%02d", minutes, seconds)));

        bossBar = plugin.createBossBar(currentHunter, currentTarget);

        huntTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isActive) {
                    cancel();
                    return;
                }

                Player huntHunter = Bukkit.getPlayer(hunterUUID);
                Player huntTarget = Bukkit.getPlayer(targetUUID);

                // HUNTER logs off = GAME OVER
                if (huntHunter == null || !huntHunter.isOnline()) {
                    endHuntHunterLeft();
                    cancel();
                    return;
                }

                // TARGET logs off = pause timer
                if (huntTarget == null || !huntTarget.isOnline()) {
                    if (!wasPaused) {
                        wasPaused = true;
                        if (bossBar != null) {
                            bossBar.setVisible(false);
                        }
                    }
                    return;
                }

                if (wasPaused) {
                    wasPaused = false;
                    if (bossBar != null) {
                        bossBar.setVisible(true);
                    }
                }

                if (huntTarget.isDead()) {
                    completeHunt(true);
                    cancel();
                    return;
                }

                double currentDistance = huntHunter.getLocation().distance(huntTarget.getLocation());
                if (bossBar != null) {
                    bossBar.removeAll();
                }
                bossBar = plugin.createBossBar(huntHunter, huntTarget);

                huntTimeRemaining--;

                if (huntTimeRemaining <= 0) {
                    completeHunt(false);
                    cancel();
                }
            }
        };
        huntTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void endHuntHunterLeft() {
        isActive = false;
        inHuntPhase = false;

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        Player huntTarget = Bukkit.getPlayer(targetUUID);
        if (huntTarget != null && huntTarget.isOnline()) {
            huntTarget.sendMessage(plugin.getMessage("hunt-ended-hunter-left"));
        }

        plugin.getActiveHunts().remove(hunterUUID);
    }

    public void completeHunt(boolean success) {
        isActive = false;
        inHuntPhase = false;
        wasPaused = false;

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        Player currentHunter = Bukkit.getPlayer(hunterUUID);
        Player currentTarget = Bukkit.getPlayer(targetUUID);

        if (success) {
            if (plugin.isLootReward() && currentHunter != null && currentTarget != null) {
                org.bukkit.inventory.PlayerInventory targetInv = currentTarget.getInventory();
                for (ItemStack item : targetInv.getContents()) {
                    if (item != null) {
                        currentHunter.getInventory().addItem(item);
                    }
                }
                targetInv.clear();
                
                currentHunter.sendMessage(plugin.getMessage("hunter-won").replace("{target}", currentTarget.getName()));
                currentTarget.sendMessage(plugin.getMessage("target-killed").replace("{hunter}", currentHunter.getName()));
            }
        } else {
            if (plugin.isDeathPenalty() && currentHunter != null && currentHunter.isOnline()) {
                currentHunter.setHealth(0);
                currentHunter.sendMessage(plugin.getMessage("hunter-failed"));
            }
            if (currentTarget != null && currentTarget.isOnline()) {
                currentTarget.sendMessage(plugin.getMessage("target-survived"));
            }
        }

        plugin.getActiveHunts().remove(hunterUUID);
    }

    public void cancel() {
        isActive = false;
        inHuntPhase = false;
        wasPaused = false;
        if (prepTask != null) {
            prepTask.cancel();
            prepTask = null;
        }
        if (huntTask != null) {
            huntTask.cancel();
            huntTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }
}
