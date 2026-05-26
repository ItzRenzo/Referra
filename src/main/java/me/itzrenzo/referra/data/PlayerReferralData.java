package me.itzrenzo.referra.data;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

public class PlayerReferralData {
    private final UUID playerId;
    private final String playerName;
    private final Set<UUID> confirmedReferrals;
    private final Map<UUID, Long> pendingReferrals;
    private boolean referralEnabled;
    private boolean claimedReward;
    
    public PlayerReferralData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.confirmedReferrals = new HashSet<>();
        this.pendingReferrals = new HashMap<>();
        this.referralEnabled = false;
        this.claimedReward = false;
    }
    
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public Set<UUID> getReferredPlayers() { return confirmedReferrals; }
    public Set<UUID> getConfirmedReferrals() { return confirmedReferrals; }
    public Map<UUID, Long> getPendingReferrals() { return pendingReferrals; }
    public int getReferralCount() { return confirmedReferrals.size(); }
    public int getPendingCount() { return pendingReferrals.size(); }
    public int getTotalReferralCount() { return confirmedReferrals.size() + pendingReferrals.size(); }
    public boolean isReferralEnabled() { return referralEnabled; }
    public void setReferralEnabled(boolean enabled) { this.referralEnabled = enabled; }
    public boolean hasClaimedReward() { return claimedReward; }
    public void setClaimedReward(boolean claimedReward) { this.claimedReward = claimedReward; }
    
    public boolean addPendingReferral(UUID referredPlayerId, long firstJoinTime) {
        return pendingReferrals.put(referredPlayerId, firstJoinTime) == null;
    }
    
    public boolean confirmReferral(UUID referredPlayerId) {
        if (pendingReferrals.remove(referredPlayerId) != null) {
            return confirmedReferrals.add(referredPlayerId);
        }
        return false;
    }
    
    public boolean addReferral(UUID referredPlayerId) {
        return confirmedReferrals.add(referredPlayerId);
    }
    
    public void removeReferral(UUID referredPlayerId) {
        confirmedReferrals.remove(referredPlayerId);
        pendingReferrals.remove(referredPlayerId);
    }
    
    public boolean canClaimPayout(int payoutThreshold) {
        return !claimedReward && getReferralCount() >= payoutThreshold;
    }
}