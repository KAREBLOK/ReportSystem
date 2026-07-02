package com.reportsystem.spigot.webreplay;

import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft 1.21.1 block state ID mapping.
 * Material adi -> protokol block state ID (defaultState).
 * Kaynak: PrismarineJS/minecraft-data/data/pc/1.21.1/blocks.json
 */
public final class WebReplayBlockStateMap {

    private static final Map<String, Integer> BLOCK_STATES = new HashMap<>();

    static {
        // Temel bloklar
        put("AIR", 0);
        put("STONE", 1);
        put("GRANITE", 2);
        put("POLISHED_GRANITE", 3);
        put("DIORITE", 4);
        put("POLISHED_DIORITE", 5);
        put("ANDESITE", 6);
        put("POLISHED_ANDESITE", 7);
        put("GRASS_BLOCK", 9); // snowy=false (8=snowy=true)
        put("DIRT", 10);
        put("COARSE_DIRT", 11);
        put("PODZOL", 13); // snowy=false
        put("COBBLESTONE", 14);
        put("OAK_PLANKS", 15);
        put("SPRUCE_PLANKS", 16);
        put("BIRCH_PLANKS", 17);
        put("JUNGLE_PLANKS", 18);
        put("ACACIA_PLANKS", 19);
        put("CHERRY_PLANKS", 20);
        put("DARK_OAK_PLANKS", 21);
        put("MANGROVE_PLANKS", 22);
        put("BAMBOO_PLANKS", 23);
        put("BEDROCK", 79);
        put("SAND", 112);
        put("RED_SAND", 117);
        put("GRAVEL", 118);
        put("GOLD_ORE", 123);
        put("DEEPSLATE_GOLD_ORE", 124);
        put("IRON_ORE", 125);
        put("DEEPSLATE_IRON_ORE", 126);
        put("COAL_ORE", 127);
        put("DEEPSLATE_COAL_ORE", 128);

        // Ahsap loglar (axis=y default)
        put("OAK_LOG", 131);
        put("SPRUCE_LOG", 134);
        put("BIRCH_LOG", 137);
        put("JUNGLE_LOG", 140);
        put("ACACIA_LOG", 143);
        put("CHERRY_LOG", 146);
        put("DARK_OAK_LOG", 149);
        put("MANGROVE_LOG", 152);

        // Yapraklar (defaultState)
        put("OAK_LEAVES", 264);
        put("SPRUCE_LEAVES", 292);
        put("BIRCH_LEAVES", 320);
        put("JUNGLE_LEAVES", 348);

        // Diger yaygin bloklar
        put("GLASS", 519);
        put("LAPIS_ORE", 520);
        put("LAPIS_BLOCK", 522);
        put("DISPENSER", 524);
        put("SANDSTONE", 535);
        put("NOTE_BLOCK", 539);
        put("COBWEB", 2004);
        put("TALL_GRASS", 10756);

        // Yun bloklari
        put("WHITE_WOOL", 2047);
        put("ORANGE_WOOL", 2048);
        put("MAGENTA_WOOL", 2049);
        put("LIGHT_BLUE_WOOL", 2050);
        put("YELLOW_WOOL", 2051);
        put("LIME_WOOL", 2052);
        put("PINK_WOOL", 2053);
        put("GRAY_WOOL", 2054);
        put("LIGHT_GRAY_WOOL", 2055);
        put("CYAN_WOOL", 2056);
        put("PURPLE_WOOL", 2057);
        put("BLUE_WOOL", 2058);
        put("BROWN_WOOL", 2059);
        put("GREEN_WOOL", 2060);
        put("RED_WOOL", 2061);
        put("BLACK_WOOL", 2062);

        // Deger bloklar
        put("GOLD_BLOCK", 2091);
        put("IRON_BLOCK", 2092);
        put("BRICKS", 2093);
        put("TNT", 2095);
        put("BOOKSHELF", 2096);
        put("MOSSY_COBBLESTONE", 2353);
        put("OBSIDIAN", 2354);
        put("TORCH", 2355);

        // Degerli bloklar
        put("DIAMOND_ORE", 4274);
        put("DIAMOND_BLOCK", 4276);
        put("CRAFTING_TABLE", 4277);
        put("FURNACE", 4295);
        put("SNOW_BLOCK", 5781);
        put("ICE", 5780);
        put("CLAY", 5798);
        put("NETHERRACK", 5849);
        put("SOUL_SAND", 5850);
        put("GLOWSTONE", 5863);

        // Renkli beton
        put("WHITE_CONCRETE", 12728);
        put("ORANGE_CONCRETE", 12729);
        put("MAGENTA_CONCRETE", 12730);
        put("LIGHT_BLUE_CONCRETE", 12731);
        put("YELLOW_CONCRETE", 12732);
        put("LIME_CONCRETE", 12733);
        put("PINK_CONCRETE", 12734);
        put("GRAY_CONCRETE", 12735);
        put("LIGHT_GRAY_CONCRETE", 12736);
        put("CYAN_CONCRETE", 12737);
        put("PURPLE_CONCRETE", 12738);
        put("BLUE_CONCRETE", 12739);
        put("BROWN_CONCRETE", 12740);
        put("GREEN_CONCRETE", 12741);
        put("RED_CONCRETE", 12742);
        put("BLACK_CONCRETE", 12743);

        // Sivislar (level=0 default)
        put("WATER", 80);
        put("LAVA", 96);

        // Camlar
        put("WHITE_STAINED_GLASS", 5945);
        put("ORANGE_STAINED_GLASS", 5946);

        // Nether / End
        put("NETHER_BRICKS", 7272);
        put("END_STONE", 7415);
        put("EMERALD_BLOCK", 7665);
        put("QUARTZ_BLOCK", 9235);

        // Deepslate
        put("DEEPSLATE", 24905);
        put("COBBLED_DEEPSLATE", 24907);

        // Modern bloklar
        put("COPPER_BLOCK", 22938);
        put("AMETHYST_BLOCK", 21031);
        put("TUFF", 21081);
        put("CALCITE", 22316);

        // Diger sikca kullanilan
        put("BARRIER", 10366);
        put("PACKED_ICE", 10746);
        put("BLUE_ICE", 12941);
        put("CRYING_OBSIDIAN", 19449);

        // Tas cesitleri
        put("STONE_BRICKS", 6537);
        put("MOSSY_STONE_BRICKS", 6538);
        put("CRACKED_STONE_BRICKS", 6539);
        put("CHISELED_STONE_BRICKS", 6540);
        put("SMOOTH_STONE", 11306);
        put("SMOOTH_SANDSTONE", 11307);
        put("RED_SANDSTONE", 11079);
        put("MUD", 24903);
        put("MUD_BRICKS", 6542);
        put("PACKED_MUD", 6541);

        // Ek yapraklar
        put("ACACIA_LEAVES", 376);
        put("DARK_OAK_LEAVES", 432);
        put("CHERRY_LEAVES", 404);
        put("MANGROVE_LEAVES", 460);

        // Soyulmus loglar ve odun
        put("STRIPPED_OAK_LOG", 181);
        put("STRIPPED_SPRUCE_LOG", 163);
        put("STRIPPED_BIRCH_LOG", 166);
        put("STRIPPED_JUNGLE_LOG", 169);
        put("STRIPPED_ACACIA_LOG", 172);
        put("STRIPPED_DARK_OAK_LOG", 178);
        put("OAK_WOOD", 190);
        put("SPRUCE_WOOD", 193);
        put("BIRCH_WOOD", 196);

        // Merdivenler, dilimler, citler
        put("OAK_STAIRS", 2885);
        put("COBBLESTONE_STAIRS", 4693);
        put("STONE_BRICK_STAIRS", 7120);
        put("OAK_SLAB", 11165);
        put("COBBLESTONE_SLAB", 11255);
        put("STONE_BRICK_SLAB", 11267);
        put("STONE_SLAB", 11225);
        put("SMOOTH_STONE_SLAB", 11231);
        put("OAK_FENCE", 5848);
        put("SPRUCE_FENCE", 11597);
        put("BIRCH_FENCE", 11629);

        // Kapilar ve kapaklar
        put("OAK_DOOR", 4601);
        put("IRON_DOOR", 5663);
        put("OAK_TRAPDOOR", 5976);
        put("IRON_TRAPDOOR", 10414);
        put("LADDER", 4655);

        // Raylar
        put("RAIL", 4663);
        put("POWERED_RAIL", 1957);
        put("DETECTOR_RAIL", 1981);

        // Kasalar ve is bloklari
        put("CHEST", 2955);
        put("ENDER_CHEST", 7514);
        put("BARREL", 18409);
        put("ANVIL", 9107);
        put("GRINDSTONE", 18442);
        put("STONECUTTER", 18467);
        put("LOOM", 18404);
        put("CARTOGRAPHY_TABLE", 18436);
        put("SMITHING_TABLE", 18466);

        // Isik kaynaklari
        put("LANTERN", 18506);
        put("SOUL_LANTERN", 18510);
        put("CAMPFIRE", 18514);
        put("SOUL_CAMPFIRE", 18546);
        put("SEA_LANTERN", 10724);
        put("SHROOMLIGHT", 18610);
        put("FLOWER_POT", 8567);

        // Tarim ve dogal
        put("HAY_BLOCK", 10726);
        put("MELON", 6812);
        put("PUMPKIN", 6811);
        put("CARVED_PUMPKIN", 5866);
        put("JACK_O_LANTERN", 5870);
        put("CACTUS", 5782);
        put("SUGAR_CANE", 5799);
        put("BAMBOO", 12945);
        put("BROWN_MUSHROOM", 2089);
        put("RED_MUSHROOM", 2090);
        put("WHEAT", 4278);
        put("POTATOES", 8603);
        put("CARROTS", 8595);
        put("BEETROOTS", 12509);
        put("DIRT_PATH", 12513);
        put("FARMLAND", 4286);
        put("ROOTED_DIRT", 24902);
        put("MOSS_BLOCK", 24843);

        // Cevherler
        put("COPPER_ORE", 22942);
        put("DEEPSLATE_COPPER_ORE", 22943);
        put("EMERALD_ORE", 7511);
        put("DEEPSLATE_EMERALD_ORE", 7512);
        put("REDSTONE_ORE", 5735);
        put("DEEPSLATE_REDSTONE_ORE", 5737);
        put("REDSTONE_BLOCK", 9223);
        put("RAW_COPPER_BLOCK", 26559);
        put("RAW_IRON_BLOCK", 26558);
        put("RAW_GOLD_BLOCK", 26560);

        // Terracotta
        put("TERRACOTTA", 10744);
        put("WHITE_TERRACOTTA", 9356);
        put("ORANGE_TERRACOTTA", 9357);
        put("MAGENTA_TERRACOTTA", 9358);
        put("LIGHT_BLUE_TERRACOTTA", 9359);
        put("YELLOW_TERRACOTTA", 9360);
        put("LIME_TERRACOTTA", 9361);
        put("PINK_TERRACOTTA", 9362);
        put("GRAY_TERRACOTTA", 9363);
        put("LIGHT_GRAY_TERRACOTTA", 9364);
        put("CYAN_TERRACOTTA", 9365);
        put("PURPLE_TERRACOTTA", 9366);
        put("BLUE_TERRACOTTA", 9367);
        put("BROWN_TERRACOTTA", 9368);
        put("GREEN_TERRACOTTA", 9369);
        put("RED_TERRACOTTA", 9370);
        put("BLACK_TERRACOTTA", 9371);
        put("MYCELIUM", 7270);

        // Nether bloklar
        put("BASALT", 5853);
        put("POLISHED_BASALT", 5856);
        put("SMOOTH_BASALT", 26557);
        put("BLACKSTONE", 19460);
        put("POLISHED_BLACKSTONE", 19871);
        put("POLISHED_BLACKSTONE_BRICKS", 19872);
        put("CRIMSON_NYLIUM", 18608);
        put("WARPED_NYLIUM", 18591);
        put("CRIMSON_STEM", 18597);
        put("WARPED_STEM", 18580);
        put("NETHER_WART_BLOCK", 12544);
        put("WARPED_WART_BLOCK", 18593);

        // Deepslate cesitleri
        put("POLISHED_DEEPSLATE", 25318);
        put("DEEPSLATE_BRICKS", 26140);
        put("DEEPSLATE_TILES", 25729);
        put("CHISELED_DEEPSLATE", 26551);
        put("REINFORCED_DEEPSLATE", 26573);

        // End / Purpur
        put("PURPUR_BLOCK", 12410);
        put("END_STONE_BRICKS", 12494);
        put("PRISMARINE", 10463);
        put("PRISMARINE_BRICKS", 10464);
        put("DARK_PRISMARINE", 10465);

        // Diger
        put("SPONGE", 517);
        put("WET_SPONGE", 518);
        put("SLIME_BLOCK", 10364);
        put("HONEY_BLOCK", 19445);
        put("HONEYCOMB_BLOCK", 19446);
        put("BONE_BLOCK", 12547);
        put("SCULK", 22799);
        put("POWDER_SNOW", 22318);
        put("DRIPSTONE_BLOCK", 24768);
    }

