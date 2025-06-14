package me.itzrenzo.referra.data;

import me.itzrenzo.referra.database.DatabaseManager;
import me.itzrenzo.referra.database.impl.MysqlDatabaseManager;
import me.itzrenzo.referra.database.impl.SqliteDatabaseManager;
import me.itzrenzo.referra.database.impl.YmlDatabaseManager;
import me.itzrenzo.referra.discord.DiscordWebhookManager;
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
    private final Map<UUID, String> playerIPs; // Maps player ID to IP address for anti-abuse
    
    // Configuration values - loaded from config.yml
    private long requiredPlaytimeHours;
    private long requiredPlaytimeTicks;
    private int checkIntervalMinutes;
    private int payoutThreshold;
    
    // Database manager
    private DatabaseManager databaseManager;
    
    // Discord webhook manager
    private DiscordWebhookManager discordManager;
    
    public ReferralDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerData = new HashMap<>();
        this.referredBy = new HashMap<>();
        this.playerFirstJoinTime = new HashMap<>();
        this.playerIPs = new HashMap<>();
        
        // Load configuration
        loadConfiguration();
        
        // Initialize Discord webhook manager
        this.discordManager = new DiscordWebhookManager(plugin);
        
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
        
        // Initialize database synchronously during startup
        try {
            databaseManager.initialize().get(); // Wait for initialization to complete
            plugin.getLogger().info("Database initialized: " + databaseManager.getDatabaseType());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    public void reloadConfiguration() {
        plugin.reloadConfig();
        loadConfiguration();
        
        // Reload Discord configuration
        discordManager.loadConfiguration();
        
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
        try {
            // Load data synchronously during startup to ensure it's available immediately
            plugin.getLogger().info("Loading referral data from database...");
            
            // Load player data
            Map<UUID, PlayerReferralData> loadedPlayerData = databaseManager.loadAllPlayerData().get();
            playerData.clear();
            playerData.putAll(loadedPlayerData);
            plugin.getLogger().info("Loaded " + playerData.size() + " player records");
            
            // Load referral mappings
            Map<UUID, UUID> mappings = databaseManager.loadReferralMappings().get();
            referredBy.clear();
            referredBy.putAll(mappings);
            plugin.getLogger().info("Loaded " + referredBy.size() + " referral mappings");
            
            // Load first join times
            Map<UUID, Long> firstJoinTimes = databaseManager.loadFirstJoinTimes().get();
            playerFirstJoinTime.clear();
            playerFirstJoinTime.putAll(firstJoinTimes);
            plugin.getLogger().info("Loaded " + playerFirstJoinTime.size() + " first join times");
            
            // Load player IPs
            Map<UUID, String> ipMappings = databaseManager.loadPlayerIPs().get();
            playerIPs.clear();
            playerIPs.putAll(ipMappings);
            plugin.getLogger().info("Loaded " + playerIPs.size() + " player IP mappings");
            
            plugin.getLogger().info("Referral data loading completed successfully!");
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load referral data from database: " + e.getMessage());
            e.printStackTrace();
        }
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
    
    public void recordPlayerIP(UUID playerId, String ipAddress) {
        playerIPs.put(playerId, ipAddress);
        databaseManager.savePlayerIP(playerId, ipAddress);
    }
    
    public boolean hasSameIPReferral(UUID referrerId, UUID referredId) {
        String referrerIP = playerIPs.get(referrerId);
        String referredIP = playerIPs.get(referredId);
        
        // If we don't have IP data for either player, allow the referral
        if (referrerIP == null || referredIP == null) {
            return false;
        }
        
        // Check if they have the same IP address
        return referrerIP.equals(referredIP);
    }
    
    public long countReferralsFromSameIP(UUID referrerId, String ipAddress) {
        if (ipAddress == null) return 0;
        
        PlayerReferralData referrerData = playerData.get(referrerId);
        if (referrerData == null) return 0;
        
        long count = 0;
        
        // Check confirmed referrals
        for (UUID referredId : referrerData.getConfirmedReferrals()) {
            String referredIP = playerIPs.get(referredId);
            if (ipAddress.equals(referredIP)) {
                count++;
            }
        }
        
        // Check pending referrals
        for (UUID referredId : referrerData.getPendingReferrals().keySet()) {
            String referredIP = playerIPs.get(referredId);
            if (ipAddress.equals(referredIP)) {
                count++;
            }
        }
        
        return count;
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
        
        // Anti-abuse: Check if referrer and referred player have same IP
        if (hasSameIPReferral(referrerId, referredId)) {
            plugin.getLogger().warning("Blocked referral attempt: Same IP detected for referrer " + referrerId + " and referred " + referredId);
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
                    
                    // Get referrer info
                    Player referrer = plugin.getServer().getPlayer(referrerId);
                    String referrerName = referrer != null ? referrer.getName() : referrerData.getPlayerName();
                    
                    // Check if referrer has reached payout threshold
                    if (referrerData.getReferralCount() == payoutThreshold) {
                        // Send Discord notification for threshold reached
                        discordManager.sendThresholdReachedNotification(referrerName, referrerData.getReferralCount(), payoutThreshold);
                        
                        // Notify player about payout eligibility
                        if (referrer != null && referrer.isOnline()) {
                            String payoutMessage = plugin.getConfig().getString("messages.payout-eligible", "&a&lCongratulations! &r&aYou're now eligible for IRL payout!");
                            referrer.sendMessage(payoutMessage.replace("&", "§"));
                            
                            String discordInstructions = plugin.getConfig().getString("messages.discord-instructions", "&eJoin our Discord server and create a ticket to claim your reward: &b{invite}");
                            if (discordManager.isEnabled() && !discordManager.getServerInvite().isEmpty()) {
                                referrer.sendMessage(discordInstructions.replace("&", "§").replace("{invite}", discordManager.getServerInvite()));
                            }
                        }
                    }
                    
                    // Send Discord notification for referral confirmed (if enabled)
                    discordManager.sendReferralConfirmedNotification(player.getName(), referrerName, referrerData.getReferralCount());
                    
                    // Notify the referrer if online
                    if (referrer != null && referrer.isOnline()) {
                        long hours = getRequiredPlaytimeHours();
                        String timeDesc = hours >= 24 ? (hours / 24) + " day" + (hours / 24 != 1 ? "s" : "") : hours + " hour" + (hours != 1 ? "s" : "");
                        referrer.sendMessage("§a[REFERRAL] " + player.getName() + " has now played for " + timeDesc + "! Referral confirmed. Total: " + referrerData.getReferralCount());
                    }
                    
                    plugin.getLogger().info("Confirmed referral: " + player.getName() + " referred by " + referrerName);
                }
            }
        }
    }
    
    public List<PlayerReferralData> getTopReferrers(int limit) {
        return playerData.values().stream()
                .filter(data -> !data.getPlayerName().equalsIgnoreCase("unknown")) // Filter out "unknown" players (likely bots)
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
            // DON'T remove referral mappings from referredBy map
            // The referred players should still show they were referred by this player
            // Only clear the referrer's own referral lists and reset payout status
            
            data.getConfirmedReferrals().clear();
            data.getPendingReferrals().clear();
            data.setClaimedPayout(false);
            
            // Save to database
            databaseManager.savePlayerData(data);
        }
    }
    
    public boolean claimPayout(UUID playerId) {
        PlayerReferralData data = playerData.get(playerId);
        if (data != null && data.canClaimPayout(payoutThreshold)) {
            // Store the referrals that will be removed for payout
            List<UUID> referralsToRemove = new ArrayList<>();
            List<UUID> referralsList = new ArrayList<>(data.getConfirmedReferrals());
            
            // Get the first N referrals to remove (where N = payoutThreshold)
            for (int i = 0; i < payoutThreshold && i < referralsList.size(); i++) {
                referralsToRemove.add(referralsList.get(i));
            }
            
            // Remove the payout referrals from the player's data
            if (data.removePayoutReferrals(payoutThreshold)) {
                // Don't remove from referredBy map - players should still show who referred them
                // Only set the payout as claimed and save
                data.setClaimedPayout(true);
                databaseManager.savePlayerData(data);
                return true;
            }
        }
        return false;
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
    
    public DiscordWebhookManager getDiscordManager() {
        return discordManager;
    }
}