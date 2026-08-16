# 🛡️ ReportSystem - Advanced Player Reporting & Replay System

🇹🇷 [Türkçe sürüm için tıklayın (Turkish)](README_tr.md)

ReportSystem is a next-generation, feature-rich reporting and player moderation system for Minecraft servers. It completely replaces the traditional text-based report plugins by offering a fully interactive GUI, **visual replay recordings**, and an Overwatch (CS:GO-style) community review system!

It is highly optimized and supports Spigot, BungeeCord, and Velocity networks.

## ✨ Features

- 🎥 **Replay System:** Automatically records the reported player (movements, combat, blocks, etc.) allowing staff to watch exactly what happened.
- ⚖️ **Overwatch System:** Let your trusted players review reports and vote on verdicts (Guilty/Innocent), just like CS:GO!
- 🤖 **Anti-Cheat Integrations:** Automatically records and reports players flagged by **Polar**, **Vulcan**, or **GrimAC**.
- 🌐 **Cross-Server Support:** Full support for BungeeCord and Velocity networks. Reports and punishments sync globally!
- 🖥️ **Interactive GUIs:** Manage reports, watch replays, and punish players easily through customizable menus.
- 📊 **Trust Level System:** Players gain or lose trust points based on their report accuracy and behavior.
- 💬 **Discord Webhooks:** Sends detailed report and punishment logs straight to your Discord server.
- 💾 **Database Support:** Seamlessly works with SQLite (Local) or MySQL (Network).

## 🚀 Installation

1. Download the latest `.jar` from the Releases section (or build it yourself).
2. Place the file into your server's `plugins/` folder.
   - *If using a network, place it in the Spigot servers AND the BungeeCord/Velocity `plugins/` folder.*
3. Restart your server to generate the configuration files.
4. Edit the `config.yml` to your liking (Setup MySQL if using a proxy).
5. Restart the server once more. You are ready to go!

## ⚙️ Commands & Permissions

- `/report <player> <reason>` - Opens the report GUI (`reportsystem.report`)
- `/reports` - Opens the staff report management GUI (`reportsystem.admin`)
- `/overwatch` - Opens the Overwatch menu (`reportsystem.overwatch`)

## 🌐 Links
- **Website & Support:** [kareblok.tc](https://kareblok.tc)

---
*Developed by KAREBLOK. Licensed under the MIT License.*
