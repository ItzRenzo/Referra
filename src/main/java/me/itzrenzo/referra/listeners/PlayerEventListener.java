package me.itzrenzo.referra.listeners;

import me.itzrenzo.referra.data.ReferralDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerEventListener implements Listener {
    private final ReferralDataManager dataManager;
    private final JavaPlugin plugin;
    
    public PlayerEventListener(ReferralDataManager dataManager, JavaPlugin plugin) {
        this.dataManager = dataManager;
        this.plugin = plugin;
        
        // Start periodic task using configurable interval
        long intervalTicks = dataManager.getCheckIntervalMinutes() * 20L * 60; // Convert minutes to ticks
        new BukkitRunnable() {
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
        
        // Record first join time if this is their first time
        dataManager.recordFirstJoin(player.getUniqueId());
        
        // Check if any referrals can be confirmed for this player
        dataManager.checkAndConfirmReferrals(player);
    }
    
    private void checkAllPlayersForReferralConfirmation() {
        // Check all online players for referral confirmations
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            dataManager.checkAndConfirmReferrals(player);
        }
    }
}