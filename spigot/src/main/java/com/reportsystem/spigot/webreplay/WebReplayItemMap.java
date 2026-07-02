package com.reportsystem.spigot.webreplay;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft 1.21.1 item ID mapping.
 * Material adi -> protokol item registry ID.
 * entity_equipment paketinde kullanilir.
 * Kaynak: PrismarineJS/minecraft-data/data/pc/1.21.1/items.json
 */
public final class WebReplayItemMap {

    private static final Map<String, Integer> ITEM_IDS = new HashMap<>();

    static {
        // Kiliclar
        put("WOODEN_SWORD", 818);
        put("STONE_SWORD", 823);
        put("GOLDEN_SWORD", 828);
        put("IRON_SWORD", 833);
        put("DIAMOND_SWORD", 838);
        put("NETHERITE_SWORD", 843);

        // Kazma
        put("WOODEN_PICKAXE", 820);
        put("STONE_PICKAXE", 825);
        put("GOLDEN_PICKAXE", 830);
        put("IRON_PICKAXE", 835);
        put("DIAMOND_PICKAXE", 840);
        put("NETHERITE_PICKAXE", 845);

        // Balta
        put("WOODEN_AXE", 821);
        put("STONE_AXE", 826);
        put("GOLDEN_AXE", 831);
        put("IRON_AXE", 836);
        put("DIAMOND_AXE", 841);
        put("NETHERITE_AXE", 846);

        // Kurek
        put("WOODEN_SHOVEL", 819);
        put("STONE_SHOVEL", 824);
        put("GOLDEN_SHOVEL", 829);
        put("IRON_SHOVEL", 834);
        put("DIAMOND_SHOVEL", 839);
        put("NETHERITE_SHOVEL", 844);

        // Capa
        put("WOODEN_HOE", 822);
        put("STONE_HOE", 827);
        put("GOLDEN_HOE", 832);
        put("IRON_HOE", 837);
        put("DIAMOND_HOE", 842);
        put("NETHERITE_HOE", 847);

        // Zirhlar - Deri
        put("LEATHER_HELMET", 856);
        put("LEATHER_CHESTPLATE", 857);
        put("LEATHER_LEGGINGS", 858);
        put("LEATHER_BOOTS", 859);

        // Zirhlar - Chainmail
        put("CHAINMAIL_HELMET", 860);
        put("CHAINMAIL_CHESTPLATE", 861);
        put("CHAINMAIL_LEGGINGS", 862);
        put("CHAINMAIL_BOOTS", 863);

        // Zirhlar - Demir
        put("IRON_HELMET", 864);
        put("IRON_CHESTPLATE", 865);
        put("IRON_LEGGINGS", 866);
        put("IRON_BOOTS", 867);

        // Zirhlar - Elmas
        put("DIAMOND_HELMET", 868);
        put("DIAMOND_CHESTPLATE", 869);
        put("DIAMOND_LEGGINGS", 870);
        put("DIAMOND_BOOTS", 871);

        // Zirhlar - Altin
        put("GOLDEN_HELMET", 872);
        put("GOLDEN_CHESTPLATE", 873);
        put("GOLDEN_LEGGINGS", 874);
        put("GOLDEN_BOOTS", 875);

        // Zirhlar - Netherite
        put("NETHERITE_HELMET", 876);
        put("NETHERITE_CHESTPLATE", 877);
        put("NETHERITE_LEGGINGS", 878);
        put("NETHERITE_BOOTS", 879);

        // Yay ve ok
        put("BOW", 801);
        put("ARROW", 802);
        put("SPECTRAL_ARROW", 1159);
        put("TIPPED_ARROW", 1160);
        put("CROSSBOW", 1192);
        put("TRIDENT", 1188);

        // Kalkan
        put("SHIELD", 1162);

        // Yiyecekler
        put("APPLE", 800);
        put("GOLDEN_APPLE", 884);
        put("ENCHANTED_GOLDEN_APPLE", 885);
        put("BREAD", 855);
        put("COOKED_BEEF", 989);
        put("COOKED_PORKCHOP", 882);

        // Iksirler
        put("POTION", 998);
        put("SPLASH_POTION", 1158);
        put("LINGERING_POTION", 1161);

        // Diger
        put("ENDER_PEARL", 993);
        put("FISHING_ROD", 931);
        put("SNOWBALL", 912);
        put("EGG", 927);
        put("COMPASS", 928);
        put("CLOCK", 932);
        put("TOTEM_OF_UNDYING", 1163);
        put("ELYTRA", 773);
        put("FIREWORK_ROCKET", 1112);
        put("SPYGLASS", 933);

        // Blok itemler (sikca elde tutulan)
        put("TORCH", 291);
        put("COBBLESTONE", 35);
        put("DIRT", 28);
        put("OAK_PLANKS", 36);
        put("STONE", 1);
        put("TNT", 679);
        put("OBSIDIAN", 290);
    }

    private static void put(String material, int itemId) {
        ITEM_IDS.put(material, itemId);
    }

    // ==================== NMS REFLECTION (tum itemler icin) ====================

    private static java.lang.reflect.Method nmsGetIdMethod;
    private static Object itemRegistry;
    private static boolean nmsItemInitialized = false;
    private static boolean nmsItemAvailable = false;

    private static synchronized void initNmsItem() {
        if (nmsItemInitialized) return;
        nmsItemInitialized = true;
        try {
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            itemRegistry = registriesClass.getDeclaredField("ITEM").get(null);

            for (java.lang.reflect.Method m : itemRegistry.getClass().getMethods()) {
                if (m.getName().equals("getId") && m.getParameterCount() == 1
                        && m.getReturnType() == int.class) {
                    nmsGetIdMethod = m;
                    break;
                }
            }
            nmsItemAvailable = (nmsGetIdMethod != null);
        } catch (Exception e) {
            nmsItemAvailable = false;
            org.bukkit.Bukkit.getLogger().info("[WebReplay] NMS item registry reflection basarisiz: " + e.getMessage());
        }
    }

    /**
     * Material adini item ID'ye cevirir.
     * NMS reflection ile sunucu versiyonuna uyumlu gercek protokol ID'sini alir.
     * Basarisiz olursa static map'e, o da yoksa -1 doner.
     */
    public static int getItemId(String materialName) {
        if (materialName == null || materialName.isEmpty()
                || materialName.equalsIgnoreCase("AIR")) {
            return -1;
        }
        String name = materialName.replace("minecraft:", "").toUpperCase();

        // NMS ile gercek item ID'sini al
        initNmsItem();
        if (nmsItemAvailable) {
            try {
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name);
                if (mat != null && mat.isItem()) {
                    // CraftItemStack.asNMSCopy ile NMS ItemStack olustur
                    org.bukkit.inventory.ItemStack bukkitStack = new org.bukkit.inventory.ItemStack(mat);
                    Class<?> craftItemStackClass = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
                    java.lang.reflect.Method asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", org.bukkit.inventory.ItemStack.class);
                    Object nmsStack = asNMSCopy.invoke(null, bukkitStack);

                    // nmsStack.getItem() -> NMS Item
                    java.lang.reflect.Method getItem = nmsStack.getClass().getMethod("getItem");
                    Object nmsItem = getItem.invoke(nmsStack);

                    // BuiltInRegistries.ITEM.getId(nmsItem) -> protocol ID
                    int id = (int) nmsGetIdMethod.invoke(itemRegistry, nmsItem);
                    if (id >= 0) return id;
                }
            } catch (Exception ignored) {
                // fallback to static map
            }
        }

        return ITEM_IDS.getOrDefault(name, -1);
    }

    private WebReplayItemMap() {}
}
