package me.itzrenzo.referra.database.impl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.itzrenzo.referra.data.PlayerReferralData;
import me.itzrenzo.referra.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MysqlDatabaseManager implements DatabaseManager {
    private final JavaPlugin plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int maxPoolSize;
    private final long connectionTimeout;
    
    private HikariDataSource dataSource;
    
    public MysqlDatabaseManager(JavaPlugin plugin, String host, int port, String database, 
                               String username, String password, int maxPoolSize, long connectionTimeout) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.maxPoolSize = maxPoolSize;
        this.connectionTimeout = connectionTimeout;
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true");
                config.setUsername(username);
                config.setPassword(password);
                config.setMaximumPoolSize(maxPoolSize);
                config.setConnectionTimeout(connectionTimeout);
                config.setLeakDetectionThreshold(60000);
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                
                dataSource = new HikariDataSource(config);
                
                createTables();
                plugin.getLogger().info("MySQL database initialized successfully: " + host + ":" + port + "/" + database);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to initialize MySQL database: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    private void createTables() throws SQLException {
        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS players (
                uuid VARCHAR(36) PRIMARY KEY,
                name VARCHAR(16) NOT NULL,
                referral_enabled BOOLEAN DEFAULT TRUE,
                claimed_payout BOOLEAN DEFAULT FALSE,
                first_join_time BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
            )
        """;
        
        String createConfirmedReferralsTable = """
            CREATE TABLE IF NOT EXISTS confirmed_referrals (
                referrer_uuid VARCHAR(36),
                referred_uuid VARCHAR(36),
                confirmed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (referrer_uuid, referred_uuid),
                FOREIGN KEY (referrer_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
                INDEX idx_referrer (referrer_uuid),
                INDEX idx_referred (referred_uuid)
            )
        """;
        
        String createPendingReferralsTable = """
            CREATE TABLE IF NOT EXISTS pending_referrals (
                referrer_uuid VARCHAR(36),
                referred_uuid VARCHAR(36),
                timestamp BIGINT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (referrer_uuid, referred_uuid),
                FOREIGN KEY (referrer_uuid) REFERENCES players(uuid) ON DELETE CASCADE,
                INDEX idx_referrer (referrer_uuid),
                INDEX idx_referred (referred_uuid)
            )
        """;
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayersTable);
            stmt.execute(createConfirmedReferralsTable);
            stmt.execute(createPendingReferralsTable);
        }
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    @Override
    public CompletableFuture<Map<UUID, PlayerReferralData>> loadAllPlayerData() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerReferralData> playerData = new HashMap<>();
            
            try (Connection conn = dataSource.getConnection()) {
                // Load basic player data
                String sql = "SELECT uuid, name, referral_enabled, claimed_payout FROM players";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
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
                try (PreparedStatement stmt = conn.prepareStatement(sql);
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
                try (PreparedStatement stmt = conn.prepareStatement(sql);
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
                plugin.getLogger().severe("Error loading player data from MySQL: " + e.getMessage());
                throw new RuntimeException(e);
            }
            
            return playerData;
        });
    }
    
    @Override
    public CompletableFuture<Void> savePlayerData(PlayerReferralData data) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                
                // Save basic player data
                String sql = "INSERT INTO players (uuid, name, referral_enabled, claimed_payout) VALUES (?, ?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE name = VALUES(name), referral_enabled = VALUES(referral_enabled), " +
                           "claimed_payout = VALUES(claimed_payout)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, data.getPlayerId().toString());
                    stmt.setString(2, data.getPlayerName());
                    stmt.setBoolean(3, data.isReferralEnabled());
                    stmt.setBoolean(4, data.hasClaimedPayout());
                    stmt.executeUpdate();
                }
                
                // Clear existing referrals
                sql = "DELETE FROM confirmed_referrals WHERE referrer_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, data.getPlayerId().toString());
                    stmt.executeUpdate();
                }
                
                sql = "DELETE FROM pending_referrals WHERE referrer_uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, data.getPlayerId().toString());
                    stmt.executeUpdate();
                }
                
                // Save confirmed referrals
                if (!data.getConfirmedReferrals().isEmpty()) {
                    sql = "INSERT INTO confirmed_referrals (referrer_uuid, referred_uuid) VALUES (?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        for (Map.Entry<UUID, Long> entry : data.getPendingReferrals().entrySet()) {
                            stmt.setString(1, data.getPlayerId().toString());
                            stmt.setString(2, entry.getKey().toString());
                            stmt.setLong(3, entry.getValue());
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }
                }
                
                conn.commit();
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving player data to MySQL: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> saveAllPlayerData(Map<UUID, PlayerReferralData> playerData) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                
                for (PlayerReferralData data : playerData.values()) {
                    savePlayerDataSync(conn, data);
                }
                
                conn.commit();
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving all player data to MySQL: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    private void savePlayerDataSync(Connection conn, PlayerReferralData data) throws SQLException {
        String sql = "INSERT INTO players (uuid, name, referral_enabled, claimed_payout) VALUES (?, ?, ?, ?) " +
                   "ON DUPLICATE KEY UPDATE name = VALUES(name), referral_enabled = VALUES(referral_enabled), " +
                   "claimed_payout = VALUES(claimed_payout)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
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
            
            try (Connection conn = dataSource.getConnection()) {
                // Load confirmed referrals
                String sql = "SELECT referrer_uuid, referred_uuid FROM confirmed_referrals";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID referrerUuid = UUID.fromString(rs.getString("referrer_uuid"));
                        UUID referredUuid = UUID.fromString(rs.getString("referred_uuid"));
                        mappings.put(referredUuid, referrerUuid);
                    }
                }
                
                // Load pending referrals
                sql = "SELECT referrer_uuid, referred_uuid FROM pending_referrals";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID referrerUuid = UUID.fromString(rs.getString("referrer_uuid"));
                        UUID referredUuid = UUID.fromString(rs.getString("referred_uuid"));
                        mappings.put(referredUuid, referrerUuid);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading referral mappings from MySQL: " + e.getMessage());
                throw new RuntimeException(e);
            }
            
            return mappings;
        });
    }
    
    @Override
    public CompletableFuture<Void> saveReferralMapping(UUID referredPlayer, UUID referrer) {
        // This is handled by savePlayerData in MySQL implementation
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Void> removeReferralMapping(UUID referredPlayer) {
        // This is handled by savePlayerData in MySQL implementation
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<Map<UUID, Long>> loadFirstJoinTimes() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Long> firstJoinTimes = new HashMap<>();
            
            try (Connection conn = dataSource.getConnection()) {
                String sql = "SELECT uuid, first_join_time FROM players WHERE first_join_time IS NOT NULL";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {
                    
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        long timestamp = rs.getLong("first_join_time");
                        firstJoinTimes.put(uuid, timestamp);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading first join times from MySQL: " + e.getMessage());
                throw new RuntimeException(e);
            }
            
            return firstJoinTimes;
        });
    }
    
    @Override
    public CompletableFuture<Void> saveFirstJoinTime(UUID playerId, long timestamp) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = "UPDATE players SET first_join_time = ? WHERE uuid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setLong(1, timestamp);
                    stmt.setString(2, playerId.toString());
                    
                    int rowsAffected = stmt.executeUpdate();
                    if (rowsAffected == 0) {
                        // Player doesn't exist, insert them
                        sql = "INSERT INTO players (uuid, name, first_join_time) VALUES (?, 'Unknown', ?)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(sql)) {
                            insertStmt.setString(1, playerId.toString());
                            insertStmt.setLong(2, timestamp);
                            insertStmt.executeUpdate();
                        }
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving first join time to MySQL: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    @Override
    public String getDatabaseType() {
        return "MYSQL";
    }
}