package me.itzrenzo.referra.database.impl;

import me.itzrenzo.referra.data.PlayerReferralData;
import me.itzrenzo.referra.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SqliteDatabaseManager implements DatabaseManager {
    private final JavaPlugin plugin;
    private final String filename;
    private Connection connection;
    
    public SqliteDatabaseManager(JavaPlugin plugin, String filename) {
        this.plugin = plugin;
        this.filename = filename;
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                File dbFile = new File(plugin.getDataFolder(), filename);
                plugin.getDataFolder().mkdirs();
                
                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);
                
                createTables();
                plugin.getLogger().info("SQLite database initialized successfully: " + filename);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to initialize SQLite database: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    private void createTables() throws SQLException {
        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                referral_enabled BOOLEAN DEFAULT TRUE,
                claimed_payout BOOLEAN DEFAULT FALSE,
                first_join_time INTEGER
            )
        """;
        
        String createConfirmedReferralsTable = """
            CREATE TABLE IF NOT EXISTS confirmed_referrals (
                referrer_uuid TEXT,
                referred_uuid TEXT,
                PRIMARY KEY (referrer_uuid, referred_uuid),
                FOREIGN KEY (referrer_uuid) REFERENCES players(uuid)
            )
        """;
        
        String createPendingReferralsTable = """
            CREATE TABLE IF NOT EXISTS pending_referrals (
                referrer_uuid TEXT,
                referred_uuid TEXT,
                timestamp INTEGER,
                PRIMARY KEY (referrer_uuid, referred_uuid),
                FOREIGN KEY (referrer_uuid) REFERENCES players(uuid)
            )
        """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayersTable);
            stmt.execute(createConfirmedReferralsTable);
            stmt.execute(createPendingReferralsTable);
        }
    }
    
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing SQLite connection: " + e.getMessage());
        }
    }
    
    @Override
    public CompletableFuture<Map<UUID, PlayerReferralData>> loadAllPlayerData() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerReferralData> playerData = new HashMap<>();
            
            try {
                // Load basic player data
                String sql = "SELECT uuid, name, referral_enabled, claimed_payout FROM players";
                try (PreparedStatement stmt = connection.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("name");
                        boolean enabled = rs.getBoolean("referral_enabled");
                        boolean claimed = rs.getBoolean("claimed_payout");
                        
                        PlayerReferralData data = new PlayerReferralData(uuid, name);
                        data.setReferralEnabled(enabled);
                        data.setClaimedPayout(claimed);
                        
                        playerData.put(uuid, data);
                    }
                }
                
                // Load confirmed referrals
                sql = "SELECT referrer_uuid, referred_uuid FROM confirmed_referrals";
                try (PreparedStatement stmt = connection.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID referrerUuid = UUID.fromString(rs.getString("referrer_uuid"));
                        UUID referredUuid = UUID.fromString(rs.getString("referred_uuid"));
                        
                        PlayerReferralData data = playerData.get(referrerUuid);
                        if (data != null) {
                            data.addReferral(referredUuid);
                        }
                    }
                }
                
                // Load pending referrals
                sql = "SELECT referrer_uuid, referred_uuid, timestamp FROM pending_referrals";
                try (PreparedStatement stmt = connection.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID referrerUuid = UUID.fromString(rs.getString("referrer_uuid"));
                        UUID referredUuid = UUID.fromString(rs.getString("referred_uuid"));
                        long timestamp = rs.getLong("timestamp");
                        
                        PlayerReferralData data = playerData.get(referrerUuid);
                        if (data != null) {
                            data.addPendingReferral(referredUuid, timestamp);
                        }
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading player data from SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
            
            return playerData;
        });
    }
    
    @Override
    public CompletableFuture<Void> savePlayerData(PlayerReferralData data) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                
                // Save basic player data
                String sql = "INSERT OR REPLACE INTO players (uuid, name, referral_enabled, claimed_payout) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, data.getPlayerId().toString());
                    stmt.setString(2, data.getPlayerName());
                    stmt.setBoolean(3, data.isReferralEnabled());
                    stmt.setBoolean(4, data.hasClaimedPayout());
                    stmt.executeUpdate();
                }
                
                // Clear existing referrals
                sql = "DELETE FROM confirmed_referrals WHERE referrer_uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, data.getPlayerId().toString());
                    stmt.executeUpdate();
                }
                
                sql = "DELETE FROM pending_referrals WHERE referrer_uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, data.getPlayerId().toString());
                    stmt.executeUpdate();
                }
                
                // Save confirmed referrals
                if (!data.getConfirmedReferrals().isEmpty()) {
                    sql = "INSERT INTO confirmed_referrals (referrer_uuid, referred_uuid) VALUES (?, ?)";
                    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                        for (UUID referredUuid : data.getConfirmedReferrals()) {
                            stmt.setString(1, data.getPlayerId().toString());
                            stmt.setString(2, referredUuid.toString());
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }
                }
                
                // Save pending referrals
                if (!data.getPendingReferrals().isEmpty()) {
                    sql = "INSERT INTO pending_referrals (referrer_uuid, referred_uuid, timestamp) VALUES (?, ?, ?)";
                    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                        for (Map.Entry<UUID, Long> entry : data.getPendingReferrals().entrySet()) {
                            stmt.setString(1, data.getPlayerId().toString());
                            stmt.setString(2, entry.getKey().toString());
                            stmt.setLong(3, entry.getValue());
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }
                }
                
                connection.commit();
                connection.setAutoCommit(true);
                
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Error rolling back transaction: " + rollbackEx.getMessage());
                }
                plugin.getLogger().severe("Error saving player data to SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> saveAllPlayerData(Map<UUID, PlayerReferralData> playerData) {
        return CompletableFuture.runAsync(() -> {
            try {
                connection.setAutoCommit(false);
                
                for (PlayerReferralData data : playerData.values()) {
                    savePlayerDataSync(data);
                }
                
                connection.commit();
                connection.setAutoCommit(true);
                
            } catch (SQLException e) {
                try {
                    connection.rollback();
                    connection.setAutoCommit(true);
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().severe("Error rolling back bulk save transaction: " + rollbackEx.getMessage());
                }
                plugin.getLogger().severe("Error saving all player data to SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    private void savePlayerDataSync(PlayerReferralData data) throws SQLException {
        // This is a synchronous version of savePlayerData for use in transactions
        String sql = "INSERT OR REPLACE INTO players (uuid, name, referral_enabled, claimed_payout) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, data.getPlayerId().toString());
            stmt.setString(2, data.getPlayerName());
            stmt.setBoolean(3, data.isReferralEnabled());
            stmt.setBoolean(4, data.hasClaimedPayout());
            stmt.executeUpdate();
        }
    }
    
    @Override
    public CompletableFuture<Map<UUID, UUID>> loadReferralMappings() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, UUID> mappings = new HashMap<>();
            
            try {
                // Load confirmed referrals
                String sql = "SELECT referrer_uuid, referred_uuid FROM confirmed_referrals";
                try (PreparedStatement stmt = connection.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID referrerUuid = UUID.fromString(rs.getString("referrer_uuid"));
                        UUID referredUuid = UUID.fromString(rs.getString("referred_uuid"));
                        mappings.put(referredUuid, referrerUuid);
                    }
                }
                
                // Load pending referrals
                sql = "SELECT referrer_uuid, referred_uuid FROM pending_referrals";
                try (PreparedStatement stmt = connection.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID referrerUuid = UUID.fromString(rs.getString("referrer_uuid"));
                        UUID referredUuid = UUID.fromString(rs.getString("referred_uuid"));
                        mappings.put(referredUuid, referrerUuid);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading referral mappings from SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
            
            return mappings;
        });
    }
    
    @Override
    public CompletableFuture<Void> saveReferralMapping(UUID referredPlayer, UUID referrer) {
        // This is handled by savePlayerData in SQLite implementation
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> removeReferralMapping(UUID referredPlayer) {
        // This is handled by savePlayerData in SQLite implementation
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Map<UUID, Long>> loadFirstJoinTimes() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Long> firstJoinTimes = new HashMap<>();
            
            try {
                String sql = "SELECT uuid, first_join_time FROM players WHERE first_join_time IS NOT NULL";
                try (PreparedStatement stmt = connection.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        long timestamp = rs.getLong("first_join_time");
                        firstJoinTimes.put(uuid, timestamp);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading first join times from SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
            
            return firstJoinTimes;
        });
    }
    
    @Override
    public CompletableFuture<Void> saveFirstJoinTime(UUID playerId, long timestamp) {
        return CompletableFuture.runAsync(() -> {
            try {
                String sql = "UPDATE players SET first_join_time = ? WHERE uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setLong(1, timestamp);
                    stmt.setString(2, playerId.toString());
                    
                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected == 0) {
                        // Player doesn't exist, insert them
                        sql = "INSERT INTO players (uuid, name, first_join_time) VALUES (?, 'Unknown', ?)";
                        try (PreparedStatement insertStmt = connection.prepareStatement(sql)) {
                            insertStmt.setString(1, playerId.toString());
                            insertStmt.setLong(2, timestamp);
                            insertStmt.executeUpdate();
                        }
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving first join time to SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public String getDatabaseType() {
        return "SQLITE";
    }
}