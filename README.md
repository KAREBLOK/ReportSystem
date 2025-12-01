# ReportSystem

<div align="center">

**Modern, feature-rich report system for Minecraft servers**

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.18--1.21-brightgreen.svg)](https://www.spigotmc.org/)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

[Features](#-features) • [Installation](#-installation) • [Commands](#-commands) • [Permissions](#-permissions) • [Configuration](#-configuration) • [Support](#-support)

</div>

---

## 📋 Overview

**ReportSystem** is a comprehensive player report management plugin for Minecraft servers with advanced features including:
- **CS:GO-style Overwatch System** - Community-driven report review system
- **Replay Recording** - Automatic recording and playback of reported incidents
- **Modern GUI Interface** - User-friendly graphical menus
- **BungeeCord Support** - Cross-server reporting for networks
- **Multi-language** - English and Turkish language support
- **MySQL & SQLite** - Flexible database options

Perfect for small servers and large networks alike!

---

## ✨ Features

### 🎯 Core Features
- **Easy Reporting** - GUI-based report creation with predefined categories
- **Report Management** - View, accept, reject, and delete reports
- **Punishment System** - Integrated ban, mute, kick, and warn system
- **Notification System** - Real-time alerts for staff members
- **Rate Limiting** - Prevent report spam with cooldowns and limits
- **Auto-Close** - Automatically close old reports after X days

### 🎮 Overwatch System (Unique!)
Inspired by CS:GO's Overwatch, let trusted players review reports:
- **Community Review** - Trusted players vote on reports (Guilty/Innocent/Skip)
- **XP & Ranking** - Reviewers earn XP and ranks (Bronze → Silver → Gold → Diamond)
- **Consensus System** - Auto-punishment when threshold is reached
- **Leaderboards** - Track top reviewers
- **Reward Commands** - Give rewards for reviewing

### 🎬 Replay System
Automatic recording and playback of reported incidents:
- **Auto-Recording** - Records last 45 seconds when player is reported
- **NPC Playback** - Watch the replay as NPC entities
- **Full Controls** - Play, pause, speed control, teleport to player
- **Nearby Players** - Shows other players in the area during replay
- **Cross-Server** - Works across BungeeCord network

### 🌐 Network Features
- **BungeeCord Support** - Full cross-server compatibility
- **MySQL Database** - Shared database for all servers
- **Cross-Server Notifications** - Staff on any server gets notified
- **Global Punishments** - Ban players network-wide

---

## 📦 Installation

### Requirements
- **Minecraft**: 1.18 - 1.21 (Paper/Spigot/Purpur)
- **Java**: 17 or higher
- **Dependencies**: [PacketEvents 2.0+](https://www.spigotmc.org/resources/packetevents-api.80279/) **(REQUIRED)**
- **Optional**: PlaceholderAPI, Vault (for economy rewards)

### Installation Steps

#### For Single Server (Spigot/Paper):
1. Download **PacketEvents** from [SpigotMC](https://www.spigotmc.org/resources/packetevents-api.80279/)
2. Download **ReportSystem-Spigot-1.0.0.jar**
3. Place both JARs in your `plugins` folder
4. Restart your server
5. Configure `plugins/ReportSystem-Spigot/config.yml`
6. Reload with `/rs reload`

#### For BungeeCord Network:
1. **On BungeeCord:**
   - Place `ReportSystem-Bungee-1.0.0.jar` in `plugins` folder
   - Configure `plugins/ReportSystem-Bungee/config.yml` with MySQL settings

2. **On Each Spigot Server:**
   - Install **PacketEvents** (required)
   - Place `ReportSystem-Spigot-1.0.0.jar` in `plugins` folder
   - Configure `config.yml`:
     ```yaml
     database:
       type: mysql  # Must use MySQL for BungeeCord
       mysql:
         host: "your-host"
         port: 3306
         database: "reportsystem"
         username: "your-username"
         password: "your-password"

     bungeecord:
       enabled: true  # Enable BungeeCord mode
     ```

3. Restart all servers

---

## 🎮 Commands

### Player Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/report <player> [reason]` | Report a player (opens GUI) | `reportsystem.report` |
| `/reports [page]` | View your submitted reports | `reportsystem.view` |

### Staff Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/reports all [page]` | View all reports | `reportsystem.view.all` |
| `/rs reload` | Reload configuration | `reportsystem.admin` |
| `/rs stats` | View database statistics | `reportsystem.admin` |
| `/rs info` | View plugin information | `reportsystem.admin` |
| `/rs debug <true\|false>` | Toggle debug mode | `reportsystem.admin` |

### Overwatch Commands
| Command | Description | Permission |
|---------|-------------|------------|
| `/overwatch` | Start reviewing next report | `reportsystem.overwatch` |
| `/overwatch stats [player]` | View reviewer statistics | `reportsystem.overwatch` |
| `/overwatch leaderboard` | View top reviewers | `reportsystem.overwatch` |
| `/overwatch npc create` | Create Overwatch NPC | `reportsystem.overwatch.admin` |
| `/overwatch npc remove` | Remove nearest NPC | `reportsystem.overwatch.admin` |

**Aliases:**
- `/report` = `/rapor`, `/reportplayer`, `/raporla`
- `/reports` = `/raporlar`, `/reportlist`
- `/reportsystem` = `/rs`, `/rsystem`, `/raporsistem`
- `/overwatch` = `/ow`, `/overwatchsystem`

---

## 🔐 Permissions

### Basic Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `reportsystem.report` | Create reports | `true` (all players) |
| `reportsystem.view` | View own reports | `true` |
| `reportsystem.view.all` | View all reports | `op` |
| `reportsystem.notify` | Receive report notifications | `op` |
| `reportsystem.admin` | Admin commands | `op` |
| `reportsystem.exempt` | Cannot be reported | `op` |
| `reportsystem.cooldown.bypass` | Bypass report cooldown | `op` |

### Management Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `reportsystem.manage` | Accept/reject reports | `op` |
| `reportsystem.punish` | Punish reported players | `op` |
| `reportsystem.delete` | Delete reports | `op` |

### Overwatch Permissions
| Permission | Description | Default |
|------------|-------------|---------|
| `reportsystem.overwatch` | Review reports in Overwatch | `op` |
| `reportsystem.overwatch.admin` | Manage Overwatch NPCs/queue | `op` |

### Wildcard Permission
| Permission | Description | Default |
|------------|-------------|---------|
| `reportsystem.*` | All permissions | `op` |

---

## ⚙️ Configuration

### Basic Setup (`config.yml`)

```yaml
general:
  # Language file (messages_en.yml or messages_tr.yml)
  language: "en"

  # Enable debug logging
  debug: false

database:
  # Database type: 'sqlite' or 'mysql'
  # For BungeeCord networks, use 'mysql'
  type: sqlite

  # MySQL settings (only if type is mysql)
  mysql:
    host: "localhost"
    port: 3306
    database: "reportsystem"
    username: "root"
    password: "CHANGE_THIS_PASSWORD"

bungeecord:
  # Enable BungeeCord support
  enabled: false

reports:
  # Require target player to be online
  require-online: true

  # Cooldown between reports (seconds)
  cooldown: 60

  # Maximum reports per player pair
  max-reports-per-player: 3

  # Auto-close reports after X days (-1 to disable)
  auto-close-days: 30

replay:
  # Enable replay recording
  enabled: true

  # Auto-record on report
  auto-record: true

  # Recording duration (seconds)
  recording-duration: 45

  # Auto-delete replays after X days
  auto-delete-days: 7

overwatch:
  # Enable Overwatch system
  enabled: true

  # Minimum reviewers required for consensus
  min-reviewers: 3

  # Consensus threshold (percentage)
  # If 70% vote guilty, auto-punishment is applied
  consensus-threshold: 70.0

  # Auto-ban duration (days)
  auto-ban-duration-days: 30
```

### Report Categories

Edit in `config.yml`:
```yaml
reports:
  categories:
    - "Cheating/Hacking"
    - "Harassment/Abuse"
    - "Spam"
    - "Griefing"
    - "Bug Abuse"
    - "Other"
```

### Custom Messages

Edit language files:
- `messages_en.yml` - English messages
- `messages_tr.yml` - Turkish messages

You can create custom language files by copying and renaming these files.

---

## 🎨 GUI Customization

All GUIs are fully customizable in `plugins/ReportSystem-Spigot/guis/`:
- `report-create.yml` - Report creation GUI
- `report-list.yml` - Report list GUI
- `report-detail.yml` - Report details GUI
- `punishment-selection.yml` - Punishment selection GUI
- `punishment.yml` - Punishment GUI

Example:
```yaml
title: "&c&lReport Player"
size: 27

items:
  target-player:
    slot: 13
    material: PLAYER_HEAD
    name: "&e%player%"
    lore:
      - "&7Click to report this player"
```

---

## 📊 Overwatch System Guide

### What is Overwatch?
Inspired by CS:GO, the Overwatch system allows trusted players to review reports and vote on verdicts. This reduces admin workload and involves the community.

### How It Works:
1. **Report is Created** → Added to Overwatch queue (optional)
2. **Reviewer Starts Review** → `/overwatch` command or NPC
3. **Watch Replay** → NPC shows reported player's actions
4. **Vote Verdict** → Guilty / Innocent / Skip
5. **Consensus Reached** → Auto-punishment if threshold met (e.g., 70% guilty)
6. **Earn XP & Ranks** → Reviewers level up and earn rewards

### XP & Ranks:
- **Bronze**: 0 XP (starting rank)
- **Silver**: 500 XP
- **Gold**: 1,000 XP
- **Diamond**: 2,500 XP

**XP Rewards:**
- Guilty/Innocent vote: 15 XP
- Skip vote: 5 XP

### Creating Overwatch NPCs:
```
/overwatch npc create
```
Players can click the NPC to start reviewing. The hologram shows:
- Total reviews completed
- Current XP and rank

---

## 🔌 Developer API

### Maven Dependency
```xml
<dependency>
    <groupId>com.reportsystem</groupId>
    <artifactId>common</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Example Usage
```java
// Get ReportSystem instance
ReportSystemSpigot plugin = (ReportSystemSpigot) Bukkit.getPluginManager().getPlugin("ReportSystem-Spigot");

// Create a report programmatically
Report report = new Report();
report.setReporterUuid(player.getUniqueId());
report.setReportedPlayerUuid(target.getUniqueId());
report.setReason("Custom reason");
report.setCategory("Cheating");
report.setStatus(ReportStatus.PENDING);

plugin.getReportService().createReport(report);

// Listen to report events
@EventHandler
public void onReportCreate(ReportCreateEvent event) {
    Report report = event.getReport();
    Player reporter = event.getReporter();
    // Custom logic here
}
```

---

## 🐛 Troubleshooting

### Common Issues

**"PacketEvents not found" error**
- Download PacketEvents 2.0+ from SpigotMC
- Make sure it's in the plugins folder
- Restart server (not just reload)

**Reports not showing in BungeeCord network**
- Ensure ALL servers use the same MySQL database
- Check `bungeecord.enabled: true` in config
- Verify MySQL credentials are correct

**Replay not recording**
- Check `replay.enabled: true` and `replay.auto-record: true`
- Ensure MySQL has enough storage space
- Check console for errors

**GUI not opening**
- Update to latest Paper/Spigot version
- Check for plugin conflicts
- Enable debug mode: `/rs debug true`

---

## 📝 Planned Features

- [ ] Web panel for report management
- [ ] More punishment types (tempban, IP ban)
- [ ] Report appeals system
- [ ] Statistics dashboard
- [ ] Export reports to PDF
- [ ] Discord bot integration
- [ ] More language translations

---

## 🤝 Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/ReportSystem/issues)
- **Discord**: Join our [Discord server](https://discord.gg/yourserver)
- **SpigotMC**: [Plugin page](https://www.spigotmc.org/resources/reportsystem.xxxxx/)

---

## 📜 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Credits

- **Developer**: BaranMRJ
- **Inspired by**: CS:GO Overwatch System
- **Dependencies**: [PacketEvents](https://github.com/retrooper/packetevents)

---

## ⭐ Show Your Support

If you like this plugin, please:
- ⭐ Star this repository
- 📝 Leave a review on SpigotMC
- 🐦 Share with other server owners

---

<div align="center">

**Made with ❤️ for the Minecraft community**

[Back to Top](#reportsystem)

</div>
