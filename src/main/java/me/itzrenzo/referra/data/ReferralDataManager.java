package me.itzrenzo.referra.data;

import me.itzrenzo.referra.database.DatabaseManager;
import me.itzrenzo.referra.database.impl.MysqlDatabaseManager;
import me.itzrenzo.referra.database.impl.SqliteDatabaseManager;
import me.itzrenzo.referra.discord.DiscordWebhookManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ReferralDataManager {
    private final JavaPlugin plugin;
    private final Map<UUID, PlayerReferralData> playerData = new HashMap<>();
    private final Map<UUID, UUID> referredBy = new HashMap<>();
    private final Map<UUID, Long> playerFirstJoinTime = new HashMap<>();
    private final Map<UUID, String> playerIPs = new HashMap<>();

    private long requiredPlaytimeHours;
    private long requiredPlaytimeTicks;
    private long createRequiredPlaytimeHours;
    private long createRequiredPlaytimeTicks;
    private int checkIntervalMinutes;
    private int maxReferralsPerPlayer;
    private int payoutThreshold;
    private List<String> referrerRewardCommands;
    private List<String> referredRewardCommands;

    private DatabaseManager databaseManager;
    private final DiscordWebhookManager discordManager;

    public ReferralDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
        this.discordManager = new DiscordWebhookManager(plugin);
        initializeDatabase();
        loadData();
    }

    private void loadConfiguration() {
        plugin.saveDefaultConfig();

        requiredPlaytimeHours = plugin.getConfig().getLong("referral.required-playtime-hours", 168);
        createRequiredPlaytimeHours = Math.max(0, plugin.getConfig().getLong("referral.create-required-playtime-hours", 2));
        checkIntervalMinutes = plugin.getConfig().getInt("referral.check-interval-minutes", 5);
        maxReferralsPerPlayer = Math.max(1, plugin.getConfig().getInt("referral.max-referrals-per-player", 1));
        payoutThreshold = Math.max(1, plugin.getConfig().getInt("referral.payout-threshold", 1));
        if (payoutThreshold > maxReferralsPerPlayer) {
            plugin.getLogger().warning("referral.payout-threshold is higher than referral.max-referrals-per-player. Capping reward threshold to " + maxReferralsPerPlayer + ".");
            payoutThreshold = maxReferralsPerPlayer;
        }

        referrerRewardCommands = List.copyOf(plugin.getConfig().getStringList("rewards.referrer.commands"));
        referredRewardCommands = List.copyOf(plugin.getConfig().getStringList("rewards.referred.commands"));
        requiredPlaytimeTicks = requiredPlaytimeHours * 20L * 60 * 60;
        createRequiredPlaytimeTicks = createRequiredPlaytimeHours * 20L * 60 * 60;

        plugin.getLogger().info("Configuration loaded: Required playtime = " + requiredPlaytimeHours + " hours (" +
                (requiredPlaytimeHours / 24.0) + " days)");
    }

    private void initializeDatabase() {
        String databaseType = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();

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
                plugin.getLogger().warning("Unknown database type '" + databaseType + "'. Falling back to SQLITE.");
                String filename = plugin.getConfig().getString("database.sqlite.filename", "referrals.db");
                databaseManager = new SqliteDatabaseManager(plugin, filename);
            }
        }

        try {
            databaseManager.initialize().get();
            plugin.getLogger().info("Database initialized: " + databaseManager.getDatabaseType());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public void reloadConfiguration() {
        plugin.reloadConfig();
        loadConfiguration();
        discordManager.loadConfiguration();

        String newDatabaseType = plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
        if (!newDatabaseType.equals(databaseManager.getDatabaseType())) {
            plugin.getLogger().info("Database type changed, reinitializing...");
            saveData();
            databaseManager.close();
            initializeDatabase();
            loadData();
        }

        plugin.getLogger().info("Configuration reloaded!");
    }

    public void loadData() {
        try {
            plugin.getLogger().info("Loading referral data from database...");

            playerData.clear();
            playerData.putAll(databaseManager.loadAllPlayerData().get());
            plugin.getLogger().info("Loaded " + playerData.size() + " player records");

            referredBy.clear();
            referredBy.putAll(databaseManager.loadReferralMappings().get());
            plugin.getLogger().info("Loaded " + referredBy.size() + " referral mappings");

            playerFirstJoinTime.clear();
            playerFirstJoinTime.putAll(databaseManager.loadFirstJoinTimes().get());
            plugin.getLogger().info("Loaded " + playerFirstJoinTime.size() + " first join times");

            playerIPs.clear();
            playerIPs.putAll(databaseManager.loadPlayerIPs().get());
            plugin.getLogger().info("Loaded " + playerIPs.size() + " player IP mappings");

            plugin.getLogger().info("Referral data loading completed successfully!");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load referral data from database: " + e.getMessage());
            throw new RuntimeException("Referral data loading failed", e);
        }
    }

    public void saveData() {
        databaseManager.saveAllPlayerData(playerData);
    }

    public PlayerReferralData getPlayerData(UUID playerId, String playerName) {
        return playerData.computeIfAbsent(playerId, ignored -> new PlayerReferralData(playerId, playerName));
    }

    public void recordFirstJoin(UUID playerId) {
        if (playerFirstJoinTime.containsKey(playerId)) {
            return;
        }

        long timestamp = System.currentTimeMillis();
        playerFirstJoinTime.put(playerId, timestamp);
        databaseManager.saveFirstJoinTime(playerId, timestamp);
    }

    public void recordPlayerIP(UUID playerId, String ipAddress) {
        if (ipAddress == null || ipAddress.equals(playerIPs.get(playerId))) {
            return;
        }

        playerIPs.put(playerId, ipAddress);
        databaseManager.savePlayerIP(playerId, ipAddress);
    }

    public boolean hasSameIPReferral(UUID referrerId, UUID referredId) {
        String referrerIP = playerIPs.get(referrerId);
        String referredIP = playerIPs.get(referredId);
        return referrerIP != null && referrerIP.equals(referredIP);
    }

    public boolean hasPlayedRequiredTime(Player player) {
        return isPlaytimeRequirementDisabled() || player.getStatistic(Statistic.PLAY_ONE_MINUTE) >= requiredPlaytimeTicks;
    }

    public boolean hasPlayedRequiredTimeForCreate(Player player) {
        return createRequiredPlaytimeHours == 0 || player.getStatistic(Statistic.PLAY_ONE_MINUTE) >= createRequiredPlaytimeTicks;
    }

    public boolean addReferral(UUID referrerId, UUID referredId) {
        if (referredBy.containsKey(referredId)) {
            return false;
        }

        PlayerReferralData referrerData = playerData.get(referrerId);
        if (referrerData == null || !referrerData.isReferralEnabled()) {
            return false;
        }

        if (referrerData.getTotalReferralCount() >= maxReferralsPerPlayer) {
            return false;
        }

        if (hasSameIPReferral(referrerId, referredId)) {
            plugin.getLogger().warning("Blocked referral attempt: Same IP detected for referrer " + referrerId + " and referred " + referredId);
            return false;
        }

        long currentTime = System.currentTimeMillis();
        referrerData.addPendingReferral(referredId, currentTime);
        referredBy.put(referredId, referrerId);
        recordFirstJoin(referredId);
        databaseManager.savePlayerData(referrerData);
        return true;
    }

    public void checkAndConfirmReferrals(Player player) {
        UUID playerId = player.getUniqueId();
        UUID referrerId = referredBy.get(playerId);
        if (referrerId == null || !hasPlayedRequiredTime(player)) {
            return;
        }

        PlayerReferralData referrerData = playerData.get(referrerId);
        if (referrerData == null || !referrerData.getPendingReferrals().containsKey(playerId)) {
            return;
        }

        if (!referrerData.confirmReferral(playerId)) {
            return;
        }

        databaseManager.savePlayerData(referrerData);

        Player referrer = plugin.getServer().getPlayer(referrerId);
        String referrerName = referrer != null ? referrer.getName() : referrerData.getPlayerName();

        if (referrerData.getReferralCount() == payoutThreshold) {
            discordManager.sendThresholdReachedNotification(referrerName, referrerData.getReferralCount(), payoutThreshold);
            if (referrer != null && referrer.isOnline()) {
                sendConfiguredMessage(referrer, plugin.getConfig().getString("messages.payout-eligible",
                        "&a&lCongratulations! &r&aYour referral has been confirmed."),
                        Map.of("player", referrer.getName(), "count", String.valueOf(referrerData.getReferralCount())));

                if (hasConfiguredReferrerReward()) {
                    sendConfiguredMessage(referrer, plugin.getConfig().getString("messages.reward-ready",
                                    "&aYour referral reward is ready. Run &e/referral claim &ato receive it."),
                            Map.of("player", referrer.getName(), "count", String.valueOf(referrerData.getReferralCount())));
                }

                String discordInstructions = plugin.getConfig().getString("messages.discord-instructions",
                        "&eJoin our Discord server and create a ticket to claim your reward: &b{invite}");
                if (discordManager.isEnabled() && !discordManager.getServerInvite().isEmpty()) {
                    sendConfiguredMessage(referrer, discordInstructions,
                            Map.of("invite", discordManager.getServerInvite(), "player", referrer.getName()));
                }
            }
        }

        discordManager.sendReferralConfirmedNotification(player.getName(), referrerName, referrerData.getReferralCount());

        if (referrer != null && referrer.isOnline()) {
            long hours = getRequiredPlaytimeHours();
            String timeDesc = hours >= 24
                    ? (hours / 24) + " day" + (hours / 24 != 1 ? "s" : "")
                    : hours + " hour" + (hours != 1 ? "s" : "");
            referrer.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                    "&a[REFERRAL] " + player.getName() + " has now played for " + timeDesc + "! Referral confirmed. Total: " + referrerData.getReferralCount()));
        }

        plugin.getLogger().info("Confirmed referral: " + player.getName() + " referred by " + referrerName);
    }

    public List<PlayerReferralData> getTopReferrers(int limit) {
        return playerData.values().stream()
                .filter(data -> data.getReferralCount() > 0)
                .filter(data -> !data.getPlayerName().equalsIgnoreCase("unknown"))
                .sorted((left, right) -> Integer.compare(right.getReferralCount(), left.getReferralCount()))
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
        if (data == null) {
            return;
        }

        data.getConfirmedReferrals().clear();
        data.getPendingReferrals().clear();
        data.setClaimedReward(false);
        databaseManager.savePlayerData(data);
    }

    public boolean hasReachedReferralLimit(UUID playerId) {
        PlayerReferralData data = playerData.get(playerId);
        return data != null && data.getTotalReferralCount() >= maxReferralsPerPlayer;
    }

    public boolean hasPendingReward(UUID playerId) {
        PlayerReferralData data = playerData.get(playerId);
        return data != null && data.canClaimPayout(payoutThreshold);
    }

    public boolean claimReferrerReward(Player player) {
        PlayerReferralData data = playerData.get(player.getUniqueId());
        if (data == null || !data.canClaimPayout(payoutThreshold) || !hasConfiguredReferrerReward()) {
            return false;
        }

        runConfiguredCommands(referrerRewardCommands, player, Map.of("player", player.getName()));
        data.setClaimedReward(true);
        databaseManager.savePlayerData(data);
        return true;
    }

    public boolean grantReferredReward(Player referredPlayer, Player referrer) {
        if (!hasConfiguredReferredReward()) {
            return false;
        }

        runConfiguredCommands(referredRewardCommands, referredPlayer, Map.of(
                "player", referredPlayer.getName(),
                "referrer", referrer.getName()));
        return true;
    }

    public boolean hasConfiguredReferrerReward() {
        return referrerRewardCommands != null && !referrerRewardCommands.isEmpty();
    }

    public boolean hasConfiguredReferredReward() {
        return referredRewardCommands != null && !referredRewardCommands.isEmpty();
    }

    public void sendRewardReminder(Player player) {
        if (!hasPendingReward(player.getUniqueId()) || !hasConfiguredReferrerReward()) {
            return;
        }

        PlayerReferralData data = getPlayerData(player.getUniqueId(), player.getName());
        sendConfiguredMessage(player, plugin.getConfig().getString("messages.reward-ready",
                        "&aYour referral reward is ready. Run &e/referral claim &ato receive it."),
                Map.of("player", player.getName(), "count", String.valueOf(data.getReferralCount())));
    }

    private void runConfiguredCommands(List<String> commands, Player targetPlayer, Map<String, String> replacements) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        ConsoleCommandSender console = Bukkit.getConsoleSender();
        for (String command : commands) {
            String parsedCommand = applyPlaceholders(command, replacements).trim();
            if (parsedCommand.startsWith("/")) {
                parsedCommand = parsedCommand.substring(1);
            }
            if (!parsedCommand.isBlank()) {
                Bukkit.dispatchCommand(console, parsedCommand);
            }
        }
    }

    private void sendConfiguredMessage(Player player, String message, Map<String, String> replacements) {
        if (message == null || message.isBlank()) {
            return;
        }

        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(applyPlaceholders(message, replacements)));
    }

    private String applyPlaceholders(String input, Map<String, String> replacements) {
        String output = input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }

    public long getRequiredPlaytimeTicks() {
        return requiredPlaytimeTicks;
    }

    public long getRequiredPlaytimeHours() {
        return requiredPlaytimeHours;
    }

    public long getCreateRequiredPlaytimeTicks() {
        return createRequiredPlaytimeTicks;
    }

    public long getCreateRequiredPlaytimeHours() {
        return createRequiredPlaytimeHours;
    }

    public int getCheckIntervalMinutes() {
        return checkIntervalMinutes;
    }

    public int getPayoutThreshold() {
        return payoutThreshold;
    }

    public int getMaxReferralsPerPlayer() {
        return maxReferralsPerPlayer;
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