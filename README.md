# 🎯 Referra - Advanced Minecraft Referral System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-blue.svg)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A comprehensive referral system plugin for Minecraft servers that rewards players for bringing new members to your community. Features advanced anti-abuse protection, configurable database storage, Discord webhook integration, and flexible reward systems.

## ✨ Features

### 🔒 **Advanced Anti-Abuse Protection**
- **IP Address Tracking**: Automatically records and tracks player IP addresses to prevent multi-account abuse
- **Same-IP Detection**: Blocks referral attempts from players using the same IP address
- **Playtime Verification**: Referrals only count after the referred player has played for a configurable amount of time (default: 7 days)
- **Pending System**: New referrals start as "pending" until playtime requirement is met
- **Duplicate Prevention**: Players can only be referred once
- **Self-Referral Protection**: Players cannot refer themselves
- **Real-Time Admin Alerts**: Admins receive instant notifications about blocked abuse attempts

### 💰 **Smart Payout System**
- **Multiple Payouts**: Players can claim multiple rewards as they continue earning referrals
- **Partial Consumption**: Only the threshold amount of referrals are consumed per payout (e.g., 100 out of 150)
- **Remaining Referrals**: Players keep extra referrals above the threshold for future payouts
- **Automatic Notifications**: Discord alerts when players become eligible and when they claim rewards

### 🗄️ **Flexible Database Support**
- **YML**: Simple file-based storage (default, no setup required)
- **SQLite**: Lightweight database file (perfect for medium servers)
- **MySQL**: Enterprise-grade database with connection pooling (ideal for large networks)
- **Synchronous Loading**: Fixed database loading issues on server restart
- **IP Storage**: All database types support IP address tracking for anti-abuse

### 🔔 **Discord Integration**
- **Automated Notifications**: Webhook alerts for admin team when players reach payout thresholds
- **Rich Embeds**: Professional Discord messages with player info, referral counts, and timestamps
- **Payout Management**: Automatic notifications when players claim IRL rewards
- **Player Guidance**: Automatic Discord server invites and ticket creation instructions
- **Configurable Alerts**: Choose which events trigger Discord notifications
- **Abuse Monitoring**: Notifications when suspicious activity is detected

### 🎮 **Interactive GUI System**
- **Visual Player Heads**: Real player heads showing all referred players with their status
- **Detailed Information**: Hover over heads to see playtime, progress, and requirements
- **Pagination Support**: Navigate through multiple pages for players with many referrals
- **Smart Organization**: Confirmed referrals shown first, then pending referrals

### 🖥️ **Player Commands**
- `/referral create` - Create your referral status (required to receive referrals)
- `/referral referredby <player>` - Set who referred you to the server
- `/referral count [player]` - Open interactive GUI showing all referrals with player heads
- `/referral top [page]` - Browse the referral leaderboard
- `/referral claim` - Claim IRL payout rewards (can be used multiple times)
- `/referral toggle [on|off]` - Enable/disable your referral system
- `/referral help` - Display help information

### 🛠️ **Admin Commands**
- `/referral admin stats <player>` - View detailed player statistics including IP info
- `/referral admin reset <player>` - Reset a player's referral data (preserves referral relationships)
- `/referral admin reload` - Reload configuration without restart

### ⚙️ **Highly Configurable**
- **Playtime Requirements**: Set custom hours/days required for confirmation
- **Payout Thresholds**: Configure how many referrals needed for rewards
- **Check Intervals**: Adjust how often the system validates referrals
- **Database Settings**: Switch between storage types easily
- **Discord Settings**: Customize webhook behavior and notifications

## 🚀 Installation

1. **Download** the latest `Referra.jar` from the [releases page](../../releases)
2. **Place** the jar file in your server's `plugins` folder
3. **Restart** your server
4. **Configure** the plugin by editing `plugins/Referra/config.yml`
5. **Set up Discord webhooks** (optional but recommended)
6. **Update database** (if upgrading from older version - see Database Migration section)
7. **Reload** the configuration with `/referral admin reload`

### 📋 Requirements
- **Minecraft**: 1.21 or higher
- **Server Software**: Paper, Purpur, or other Paper-based servers
- **Java**: 21 or higher
- **Database** (optional): MySQL 8.0+ or SQLite support
- **Discord Server** (optional): For webhook notifications

## 🔄 Database Migration

If you're upgrading from an older version, you may need to add the IP address column:

### MySQL Migration:
```sql
ALTER TABLE players ADD COLUMN ip_address VARCHAR(45) AFTER first_join_time;
CREATE INDEX idx_ip_address ON players(ip_address);
```

### SQLite Migration:
```sql
ALTER TABLE players ADD COLUMN ip_address TEXT;
```

**Note**: The plugin will work without migration, but IP-based anti-abuse protection won't be available until the column is added.

## ⚙️ Configuration

### Basic Configuration (`config.yml`)

