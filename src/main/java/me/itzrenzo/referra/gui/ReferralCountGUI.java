package me.itzrenzo.referra.gui;

import me.itzrenzo.referra.data.PlayerReferralData;
import me.itzrenzo.referra.data.ReferralDataManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class ReferralCountGUI implements Listener {
    private final ReferralDataManager dataManager;
    private final Map<UUID, Inventory> openGuis = new HashMap<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>(); // Track current page per player
    private final Map<UUID, Player> viewerTargets = new HashMap<>(); // Track who's viewing whose data
    private static final int ITEMS_PER_PAGE = 36; // Slots 9-44 (36 slots for player heads)
    
    public ReferralCountGUI(ReferralDataManager dataManager, JavaPlugin plugin) {
        this.dataManager = dataManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    
    public void openReferralCountGUI(Player viewer, Player targetPlayer) {
        openReferralCountGUI(viewer, targetPlayer, 1);
    }
    
    public void openReferralCountGUI(Player viewer, Player targetPlayer, int page) {
        PlayerReferralData data = dataManager.getPlayerData(targetPlayer.getUniqueId(), targetPlayer.getName());
        
        // Combine all referrals and sort them (confirmed first, then pending)
        List<UUID> allReferrals = new ArrayList<>();
        allReferrals.addAll(data.getConfirmedReferrals());
        allReferrals.addAll(data.getPendingReferrals().keySet());
        
        // Calculate pagination
        int totalItems = allReferrals.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1; // At least 1 page
        
        // Validate page number
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        
        // Store current page and target
        playerPages.put(viewer.getUniqueId(), page);
        viewerTargets.put(viewer.getUniqueId(), targetPlayer);
        
        String title = (targetPlayer.equals(viewer) ? "Your Referrals" : targetPlayer.getName() + "'s Referrals") + 
                      (totalPages > 1 ? " (" + page + "/" + totalPages + ")" : "");
        Inventory gui = Bukkit.createInventory(null, 54, Component.text(title));
        
        // Add stats item in top-left corner
        ItemStack statsItem = createStatsItem(data, targetPlayer);
        gui.setItem(0, statsItem);
        
        // Calculate items for current page
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);
        
        int slot = 9; // Start from second row
        for (int i = startIndex; i < endIndex; i++) {
            UUID referredUuid = allReferrals.get(i);
            boolean isConfirmed = data.getConfirmedReferrals().contains(referredUuid);
            
            OfflinePlayer referredPlayer = Bukkit.getOfflinePlayer(referredUuid);
            ItemStack playerHead = createPlayerHead(referredPlayer, isConfirmed, dataManager);
            gui.setItem(slot, playerHead);
            slot++;
        }
        
        // Add navigation buttons if multiple pages
        if (totalPages > 1) {
            addNavigationButtons(gui, page, totalPages);
        }
        
        // Fill empty slots with decoration
        fillEmptySlots(gui);
        
        openGuis.put(viewer.getUniqueId(), gui);
        viewer.openInventory(gui);
    }
    
    private ItemStack createStatsItem(PlayerReferralData data, Player targetPlayer) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        
        meta.displayName(Component.text("Referral Statistics")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("Player: " + targetPlayer.getName())
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(""));
        lore.add(Component.text("✓ Confirmed Referrals: " + data.getReferralCount())
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        
        if (!dataManager.isPlaytimeRequirementDisabled()) {
            long hours = dataManager.getRequiredPlaytimeHours();
            String timeDesc = hours >= 24 ? (hours / 24) + " day" + (hours / 24 != 1 ? "s" : "") : hours + " hour" + (hours != 1 ? "s" : "");
            lore.add(Component.text("⏳ Pending Referrals: " + data.getPendingCount())
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("   (waiting for " + timeDesc + " playtime)")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        
        lore.add(Component.text(""));
        
        int payoutThreshold = dataManager.getPayoutThreshold();
        if (data.canClaimPayout(payoutThreshold)) {
            lore.add(Component.text("🎉 Eligible for IRL payout!")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            int remaining = payoutThreshold - data.getReferralCount();
            lore.add(Component.text("Need " + remaining + " more for payout")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
    
    private ItemStack createPlayerHead(OfflinePlayer player, boolean isConfirmed, ReferralDataManager dataManager) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        
        meta.setOwningPlayer(player);
        
        String statusColor = isConfirmed ? "§a" : "§e";
        String status = isConfirmed ? "✓ Confirmed" : "⏳ Pending";
        
        meta.displayName(Component.text(statusColor + player.getName())
                .decoration(TextDecoration.ITALIC, false));
        
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(""));
        lore.add(Component.text("Status: " + status)
                .color(isConfirmed ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        
        // Add playtime information
        if (player.isOnline()) {
            Player onlinePlayer = player.getPlayer();
            long playTimeTicks = onlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE);
            double playTimeHours = playTimeTicks / (20.0 * 60 * 60);
            
            lore.add(Component.text(""));
            lore.add(Component.text("Playtime: " + String.format("%.1f", playTimeHours) + " hours")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            
            if (!dataManager.isPlaytimeRequirementDisabled()) {
                long requiredHours = dataManager.getRequiredPlaytimeHours();
                boolean hasEnoughTime = dataManager.hasPlayedRequiredTime(onlinePlayer);
                
                lore.add(Component.text("Required: " + requiredHours + " hours")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                
                if (!isConfirmed) {
                    if (hasEnoughTime) {
                        lore.add(Component.text("✓ Ready for confirmation!")
                                .color(NamedTextColor.GREEN)
                                .decoration(TextDecoration.ITALIC, false));
                    } else {
                        double remaining = requiredHours - playTimeHours;
                        lore.add(Component.text("Need " + String.format("%.1f", remaining) + " more hours")
                                .color(NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }
            }
        } else {
            lore.add(Component.text(""));
            lore.add(Component.text("Player is offline")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }
    
    private void fillEmptySlots(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        
        // Fill bottom row with glass panes
        for (int i = 45; i < 54; i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }
    
    private void addNavigationButtons(Inventory gui, int page, int totalPages) {
        // Previous page button
        if (page > 1) {
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            prevMeta.displayName(Component.text("◀ Previous Page")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> prevLore = new ArrayList<>();
            prevLore.add(Component.text("Go to page " + (page - 1))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            prevMeta.lore(prevLore);
            prevButton.setItemMeta(prevMeta);
            gui.setItem(45, prevButton);
        }
        
        // Page indicator
        ItemStack pageIndicator = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageIndicator.getItemMeta();
        pageMeta.displayName(Component.text("Page " + page + " of " + totalPages)
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> pageLore = new ArrayList<>();
        pageLore.add(Component.text("Showing " + ((page - 1) * ITEMS_PER_PAGE + 1) + 
                    " - " + Math.min(page * ITEMS_PER_PAGE, totalPages * ITEMS_PER_PAGE))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        pageMeta.lore(pageLore);
        pageIndicator.setItemMeta(pageMeta);
        gui.setItem(49, pageIndicator); // Center of bottom row
        
        // Next page button
        if (page < totalPages) {
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            nextMeta.displayName(Component.text("Next Page ▶")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> nextLore = new ArrayList<>();
            nextLore.add(Component.text("Go to page " + (page + 1))
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            nextMeta.lore(nextLore);
            nextButton.setItemMeta(nextMeta);
            gui.setItem(53, nextButton);
        }
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;
        
        // Check if this is one of our GUIs
        Inventory playerGui = openGuis.get(player.getUniqueId());
        if (playerGui == null || !playerGui.equals(clickedInventory)) {
            return;
        }
        
        // Cancel all clicks in our GUI
        event.setCancelled(true);
        
        // Handle navigation button clicks
        int slot = event.getSlot();
        Integer currentPage = playerPages.get(player.getUniqueId());
        Player targetPlayer = viewerTargets.get(player.getUniqueId());
        
        if (currentPage == null || targetPlayer == null) return;
        
        if (slot == 45) { // Previous page button
            if (currentPage > 1) {
                openReferralCountGUI(player, targetPlayer, currentPage - 1);
            }
        } else if (slot == 53) { // Next page button
            // We need to calculate max pages to validate
            PlayerReferralData data = dataManager.getPlayerData(targetPlayer.getUniqueId(), targetPlayer.getName());
            int totalItems = data.getReferralCount() + data.getPendingCount();
            int maxPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
            if (maxPages == 0) maxPages = 1;
            
            if (currentPage < maxPages) {
                openReferralCountGUI(player, targetPlayer, currentPage + 1);
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        
        // Remove all tracking data for this player
        openGuis.remove(player.getUniqueId());
        playerPages.remove(player.getUniqueId());
        viewerTargets.remove(player.getUniqueId());
    }
}