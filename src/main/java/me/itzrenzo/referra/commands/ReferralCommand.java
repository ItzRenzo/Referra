package me.itzrenzo.referra.commands;

import me.itzrenzo.referra.Referra;
import me.itzrenzo.referra.data.PlayerReferralData;
import me.itzrenzo.referra.data.ReferralDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class ReferralCommand implements CommandExecutor, TabCompleter {
    private static final int PLAYERS_PER_PAGE = 10;
    private static final Set<String> SUB_COMMANDS = Set.of("help", "create", "claim", "top", "admin");

    private final ReferralDataManager dataManager;
    private final JavaPlugin plugin;

    public ReferralCommand(ReferralDataManager dataManager, JavaPlugin plugin) {
        this.dataManager = dataManager;
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        String firstArg = args[0].toLowerCase(Locale.ROOT);
        switch (firstArg) {
            case "help" -> showHelp(player);
            case "create" -> handleCreate(player);
            case "claim" -> handleClaim(player);
            case "top" -> handleTop(player, args);
            case "admin" -> handleAdmin(player, args);
            default -> handleReferralTarget(player, args[0], args.length);
        }

        return true;
    }

    private void showHelp(Player player) {
        int payoutThreshold = dataManager.getPayoutThreshold();

        player.sendMessage(Component.text("=== Referral System Help ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/referral <referrer-ign>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Set who referred you").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/referral create").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Create your referral status").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/referral claim").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Claim your referrer reward").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/referral top [page]").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View top referrers").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Max referrals per player: " + dataManager.getMaxReferralsPerPlayer()).color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("Create requirement: " + dataManager.getCreateRequiredPlaytimeHours() + " hours played").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("Referral reward threshold: " + payoutThreshold).color(NamedTextColor.GRAY));

        if (player.hasPermission("referral.admin")) {
            player.sendMessage(Component.text("Admin Commands:").color(NamedTextColor.RED));
            player.sendMessage(Component.text("/referral admin stats <player>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - View player stats").color(NamedTextColor.WHITE)));
            player.sendMessage(Component.text("/referral admin reset <player>").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Reset player data").color(NamedTextColor.WHITE)));
            player.sendMessage(Component.text("/referral admin reload").color(NamedTextColor.YELLOW)
                    .append(Component.text(" - Reload configuration").color(NamedTextColor.WHITE)));
        }
    }

    private void handleCreate(Player player) {
        PlayerReferralData referrerData = dataManager.getPlayerData(player.getUniqueId(), player.getName());
        if (referrerData.isReferralEnabled()) {
            player.sendMessage(Component.text("Your referral status is already active!").color(NamedTextColor.RED));
            return;
        }

        if (!dataManager.hasPlayedRequiredTimeForCreate(player)) {
            double playedHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20.0 * 60 * 60);
            double requiredHours = dataManager.getCreateRequiredPlaytimeHours();
            double remainingHours = Math.max(0.0, requiredHours - playedHours);

            player.sendMessage(Component.text("You need at least " + formatHours(requiredHours) + " hours of playtime before using /referral create.")
                    .color(NamedTextColor.RED));
            player.sendMessage(Component.text("Time remaining: " + formatHours(remainingHours) + " hours.").color(NamedTextColor.YELLOW));
            return;
        }

        referrerData.setReferralEnabled(true);
        dataManager.saveData();

        player.sendMessage(Component.text("Your referral status is now active!").color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Share your IGN with new players so they can use /referral " + player.getName()).color(NamedTextColor.YELLOW));
    }

    private void handleReferralTarget(Player player, String referrerName, int argCount) {
        if (argCount > 1) {
            player.sendMessage(Component.text("Usage: /referral <referrer-ign>").color(NamedTextColor.RED));
            return;
        }

        Player referrer = Bukkit.getPlayerExact(referrerName);
        if (referrer == null) {
            player.sendMessage(Component.text("Player '" + referrerName + "' is not online or couldn't be found.").color(NamedTextColor.RED));
            return;
        }

        if (referrer.equals(player)) {
            player.sendMessage(Component.text("You cannot refer yourself!").color(NamedTextColor.RED));
            return;
        }

        PlayerReferralData referrerData = dataManager.getPlayerData(referrer.getUniqueId(), referrer.getName());
        if (!referrerData.isReferralEnabled()) {
            player.sendMessage(Component.text("Player '" + referrerName + "' does not have referrals enabled.").color(NamedTextColor.RED));
            return;
        }

        if (dataManager.hasReachedReferralLimit(referrer.getUniqueId())) {
            player.sendMessage(Component.text("Player '" + referrerName + "' has already used their referral slot.").color(NamedTextColor.RED));
            return;
        }

        if (dataManager.isPlayerReferred(player.getUniqueId())) {
            player.sendMessage(Component.text("You have already been referred by someone!").color(NamedTextColor.RED));
            return;
        }

        boolean success = dataManager.addReferral(referrer.getUniqueId(), player.getUniqueId());
        if (success) {
            player.sendMessage(Component.text("You have been referred by " + referrer.getName() + "!").color(NamedTextColor.GREEN));
            if (dataManager.grantReferredReward(player, referrer)) {
                player.sendMessage(Component.text("Your referral reward has been delivered.").color(NamedTextColor.YELLOW));
            }
            referrer.sendMessage(Component.text("You have a new referral: " + player.getName()).color(NamedTextColor.GREEN));
            return;
        }

        if (dataManager.hasSameIPReferral(referrer.getUniqueId(), player.getUniqueId())) {
            player.sendMessage(Component.text("Referral blocked: anti-abuse protection detected suspicious activity.").color(NamedTextColor.RED));
            notifyAdminsOfBlockedReferral(player, referrer);
            return;
        }

        player.sendMessage(Component.text("Failed to add referral. You may already be referred by someone.").color(NamedTextColor.RED));
    }

    private void handleClaim(Player player) {
        if (!dataManager.hasPendingReward(player.getUniqueId())) {
            player.sendMessage(Component.text("You do not have a reward ready to claim right now.").color(NamedTextColor.RED));
            return;
        }

        if (dataManager.claimReferrerReward(player)) {
            player.sendMessage(Component.text("Your referral reward has been claimed successfully!").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Your reward could not be claimed right now. Please contact staff.").color(NamedTextColor.RED));
        }
    }

    private void notifyAdminsOfBlockedReferral(Player player, Player referrer) {
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("referral.admin")) {
                admin.sendMessage(Component.text("[REFERRAL] Blocked same-IP referral attempt: " + player.getName() + " -> " + referrer.getName())
                        .color(NamedTextColor.YELLOW));
            }
        }
    }

    private void handleTop(Player player, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Invalid page number!").color(NamedTextColor.RED));
                return;
            }
        }

        List<PlayerReferralData> topReferrers = dataManager.getTopReferrers(100);
        if (topReferrers.isEmpty()) {
            player.sendMessage(Component.text("No confirmed referrals have been recorded yet.").color(NamedTextColor.YELLOW));
            return;
        }

        int totalPages = (int) Math.ceil((double) topReferrers.size() / PLAYERS_PER_PAGE);
        if (page < 1 || page > totalPages) {
            player.sendMessage(Component.text("Invalid page number! Valid pages: 1-" + totalPages).color(NamedTextColor.RED));
            return;
        }

        int startIndex = (page - 1) * PLAYERS_PER_PAGE;
        int endIndex = Math.min(startIndex + PLAYERS_PER_PAGE, topReferrers.size());

        player.sendMessage(Component.text("=== Top Referrers (Page " + page + "/" + totalPages + ") ===").color(NamedTextColor.GOLD));
        for (int i = startIndex; i < endIndex; i++) {
            PlayerReferralData data = topReferrers.get(i);
            int rank = i + 1;
            player.sendMessage(Component.text("#" + rank + ". ").color(NamedTextColor.YELLOW)
                    .append(Component.text(data.getPlayerName()).color(NamedTextColor.WHITE))
                    .append(Component.text(" - ").color(NamedTextColor.GRAY))
                    .append(Component.text(data.getReferralCount() + " referrals").color(NamedTextColor.GREEN)));
        }
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("referral.admin")) {
            player.sendMessage(Component.text("You don't have permission to use admin commands!").color(NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /referral admin <stats|reset|reload> [player]").color(NamedTextColor.RED));
            return;
        }

        String adminCommand = args[1].toLowerCase(Locale.ROOT);
        switch (adminCommand) {
            case "stats" -> handleAdminStats(player, args);
            case "reset" -> handleAdminReset(player, args);
            case "reload" -> handleAdminReload(player);
            default -> player.sendMessage(Component.text("Unknown admin command! Use: stats, reset, reload").color(NamedTextColor.RED));
        }
    }

    private void handleAdminStats(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /referral admin stats <player>").color(NamedTextColor.RED));
            return;
        }

        Player targetPlayer = Bukkit.getPlayerExact(args[2]);
        if (targetPlayer == null) {
            player.sendMessage(Component.text("Player '" + args[2] + "' is not online or couldn't be found.").color(NamedTextColor.RED));
            return;
        }

        PlayerReferralData data = dataManager.getPlayerData(targetPlayer.getUniqueId(), targetPlayer.getName());
        player.sendMessage(Component.text("=== Referral Stats for " + targetPlayer.getName() + " ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("Confirmed Referrals: ").color(NamedTextColor.YELLOW)
                .append(Component.text(data.getReferralCount()).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Pending Referrals: ").color(NamedTextColor.YELLOW)
                .append(Component.text(data.getPendingCount()).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("Referral Enabled: ").color(NamedTextColor.YELLOW)
                .append(Component.text(data.isReferralEnabled() ? "Yes" : "No")
                        .color(data.isReferralEnabled() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        player.sendMessage(Component.text("Reward Eligible: ").color(NamedTextColor.YELLOW)
                .append(Component.text(data.canClaimPayout(dataManager.getPayoutThreshold()) ? "Yes" : "No")
                        .color(data.canClaimPayout(dataManager.getPayoutThreshold()) ? NamedTextColor.GREEN : NamedTextColor.RED)));
        player.sendMessage(Component.text("Reward Claimed: ").color(NamedTextColor.YELLOW)
                .append(Component.text(data.hasClaimedReward() ? "Yes" : "No")
                        .color(data.hasClaimedReward() ? NamedTextColor.GREEN : NamedTextColor.RED)));

        long playTimeTicks = targetPlayer.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long requiredTicks = dataManager.getRequiredPlaytimeTicks();
        double playTimeHours = playTimeTicks / (20.0 * 60 * 60);
        double requiredHours = requiredTicks / (20.0 * 60 * 60);
        boolean hasPlayedEnough = dataManager.hasPlayedRequiredTime(targetPlayer);

        player.sendMessage(Component.text("Playtime: ").color(NamedTextColor.YELLOW)
                .append(Component.text(String.format(Locale.US, "%.1f", playTimeHours)).color(NamedTextColor.WHITE))
                .append(Component.text(" / ").color(NamedTextColor.GRAY))
                .append(Component.text(String.format(Locale.US, "%.1f", requiredHours)).color(NamedTextColor.WHITE))
                .append(Component.text(" hours").color(NamedTextColor.GRAY)));
        player.sendMessage(Component.text("Playtime Requirement: ").color(NamedTextColor.YELLOW)
                .append(Component.text(hasPlayedEnough ? "Met" : "Not Met")
                        .color(hasPlayedEnough ? NamedTextColor.GREEN : NamedTextColor.RED)));

        if (dataManager.isPlayerReferred(targetPlayer.getUniqueId())) {
            UUID referrerId = dataManager.getReferrer(targetPlayer.getUniqueId());
            Player referrer = plugin.getServer().getPlayer(referrerId);
            String referrerName = referrer != null ? referrer.getName() : "Unknown";
            player.sendMessage(Component.text("Referred by: ").color(NamedTextColor.YELLOW)
                    .append(Component.text(referrerName).color(NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("Referred by: ").color(NamedTextColor.YELLOW)
                    .append(Component.text("No one").color(NamedTextColor.GRAY)));
        }
    }

    private void handleAdminReset(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /referral admin reset <player>").color(NamedTextColor.RED));
            return;
        }

        Player targetPlayer = Bukkit.getPlayerExact(args[2]);
        if (targetPlayer == null) {
            player.sendMessage(Component.text("Player '" + args[2] + "' is not online or couldn't be found.").color(NamedTextColor.RED));
            return;
        }

        dataManager.resetPlayerData(targetPlayer.getUniqueId());
        player.sendMessage(Component.text("Successfully reset referral data for " + targetPlayer.getName() + "!").color(NamedTextColor.GREEN));
        targetPlayer.sendMessage(Component.text("Your referral data has been reset by an administrator.").color(NamedTextColor.YELLOW));
    }

    private void handleAdminReload(Player player) {
        dataManager.reloadConfiguration();
        if (plugin instanceof Referra referra) {
            referra.getEventListener().reloadCheckTask();
        }

        player.sendMessage(Component.text("Configuration reloaded successfully!").color(NamedTextColor.GREEN));
        long hours = dataManager.getRequiredPlaytimeHours();
        long createHours = dataManager.getCreateRequiredPlaytimeHours();
        int maxReferrals = dataManager.getMaxReferralsPerPlayer();
        int threshold = dataManager.getPayoutThreshold();
        int interval = dataManager.getCheckIntervalMinutes();

        player.sendMessage(Component.text("Current Settings:").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("- Database Type: " + dataManager.getDatabaseType()).color(NamedTextColor.GRAY));
        player.sendMessage(Component.text(hours == 0
                        ? "- Playtime Requirement: Disabled"
                        : "- Required Playtime: " + hours + " hours (" + (hours / 24.0) + " days)")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text(createHours == 0
                        ? "- Create Requirement: Disabled"
                        : "- Create Requirement: " + createHours + " hours")
                .color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("- Max Referrals Per Player: " + maxReferrals).color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("- Reward Threshold: " + threshold + " referrals").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("- Check Interval: " + interval + " minutes").color(NamedTextColor.GRAY));
    }

    private String formatHours(double hours) {
        return String.format(Locale.US, "%.1f", hours);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            for (String subCommand : SUB_COMMANDS) {
                if (subCommand.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    if (!subCommand.equals("admin") || sender.hasPermission("referral.admin")) {
                        completions.add(subCommand);
                    }
                }
            }

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add(onlinePlayer.getName());
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("referral.admin")) {
                for (String option : List.of("stats", "reset", "reload")) {
                    if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        completions.add(option);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("referral.admin")) {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getName().toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    completions.add(onlinePlayer.getName());
                }
            }
        }

        return completions;
    }
}
