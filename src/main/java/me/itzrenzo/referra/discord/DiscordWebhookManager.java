package me.itzrenzo.referra.discord;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhookManager {
    private final JavaPlugin plugin;

    private boolean enabled;
    private String webhookUrl;
    private String serverInvite;
    private String username;
    private String avatarUrl;
    private int embedColor;
    private boolean notifyThresholdReached;
    private boolean notifyReferralConfirmed;

    public DiscordWebhookManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    public void loadConfiguration() {
        enabled = plugin.getConfig().getBoolean("discord.enabled", true);
        webhookUrl = plugin.getConfig().getString("discord.webhook-url", "");
        serverInvite = plugin.getConfig().getString("discord.server-invite", "");
        username = plugin.getConfig().getString("discord.webhook.username", "Referral System");
        avatarUrl = plugin.getConfig().getString("discord.webhook.avatar-url", "");
        embedColor = plugin.getConfig().getInt("discord.webhook.embed-color", 5814783);
        notifyThresholdReached = plugin.getConfig().getBoolean("discord.notifications.threshold-reached", true);
        notifyReferralConfirmed = plugin.getConfig().getBoolean("discord.notifications.referral-confirmed", false);

        if (enabled && (webhookUrl.isEmpty() || webhookUrl.contains("YOUR_WEBHOOK"))) {
            plugin.getLogger().warning("Discord webhook is enabled but no valid webhook URL is configured!");
            enabled = false;
        }
    }

    public void sendThresholdReachedNotification(String playerName, int referralCount, int threshold) {
        if (!enabled || !notifyThresholdReached) {
            return;
        }

        DiscordEmbed embed = new DiscordEmbed()
                .setTitle("Player Reached Reward Threshold")
                .setDescription(String.format("**%s** has reached the reward threshold with **%d referrals**!\n\nThreshold: %d referrals",
                        playerName, referralCount, threshold))
                .setColor(embedColor)
                .setTimestamp(Instant.now().toString())
                .addField("Player", playerName, true)
                .addField("Referrals", String.valueOf(referralCount), true)
                .addField("Status", "Eligible for Reward", true);

        sendWebhook(embed);
    }

    public void sendReferralConfirmedNotification(String referredPlayer, String referrerPlayer, int newTotal) {
        if (!enabled || !notifyReferralConfirmed) {
            return;
        }

        DiscordEmbed embed = new DiscordEmbed()
                .setTitle("Referral Confirmed")
                .setDescription(String.format("**%s** referred by **%s** has been confirmed!\n\n%s now has **%d** confirmed referrals.",
                        referredPlayer, referrerPlayer, referrerPlayer, newTotal))
                .setColor(0x0099FF)
                .setTimestamp(Instant.now().toString())
                .addField("Referred Player", referredPlayer, true)
                .addField("Referrer", referrerPlayer, true)
                .addField("Total Referrals", String.valueOf(newTotal), true);

        sendWebhook(embed);
    }

    private void sendWebhook(DiscordEmbed embed) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = buildWebhookPayload(embed).getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != 200 && responseCode != 204) {
                    plugin.getLogger().warning("Discord webhook failed with response code: " + responseCode);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }

    private String buildWebhookPayload(DiscordEmbed embed) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        if (!username.isEmpty()) {
            json.append("\"username\":\"").append(escapeJson(username)).append("\",");
        }

        if (!avatarUrl.isEmpty()) {
            json.append("\"avatar_url\":\"").append(escapeJson(avatarUrl)).append("\",");
        }

        json.append("\"embeds\":[");
        json.append(embed.toJson());
        json.append("]}");
        return json.toString();
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public String getServerInvite() {
        return serverInvite;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private static class DiscordEmbed {
        private String title;
        private String description;
        private int color;
        private String timestamp;
        private final StringBuilder fields = new StringBuilder();

        public DiscordEmbed setTitle(String title) {
            this.title = title;
            return this;
        }

        public DiscordEmbed setDescription(String description) {
            this.description = description;
            return this;
        }

        public DiscordEmbed setColor(int color) {
            this.color = color;
            return this;
        }

        public DiscordEmbed setTimestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public DiscordEmbed addField(String name, String value, boolean inline) {
            if (fields.length() > 0) {
                fields.append(",");
            }

            fields.append("{\"name\":\"").append(escapeJson(name))
                    .append("\",\"value\":\"").append(escapeJson(value))
                    .append("\",\"inline\":").append(inline).append("}");
            return this;
        }

        public String toJson() {
            StringBuilder json = new StringBuilder("{");

            if (title != null) {
                json.append("\"title\":\"").append(escapeJson(title)).append("\",");
            }

            if (description != null) {
                json.append("\"description\":\"").append(escapeJson(description)).append("\",");
            }

            json.append("\"color\":").append(color).append(",");

            if (timestamp != null) {
                json.append("\"timestamp\":\"").append(timestamp).append("\",");
            }

            if (fields.length() > 0) {
                json.append("\"fields\":[").append(fields).append("],");
            }

            if (json.charAt(json.length() - 1) == ',') {
                json.setLength(json.length() - 1);
            }

            json.append("}");
            return json.toString();
        }

        private String escapeJson(String text) {
            return text.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}