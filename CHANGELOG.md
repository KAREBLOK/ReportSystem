# Changelog

All notable changes to ReportSystem will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2025-01-XX (Initial Release)

### Added
- **Core Report System**
  - GUI-based player reporting with predefined categories
  - Report list view with pagination
  - Report detail view with full information
  - Report management (accept/reject/delete)
  - Staff notification system (chat, actionbar, title, sound)
  - Rate limiting and cooldown system
  - Auto-close old reports after configurable days

- **Overwatch System** (CS:GO-inspired)
  - Community-driven report review system
  - Three verdict options: Guilty, Innocent, Skip
  - XP and ranking system (Bronze → Silver → Gold → Diamond)
  - Leaderboards for top reviewers
  - Consensus-based auto-punishment
  - Configurable reward commands for reviewers
  - Interactive NPC system for easy access
  - Review statistics tracking

- **Replay System**
  - Automatic recording when player is reported
  - Configurable recording duration (default 45 seconds)
  - Full playback with NPC entities
  - Nearby player tracking during recording
  - Playback controls (play, pause, speed, teleport)
  - Cross-server replay support (BungeeCord)
  - Auto-delete old replays after configurable days
  - Compressed replay data storage

- **Punishment System**
  - Integrated punishment GUI
  - Ban (temporary and permanent)
  - Mute (temporary)
  - Kick
  - Warn
  - BungeeCord network-wide punishments
  - Configurable punishment durations
  - Punishment reason templates

- **Database Support**
  - SQLite support for single servers
  - MySQL support for BungeeCord networks
  - HikariCP connection pooling
  - Async database operations
  - Automatic table creation
  - Database migration support

- **BungeeCord/Velocity Support**
  - Cross-server reporting
  - Cross-server notifications
  - Cross-server replay viewing
  - Global punishment system
  - Shared MySQL database

- **Multi-Language Support**
  - English language (messages_en.yml)
  - Turkish language (messages_tr.yml)
  - Fully customizable messages
  - Easy to add custom languages

- **Customization**
  - Fully customizable GUIs (colors, items, layouts)
  - Configurable report categories
  - Configurable punishment durations
  - Discord webhook integration
  - PlaceholderAPI support

- **Performance Features**
  - Async database operations
  - In-memory caching system
  - HikariCP connection pooling
  - Optimized replay compression
  - Rate limiting and spam prevention

### Technical Details
- **Minecraft Version**: 1.18 - 1.21
- **Java Version**: 17+
- **Dependencies**: PacketEvents 2.0+ (required)
- **Optional Dependencies**: PlaceholderAPI, Vault
- **Database**: SQLite, MySQL
- **API**: Full developer API for custom integrations

### Configuration
- 300+ configurable options
- 8 customizable GUI files
- 2 language files included
- Full permission system

### Commands
- `/report` - Report a player
- `/reports` - View reports
- `/reportsystem` - Admin commands
- `/overwatch` - Review reports

### Permissions
- 20+ granular permissions
- Wildcard permission support
- Default permissions for players and staff

---

## [Unreleased]

### Planned Features
- Web panel for remote management
- IP ban support
- Report appeals system
- Statistics dashboard
- PDF export for reports
- Discord bot integration
- More language translations
- Mobile app (future consideration)

---

## Version History

### Version Numbering
- **MAJOR.MINOR.PATCH** (e.g., 1.0.0)
- **MAJOR**: Breaking changes, major feature additions
- **MINOR**: New features, non-breaking changes
- **PATCH**: Bug fixes, minor improvements

### Release Types
- **Stable**: Fully tested, production-ready
- **Beta**: Feature-complete, testing phase
- **Alpha**: Early development, unstable

---

## How to Update

### Backup First!
Always backup your:
1. Database (MySQL dump or SQLite file)
2. Configuration files
3. Custom language files

### Update Steps
1. Stop your server
2. Backup database and configs
3. Replace old JAR with new JAR
4. Start server (new config options will be added automatically)
5. Check console for any errors
6. Run `/rs reload` to ensure everything is loaded

### Breaking Changes
Breaking changes will be marked with **[BREAKING]** in the changelog.
Major version updates (e.g., 1.x.x → 2.0.0) may contain breaking changes.

---

## Support

- **Issues**: [GitHub Issues](https://github.com/yourusername/ReportSystem/issues)
- **Discord**: [Join Discord](https://discord.gg/yourserver)
- **SpigotMC**: [Plugin Page](https://www.spigotmc.org/resources/reportsystem.xxxxx/)

---

## Contributors

- **BaranMRJ** - Lead Developer

Special thanks to all contributors and testers!

---

[1.0.0]: https://github.com/yourusername/ReportSystem/releases/tag/v1.0.0
[Unreleased]: https://github.com/yourusername/ReportSystem/compare/v1.0.0...HEAD
