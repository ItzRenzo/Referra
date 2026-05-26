package me.itzrenzo.referra.listeners;

import me.itzrenzo.referra.data.ReferralDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerEventListener implements Listener {
    private final ReferralDataManager dataManager;
    private final JavaPlugin plugin;
    private BukkitTask referralCheckTask;
    
    public PlayerEventListener(ReferralDataManager dataManager, JavaPlugin plugin) {
        this.dataManager = dataManager;
        this.plugin = plugin;

        reloadCheckTask();
    }

    public void reloadCheckTask() {
        if (referralCheckTask != null) {
            referralCheckTask.cancel();
        }

        long intervalTicks = Math.max(1L, dataManager.getCheckIntervalMinutes()) * 20L * 60;
        referralCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkAllPlayersForReferralConfirmation();
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);

        plugin.getLogger().info("Referral check task started with " + dataManager.getCheckIntervalMinutes() + " minute intervals");
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        String playerIP = null;
        if (player.getAddress() != null && player.getAddress().getAddress() != null) {
            playerIP = player.getAddress().getAddress().getHostAddress();
        }
        dataManager.recordPlayerIP(player.getUniqueId(), playerIP);
        dataManager.recordFirstJoin(player.getUniqueId());
        dataManager.checkAndConfirmReferrals(player);
        dataManager.sendRewardReminder(player);
    }
    
    private void checkAllPlayersForReferralConfirmation() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            dataManager.checkAndConfirmReferrals(player);
        }
    }
}