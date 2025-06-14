# 🎯 Referra - Advanced Minecraft Referral System

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-green.svg)](https://www.minecraft.net/)
[![Paper](https://img.shields.io/badge/Paper-API-blue.svg)](https://papermc.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

A comprehensive referral system plugin for Minecraft servers that rewards players for bringing new members to your community. Features anti-abuse protection, configurable database storage, and flexible reward systems.

## ✨ Features

### 🔒 **Anti-Abuse Protection**
- **Playtime Verification**: Referrals only count after the referred player has played for a configurable amount of time (default: 7 days)
- **Pending System**: New referrals start as "pending" until playtime requirement is met
- **Duplicate Prevention**: Players can only be referred once
- **Self-Referral Protection**: Players cannot refer themselves

### 🗄️ **Flexible Database Support**
- **YML**: Simple file-based storage (default, no setup required)
- **SQLite**: Lightweight database file (perfect for medium servers)
- **MySQL**: Enterprise-grade database with connection pooling (ideal for large networks)

### 🎮 **Player Commands**
- `/referral refer <player>` - Refer a new player to the server
- `/referral count [player]` - View referral statistics
- `/referral top [page]` - Browse the referral leaderboard
- `/referral claim` - Claim IRL payout rewards
- `/referral toggle [on|off]` - Enable/disable your referral system
- `/referral help` - Display help information

### 🛠️ **Admin Commands**
- `/referral admin stats <player>` - View detailed player statistics
- `/referral admin reset <player>` - Reset a player's referral data
- `/referral admin reload` - Reload configuration without restart

### ⚙️ **Highly Configurable**
- **Playtime Requirements**: Set custom hours/days required for confirmation
- **Payout Thresholds**: Configure how many referrals needed for rewards
- **Check Intervals**: Adjust how often the system validates referrals
- **Database Settings**: Switch between storage types easily

## 🚀 Installation

1. **Download** the latest `Referra.jar` from the [releases page](../../releases)
2. **Place** the jar file in your server's `plugins` folder
3. **Restart** your server
4. **Configure** the plugin by editing `plugins/Referra/config.yml`
5. **Reload** the configuration with `/referral admin reload`

### 📋 Requirements
- **Minecraft**: 1.21 or higher
- **Server Software**: Paper, Purpur, or other Paper-based servers
- **Java**: 21 or higher
- **Database** (optional): MySQL 8.0+ or SQLite support

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
/referral refer Steve        # Refer player "Steve"
/referral count              # Check your referral stats
/referral top                # View leaderboard
/referral claim              # Claim your 100+ referral reward
/referral toggle off         # Temporarily disable your referrals
```

### For Admins
```
/referral admin stats Steve     # View Steve's detailed stats
/referral admin reset Steve     # Reset Steve's referral data
/referral admin reload          # Reload configuration
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

## 📊 How It Works

1. **Player A refers Player B** using `/referral refer PlayerB`
2. **Referral starts as "pending"** until playtime requirement is met
3. **System tracks Player B's playtime** using Minecraft's built-in statistics
4. **After required hours played**, referral automatically becomes "confirmed"
5. **Player A gets notified** when their referral is confirmed
6. **Confirmed referrals count** toward leaderboards and payout eligibility

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
```

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

- **Anti-Abuse**: Prevents fake account referrals through playtime verification
- **Performance**: Asynchronous database operations for zero server lag
- **Scalability**: Supports from small servers to large networks
- **Reliability**: Transaction-based operations with rollback support

## 🎖️ Credits

**Author**: ItzRenzo  
**Contributors**: [View all contributors](../../contributors)

---

**⭐ If you find this plugin useful, please consider giving it a star!**

## 📞 Support

- **Discord**: Join our [Discord Server](https://discord.gg/your-discord)
- **Issues**: [GitHub Issues](../../issues)
- **Wiki**: [Plugin Documentation](../../wiki)

---

*Made with ❤️ for the Minecraft community*