    private static void put(String material, int stateId) {
        BLOCK_STATES.put(material, stateId);
    }

    // ==================== NMS REFLECTION (tum bloklar icin) ====================

    private static java.lang.reflect.Method getStateMethod;
    private static java.lang.reflect.Method getIdMethod;
    private static Object blockStateRegistry;
    private static boolean nmsInitialized = false;
    private static boolean nmsAvailable = false;

    /**
     * NMS reflection ile Block.BLOCK_STATE_REGISTRY'ye erisim kurar.
     * Basarisiz olursa nmsAvailable=false kalir, static map kullanilir.
     */
    private static synchronized void initNms() {
        if (nmsInitialized) return;
        nmsInitialized = true;
        try {
            // Paper 1.21 Mojang mappings
            Class<?> craftBlockData = Class.forName("org.bukkit.craftbukkit.block.data.CraftBlockData");
            getStateMethod = craftBlockData.getMethod("getState");

            Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block");
            java.lang.reflect.Field registryField = blockClass.getDeclaredField("BLOCK_STATE_REGISTRY");
            registryField.setAccessible(true);
            blockStateRegistry = registryField.get(null);

            getIdMethod = blockStateRegistry.getClass().getMethod("getId", Object.class);
            nmsAvailable = true;
        } catch (Exception e) {
            nmsAvailable = false;
            org.bukkit.Bukkit.getLogger().info("[WebReplay] NMS block state reflection basarisiz - static map kullanilacak: " + e.getMessage());
        }
    }

