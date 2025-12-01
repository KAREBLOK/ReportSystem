// common/src/main/java/com/reportsystem/common/punishment/PunishmentProvider.java
package com.reportsystem.common.punishment;

public interface PunishmentProvider {
    boolean ban(String playerName, String reason, String punisher, long durationMillis);
    boolean tempBan(String playerName, String reason, String punisher, long durationMillis);
    boolean permBan(String playerName, String reason, String punisher);

    boolean mute(String playerName, String reason, String punisher, long durationMillis);
    boolean kick(String playerName, String reason, String punisher);
    boolean warn(String playerName, String reason, String punisher);

    boolean unban(String playerName);
    boolean unmute(String playerName);

    boolean isBanned(String playerName);
    boolean isMuted(String playerName);
}