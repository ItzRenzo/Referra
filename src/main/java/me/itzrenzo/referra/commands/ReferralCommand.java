package me.itzrenzo.referra.commands;

import me.itzrenzo.referra.data.PlayerReferralData;
import me.itzrenzo.referra.data.ReferralDataManager;
import me.itzrenzo.referra.gui.ReferralCountGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class ReferralCommand implements CommandExecutor, TabCompleter {
    private final ReferralDataManager dataManager;
    private final JavaPlugin plugin;
    private final ReferralCountGUI referralCountGUI;
    private static final int PLAYERS_PER_PAGE = 10;
    
    public ReferralCommand(ReferralDataManager dataManager, JavaPlugin plugin, ReferralCountGUI referralCountGUI) {
        this.dataManager = dataManager;
        this.plugin = plugin;
        this.referralCountGUI = referralCountGUI;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players!").color(NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
                showHelp(player);
                break;
            case "create":
                handleCreate(player);
                break;
            case "referredby":
                handleReferredBy(player, args);
                break;
            case "count":
                handleCount(player, args);
                break;
            case "top":
                handleTop(player, args);
                break;
            case "claim":
                handleClaim(player);
                break;
            case "toggle":
                handleToggle(player, args);
                break;
            case "admin":
                handleAdmin(player, args);
                break;
            default:
                player.sendMessage(Component.text("Unknown command! Use /referral help for available commands.").color(NamedTextColor.RED));
                break;
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        int payoutThreshold = dataManager.getPayoutThreshold();
        player.sendMessage(Component.text("=== Referral System Help ===").color(NamedTextColor.GOLD));
        player.sendMessage(Component.text("/referral create").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Create your referral status").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/referral referredby <player>").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Set who referred you").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/referral count [player]").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Show referral count").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/referral top [page]").color(NamedTextColor.YELLOW)
                .append(Component.text(" - View top referrers").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/referral claim").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Claim IRL payout (" + payoutThreshold + "+ referrals)").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/referral toggle [on|off]").color(NamedTextColor.YELLOW)
                .append(Component.text(" - Toggle referral system").color(NamedTextColor.WHITE)));
        
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
            player.sendMessage(Component.text("Your referral status is already created!").color(NamedTextColor.RED));
            return;
        }
        
        referrerData.setReferralEnabled(true);
        dataManager.saveData();
        
        player.sendMessage(Component.text("Your referral status has been created!").color(NamedTextColor.GREEN));
        player.sendMessage(Component.text("Share your player name with new players to get referrals!").color(NamedTextColor.YELLOW));
    }
    
    private void handleReferredBy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /referral referredby <player>").color(NamedTextColor.RED));
            return;
        }
        
        String referrerName = args[1];
        Player referrer = Bukkit.getPlayer(referrerName);
        
        if (referrer == null) {
            player.sendMessage(Component.text("Player '" + referrerName + "' is not online or doesn't exist!").color(NamedTextColor.RED));
            return;
        }
        
        if (referrer.equals(player)) {
            player.sendMessage(Component.text("You cannot refer yourself!").color(NamedTextColor.RED));
            return;
        }
        
        PlayerReferralData referrerData = dataManager.getPlayerData(referrer.getUniqueId(), referrer.getName());
        
        if (!referrerData.isReferralEnabled()) {
            player.sendMessage(Component.text("Player '" + referrerName + "' does not have referral system enabled!").color(NamedTextColor.RED));
            return;
        }
        
        // Check if player is already referred
        if (dataManager.isPlayerReferred(player.getUniqueId())) {
            player.sendMessage(Component.text("You have already been referred by someone!").color(NamedTextColor.RED));
            return;
        }
        
        // Add the referral
        boolean success = dataManager.addReferral(referrer.getUniqueId(), player.getUniqueId());
        
        if (success) {
            player.sendMessage(Component.text("You have been referred by " + referrer.getName() + "!").color(NamedTextColor.GREEN));
            referrer.sendMessage(Component.text("You have a new referral: " + player.getName()).color(NamedTextColor.GREEN));
            
            // Save data
            dataManager.saveData();
        } else {
            // Check if it was blocked due to IP abuse
            if (dataManager.hasSameIPReferral(referrer.getUniqueId(), player.getUniqueId())) {
                player.sendMessage(Component.text("Referral blocked: Anti-abuse protection detected suspicious activity.").color(NamedTextColor.RED));
                // Notify admins about potential abuse attempt
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("referral.admin")) {
                        admin.sendMessage(Component.text("[REFERRAL] Blocked same-IP referral attempt: " + player.getName() + " → " + referrer.getName()).color(NamedTextColor.YELLOW));
                    }
                }
            } else {
                player.sendMessage(Component.text("Failed to add referral. You may already be referred by someone.").color(NamedTextColor.RED));
            }
        }
    }
    
    private void handleCount(Player player, String[] args) {
        Player targetPlayer = player;
        
        if (args.length > 1) {
            targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer == null) {
                player.sendMessage(Component.text("Player '" + args[1] + "' is not online or doesn't exist!").color(NamedTextColor.RED));
                return;
            }
        }
        
        // Open the GUI instead of showing text
        referralCountGUI.openReferralCountGUI(player, targetPlayer);
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
    
    private void handleClaim(Player player) {
        PlayerReferralData data = dataManager.getPlayerData(player.getUniqueId(), player.getName());
        int payoutThreshold = dataManager.getPayoutThreshold();
        
        if (!data.canClaimPayout(payoutThreshold)) {
            if (data.hasClaimedPayout()) {
                player.sendMessage(Component.text("You have already claimed your IRL payout!").color(NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("You need at least " + payoutThreshold + " referrals to claim IRL payout!").color(NamedTextColor.RED));
                player.sendMessage(Component.text("Current referrals: " + data.getReferralCount()).color(NamedTextColor.YELLOW));
                
                // Show Discord instructions if they're close to the threshold (within 10 referrals)
                if (data.getReferralCount() >= payoutThreshold - 10) {
                    String discordInstructions = plugin.getConfig().getString("messages.discord-instructions", "&eJoin our Discord server and create a ticket to claim your reward: &b{invite}");
                    if (dataManager.getDiscordManager().isEnabled() && !dataManager.getDiscordManager().getServerInvite().isEmpty()) {
                        player.sendMessage(Component.text("Info: " + discordInstructions.replace("&", "§").replace("{invite}", dataManager.getDiscordManager().getServerInvite())).color(NamedTextColor.GRAY));
                    }
                }
            }
            return;
        }
        
        // Get referral count before claiming (for Discord notification)
        int referralCountBeforeClaim = data.getReferralCount();
        
        // Use the new claimPayout method that removes the threshold amount of referrals
        if (dataManager.claimPayout(player.getUniqueId())) {
            // Send Discord notification for payout claimed (using count before removal)
            dataManager.getDiscordManager().sendPayoutClaimedNotification(player.getName(), referralCountBeforeClaim);
            
            int remainingReferrals = data.getReferralCount();
            
            player.sendMessage(Component.text("Congratulations! You've claimed your IRL payout!").color(NamedTextColor.GREEN));
            player.sendMessage(Component.text("" + payoutThreshold + " referrals have been used for this payout.").color(NamedTextColor.YELLOW));
            if (remainingReferrals > 0) {
                player.sendMessage(Component.text("You have " + remainingReferrals + " referrals remaining.").color(NamedTextColor.AQUA));
            }
            player.sendMessage(Component.text("Please contact an administrator to receive your reward.").color(NamedTextColor.YELLOW));
            
            // Show Discord instructions for claiming the reward
            String discordInstructions = plugin.getConfig().getString("messages.discord-instructions", "&eJoin our Discord server and create a ticket to claim your reward: &b{invite}");
            if (dataManager.getDiscordManager().isEnabled() && !dataManager.getDiscordManager().getServerInvite().isEmpty()) {
                player.sendMessage(Component.text(discordInstructions.replace("&", "§").replace("{invite}", dataManager.getDiscordManager().getServerInvite())).color(NamedTextColor.AQUA));
            }
            
            // Notify all online admins
            for (Player admin : Bukkit.getOnlinePlayers()) {
                if (admin.hasPermission("referral.admin")) {
                    admin.sendMessage(Component.text("[REFERRAL] " + player.getName() + " has claimed their IRL payout! (" + referralCountBeforeClaim + " referrals used, " + remainingReferrals + " remaining)").color(NamedTextColor.GOLD));
                }
            }
        } else {
            player.sendMessage(Component.text("Failed to claim payout. Please try again later.").color(NamedTextColor.RED));
        }
    }
    
    private void handleToggle(Player player, String[] args) {
        PlayerReferralData data = dataManager.getPlayerData(player.getUniqueId(), player.getName());
        boolean newState;
        
        if (args.length > 1) {
            String state = args[1].toLowerCase();
            if (state.equals("on") || state.equals("true")) {
                newState = true;
            } else if (state.equals("off") || state.equals("false")) {
                newState = false;
            } else {
                player.sendMessage(Component.text("Usage: /referral toggle [on|off]").color(NamedTextColor.RED));
                return;
            }
        } else {
            newState = !data.isReferralEnabled();
        }
        
        data.setReferralEnabled(newState);
        dataManager.saveData();
        
        if (newState) {
            player.sendMessage(Component.text("Your referral system has been enabled!").color(NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Your referral system has been disabled!").color(NamedTextColor.RED));
        }
    }
    
    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("referral.admin")) {
            player.sendMessage(Component.text("You don't have permission to use admin commands!").color(NamedTextColor.RED));
            return;
        }
        
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /referral admin <stats|reset> <player>").color(NamedTextColor.RED));
            return;
        }
        
        String adminCommand = args[1].toLowerCase();
        
        switch (adminCommand) {
            case "stats":
                handleAdminStats(player, args);
                break;
            case "reset":
                handleAdminReset(player, args);
                break;
            case "reload":
                handleAdminReload(player);
                break;
            default:
                player.sendMessage(Component.text("Unknown admin command! Use: stats, reset, reload").color(NamedTextColor.RED));
                break;
        }
    }
    
    private void handleAdminStats(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /referral admin stats <player>").color(NamedTextColor.RED));
            return;
        }
        
        String targetName = args[2];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        
        if (targetPlayer == null) {
            player.sendMessage(Component.text("Player '" + targetName + "' is not online or doesn't exist!").color(NamedTextColor.RED));
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

        player.sendMessage(Component.text("Claimed Payout: ").color(NamedTextColor.YELLOW)
                .append(Component.text(data.hasClaimedPayout() ? "Yes" : "No")
                        .color(data.hasClaimedPayout() ? NamedTextColor.GREEN : NamedTextColor.RED)));

        player.sendMessage(Component.text("Can Claim Payout: ").color(NamedTextColor.YELLOW)
                .append(Component.text(data.canClaimPayout() ? "Yes" : "No")
                        .color(data.canClaimPayout() ? NamedTextColor.GREEN : NamedTextColor.RED)));
        
        // Show player's playtime status
        if (targetPlayer.isOnline()) {
            boolean hasPlayedEnough = dataManager.hasPlayedFor7Days(targetPlayer);
            long playTimeTicks = targetPlayer.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
            long requiredTicks = dataManager.getRequiredPlaytimeTicks();
            double playTimeHours = playTimeTicks / (20.0 * 60 * 60);
            double requiredHours = requiredTicks / (20.0 * 60 * 60);
            
            player.sendMessage(Component.text("Playtime: ").color(NamedTextColor.YELLOW)
                    .append(Component.text(String.format("%.1f", playTimeHours)).color(NamedTextColor.WHITE))
                    .append(Component.text(" / ").color(NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.1f", requiredHours)).color(NamedTextColor.WHITE))
                    .append(Component.text(" hours").color(NamedTextColor.GRAY)));
            
            player.sendMessage(Component.text("7 Day Requirement: ").color(NamedTextColor.YELLOW)
                    .append(Component.text(hasPlayedEnough ? "Met" : "Not Met")
                            .color(hasPlayedEnough ? NamedTextColor.GREEN : NamedTextColor.RED)));
        }
        
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
        
        String targetName = args[2];
        Player targetPlayer = Bukkit.getPlayer(targetName);
        
        if (targetPlayer == null) {
            player.sendMessage(Component.text("Player '" + targetName + "' is not online or doesn't exist!").color(NamedTextColor.RED));
            return;
        }
        
        dataManager.resetPlayerData(targetPlayer.getUniqueId());
        player.sendMessage(Component.text("Successfully reset referral data for " + targetPlayer.getName() + "!").color(NamedTextColor.GREEN));
        
        if (targetPlayer.isOnline()) {
            targetPlayer.sendMessage(Component.text("Your referral data has been reset by an administrator.").color(NamedTextColor.YELLOW));
        }
    }
    
    private void handleAdminReload(Player player) {
        dataManager.reloadConfiguration();
        player.sendMessage(Component.text("Configuration reloaded successfully!").color(NamedTextColor.GREEN));
        
        // Show current configuration values
        long hours = dataManager.getRequiredPlaytimeHours();
        int threshold = dataManager.getPayoutThreshold();
        int interval = dataManager.getCheckIntervalMinutes();
        String dbType = dataManager.getDatabaseType();
        
        player.sendMessage(Component.text("Current Settings:").color(NamedTextColor.YELLOW));
        player.sendMessage(Component.text("- Database Type: " + dbType).color(NamedTextColor.GRAY));
        if (hours == 0) {
            player.sendMessage(Component.text("- Playtime Requirement: Disabled").color(NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text("- Required Playtime: " + hours + " hours (" + (hours/24.0) + " days)").color(NamedTextColor.GRAY));
        }
        player.sendMessage(Component.text("- Payout Threshold: " + threshold + " referrals").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text("- Check Interval: " + interval + " minutes").color(NamedTextColor.GRAY));
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("help", "create", "count", "top", "claim", "toggle", "referredby");
            if (sender.hasPermission("referral.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.add("admin");
            }
            
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("refer") || args[0].equalsIgnoreCase("count")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("toggle")) {
                completions.addAll(Arrays.asList("on", "off"));
            } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("referral.admin")) {
                completions.addAll(Arrays.asList("stats", "reset", "reload"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("referral.admin")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(player.getName());
                }
            }
        }
        
        return completions;
    }
}