    /**
     * Bukkit Block'tan dogrudan protokol state ID'sini alir (NMS reflection).
     * Tum blok turleri ve state varyasyonlari (merdiven yonu, kapi durumu vs.) dogru doner.
     * NMS basarisiz olursa static map'e fallback yapar.
     */
    public static int getBlockStateIdFromBlock(org.bukkit.block.Block block) {
        if (block.getType().isAir()) return 0;

        initNms();
        if (nmsAvailable) {
            try {
                Object craftBlockData = block.getBlockData();
                Object nmsState = getStateMethod.invoke(craftBlockData);
                int id = (int) getIdMethod.invoke(blockStateRegistry, nmsState);
                if (id >= 0) return id;
            } catch (Exception ignored) {
                // Fallback
            }
        }

        // NMS basarisiz -> static map fallback
        return getBlockStateId(block.getType().name());
    }

    /**
     * Material adini block state ID'ye cevirir.
     * Oncelikle NMS ile gercek protokol ID'sini almaya calisir (sunucu versiyonuna uyumlu).
     * Basarisiz olursa static map'e fallback yapar.
     */
    public static int getBlockStateId(String materialName) {
        if (materialName == null) return 0; // AIR

        String name = materialName.replace("minecraft:", "").toUpperCase();
        int colonIdx = name.indexOf(':');
        if (colonIdx > 0) {
            name = name.substring(0, colonIdx);
        }

        // NMS ile Material'dan default block state ID'sini al
        initNms();
        if (nmsAvailable) {
            try {
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(name);
                if (mat != null && mat.isBlock()) {
                    org.bukkit.block.data.BlockData blockData = mat.createBlockData();
                    Object nmsState = getStateMethod.invoke(blockData);
                    int id = (int) getIdMethod.invoke(blockStateRegistry, nmsState);
                    if (id >= 0) return id;
                }
            } catch (Exception ignored) {
                // fallback to static map
            }
        }

        return BLOCK_STATES.getOrDefault(name, 1); // default STONE
    }

    /**
     * AIR block state ID.
     */
    public static int getAirStateId() {
        return 0;
    }

    /**
     * NMS reflection'in basarili olup olmadigini dondurur (debug icin).
     */
    public static boolean isNmsAvailable() {
        initNms();
        return nmsAvailable;
    }

    private WebReplayBlockStateMap() {}
}
