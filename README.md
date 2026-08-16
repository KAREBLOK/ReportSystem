# ReportSystem

[Türkçe Dökümantasyon için tıklayın](README_tr.md)

ReportSystem is a next-generation, comprehensive player reporting and moderation system for Minecraft networks. Moving away from traditional text-based reports, it introduces visual replay recordings, an interactive GUI, and a community-driven Overwatch review system.

Built for scale, it fully supports standalone servers as well as BungeeCord and Velocity networks.

## Core Features

* **Visual Replay System:** Automatically records the reported player's movements, combat, and block interactions. Staff can visually review the exact moment of the report.
* **Overwatch (Community Review):** A CS:GO-inspired system allowing trusted community members to review reports and cast verdicts.
* **Anti-Cheat Integration:** Native hooks for Polar, Vulcan, and GrimAC. Automatically generates reports and recordings for flagged players.
* **Cross-Server Compatibility:** Global synchronization of reports, replays, and punishments across BungeeCord and Velocity environments.
* **Interactive Menus:** Complete GUI-driven management for handling reports, reviewing replays, and issuing punishments.
* **Trust Factor:** Players earn or lose a trust score based on the accuracy of their reports.
* **Discord Webhooks:** Real-time logging for new reports and staff actions.
* **Database Support:** Seamless integration with SQLite (local) and MySQL (network).

## Installation

1. Download the latest compiled `.jar` file from the **Releases** page.
2. Place the file inside your server's `plugins/` directory. 
   > **Note:** If using a proxy network, place the plugin in both the backend servers (Spigot/Paper) and the proxy server (BungeeCord/Velocity).
3. Start the server to generate configuration files.
4. Configure `config.yml` according to your needs (e.g., configuring MySQL credentials for cross-server support).
5. Restart the server.

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/report <player> <reason>` | `reportsystem.report` | Opens the reporting interface. |
| `/reports` | `reportsystem.admin` | Opens the staff report management panel. |
| `/overwatch` | `reportsystem.overwatch` | Opens the community review interface. |

## Requirements
* Java 17 or higher
* Bukkit/Spigot/Paper 1.16+
* PacketEvents (Required dependency for the Replay system)

## Links & Support

For further documentation, community support, and discussions, visit [kareblok.tc](https://kareblok.tc).

---
Developed by KAREBLOK. Licensed under the MIT License.
