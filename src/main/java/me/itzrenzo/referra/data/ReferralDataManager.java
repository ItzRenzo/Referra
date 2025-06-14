package me.itzrenzo.referra.data;

import me.itzrenzo.referra.database.DatabaseManager;
import me.itzrenzo.referra.database.impl.MysqlDatabaseManager;
import me.itzrenzo.referra.database.impl.SqliteDatabaseManager;
import me.itzrenzo.referra.database.impl.YmlDatabaseManager;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

public class ReferralDataManager {
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerReferralData> playerData;
    private final Map<UUID, UUID> referredBy; // Maps referred player ID to referrer ID
    private final Map<UUID, Long> playerFirstJoinTime; // Maps player ID to first join timestamp
    
    // Configuration values - loaded from config.yml
    private long requiredPlaytimeHours;
    private long requiredPlaytimeTicks;
    private int checkIntervalMinutes;
    private int payoutThreshold;
    
    // Database manager
    private DatabaseManager databaseManager;
    
    public ReferralDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerData = new HashMap<>();
        this.referredBy = new HashMap<>();
        this.playerFirstJoinTime = new HashMap<>();
        
        // Load configuration
        loadConfiguration();
        
        // Initialize database
        initializeDatabase();
        
        // Load data from database
        loadData();
    }
    
    private void loadConfiguration() {
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();
        
        // Load values from config
        requiredPlaytimeHours = plugin.getConfig().getLong("referral.required-playtime-hours", 168);
        checkIntervalMinutes = plugin.getConfig().getInt("referral.check-interval-minutes", 5);
        payoutThreshold = plugin.getConfig().getInt("referral.payout-threshold", 100);
        
        // Convert hours to ticks (20 ticks per second * 60 seconds * 60 minutes * hours)
        requiredPlaytimeTicks = requiredPlaytimeHours * 20L * 60 * 60;
        
        plugin.getLogger().info("Configuration loaded: Required playtime = " + requiredPlaytimeHours + " hours (" + 
                               (requiredPlaytimeHours / 24.0) + " days)");
    }
    
    private void initializeDatabase() {
        String databaseType = plugin.getConfig().getString("database.type", "YML").toUpperCase();
        
        switch (databaseType) {
            case "MYSQL" -> {
                String host = plugin.getConfig().getString("database.mysql.host", "localhost");
                int port = plugin.getConfig().getInt("database.mysql.port", 3306);
                String database = plugin.getConfig().getString("database.mysql.database", "referral_system");
                String username = plugin.getConfig().getString("database.mysql.username", "root");
                String password = plugin.getConfig().getString("database.mysql.password", "password");
                int maxPoolSize = plugin.getConfig().getInt("database.mysql.max-pool-size", 10);
                long connectionTimeout = plugin.getConfig().getLong("database.mysql.connection-timeout", 30000);
                
                databaseManager = new MysqlDatabaseManager(plugin, host, port, database, username, password, maxPoolSize, connectionTimeout);
            }
            case "SQLITE" -> {
                String filename = plugin.getConfig().getString("database.sqlite.filename", "referrals.db");
                databaseManager = new SqliteDatabaseManager(plugin, filename);
            }
            default -> {
                databaseManager = new YmlDatabaseManager(plugin);
            }
        }
        
        // Initialize database asynchronously
        databaseManager.initialize().thenRun(() -> {
            plugin.getLogger().info("Database initialized: " + databaseManager.getDatabaseType());
        }).exceptionally(throwable -> {
            plugin.getLogger().severe("Failed to initialize database: " + throwable.getMessage());
            throwable.printStackTrace();
            return null;
        });
    }
    
    public void reloadConfiguration() {
        plugin.reloadConfig();
        loadConfiguration();
        
        // Reinitialize database if type changed
        String newDatabaseType = plugin.getConfig().getString("database.type", "YML").toUpperCase();
        if (!newDatabaseType.equals(databaseManager.getDatabaseType())) {
            plugin.getLogger().info("Database type changed, reinitializing...");
            
            // Save current data to old database
            saveData();
            
            // Close old database
            databaseManager.close();
            
            // Initialize new database
            initializeDatabase();
            
            // Load data from new database
            loadData();
        }
        
        plugin.getLogger().info("Configuration reloaded!");
    }
    
    public void loadData() {
        databaseManager.loadAllPlayerData().thenAccept(loadedPlayerData -> {
            playerData.clear();
            playerData.putAll(loadedPlayerData);
        });
        
        databaseManager.loadReferralMappings().thenAccept(mappings -> {
            referredBy.clear();
            referredBy.putAll(mappings);
        });
        
        databaseManager.loadFirstJoinTimes().thenAccept(firstJoinTimes -> {
            playerFirstJoinTime.clear();
            playerFirstJoinTime.putAll(firstJoinTimes);
        });
    }
    
    public void saveData() {
        databaseManager.saveAllPlayerData(playerData);
    }
    
    public PlayerReferralData getPlayerData(UUID playerId, String playerName) {
        return playerData.computeIfAbsent(playerId, k -> new PlayerReferralData(playerId, playerName));
    }
    
    public void recordFirstJoin(UUID playerId) {
        if (!playerFirstJoinTime.containsKey(playerId)) {
            long timestamp = System.currentTimeMillis();
            playerFirstJoinTime.put(playerId, timestamp);
            databaseManager.saveFirstJoinTime(playerId, timestamp);
        }
    }
    
    public boolean hasPlayedRequiredTime(Player player) {
        // If playtime requirement is disabled, always return true
        if (isPlaytimeRequirementDisabled()) {
            return true;
        }
        
        UUID playerId = player.getUniqueId();
        
        // Check if player has required playtime
        long playTimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        return playTimeTicks >= requiredPlaytimeTicks;
    }
    
    // Keep backward compatibility
    public boolean hasPlayedFor7Days(Player player) {
        return hasPlayedRequiredTime(player);
    }
    
    public boolean addReferral(UUID referrerId, UUID referredId) {
        if (referredBy.containsKey(referredId)) {
            return false; // Player already referred by someone
        }
        
        PlayerReferralData referrerData = playerData.get(referrerId);
        if (referrerData == null || !referrerData.isReferralEnabled()) {
            return false;
        }
        
        // Add as pending referral initially
        long currentTime = System.currentTimeMillis();
        referrerData.addPendingReferral(referredId, currentTime);
        referredBy.put(referredId, referrerId);
        recordFirstJoin(referredId);
        
        // Save to database
        databaseManager.savePlayerData(referrerData);
        
        return true;
    }
    
    public void checkAndConfirmReferrals(Player player) {
        UUID playerId = player.getUniqueId();
        UUID referrerId = referredBy.get(playerId);
        
        if (referrerId != null && hasPlayedRequiredTime(player)) {
            PlayerReferralData referrerData = playerData.get(referrerId);
            if (referrerData != null && referrerData.getPendingReferrals().containsKey(playerId)) {
                if (referrerData.confirmReferral(playerId)) {
                    // Save to database
                    databaseManager.savePlayerData(referrerData);
                    
                    // Notify the referrer if online
                    Player referrer = plugin.getServer().getPlayer(referrerId);
                    if (referrer != null && referrer.isOnline()) {
                        long hours = getRequiredPlaytimeHours();
                        String timeDesc = hours >= 24 ? (hours / 24) + " day" + (hours / 24 != 1 ? "s" : "") : hours + " hour" + (hours != 1 ? "s" : "");
                        referrer.sendMessage("§a[REFERRAL] " + player.getName() + " has now played for " + timeDesc + "! Referral confirmed. Total: " + referrerData.getReferralCount());
                    }
                    
                    plugin.getLogger().info("Confirmed referral: " + player.getName() + " referred by " + 
                            (referrer != null ? referrer.getName() : "Unknown"));
                }
            }
        }
    }
    
    public List<PlayerReferralData> getTopReferrers(int limit) {
        return playerData.values().stream()
                .sorted((a, b) -> Integer.compare(b.getReferralCount(), a.getReferralCount()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    public boolean isPlayerReferred(UUID playerId) {
        return referredBy.containsKey(playerId);
    }
    
    public UUID getReferrer(UUID playerId) {
        return referredBy.get(playerId);
    }
    
    public void resetPlayerData(UUID playerId) {
        PlayerReferralData data = playerData.get(playerId);
        if (data != null) {
            // Remove all referrals from referredBy map
            for (UUID referred : data.getConfirmedReferrals()) {
                referredBy.remove(referred);
            }
            for (UUID referred : data.getPendingReferrals().keySet()) {
                referredBy.remove(referred);
            }
            data.getConfirmedReferrals().clear();
            data.getPendingReferrals().clear();
            data.setClaimedPayout(false);
            
            // Save to database
            databaseManager.savePlayerData(data);
        }
    }
    
    public long getRequiredPlaytimeTicks() {
        return requiredPlaytimeTicks;
    }
    
    public long getRequiredPlaytimeHours() {
        return requiredPlaytimeHours;
    }
    
    public int getCheckIntervalMinutes() {
        return checkIntervalMinutes;
    }
    
    public int getPayoutThreshold() {
        return payoutThreshold;
    }
    
    public boolean isPlaytimeRequirementDisabled() {
        return requiredPlaytimeHours == 0;
    }
    
    public String getDatabaseType() {
        return databaseManager.getDatabaseType();
    }
    
    public void close() {
        if (databaseManager != null) {
            saveData();
            databaseManager.close();
        }
    }
}