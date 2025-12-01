package com.reportsystem.spigot.utils;

import org.bukkit.entity.Player;

public class SkinUtils {

    public static String[] getSkinData(Player player) {
        // Paper API'de direkt skin bilgisine erişim yok
        // Şimdilik boş dönelim, ileride PlayerProfile API kullanılabilir
        return new String[] { "", "" };
    }
}