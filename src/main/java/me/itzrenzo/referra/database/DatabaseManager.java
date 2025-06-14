package me.itzrenzo.referra.database;

import me.itzrenzo.referra.data.PlayerReferralData;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface DatabaseManager {
    
    /**
     * Initialize the database connection and create tables if needed
     */
    CompletableFuture<Void> initialize();
    
    /**
     * Close the database connection
     */
    void close();
    
    /**
     * Load all player data from the database
     */
    CompletableFuture<Map<UUID, PlayerReferralData>> loadAllPlayerData();
    
    /**
     * Save a player's referral data to the database
     */
    CompletableFuture<Void> savePlayerData(PlayerReferralData data);
    
    /**
     * Save multiple players' data at once
     */
    CompletableFuture<Void> saveAllPlayerData(Map<UUID, PlayerReferralData> playerData);
    
    /**
     * Load referral mapping (who referred whom)
     */
    CompletableFuture<Map<UUID, UUID>> loadReferralMappings();
    
    /**
     * Save referral mapping
     */
    CompletableFuture<Void> saveReferralMapping(UUID referredPlayer, UUID referrer);
    
    /**
     * Remove referral mapping
     */
    CompletableFuture<Void> removeReferralMapping(UUID referredPlayer);
    
    /**
     * Load player first join times
     */
    CompletableFuture<Map<UUID, Long>> loadFirstJoinTimes();
    
    /**
     * Save player first join time
     */
    CompletableFuture<Void> saveFirstJoinTime(UUID playerId, long timestamp);
    
    /**
     * Load player IP addresses
     */
    CompletableFuture<Map<UUID, String>> loadPlayerIPs();
    
    /**
     * Save player IP address
     */
    CompletableFuture<Void> savePlayerIP(UUID playerId, String ipAddress);
    
    /**
     * Get the database type
     */
    String getDatabaseType();
}