```yaml
# Database Configuration
database:
  type: YML  # Options: YML, SQLITE, MYSQL
  
  # MySQL Settings (only if type is MYSQL)
  mysql:
    host: localhost
    port: 3306
    database: referral_system
    username: root
    password: password
    max-pool-size: 10
    connection-timeout: 30000
  
  # SQLite Settings (only if type is SQLITE)
  sqlite:
    filename: referrals.db

# Discord Integration
discord:
  # Enable Discord webhook notifications
  enabled: true
  
  # Discord webhook URL for admin notifications
  # Get this from your Discord server settings > Integrations > Webhooks
  webhook-url: "https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN"
  
  # Discord server invite link (shown to players when they're eligible for payout)
  server-invite: "https://discord.gg/your-server-invite"
  
  # Webhook settings
  webhook:
    # Bot name that appears in Discord
    username: "Referral System"
    # Bot avatar URL (optional)
    avatar-url: "https://i.imgur.com/your-avatar.png"
    # Color for embed messages (in decimal format)
    embed-color: 5814783  # Gold color
  
  # Notification settings
  notifications:
    # Notify when player reaches payout threshold
    threshold-reached: true
    # Notify when player claims payout
    payout-claimed: true
    # Notify when referrals are confirmed
    referral-confirmed: false

# Referral Settings
referral:
  # Required playtime in hours before referral confirmation
  # Set to 0 to disable playtime requirement
  required-playtime-hours: 168  # 7 days
  
  # How often to check for confirmations (in minutes)
  check-interval-minutes: 5
  
  # Minimum referrals required for IRL payout
  payout-threshold: 100
```

### 🔔 Discord Webhook Setup

#### Step 1: Create a Discord Webhook
1. Go to your Discord server
2. Navigate to **Server Settings** → **Integrations** → **Webhooks**
3. Click **New Webhook**
4. Choose the channel for notifications
5. Copy the **Webhook URL**

#### Step 2: Configure the Plugin
```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/1234567890/your-webhook-token-here"
  server-invite: "https://discord.gg/your-server-invite"
```

#### Step 3: Test the Integration
- Have a player reach the payout threshold
- Use `/referral claim` to test payout notifications
- Check your Discord channel for webhook messages

### Database Setup Examples

#### YML (Default - No Setup Required)
```yaml
database:
  type: YML
```

#### SQLite (Recommended for Most Servers)
```yaml
database:
  type: SQLITE
  sqlite:
    filename: referrals.db
```

#### MySQL (Enterprise/Networks)
```yaml
database:
  type: MYSQL
  mysql:
    host: your-database-host.com
    port: 3306
    database: minecraft_referrals
    username: minecraft_user
    password: secure_password123
    max-pool-size: 20
    connection-timeout: 30000
```

## 🎯 Usage Examples

### For Players
```
/referral create                # Initialize your referral status
/referral referredby Steve       # Set Steve as your referrer
/referral count                  # Check your referral stats
/referral top                    # View leaderboard
/referral claim                  # Claim your 100+ referral reward (repeatable)
/referral toggle off             # Temporarily disable your referrals
```

### For Admins
```
/referral admin stats Steve     # View Steve's detailed stats (including IP info)
/referral admin reset Steve     # Reset Steve's referral data
/referral admin reload          # Reload configuration
```

## 🔔 Discord Notifications

The plugin sends rich Discord embeds for important events:

### 🎯 **Player Reaches Threshold**
- Triggered when a player reaches the payout threshold (default: 100 referrals)
- Includes player name, referral count, and eligibility status
- Alerts admins that the player can now claim their reward

### 💰 **Payout Claimed**
- Triggered when a player uses `/referral claim`
- Shows referrals used for payout and remaining count
- Alerts admins to process the IRL reward

### 🚨 **Anti-Abuse Alerts**
- Triggered when same-IP referral attempts are blocked
- Helps admins monitor for suspicious activity
- Includes player names and detection details

### ✅ **Referral Confirmed** (Optional)
- Triggered when pending referrals are confirmed
- Includes referred player, referrer, and new total
- Disabled by default to reduce spam

## 🛡️ **Anti-Abuse System**

### How It Works:
1. **IP Recording**: Every player's IP is automatically recorded on join
2. **Same-IP Detection**: System checks if referrer and referred player share an IP
3. **Automatic Blocking**: Suspicious referrals are blocked instantly
4. **Admin Notifications**: Real-time alerts about blocked attempts
5. **Logging**: All abuse attempts are logged for investigation

### Player Experience:
```
❌ "Referral blocked: Anti-abuse protection detected suspicious activity."
```

### Admin Experience:
```
🚨 "[REFERRAL] Blocked same-IP referral attempt: NewPlayer → ExistingPlayer"
```

## 💰 **Smart Payout System**

