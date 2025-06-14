package me.itzrenzo.referra.database.impl;

import me.itzrenzo.referra.data.PlayerReferralData;
import me.itzrenzo.referra.database.DatabaseManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class YmlDatabaseManager implements DatabaseManager {
    private final JavaPlugin plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    
    public YmlDatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            dataFile = new File(plugin.getDataFolder(), "referrals.yml");
            if (!dataFile.exists()) {
                plugin.getDataFolder().mkdirs();
                try {
                    dataFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create referrals.yml file: " + e.getMessage());
                }
            }
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            plugin.getLogger().info("YML database initialized successfully");
        });
    }
    
    @Override
    public void close() {
        // YML doesn't need explicit closing
        saveConfigurationSync();
    }
    
    @Override
    public CompletableFuture<Map<UUID, PlayerReferralData>> loadAllPlayerData() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerReferralData> playerData = new HashMap<>();
            
            if (!dataConfig.contains("players")) {
                return playerData;
            }
            
            for (String uuidString : dataConfig.getConfigurationSection("players").getKeys(false)) {
                UUID playerId = UUID.fromString(uuidString);
                String playerName = dataConfig.getString("players." + uuidString + ".name");
                
                PlayerReferralData data = new PlayerReferralData(playerId, playerName);
                data.setReferralEnabled(dataConfig.getBoolean("players." + uuidString + ".enabled", true));
                data.setClaimedPayout(dataConfig.getBoolean("players." + uuidString + ".claimed", false));
                
                // Load confirmed referrals
                List<String> confirmedList = dataConfig.getStringList("players." + uuidString + ".confirmed");
                for (String referredUuid : confirmedList) {
                    UUID referred = UUID.fromString(referredUuid);
                    data.addReferral(referred);
                }
                
                // Load pending referrals with timestamps
                if (dataConfig.contains("players." + uuidString + ".pending")) {
                    for (String pendingUuid : dataConfig.getConfigurationSection("players." + uuidString + ".pending").getKeys(false)) {
                        UUID referred = UUID.fromString(pendingUuid);
                        long timestamp = dataConfig.getLong("players." + uuidString + ".pending." + pendingUuid);
                        data.addPendingReferral(referred, timestamp);
                    }
                }
                
                playerData.put(playerId, data);
            }
            
            return playerData;
        });
    }
    
    @Override
    public CompletableFuture<Void> savePlayerData(PlayerReferralData data) {
        return CompletableFuture.runAsync(() -> {
            String path = "players." + data.getPlayerId().toString();
            dataConfig.set(path + ".name", data.getPlayerName());
            dataConfig.set(path + ".enabled", data.isReferralEnabled());
            dataConfig.set(path + ".claimed", data.hasClaimedPayout());
            
            // Save confirmed referrals
            List<String> confirmedList = data.getConfirmedReferrals().stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
            dataConfig.set(path + ".confirmed", confirmedList);
            
            // Save pending referrals with timestamps
            dataConfig.set(path + ".pending", null); // Clear existing
            for (Map.Entry<UUID, Long> entry : data.getPendingReferrals().entrySet()) {
                dataConfig.set(path + ".pending." + entry.getKey().toString(), entry.getValue());
            }
            
            saveConfigurationSync();
        });
    }
    
    @Override
    public CompletableFuture<Void> saveAllPlayerData(Map<UUID, PlayerReferralData> playerData) {
        return CompletableFuture.runAsync(() -> {
            for (PlayerReferralData data : playerData.values()) {
                String path = "players." + data.getPlayerId().toString();
                dataConfig.set(path + ".name", data.getPlayerName());
                dataConfig.set(path + ".enabled", data.isReferralEnabled());
                dataConfig.set(path + ".claimed", data.hasClaimedPayout());
                
                // Save confirmed referrals
                List<String> confirmedList = data.getConfirmedReferrals().stream()
                        .map(UUID::toString)
                        .collect(Collectors.toList());
                dataConfig.set(path + ".confirmed", confirmedList);
                
                // Save pending referrals with timestamps
                dataConfig.set(path + ".pending", null); // Clear existing
                for (Map.Entry<UUID, Long> entry : data.getPendingReferrals().entrySet()) {
                    dataConfig.set(path + ".pending." + entry.getKey().toString(), entry.getValue());
                }
            }
            saveConfigurationSync();
        });
    }
    
    @Override
    public CompletableFuture<Map<UUID, UUID>> loadReferralMappings() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, UUID> referralMappings = new HashMap<>();
            
            if (!dataConfig.contains("players")) {
                return referralMappings;
            }
            
            for (String uuidString : dataConfig.getConfigurationSection("players").getKeys(false)) {
                UUID referrerId = UUID.fromString(uuidString);
                
                // Load confirmed referrals
                List<String> confirmedList = dataConfig.getStringList("players." + uuidString + ".confirmed");
                for (String referredUuid : confirmedList) {
                    UUID referred = UUID.fromString(referredUuid);
                    referralMappings.put(referred, referrerId);
                }
                
                // Load pending referrals
                if (dataConfig.contains("players." + uuidString + ".pending")) {
                    for (String pendingUuid : dataConfig.getConfigurationSection("players." + uuidString + ".pending").getKeys(false)) {
                        UUID referred = UUID.fromString(pendingUuid);
                        referralMappings.put(referred, referrerId);
                    }
                }
            }
            
            return referralMappings;
        });
    }
    
    @Override
    public CompletableFuture<Void> saveReferralMapping(UUID referredPlayer, UUID referrer) {
        // For YML, this is handled as part of savePlayerData
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> removeReferralMapping(UUID referredPlayer) {
        // For YML, this is handled as part of savePlayerData
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Map<UUID, Long>> loadFirstJoinTimes() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Long> firstJoinTimes = new HashMap<>();
            
            if (dataConfig.contains("firstJoinTimes")) {
                for (String uuidString : dataConfig.getConfigurationSection("firstJoinTimes").getKeys(false)) {
                    UUID playerId = UUID.fromString(uuidString);
                    long timestamp = dataConfig.getLong("firstJoinTimes." + uuidString);
                    firstJoinTimes.put(playerId, timestamp);
                }
            }
            
            return firstJoinTimes;
        });
    }
    
    @Override
    public CompletableFuture<Void> saveFirstJoinTime(UUID playerId, long timestamp) {
        return CompletableFuture.runAsync(() -> {
            dataConfig.set("firstJoinTimes." + playerId.toString(), timestamp);
            saveConfigurationSync();
        });
    }
    
    @Override
    public CompletableFuture<Map<UUID, String>> loadPlayerIPs() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, String> playerIPs = new HashMap<>();
            
            if (dataConfig.contains("playerIPs")) {
                for (String uuidString : dataConfig.getConfigurationSection("playerIPs").getKeys(false)) {
                    UUID playerId = UUID.fromString(uuidString);
                    String ipAddress = dataConfig.getString("playerIPs." + uuidString);
                    playerIPs.put(playerId, ipAddress);
                }
            }
            
            return playerIPs;
        });
    }
    
    @Override
    public CompletableFuture<Void> savePlayerIP(UUID playerId, String ipAddress) {
        return CompletableFuture.runAsync(() -> {
            dataConfig.set("playerIPs." + playerId.toString(), ipAddress);
            saveConfigurationSync();
        });
    }
    
    @Override
    public String getDatabaseType() {
        return "YML";
    }
    
    private void saveConfigurationSync() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save referral data: " + e.getMessage());
        }
    }
}