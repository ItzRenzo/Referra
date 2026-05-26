# Referra

Referra is a Paper plugin for handling player referrals on a Minecraft server.

In its current setup:
- A player must use `/referral create` before they can receive referrals.
- A player needs enough playtime before they can use `/referral create`.
- Each player can refer only one other player.
- The referred player can receive an immediate reward.
- The referrer can claim their own reward later with `/referral claim` once the referral is confirmed.
- SQLite is the default storage backend.

## Requirements

- Java 21
- Paper 1.21 or newer

## Installation

1. Build or download `Referra.jar`.
2. Place it in your server's `plugins` folder.
3. Start the server once to generate `plugins/Referra/config.yml`.
4. Adjust the configuration to fit your server.
5. Run `/referral admin reload` after making config changes.

## Commands

### Player commands

- `/referral create`
  Enables your referral status after you meet the required playtime.
- `/referral <player>`
  Marks `<player>` as the person who referred you.
- `/referral claim`
  Claims the referrer's reward once it becomes available.
- `/referral top [page]`
  Shows the referral leaderboard.
- `/referral help`
  Shows command help.

### Admin commands

- `/referral admin stats <player>`
  Shows referral-related information for a player.
- `/referral admin reset <player>`
  Clears a player's referral progress and reward state.
- `/referral admin reload`
  Reloads the plugin configuration.

## How it works

1. A player joins and plays until they meet the create requirement.
2. They run `/referral create`.
3. A new player joins and runs `/referral <referrer-name>`.
4. The referred player gets the configured reward immediately, if one is configured.
5. The referral stays pending until the referred player meets the confirmation playtime requirement.
6. Once confirmed, the referrer is notified that their reward is ready.
7. The referrer runs `/referral claim` to receive it.

## Current referral rules

- A player can only refer one person.
- A player can only be referred once.
- Self-referrals are blocked.
- Same-IP referrals are blocked.
- If the referrer has not enabled referrals, the referral will be rejected.

## Configuration

The plugin reads from `plugins/Referra/config.yml`.

### Database

SQLite is the default:

```yaml
database:
  type: SQLITE
  sqlite:
    filename: referrals.db
```

MySQL is also supported:

```yaml
database:
  type: MYSQL
  mysql:
    host: localhost
    port: 3306
    database: referral_system
    username: root
    password: password
    max-pool-size: 10
    connection-timeout: 30000
```

### Referral settings

```yaml
referral:
  required-playtime-hours: 2
  create-required-playtime-hours: 2
  check-interval-minutes: 5
  max-referrals-per-player: 1
  payout-threshold: 1
```

Meaning:

- `required-playtime-hours`
  How long the referred player must play before the referral is confirmed.
- `create-required-playtime-hours`
  How long a player must play before they can use `/referral create`.
- `check-interval-minutes`
  How often the plugin checks pending referrals.
- `max-referrals-per-player`
  How many players one person is allowed to refer.
- `payout-threshold`
  How many confirmed referrals are required before the referrer can claim their reward.

With the current defaults, both the create requirement and the confirmation requirement are set to 2 hours, and each player gets one referral slot.

### Reward commands

Rewards are configured as console commands.

Available placeholders:

- `{player}`: the player receiving the reward
- `{referrer}`: the player who referred them

Example:

```yaml
rewards:
  referrer:
    commands:
      - "shardmanager add {player} 100"
  referred:
    commands:
      - "shardmanager add {player} 50"
```

`referred.commands` are executed when the referral is accepted.

`referrer.commands` are executed when the referrer runs `/referral claim`.

### Messages

You can adjust the built-in messages in `config.yml`:

```yaml
messages:
  referral-confirmed: "&aReferral confirmed for {player}! Total referrals: {count}"
  payout-eligible: "&a&lCongratulations! &r&aYour referral has been confirmed."
  reward-ready: "&aYour referrer reward is ready. Run &e/referral claim &ato receive it."
  discord-instructions: "&eJoin our Discord server and create a ticket to claim your reward: &b{invite}"
```

## Discord webhook support

The plugin can send webhook messages for reward threshold events and referral confirmations.

Basic setup:

```yaml
discord:
  enabled: true
  webhook-url: "https://discord.com/api/webhooks/YOUR_WEBHOOK_ID/YOUR_WEBHOOK_TOKEN"
  server-invite: "https://discord.gg/your-server-invite"
  notifications:
    threshold-reached: true
    referral-confirmed: false
```

If webhook support is enabled and a server invite is configured, the plugin can also include Discord instructions in player-facing messages.

## Notes for existing installs

- SQLite is now the default storage backend.
- The old YML storage path is no longer used.
- If you changed reward or referral logic from earlier versions, review `config.yml` after updating.

## License

This project is released under the MIT License. See [LICENSE](LICENSE).
