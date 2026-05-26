package me.itzrenzo.referra;

import me.itzrenzo.referra.commands.ReferralCommand;
import me.itzrenzo.referra.data.ReferralDataManager;
import me.itzrenzo.referra.listeners.PlayerEventListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Referra extends JavaPlugin {

    private ReferralDataManager dataManager;
    private PlayerEventListener eventListener;

    @Override
    public void onEnable() {
        dataManager = new ReferralDataManager(this);

        ReferralCommand referralCommand = new ReferralCommand(dataManager, this);
        eventListener = new PlayerEventListener(dataManager, this);

        getCommand("referral").setExecutor(referralCommand);
        getCommand("referral").setTabCompleter(referralCommand);
        getServer().getPluginManager().registerEvents(eventListener, this);

        getLogger().info("Referra plugin has been enabled.");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.close();
        }

        getLogger().info("Referra plugin has been disabled!");
    }
    
    public ReferralDataManager getDataManager() {
        return dataManager;
    }

    public PlayerEventListener getEventListener() {
        return eventListener;
    }
}
