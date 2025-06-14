package me.itzrenzo.referra;

import me.itzrenzo.referra.commands.ReferralCommand;
import me.itzrenzo.referra.data.ReferralDataManager;
import me.itzrenzo.referra.gui.ReferralCountGUI;
import me.itzrenzo.referra.listeners.PlayerEventListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Referra extends JavaPlugin {

    private ReferralDataManager dataManager;
    private ReferralCommand referralCommand;
    private PlayerEventListener eventListener;
    private ReferralCountGUI referralCountGUI;

    @Override
    public void onEnable() {
        // Initialize data manager
        dataManager = new ReferralDataManager(this);
        
        // Initialize GUI system
        referralCountGUI = new ReferralCountGUI(dataManager, this);
        
        // Initialize command handler
        referralCommand = new ReferralCommand(dataManager, this, referralCountGUI);
        
        // Initialize event listener
        eventListener = new PlayerEventListener(dataManager, this);
        
        // Register commands
        getCommand("referral").setExecutor(referralCommand);
        getCommand("referral").setTabCompleter(referralCommand);
        getCommand("referra").setExecutor(referralCommand);
        getCommand("referra").setTabCompleter(referralCommand);
        
        // Register event listener
        getServer().getPluginManager().registerEvents(eventListener, this);
        
        getLogger().info("Referra plugin has been enabled with 7-day playtime requirement!");
    }

    @Override
    public void onDisable() {
        // Close database connection properly
        if (dataManager != null) {
            dataManager.close();
        }
        
        getLogger().info("Referra plugin has been disabled!");
    }
    
    public ReferralDataManager getDataManager() {
        return dataManager;
    }
    
    public ReferralCountGUI getReferralCountGUI() {
        return referralCountGUI;
    }
}
