package me.itzrenzo.referra.database.impl;

import me.itzrenzo.referra.data.PlayerReferralData;
import me.itzrenzo.referra.database.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SqliteDatabaseManager implements DatabaseManager {
    private final JavaPlugin plugin;
    private final String filename;
    private String jdbcUrl;

    public SqliteDatabaseManager(JavaPlugin plugin, String filename) {
        this.plugin = plugin;
        this.filename = filename;
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                Class.forName("org.sqlite.JDBC");

                File dbFile = new File(plugin.getDataFolder(), filename);
                plugin.getDataFolder().mkdirs();
                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();

                createTables();
                plugin.getLogger().info("SQLite database initialized successfully: " + filename);
            } catch (ClassNotFoundException | SQLException e) {
                plugin.getLogger().severe("Failed to initialize SQLite database: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private void createTables() throws SQLException {
        String createPlayersTable = """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                referral_enabled BOOLEAN DEFAULT FALSE,
                claimed_payout BOOLEAN DEFAULT FALSE,
                first_join_time INTEGER,
                ip_address TEXT
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

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createPlayersTable);
            stmt.execute(createConfirmedReferralsTable);
            stmt.execute(createPendingReferralsTable);
            stmt.execute("ALTER TABLE players ADD COLUMN claimed_payout BOOLEAN DEFAULT FALSE");
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (!message.contains("duplicate column")) {
                throw e;
            }
        }
    }

    @Override
    public void close() {
    }

    @Override
    public CompletableFuture<Map<UUID, PlayerReferralData>> loadAllPlayerData() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, PlayerReferralData> playerData = new HashMap<>();

            try (Connection conn = getConnection()) {
                String sql = "SELECT uuid, name, referral_enabled, claimed_payout FROM players";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String name = rs.getString("name");
                        boolean enabled = rs.getBoolean("referral_enabled");
                        boolean claimedReward = rs.getBoolean("claimed_payout");

                        PlayerReferralData data = new PlayerReferralData(uuid, name);
                        data.setReferralEnabled(enabled);
                        data.setClaimedReward(claimedReward);
                        playerData.put(uuid, data);
                    }
                }

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
                plugin.getLogger().severe("Error loading player data from SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }

            return playerData;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerData(PlayerReferralData data) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            try {
                conn = getConnection();
                conn.setAutoCommit(false);
                savePlayerDataSync(conn, data, true);
                conn.commit();
            } catch (SQLException e) {
                rollbackQuietly(conn, "Error rolling back transaction");
                plugin.getLogger().severe("Error saving player data to SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            } finally {
                resetAutoCommitAndClose(conn);
            }
        });
    }

    @Override
    public CompletableFuture<Void> saveAllPlayerData(Map<UUID, PlayerReferralData> playerData) {
        return CompletableFuture.runAsync(() -> {
            Connection conn = null;
            try {
                conn = getConnection();
                conn.setAutoCommit(false);

                for (PlayerReferralData data : playerData.values()) {
                    savePlayerDataSync(conn, data, false);
                }

                conn.commit();
            } catch (SQLException e) {
                rollbackQuietly(conn, "Error rolling back bulk save transaction");
                plugin.getLogger().severe("Error saving all player data to SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            } finally {
                resetAutoCommitAndClose(conn);
            }
        });
    }

    private void savePlayerDataSync(Connection conn, PlayerReferralData data, boolean rewriteReferrals) throws SQLException {
        String sql = "INSERT INTO players (uuid, name, referral_enabled, claimed_payout) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, referral_enabled = excluded.referral_enabled, claimed_payout = excluded.claimed_payout";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, data.getPlayerId().toString());
            stmt.setString(2, data.getPlayerName());
            stmt.setBoolean(3, data.isReferralEnabled());
            stmt.setBoolean(4, data.hasClaimedReward());
            stmt.executeUpdate();
        }

        if (!rewriteReferrals) {
            clearAndInsertReferrals(conn, data);
            return;
        }

        clearAndInsertReferrals(conn, data);
    }

    private void clearAndInsertReferrals(Connection conn, PlayerReferralData data) throws SQLException {
        String sql = "DELETE FROM confirmed_referrals WHERE referrer_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, data.getPlayerId().toString());
            stmt.executeUpdate();
        }

        sql = "DELETE FROM pending_referrals WHERE referrer_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, data.getPlayerId().toString());
            stmt.executeUpdate();
        }

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
    }

    @Override
    public CompletableFuture<Map<UUID, UUID>> loadReferralMappings() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, UUID> mappings = new HashMap<>();

            try (Connection conn = getConnection()) {
                String sql = "SELECT referrer_uuid, referred_uuid FROM confirmed_referrals";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        UUID referrerUuid = UUID.fromString(rs.getString("referrer_uuid"));
                        UUID referredUuid = UUID.fromString(rs.getString("referred_uuid"));
                        mappings.put(referredUuid, referrerUuid);
                    }
                }

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
                plugin.getLogger().severe("Error loading referral mappings from SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }

            return mappings;
        });
    }

    @Override
    public CompletableFuture<Map<UUID, Long>> loadFirstJoinTimes() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, Long> firstJoinTimes = new HashMap<>();

            try (Connection conn = getConnection()) {
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
                plugin.getLogger().severe("Error loading first join times from SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }

            return firstJoinTimes;
        });
    }

    @Override
    public CompletableFuture<Void> saveFirstJoinTime(UUID playerId, long timestamp) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO players (uuid, name, referral_enabled, claimed_payout, first_join_time) VALUES (?, 'Unknown', FALSE, FALSE, ?) " +
                                 "ON CONFLICT(uuid) DO UPDATE SET first_join_time = excluded.first_join_time")) {
                stmt.setString(1, playerId.toString());
                stmt.setLong(2, timestamp);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving first join time to SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public CompletableFuture<Map<UUID, String>> loadPlayerIPs() {
        return CompletableFuture.supplyAsync(() -> {
            Map<UUID, String> playerIPs = new HashMap<>();

            try (Connection conn = getConnection()) {
                String sql = "SELECT uuid, ip_address FROM players WHERE ip_address IS NOT NULL";
                try (PreparedStatement stmt = conn.prepareStatement(sql);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String ipAddress = rs.getString("ip_address");
                        playerIPs.put(uuid, ipAddress);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error loading player IPs from SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }

            return playerIPs;
        });
    }

    @Override
    public CompletableFuture<Void> savePlayerIP(UUID playerId, String ipAddress) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT INTO players (uuid, name, referral_enabled, claimed_payout, ip_address) VALUES (?, 'Unknown', FALSE, FALSE, ?) " +
                                 "ON CONFLICT(uuid) DO UPDATE SET ip_address = excluded.ip_address")) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, ipAddress);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving player IP to SQLite: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public String getDatabaseType() {
        return "SQLITE";
    }

    private void rollbackQuietly(Connection conn, String messagePrefix) {
        if (conn == null) {
            return;
        }

        try {
            if (!conn.getAutoCommit()) {
                conn.rollback();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe(messagePrefix + ": " + e.getMessage());
        }
    }

    private void resetAutoCommitAndClose(Connection conn) {
        if (conn == null) {
            return;
        }

        try {
            if (!conn.getAutoCommit()) {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error resetting SQLite auto-commit: " + e.getMessage());
        }

        try {
            conn.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing SQLite connection: " + e.getMessage());
        }
    }
}
