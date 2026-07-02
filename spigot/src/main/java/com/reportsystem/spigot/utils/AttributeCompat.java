package com.reportsystem.spigot.utils;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * Attribute API uyumluluk katmani.
 *
 * Minecraft 1.21.3+'ta Attribute sabitleri GENERIC_ onekini kaybetti
 * (GENERIC_MAX_HEALTH -&gt; MAX_HEALTH) ve enum yerine registry-tabanli oldu.
 * Derleme hedefi 1.21 oldugundan sabiti dogrudan kullanmak yeni sunucularda
 * NoSuchFieldError firlatir; bu yuzden alan reflection ile ada gore cozulur
 * (once yeni ad, sonra eski ad). Ikisi de yoksa vanilla 20.0'a duser.
 */
public final class AttributeCompat {

    private static final Attribute MAX_HEALTH = resolve("MAX_HEALTH", "GENERIC_MAX_HEALTH");

    private static Attribute resolve(String... names) {
        for (String name : names) {
            try {
                Object value = Attribute.class.getField(name).get(null);
                if (value instanceof Attribute) {
                    return (Attribute) value;
                }
            } catch (Throwable ignored) {
                // Bu ad bu surumde yok — siradakini dene
            }
        }
        return null;
    }

    /** Oyuncunun maksimum canini dondurur; attribute cozulemezse vanilla 20.0. */
    public static double maxHealth(Player player) {
        if (MAX_HEALTH != null) {
            AttributeInstance instance = player.getAttribute(MAX_HEALTH);
            if (instance != null) {
                return instance.getValue();
            }
        }
        return 20.0;
    }

    private AttributeCompat() {
    }
}