### Multiple Payouts:
- Players can claim rewards multiple times
- Only the threshold amount (e.g., 100) is consumed per claim
- Remaining referrals are kept for future payouts

### Example:
```
Player has 250 referrals → Claims payout → 100 consumed, 150 remain
Player gets 50 more referrals (200 total) → Can claim again
```

### Player Feedback:
```
✅ "Congratulations! You've claimed your IRL payout!"
📝 "100 referrals have been used for this payout."
🎯 "You have 150 referrals remaining."
```

## 🔧 Customization Options

### Playtime Requirements
- **1 Day**: `required-playtime-hours: 24`
- **3 Days**: `required-playtime-hours: 72`
- **1 Week**: `required-playtime-hours: 168` (default)
- **2 Weeks**: `required-playtime-hours: 336`
- **Disabled**: `required-playtime-hours: 0`

### Payout Thresholds
- **Small Server**: `payout-threshold: 25`
- **Medium Server**: `payout-threshold: 100` (default)
- **Large Server**: `payout-threshold: 500`

### Discord Notification Colors
- **Gold**: `embed-color: 5814783` (default)
- **Green**: `embed-color: 65280`
- **Blue**: `embed-color: 255`
- **Red**: `embed-color: 16711680`
- **Purple**: `embed-color: 8388736`

## 📊 How It Works

1. **Player A creates referral status** using `/referral create`
2. **Player B joins the server** and uses `/referral referredby PlayerA`
3. **IP Check**: System verifies A and B don't share the same IP address
4. **Referral starts as "pending"** until playtime requirement is met
5. **System tracks Player B's playtime** using Minecraft's built-in statistics
6. **After required hours played**, referral automatically becomes "confirmed"
7. **Player A gets notified** when their referral is confirmed
8. **At 100+ referrals**, Discord webhook alerts admins automatically
9. **Player A uses `/referral claim`** to request payout (can repeat)
10. **Only threshold amount consumed**, remaining referrals kept for future payouts

## 🛡️ Permissions

```yaml
referral.use: true          # Allow basic referral commands (default: true)
referral.admin: false       # Allow admin commands (default: op only)
```

## 🔧 Developer API

The plugin provides a clean API for integration with other plugins:

```java
// Get the data manager
ReferralDataManager dataManager = Referra.getInstance().getDataManager();

// Check if player has referred someone
boolean hasReferred = dataManager.getPlayerData(playerId, playerName).getReferralCount() > 0;

// Get referral count
int referrals = dataManager.getPlayerData(playerId, playerName).getReferralCount();

// Check for IP abuse
boolean sameIP = dataManager.hasSameIPReferral(referrerId, referredId);

// Access Discord webhook manager
DiscordWebhookManager discordManager = dataManager.getDiscordManager();
```

## 🚀 Admin Workflow

### Setting Up Payouts
1. **Configure Discord webhook** in your admin channel
2. **Set payout threshold** in config.yml
3. **Create Discord ticket system** for reward processing
4. **Train staff** on payout verification process
5. **Monitor anti-abuse alerts** for suspicious activity

### Managing Payouts
1. **Player reaches threshold** → Automatic Discord notification
2. **Player claims reward** → Discord notification with consumption details
3. **Admin verifies eligibility** through Discord ticket system
4. **Process IRL reward** (PayPal, gift cards, etc.)
5. **Player can continue earning** for additional payouts

### Monitoring Anti-Abuse
1. **Real-time alerts** about blocked attempts
2. **Admin stats command** shows IP information
3. **Server logs** record all suspicious activity
4. **Manual investigation** of flagged players

## 🔍 Troubleshooting

### Common Issues:

**Referrals not showing after restart:**
- Ensure database is properly configured
- Check for synchronous loading logs on startup
- Verify database connection settings

**IP anti-abuse not working:**
- Run database migration commands
- Check if `ip_address` column exists in database
- Restart server after migration

**Discord notifications not sending:**
- Verify webhook URL is correct
- Check Discord channel permissions
- Test webhook with online tools

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🐛 Bug Reports & Feature Requests

Please use the [GitHub Issues](../../issues) page to:
- Report bugs
- Request new features
- Ask questions
- Get support

## 📈 Statistics

- **Advanced Anti-Abuse**: Prevents multi-account abuse through IP tracking and playtime verification
- **Smart Payouts**: Multiple reward claims with partial referral consumption
- **Performance**: Synchronous database loading ensures data availability on restart
- **Scalability**: Supports from small servers to large networks with enterprise databases
- **Reliability**: Transaction-based operations with rollback support
- **Discord Integration**: Professional webhook notifications for seamless admin workflow

## 🎖️ Credits

**Author**: ItzRenzo  
**Contributors**: [View all contributors](../../contributors)

---

**⭐ If you find this plugin useful, please consider giving it a star!**

## 📞 Support

- **Issues**: [GitHub Issues](../../issues)
- **Wiki**: [Plugin Documentation](../../wiki)