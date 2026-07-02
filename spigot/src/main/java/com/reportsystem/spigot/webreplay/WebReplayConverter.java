package com.reportsystem.spigot.webreplay;

import com.reportsystem.common.replay.actions.*;
import com.reportsystem.spigot.utils.ItemSerializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

/**
 * ReplayAction listesini minecraft-web-client .packets.txt formatina donusturur.
 * Hile tespiti icin kritik: timing ve hareket verileri birebir korunur.
 */
public class WebReplayConverter {

    // Sunucu versiyonu otomatik tespit edilir - block state ID'leri versiyona baglidir
    private static String MC_VERSION = null;
    // Player entity type ID - NMS ile otomatik tespit edilir
    private static int PLAYER_ENTITY_TYPE = -1;

    /**
     * Sunucunun gercek Minecraft versiyonunu dondurur.
     * Block state ID'leri versiyonlar arasi degisir, web client dogru versiyonu bilmeli.
     */
    private static String getServerVersion() {
        if (MC_VERSION == null) {
            try {
                MC_VERSION = org.bukkit.Bukkit.getMinecraftVersion();
            } catch (Exception e) {
                MC_VERSION = "1.21.4"; // fallback
            }
        }
        return MC_VERSION;
    }

    /**
     * Player entity type ID'sini NMS reflection ile alir.
     * Versiyonlar arasi entity type ID'leri degisir (yeni entity'ler eklenince kayar).
     */
    private static int getPlayerEntityType() {
        if (PLAYER_ENTITY_TYPE < 0) {
            try {
                // Dogrudan NMS EntityType.PLAYER static field'ina eris
                Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
                Object playerType = entityTypeClass.getField("PLAYER").get(null);

                // BuiltInRegistries.ENTITY_TYPE.getId(playerType)
                Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
                Object registry = registriesClass.getDeclaredField("ENTITY_TYPE").get(null);

                java.lang.reflect.Method getIdMethod = null;
                for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
                    if (m.getName().equals("getId") && m.getParameterCount() == 1
                            && m.getReturnType() == int.class) {
                        getIdMethod = m;
                        break;
                    }
                }

                if (getIdMethod != null) {
                    PLAYER_ENTITY_TYPE = (int) getIdMethod.invoke(registry, playerType);
                }
            } catch (Exception e) {
                org.bukkit.Bukkit.getLogger().info("[WebReplay] Player entity type NMS failed: " + e.getMessage());
            }

            if (PLAYER_ENTITY_TYPE < 0) PLAYER_ENTITY_TYPE = 128;
            org.bukkit.Bukkit.getLogger().info("[WebReplay] Player entity type ID: " + PLAYER_ENTITY_TYPE +
                    " (MC " + getServerVersion() + ")");
        }
        return PLAYER_ENTITY_TYPE;
    }

    /**
     * NMS reflection ile entity type name'den protokol ID'sini alir.
     * Tum versiyonlarda dogru calisiyor.
     */
    private static int getEntityTypeIdFromNms(String entityTypeName) {
        try {
            String name = entityTypeName.replace("minecraft:", "").toLowerCase();

            // NMS EntityType sinifinda static field olarak entity type'lari bul
            // Ornek: EntityType.ZOMBIE, EntityType.SKELETON, EntityType.ARROW vs.
            Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object registry = registriesClass.getDeclaredField("ENTITY_TYPE").get(null);

            // getId metodu
            java.lang.reflect.Method getIdMethod = null;
            for (java.lang.reflect.Method m : registry.getClass().getMethods()) {
                if (m.getName().equals("getId") && m.getParameterCount() == 1
                        && m.getReturnType() == int.class) {
                    getIdMethod = m;
                    break;
                }
            }
            if (getIdMethod == null) return -1;

            // Static field adini olustur: "zombie" -> "ZOMBIE", "falling_block" -> "FALLING_BLOCK"
            String fieldName = name.toUpperCase();
            try {
                Object nmsEntityType = entityTypeClass.getField(fieldName).get(null);
                int id = (int) getIdMethod.invoke(registry, nmsEntityType);
                return id;
            } catch (NoSuchFieldException e) {
                // Bazi isimler farkli olabilir, alternatifleri dene
            }

            // Alternatif NMS field isimleri
            String[] fieldAlternatives = getNmsFieldAlternatives(name);
            for (String alt : fieldAlternatives) {
                try {
                    Object nmsEntityType = entityTypeClass.getField(alt).get(null);
                    int id = (int) getIdMethod.invoke(registry, nmsEntityType);
                    return id;
                } catch (NoSuchFieldException ignored) {}
            }

            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String[] getNmsFieldAlternatives(String name) {
        // NMS EntityType field isimleri genelde UPPER_CASE, bazi ozel durumlar var
        switch (name) {
            case "fishing_bobber": return new String[]{"FISHING_BOBBER", "FISHING_HOOK"};
            case "tnt":            return new String[]{"TNT", "PRIMED_TNT"};
            case "experience_bottle": return new String[]{"EXPERIENCE_BOTTLE", "THROWN_EXP_BOTTLE"};
            case "snow_golem":     return new String[]{"SNOW_GOLEM", "SNOWMAN"};
            case "mooshroom":      return new String[]{"MOOSHROOM", "MUSHROOM_COW"};
            case "ender_pearl":    return new String[]{"ENDER_PEARL"};
            case "firework_rocket": return new String[]{"FIREWORK_ROCKET", "FIREWORKS_ROCKET"};
            default:               return new String[]{};
        }
    }

    private final WebReplayEntityManager entityManager;
    private final WebReplayPacketBuilder builder;

    // Entity basina flag takibi (entityId -> flags byte)
    private final Map<Integer, Byte> entityFlags = new HashMap<>();

    // Entity basina son konum takibi (entityId -> {x, y, z}) - akici hareket icin
    private final Map<Integer, double[]> lastPositions = new HashMap<>();

    // Ayni timestamp'te birden fazla entity olusumunda UUID cakismasini onlemek icin sayac
    private int entityCounter = 0;

    // Olta hook ve arac entity takibi (cleanup icin)
    private int lastHookEntityId = -1;
    private int lastVehicleEntityId = -1;

    // HUD takibi
    private long replayStartTime = 0;
    private long replayEndTime = 0;
    private String recordedPlayerName = "";
    // HUD guncelleme throttle (500ms aralıkla)
    private long lastHUDUpdateTime = 0;

    public WebReplayConverter() {
        this.entityManager = new WebReplayEntityManager();
        this.builder = new WebReplayPacketBuilder();
    }

    private byte getEntityFlags(int entityId) {
        return entityFlags.getOrDefault(entityId, (byte) 0);
    }

    private void setEntityFlags(int entityId, byte flags) {
        entityFlags.put(entityId, flags);
    }

    /**
     * ReplayAction listesini .packets.txt icerigine donusturur.
     *
     * @param actions    kayitli aksiyonlar
     * @param playerName raporlanan oyuncunun adi
     * @param playerUuid raporlanan oyuncunun UUID'si (null olabilir)
     * @param skinTexture oyuncu skin texture (null olabilir)
     * @param skinSignature oyuncu skin signature (null olabilir)
     * @return .packets.txt dosya icerigi
     */
    public String convert(List<ReplayAction> actions, String playerName,
                          String playerUuid, String skinTexture, String skinSignature,
                          List<int[]> terrainBlocks) {
        if (actions == null || actions.isEmpty()) {
            return "";
        }

        // Baslangic konumunu bul
        double startX = 0, startY = 64, startZ = 0;
        float startYaw = 0, startPitch = 0;
        for (ReplayAction action : actions) {
            if (action instanceof LocationAction && action.isMainPlayer()) {
                LocationAction loc = (LocationAction) action;
                startX = loc.getX();
                startY = loc.getY();
                startZ = loc.getZ();
                startYaw = loc.getYaw();
                startPitch = loc.getPitch();
                break;
            }
        }

        // Skin bilgisini aksiyonlardan al (eger parametre null ise)
        if (skinTexture == null) {
            for (ReplayAction action : actions) {
                if (action instanceof PlayerInfoAction && action.isMainPlayer()) {
                    PlayerInfoAction info = (PlayerInfoAction) action;
                    skinTexture = info.getSkinTexture();
                    skinSignature = info.getSkinSignature();
                    break;
                }
            }
        }

        // UUID olustur
        String uuid = playerUuid != null ? playerUuid : UUID.randomUUID().toString();

        // Header
        builder.writeHeader(getServerVersion());

        // Setup paketleri
        writeLoginPacket();
        writeGameStateChange();
        writeTimePacket(); // Gun ortasi - aydinlik terrain
        writeSpawnPositionPacket((int) startX, (int) startY, (int) startZ);
        // Izleyiciyi NPC'nin yanina ve biraz yukarina konumlandir (spectator mod, serbestce ucabilir)
        writePositionPacket(startX + 5, startY + 3, startZ + 5, -135, 20);

        // Terrain varsa chunk alanini terrain'e gore hesapla, yoksa sabit 9x9
        if (terrainBlocks != null && !terrainBlocks.isEmpty()) {
            List<int[]> corrected = correctTerrainForActions(terrainBlocks, actions);
            writeChunksForTerrainWithLight(corrected);
            writeTerrainBlocks(corrected);
        } else {
            writeSuperFlatChunks((int) startX >> 4, (int) startZ >> 4);
            writeGroundPlatform(startX, startY, startZ);
        }
        writePlayerInfoPacket(uuid, playerName, skinTexture, skinSignature);
        writeSpawnEntityPacket(uuid, startX, startY, startZ, startYaw, startPitch);
        writeSkinLayersMetadata(entityManager.getMainPlayerEntityId());

        // Takim rengi (kirmizi isim) + kalp gostergesi (oyun ici ile ayni)
        this.recordedPlayerName = playerName;
        setupTeamAndHealth(playerName, entityManager.getMainPlayerEntityId());

        // HUD baslat
        this.replayStartTime = actions.get(0).getTimestamp();
        this.replayEndTime = actions.get(actions.size() - 1).getTimestamp();
        setupHUD(playerName);

        // Initial equipment (ilk EquipmentAction'i bul)
        for (ReplayAction action : actions) {
            if (action instanceof EquipmentAction && action.isMainPlayer()) {
                convertEquipment((EquipmentAction) action, actions.get(0).getTimestamp());
                break;
            }
        }

        // Aksiyonlari donustur + HUD guncelle
        for (ReplayAction action : actions) {
            convertAction(action);
            if (action.isMainPlayer()) {
                updateHUD(action);
            }
        }

        return builder.build();
    }

    // ==================== SETUP PAKETLERI ====================

    private void writeLoginPacket() {
        // entityId=999: Izleyicinin entity ID'si. Ana oyuncu NPC'si entityId=1 kullanir.
        // Farkli ID'ler olmazsa client NPC'yi "kendisi" sanip 1.sahis gosterir.
        builder.writeSetupPacket("login",
                "{\"entityId\":999" +
                ",\"isHardcore\":false" +
                ",\"dimensionCount\":3" +
                ",\"worldNames\":[\"minecraft:overworld\",\"minecraft:the_nether\",\"minecraft:the_end\"]" +
                ",\"maxPlayers\":20" +
                ",\"viewDistance\":8" +
                ",\"simulationDistance\":8" +
                ",\"reducedDebugInfo\":true" +
                ",\"enableRespawnScreen\":true" +
                ",\"doLimitedCrafting\":false" +
                ",\"worldState\":{" +
                    "\"dimension\":0" +
                    ",\"name\":\"minecraft:overworld\"" +
                    ",\"hashedSeed\":[0,0]" +
                    ",\"gamemode\":\"spectator\"" +
                    ",\"previousGamemode\":-1" +
                    ",\"isDebug\":false" +
                    ",\"isFlat\":false" +
                    ",\"portalCooldown\":0" +
                "}" +
                ",\"enforcesSecureChat\":false" +
                "}");
    }

    private void writeGameStateChange() {
        // reason=13 -> Start waiting for level chunks (client spawn tetikler)
        builder.writeSetupPacket("game_state_change",
                "{\"reason\":13,\"gameMode\":0}");
    }

    private void writeTimePacket() {
        // worldAge=6000, time=6000 (gun ortasi = tam aydinlik)
        builder.writeSetupPacket("update_time",
                "{\"age\":[0,6000],\"time\":[0,6000]}");
    }

    private void writeSpawnPositionPacket(int x, int y, int z) {
        builder.writeSetupPacket("spawn_position",
                "{\"location\":{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}" +
                ",\"angle\":0}");
    }

    private void writePositionPacket(double x, double y, double z, float yaw, float pitch) {
        builder.writeSetupPacket("position",
                "{\"x\":" + x +
                ",\"y\":" + y +
                ",\"z\":" + z +
                ",\"yaw\":" + yaw +
                ",\"pitch\":" + pitch +
                ",\"flags\":0" +
                ",\"teleportId\":1" +
                "}");
    }

    private void writeSuperFlatChunks(int centerChunkX, int centerChunkZ) {
        // 1.21.1: Overworld = 384 blok yukseklik (-64 to 319), 24 section
        // Her section 8 byte: blockCount(2) + blockStates(3) + biomes(3)
        // Tumu air/plains = hepsi 0x00
        // Terrain tarama yaricapi 24 blok = ~2 chunk, guvenlik icin 4 chunk yaricapi
        String chunkData = generateEmptyChunkData();

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int cx = centerChunkX + dx;
                int cz = centerChunkZ + dz;
                builder.writeSetupPacket("map_chunk",
                        "{\"x\":" + cx +
                        ",\"z\":" + cz +
                        ",\"heightmaps\":{\"type\":\"compound\",\"name\":\"\",\"value\":{}}" +
                        ",\"chunkData\":{\"type\":\"Buffer\",\"data\":" + chunkData + "}" +
                        ",\"blockEntities\":[]" +
                        ",\"skyLightMask\":[]" +
                        ",\"blockLightMask\":[]" +
                        ",\"emptySkyLightMask\":[]" +
                        ",\"emptyBlockLightMask\":[]" +
                        ",\"skyLight\":[]" +
                        ",\"blockLight\":[]" +
                        "}");
            }
        }
    }

    private String generateEmptyChunkData() {
        // Her section: blockCount=0(2byte) + blockStates(bitsPerEntry=0, palette=0(air), dataLen=0) + biomes(bitsPerEntry=0, palette=0(plains), dataLen=0)
        // = 8 byte sifir per section * 24 section = 192 byte
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 192; i++) {
            if (i > 0) sb.append(",");
            sb.append("0");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Terrain verisini kayit sirasindaki blok degisikliklerine gore duzeltir.
     * - PLACE_BLOCK: kayit sirasinda konulan blok -> terrain'den cikar (replay sirasinda konulacak)
     * - START_BREAKING (blockType var): kayit sirasinda kirilan blok -> terrain'e ekle (replay basindan gorunsun)
     */
    private List<int[]> correctTerrainForActions(List<int[]> terrainBlocks, List<ReplayAction> actions) {
        // Kayit sirasinda konulan bloklarin pozisyonlarini topla
        java.util.Set<Long> placedPositions = new java.util.HashSet<>();
        // Kayit sirasinda kirilan bloklari topla (pozisyon -> stateId)
        java.util.Map<Long, Integer> brokenBlocks = new java.util.HashMap<>();

        for (ReplayAction action : actions) {
            if (action instanceof BlockAction) {
                BlockAction ba = (BlockAction) action;
                long posKey = packPos(ba.getX(), ba.getY(), ba.getZ());

                if (ba.getActionType() == BlockAction.BlockActionType.PLACE_BLOCK) {
                    placedPositions.add(posKey);
                    // Kapi, yatak gibi cok bloklu yapilar icin Y+1 ve Y-1 pozisyonlarini da cikar
                    placedPositions.add(packPos(ba.getX(), ba.getY() + 1, ba.getZ()));
                    placedPositions.add(packPos(ba.getX(), ba.getY() - 1, ba.getZ()));
                    brokenBlocks.remove(posKey);
                } else if (ba.getActionType() == BlockAction.BlockActionType.START_BREAKING
                        && ba.getBlockType() != null && !ba.getBlockType().isEmpty()) {
                    if (placedPositions.contains(posKey)) {
                        // Oyuncunun koydugu blogu kiriyor - terrain'den degil, terrain'e eklemeye gerek yok
                        placedPositions.remove(posKey);
                    } else if (!brokenBlocks.containsKey(posKey)) {
                        // Terrain'deki blogu kiriyor - basindan gorunsun
                        int stateId = WebReplayBlockStateMap.getBlockStateId(ba.getBlockType());
                        brokenBlocks.put(posKey, stateId);
                    }
                }
            }
        }

        // Terrain'den konulan bloklari cikar
        List<int[]> corrected = new java.util.ArrayList<>(terrainBlocks.size());
        java.util.Set<Long> terrainPositions = new java.util.HashSet<>();

        for (int[] block : terrainBlocks) {
            long posKey = packPos(block[0], block[1], block[2]);
            if (!placedPositions.contains(posKey)) {
                corrected.add(block);
            }
            terrainPositions.add(posKey);
        }

        // Kirilan bloklari terrain'e ekle (eger terrain'de zaten yoksa)
        for (java.util.Map.Entry<Long, Integer> entry : brokenBlocks.entrySet()) {
            if (!terrainPositions.contains(entry.getKey())) {
                long pos = entry.getKey();
                int x = (int) (pos >> 40) & 0xFFFFF;
                int z = (int) (pos >> 20) & 0xFFFFF;
                int y = (int) pos & 0xFFFFF;
                // Negatif degerler icin sign extension (20-bit signed)
                if ((x & 0x80000) != 0) x |= ~0xFFFFF;
                if ((z & 0x80000) != 0) z |= ~0xFFFFF;
                if ((y & 0x80000) != 0) y |= ~0xFFFFF;
                corrected.add(new int[]{x, y, z, entry.getValue()});
            }
        }

        return corrected;
    }

    private static long packPos(int x, int y, int z) {
        return ((long)(x & 0xFFFFF) << 40) | ((long)(z & 0xFFFFF) << 20) | (y & 0xFFFFF);
    }

    /**
     * Terrain bloklari icin chunk'lari olusturur (skyLight dahil).
     * Terrain'in kapsadigi Y section'larina skyLight=15 ekler → aydinlik terrain.
     */
    private void writeChunksForTerrainWithLight(List<int[]> terrainBlocks) {
        // Terrain bloklarinin kapsadigi chunk ve Y alanini hesapla
        int minCX = Integer.MAX_VALUE, minCZ = Integer.MAX_VALUE;
        int maxCX = Integer.MIN_VALUE, maxCZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;

        for (int[] block : terrainBlocks) {
            int cx = block[0] >> 4;
            int cz = block[2] >> 4;
            minCX = Math.min(minCX, cx);
            minCZ = Math.min(minCZ, cz);
            maxCX = Math.max(maxCX, cx);
            maxCZ = Math.max(maxCZ, cz);
            minY = Math.min(minY, block[1]);
            maxY = Math.max(maxY, block[1]);
        }

        // 1 chunk margin ekle
        minCX -= 1; minCZ -= 1;
        maxCX += 1; maxCZ += 1;

        // SkyLight: terrain section'lari + 2 ustundeki section'a kadar
        // Section index: (Y + 64) / 16, light section index: section + 1 (cunku -1 boundary var)
        int minLightSection = Math.max(0, ((minY + 64) / 16));
        int maxLightSection = Math.min(25, ((maxY + 64) / 16) + 2);

        // skyLightMask: hangi light section'larda veri var
        long skyLightMaskValue = 0;
        for (int s = minLightSection; s <= maxLightSection; s++) {
            skyLightMaskValue |= (1L << s);
        }

        // Her section icin 2048 byte full brightness (0xFF) - onceden olustur
        String fullLightSection = generateFullLightSection();
        int lightSectionCount = maxLightSection - minLightSection + 1;

        StringBuilder skyLightArray = new StringBuilder("[");
        for (int i = 0; i < lightSectionCount; i++) {
            if (i > 0) skyLightArray.append(",");
            skyLightArray.append(fullLightSection);
        }
        skyLightArray.append("]");
        String skyLightStr = skyLightArray.toString();

        String chunkData = generateEmptyChunkData();

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                builder.writeSetupPacket("map_chunk",
                        "{\"x\":" + cx +
                        ",\"z\":" + cz +
                        ",\"heightmaps\":{\"type\":\"compound\",\"name\":\"\",\"value\":{}}" +
                        ",\"chunkData\":{\"type\":\"Buffer\",\"data\":" + chunkData + "}" +
                        ",\"blockEntities\":[]" +
                        ",\"skyLightMask\":[" + skyLightMaskValue + "]" +
                        ",\"blockLightMask\":[]" +
                        ",\"emptySkyLightMask\":[]" +
                        ",\"emptyBlockLightMask\":[]" +
                        ",\"skyLight\":" + skyLightStr +
                        ",\"blockLight\":[]" +
                        "}");
            }
        }
    }

    /**
     * 2048 byte full brightness (0xFF) sky light section verisini olusturur.
     * Her byte = 0xFF = iki nibble 15 (max sky light).
     */
    private String generateFullLightSection() {
        StringBuilder sb = new StringBuilder("{\"type\":\"Buffer\",\"data\":[");
        for (int i = 0; i < 2048; i++) {
            if (i > 0) sb.append(",");
            sb.append("255");
        }
        sb.append("]}");
        return sb.toString();
    }

    private void writeTerrainBlocks(List<int[]> terrainBlocks) {
        // Chest bloklari icin block entity data gonder (double chest render)
        java.util.Set<Integer> chestStateIds = detectChestStateIds(terrainBlocks);

        for (int[] block : terrainBlocks) {
            // block = {x, y, z, blockStateId}
            builder.writeSetupPacket("block_change",
                    "{\"location\":{\"x\":" + block[0] + ",\"y\":" + block[1] + ",\"z\":" + block[2] +
                    "},\"type\":" + block[3] + "}");

            // Chest bloklari icin block entity data (web client'in 3D model render icin ihtiyaci var)
            if (chestStateIds.contains(block[3])) {
                builder.writeSetupPacket("block_entity_data",
                        "{\"location\":{\"x\":" + block[0] + ",\"y\":" + block[1] + ",\"z\":" + block[2] + "}" +
                        ",\"action\":1" +
                        ",\"nbtData\":{\"type\":\"compound\",\"name\":\"\",\"value\":" +
                        "{\"id\":{\"type\":\"string\",\"value\":\"minecraft:chest\"}}}}");
            }
        }
    }

    /**
     * Terrain bloklari arasindaki chest state ID'lerini NMS ile tespit eder.
     * Paper 1.21+ Mojang mapped isimleri kullanir. NMS bulunamazsa bos set doner.
     */
    private static java.util.Set<Integer> detectChestStateIds(List<int[]> terrainBlocks) {
        java.util.Set<Integer> chestIds = new java.util.HashSet<>();
        java.util.Set<Integer> checked = new java.util.HashSet<>();

        try {
            Class<?> blockClass = Class.forName("net.minecraft.world.level.block.Block");
            Object blockStateRegistry = blockClass.getField("BLOCK_STATE_REGISTRY").get(null);

            java.lang.reflect.Method byIdMethod = null;
            for (java.lang.reflect.Method m : blockStateRegistry.getClass().getMethods()) {
                if (m.getName().equals("byId") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == int.class) {
                    byIdMethod = m;
                    break;
                }
            }
            if (byIdMethod == null) return chestIds;

            Class<?> chestBlockClass = Class.forName("net.minecraft.world.level.block.ChestBlock");

            for (int[] block : terrainBlocks) {
                int stateId = block[3];
                if (!checked.add(stateId)) continue;

                try {
                    Object blockState = byIdMethod.invoke(blockStateRegistry, stateId);
                    if (blockState == null) continue;
                    Object nmsBlock = blockState.getClass().getMethod("getBlock").invoke(blockState);
                    if (chestBlockClass.isInstance(nmsBlock)) {
                        chestIds.add(stateId);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            // NMS bulunamadiysa chest tespiti yapilamaz - graceful degradation
        }

        return chestIds;
    }

    private void writeGroundPlatform(double startX, double startY, double startZ) {
        // NPC'nin altina 21x21 bloklik bir zemin olustur
        int blockY = (int) startY - 1;
        int centerX = (int) startX;
        int centerZ = (int) startZ;
        int radius = 10;

        // Grass block state ID (1.21.1)
        int grassStateId = WebReplayBlockStateMap.getBlockStateId("GRASS_BLOCK");
        if (grassStateId < 0) grassStateId = 9; // fallback

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                builder.writeSetupPacket("block_change",
                        "{\"location\":{\"x\":" + x + ",\"y\":" + blockY + ",\"z\":" + z +
                        "},\"type\":" + grassStateId + "}");
            }
        }
    }

    private void writePlayerInfoPacket(String uuid, String name,
                                       String skinTexture, String skinSignature) {
        StringBuilder props = new StringBuilder("[");
        if (skinTexture != null && !skinTexture.isEmpty()) {
            props.append("{\"name\":\"textures\",\"value\":\"")
                 .append(escapeJson(skinTexture))
                 .append("\"");
            if (skinSignature != null && !skinSignature.isEmpty()) {
                props.append(",\"signature\":\"")
                     .append(escapeJson(skinSignature))
                     .append("\"");
            }
            props.append("}");
        }
        props.append("]");

        builder.writeSetupPacket("player_info",
                "{\"action\":" +
                "{\"add_player\":true" +
                ",\"initialize_chat\":false" +
                ",\"update_game_mode\":true" +
                ",\"update_listed\":true" +
                ",\"update_latency\":true" +
                ",\"update_display_name\":false}" +
                ",\"data\":[{" +
                "\"uuid\":\"" + uuid + "\"" +
                ",\"player\":{\"name\":\"" + escapeJson(name) + "\",\"properties\":" + props + "}" +
                ",\"gamemode\":0" +
                ",\"listed\":true" +
                ",\"latency\":0" +
                "}]}");
    }

    private void writeSpawnEntityPacket(String uuid, double x, double y, double z,
                                        float yaw, float pitch) {
        int entityId = entityManager.getMainPlayerEntityId();
        builder.writeSetupPacket("spawn_entity",
                "{\"entityId\":" + entityId +
                ",\"objectUUID\":\"" + uuid + "\"" +
                ",\"type\":" + getPlayerEntityType() +
                ",\"x\":" + x +
                ",\"y\":" + y +
                ",\"z\":" + z +
                ",\"pitch\":" + WebReplayPacketBuilder.angleToByte(pitch) +
                ",\"yaw\":" + WebReplayPacketBuilder.angleToByte(yaw) +
                ",\"objectData\":0" +
                ",\"velocityX\":0" +
                ",\"velocityY\":0" +
                ",\"velocityZ\":0" +
                "}");

        // Kafa rotasyonunu ayri paket olarak gonder
        builder.writeSetupPacket("entity_head_rotation",
                "{\"entityId\":" + entityId +
                ",\"headYaw\":" + WebReplayPacketBuilder.angleToByte(yaw) +
                "}");
    }

    private void writeSkinLayersMetadata(int entityId) {
        // Skin layer'lari ac (hat haric - web client'ta hat katmani gokyuzu gibi render ediliyor)
        // 0x3F = 63 = cape + jacket + left sleeve + right sleeve + left pants + right pants (hat haric)
        builder.writeSetupPacket("entity_metadata",
                "{\"entityId\":" + entityId +
                ",\"metadata\":[" +
                "{\"key\":17,\"type\":0,\"value\":126}" +
                "]}");
    }

    // ==================== AKSIYON DONUSTURME ====================

    private void convertAction(ReplayAction action) {
        if (action instanceof LocationAction) {
            convertLocation((LocationAction) action);
        } else if (action instanceof AnimationAction) {
            convertAnimation((AnimationAction) action);
        } else if (action instanceof EntityStateAction) {
            convertEntityState((EntityStateAction) action);
        } else if (action instanceof EquipmentAction) {
            convertEquipment((EquipmentAction) action, action.getTimestamp());
        } else if (action instanceof BlockAction) {
            convertBlock((BlockAction) action);
        } else if (action instanceof HealthAction) {
            convertHealth((HealthAction) action);
        } else if (action instanceof NearbyPlayerAction) {
            convertNearbyPlayer((NearbyPlayerAction) action);
        } else if (action instanceof DamageAction) {
            convertDamage((DamageAction) action);
        } else if (action instanceof VelocityAction) {
            convertVelocity((VelocityAction) action);
        } else if (action instanceof PoseAction) {
            convertPose((PoseAction) action);
        } else if (action instanceof FireAction) {
            convertFire((FireAction) action);
        } else if (action instanceof DeathAction) {
            convertDeath((DeathAction) action);
        } else if (action instanceof ChatAction) {
            convertChat((ChatAction) action);
        } else if (action instanceof SoundAction) {
            convertSound((SoundAction) action);
        } else if (action instanceof ExplosionAction) {
            convertExplosion((ExplosionAction) action);
        } else if (action instanceof TeleportAction) {
            convertTeleport((TeleportAction) action);
        } else if (action instanceof PlayerStateAction) {
            convertPlayerState((PlayerStateAction) action);
        } else if (action instanceof GameModeAction) {
            convertGameMode((GameModeAction) action);
        } else if (action instanceof WeatherAction) {
            convertWeather((WeatherAction) action);
        } else if (action instanceof PlayerInfoAction) {
            convertPlayerInfo((PlayerInfoAction) action);
        } else if (action instanceof UseItemAction) {
            convertUseItem((UseItemAction) action);
        } else if (action instanceof ProjectileAction) {
            convertProjectile((ProjectileAction) action);
        } else if (action instanceof EntitySpawnAction) {
            convertEntitySpawn((EntitySpawnAction) action);
        } else if (action instanceof EntityUpdateAction) {
            convertEntityUpdate((EntityUpdateAction) action);
        } else if (action instanceof PotionEffectAction) {
            convertPotionEffect((PotionEffectAction) action);
        } else if (action instanceof InteractEntityAction) {
            convertInteractEntity((InteractEntityAction) action);
        } else if (action instanceof ItemAction) {
            convertItem((ItemAction) action);
        } else if (action instanceof BucketAction) {
            convertBucket((BucketAction) action);
        } else if (action instanceof FallingBlockAction) {
            convertFallingBlock((FallingBlockAction) action);
        } else if (action instanceof ContainerAction) {
            convertContainer((ContainerAction) action);
        } else if (action instanceof BlockIgniteAction) {
            convertBlockIgnite((BlockIgniteAction) action);
        } else if (action instanceof BedAction) {
            convertBed((BedAction) action);
        } else if (action instanceof PortalAction) {
            convertPortal((PortalAction) action);
        } else if (action instanceof FishingAction) {
            convertFishing((FishingAction) action);
        } else if (action instanceof VehicleAction) {
            convertVehicle((VehicleAction) action);
        } else if (action instanceof EntityDyeAction) {
            convertEntityDye((EntityDyeAction) action);
        } else if (action instanceof HangingAction) {
            convertHanging((HangingAction) action);
        } else if (action instanceof BreedAction) {
            convertBreed((BreedAction) action);
        } else if (action instanceof NoteBlockAction) {
            convertNoteBlock((NoteBlockAction) action);
        } else if (action instanceof SignAction) {
            convertSign((SignAction) action);
        } else if (action instanceof AnvilAction) {
            convertAnvil((AnvilAction) action);
        } else if (action instanceof BrewAction) {
            convertBrew((BrewAction) action);
        } else if (action instanceof CraftAction) {
            convertCraft((CraftAction) action);
        } else if (action instanceof EnchantAction) {
            convertEnchant((EnchantAction) action);
        } else if (action instanceof UtilityBlockAction) {
            convertUtilityBlock((UtilityBlockAction) action);
        } else if (action instanceof BookEditAction) {
            convertBookEdit((BookEditAction) action);
        } else if (action instanceof FarmingAction) {
            convertFarming((FarmingAction) action);
        } else if (action instanceof DecorationAction) {
            convertDecoration((DecorationAction) action);
        } else if (action instanceof RedstoneAction) {
            convertRedstone((RedstoneAction) action);
        } else if (action instanceof EntityCommandAction) {
            convertEntityCommand((EntityCommandAction) action);
        } else if (action instanceof ArmorStandAction) {
            convertArmorStand((ArmorStandAction) action);
        }
    }

    // ==================== TIER 1: TEMEL AKSIYONLAR ====================

    private void convertLocation(LocationAction action) {
        int entityId = entityManager.resolveEntityId(action.getOwnerUUID());
        if (entityId < 0) return;

        double newX = action.getX();
        double newY = action.getY();
        double newZ = action.getZ();
        int yawByte = WebReplayPacketBuilder.angleToByte(action.getYaw());
        int pitchByte = WebReplayPacketBuilder.angleToByte(action.getPitch());

        double[] lastPos = lastPositions.get(entityId);

        if (lastPos != null) {
            double dx = newX - lastPos[0];
            double dy = newY - lastPos[1];
            double dz = newZ - lastPos[2];

            // Kucuk hareket (< 8 blok) -> entity_move_look (akici interpolasyon)
            if (Math.abs(dx) < 8 && Math.abs(dy) < 8 && Math.abs(dz) < 8) {
                // Protokol: delta * 4096 (fixed point short)
                short protoDx = (short) (dx * 4096);
                short protoDy = (short) (dy * 4096);
                short protoDz = (short) (dz * 4096);

                builder.writePacket("entity_move_look", action.getTimestamp(),
                        "{\"entityId\":" + entityId +
                        ",\"dX\":" + protoDx +
                        ",\"dY\":" + protoDy +
                        ",\"dZ\":" + protoDz +
                        ",\"yaw\":" + yawByte +
                        ",\"pitch\":" + pitchByte +
                        ",\"onGround\":" + action.isOnGround() +
                        "}");
            } else {
                // Buyuk hareket (teleport) -> entity_teleport
                builder.writePacket("entity_teleport", action.getTimestamp(),
                        "{\"entityId\":" + entityId +
                        ",\"x\":" + newX +
                        ",\"y\":" + newY +
                        ",\"z\":" + newZ +
                        ",\"yaw\":" + yawByte +
                        ",\"pitch\":" + pitchByte +
                        ",\"onGround\":" + action.isOnGround() +
                        "}");
            }
        } else {
            // Ilk konum -> entity_teleport
            builder.writePacket("entity_teleport", action.getTimestamp(),
                    "{\"entityId\":" + entityId +
                    ",\"x\":" + newX +
                    ",\"y\":" + newY +
                    ",\"z\":" + newZ +
                    ",\"yaw\":" + yawByte +
                    ",\"pitch\":" + pitchByte +
                    ",\"onGround\":" + action.isOnGround() +
                    "}");
        }

        // Pozisyonu kaydet
        lastPositions.put(entityId, new double[]{newX, newY, newZ});

        // Kafa rotasyonu
        builder.writePacket("entity_head_rotation", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"headYaw\":" + yawByte +
                "}");
    }

    private void convertAnimation(AnimationAction action) {
        int entityId = entityManager.resolveEntityId(action.getOwnerUUID());
        if (entityId < 0) return;

        int animId;
        switch (action.getAnimationType()) {
            case SWING_MAIN_HAND: animId = 0; break;
            case TAKE_DAMAGE:     animId = 1; break;
            case SWING_OFF_HAND:  animId = 3; break;
            default: return;
        }

        builder.writePacket("animation", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"animation\":" + animId +
                "}");
    }

    private void convertEntityState(EntityStateAction action) {
        int entityId;
        if (action.isForNearbyPlayer() && action.getEntityUUID() != null) {
            entityId = entityManager.getEntityId(action.getEntityUUID());
            if (entityId < 0) return;
        } else {
            entityId = entityManager.getMainPlayerEntityId();
        }

        // Entity flag'leri guncelle (her entity icin ayri takip)
        byte flags = getEntityFlags(entityId);
        switch (action.getStateType()) {
            case SNEAKING:  flags = setFlag(flags, 0x02, action.isEnabled()); break;
            case SPRINTING: flags = setFlag(flags, 0x08, action.isEnabled()); break;
            case SWIMMING:  flags = setFlag(flags, 0x10, action.isEnabled()); break;
            case FLYING:    flags = setFlag(flags, 0x04, action.isEnabled()); break;
            case GLIDING:   flags = setFlag(flags, 0x80, action.isEnabled()); break;
            case BURNING:   flags = setFlag(flags, 0x01, action.isEnabled()); break;
            case INVISIBLE: flags = setFlag(flags, 0x20, action.isEnabled()); break;
            case GLOWING:   flags = setFlag(flags, 0x40, action.isEnabled()); break;
            default: return;
        }
        setEntityFlags(entityId, flags);

        builder.writePacket("entity_metadata", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"metadata\":[" +
                "{\"key\":0,\"type\":0,\"value\":" + (flags & 0xFF) + "}" +
                "]}");
    }

    private void convertEquipment(EquipmentAction action, long timestamp) {
        int entityId = entityManager.resolveEntityId(action.getOwnerUUID());
        if (entityId < 0) return;

        int slot = equipmentSlotToProtocol(action.getSlot());

        String itemJson;
        if (action.getItemData() != null && action.getItemData().getMaterial() != null) {
            int itemId = WebReplayItemMap.getItemId(action.getItemData().getMaterial());
            if (itemId >= 0) {
                itemJson = "{\"itemCount\":" + action.getItemData().getAmount() +
                           ",\"itemId\":" + itemId +
                           ",\"addedComponentCount\":0" +
                           ",\"removedComponentCount\":0" +
                           ",\"components\":[]" +
                           ",\"removeComponents\":[]}";
            } else {
                itemJson = "{\"itemCount\":0}"; // Bos slot
            }
        } else {
            itemJson = "{\"itemCount\":0}";
        }

        builder.writePacket("entity_equipment", timestamp,
                "{\"entityId\":" + entityId +
                ",\"equipments\":[" +
                "{\"slot\":" + slot + ",\"item\":" + itemJson + "}" +
                "]}");
    }

    private void convertBlock(BlockAction action) {
        long ts = action.getTimestamp();

        switch (action.getActionType()) {
            case PLACE_BLOCK:
                int stateId = WebReplayBlockStateMap.getBlockStateId(action.getBlockType());
                builder.writePacket("block_change", ts,
                        "{\"location\":{\"x\":" + action.getX() +
                        ",\"y\":" + action.getY() +
                        ",\"z\":" + action.getZ() +
                        "},\"type\":" + stateId + "}");
                // Blok koyma sesi
                writeActionSound(ts, "block.stone.place", 1.0f, 0.8f);
                break;

            case STOP_BREAKING:
                if (action.getStage() == 10) { // Tamamen kirildi
                    builder.writePacket("block_change", ts,
                            "{\"location\":{\"x\":" + action.getX() +
                            ",\"y\":" + action.getY() +
                            ",\"z\":" + action.getZ() +
                            "},\"type\":" + WebReplayBlockStateMap.getAirStateId() + "}");
                    // Blok kirma sesi
                    writeActionSound(ts, "block.stone.break", 1.0f, 0.8f);
                }
                break;

            case START_BREAKING:
                // Blok kirma animasyonu (opsiyonel, web client destekleyebilir)
                builder.writePacket("block_break_animation", ts,
                        "{\"entityId\":" + entityManager.getMainPlayerEntityId() +
                        ",\"location\":{\"x\":" + action.getX() +
                        ",\"y\":" + action.getY() +
                        ",\"z\":" + action.getZ() +
                        "},\"destroyStage\":" + action.getStage() + "}");
                break;

            case BREAK_PROGRESS:
                builder.writePacket("block_break_animation", ts,
                        "{\"entityId\":" + entityManager.getMainPlayerEntityId() +
                        ",\"location\":{\"x\":" + action.getX() +
                        ",\"y\":" + action.getY() +
                        ",\"z\":" + action.getZ() +
                        "},\"destroyStage\":" + action.getStage() + "}");
                break;

            case INTERACT_BLOCK:
                if (action.getBlockType() != null) {
                    int interactStateId = WebReplayBlockStateMap.getBlockStateId(action.getBlockType());
                    builder.writePacket("block_change", ts,
                            "{\"location\":{\"x\":" + action.getX() +
                            ",\"y\":" + action.getY() +
                            ",\"z\":" + action.getZ() +
                            "},\"type\":" + interactStateId + "}");

                    // Blok turune gore ses
                    String bt = action.getBlockType().toUpperCase();
                    if (bt.contains("DOOR")) {
                        writeActionSound(ts, "block.wooden_door.open", 1.0f, 1.0f);
                    } else if (bt.contains("TRAPDOOR")) {
                        writeActionSound(ts, "block.wooden_trapdoor.open", 1.0f, 1.0f);
                    } else if (bt.contains("GATE")) {
                        writeActionSound(ts, "block.fence_gate.open", 1.0f, 1.0f);
                    } else if (bt.contains("BUTTON")) {
                        writeActionSound(ts, "block.stone_button.click_on", 0.3f, 0.6f);
                    } else if (bt.contains("LEVER")) {
                        writeActionSound(ts, "block.lever.click", 0.3f, 0.6f);
                    }
                }
                builder.writePacket("animation", ts,
                        "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");
                break;

            default:
                break;
        }
    }

    private void convertHealth(HealthAction action) {
        int entityId = entityManager.resolveEntityId(action.getOwnerUUID());
        if (entityId < 0) return;

        // Entity metadata ile saglik goster
        StringBuilder metadata = new StringBuilder("[");
        metadata.append("{\"key\":9,\"type\":3,\"value\":").append(action.getHealth()).append("}");

        // Absorption (altin kalpler) varsa ekle - index 15 (Player entity'de absorption)
        if (action.getAbsorption() > 0) {
            metadata.append(",{\"key\":15,\"type\":3,\"value\":").append(action.getAbsorption()).append("}");
        }
        metadata.append("]");

        builder.writePacket("entity_metadata", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"metadata\":" + metadata +
                "}");

        // Ana oyuncu icin HUD saglik guncelle + kalp skoru
        if (action.isMainPlayer()) {
            builder.writePacket("update_health", action.getTimestamp(),
                    "{\"health\":" + action.getHealth() +
                    ",\"food\":" + action.getFoodLevel() +
                    ",\"foodSaturation\":" + action.getSaturation() +
                    "}");

            // Isim altindaki kalp gostergesi guncelle
            updateHealthScore(recordedPlayerName, (float) action.getHealth(),
                    (float) action.getAbsorption(), action.getTimestamp());
        }
    }

    private void convertNearbyPlayer(NearbyPlayerAction action) {
        switch (action.getActionType()) {
            case PLAYER_APPEAR: {
                UUID uuid = action.getPlayerUuid();
                int entityId = entityManager.getOrAssignEntityId(uuid);
                String uuidStr = uuid.toString();

                // player_info - tab listesine ekle (skin ile)
                StringBuilder props = new StringBuilder("[");
                if (action.getSkinTexture() != null && !action.getSkinTexture().isEmpty()) {
                    props.append("{\"name\":\"textures\",\"value\":\"")
                         .append(escapeJson(action.getSkinTexture()))
                         .append("\"");
                    if (action.getSkinSignature() != null) {
                        props.append(",\"signature\":\"")
                             .append(escapeJson(action.getSkinSignature()))
                             .append("\"");
                    }
                    props.append("}");
                }
                props.append("]");

                String name = action.getPlayerName() != null ? action.getPlayerName() : "Player";

                builder.writePacket("player_info", action.getTimestamp(),
                        "{\"action\":" +
                        "{\"add_player\":true" +
                        ",\"initialize_chat\":false" +
                        ",\"update_game_mode\":true" +
                        ",\"update_listed\":true" +
                        ",\"update_latency\":true" +
                        ",\"update_display_name\":false}" +
                        ",\"data\":[{" +
                        "\"uuid\":\"" + uuidStr + "\"" +
                        ",\"player\":{\"name\":\"" + escapeJson(name) + "\",\"properties\":" + props + "}" +
                        ",\"gamemode\":0" +
                        ",\"listed\":true" +
                        ",\"latency\":0" +
                        "}]}");

                // spawn_entity
                builder.writePacket("spawn_entity", action.getTimestamp(),
                        "{\"entityId\":" + entityId +
                        ",\"objectUUID\":\"" + uuidStr + "\"" +
                        ",\"type\":" + getPlayerEntityType() +
                        ",\"x\":" + action.getX() +
                        ",\"y\":" + action.getY() +
                        ",\"z\":" + action.getZ() +
                        ",\"pitch\":" + WebReplayPacketBuilder.angleToByte(action.getPitch()) +
                        ",\"yaw\":" + WebReplayPacketBuilder.angleToByte(action.getYaw()) +
                        ",\"objectData\":0" +
                        ",\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0" +
                        "}");

                // Kafa rotasyonu ayri paket
                builder.writePacket("entity_head_rotation", action.getTimestamp(),
                        "{\"entityId\":" + entityId +
                        ",\"headYaw\":" + WebReplayPacketBuilder.angleToByte(action.getYaw()) +
                        "}");

                // Skin layer'lari ac
                builder.writePacket("entity_metadata", action.getTimestamp(),
                        "{\"entityId\":" + entityId +
                        ",\"metadata\":[" +
                        "{\"key\":17,\"type\":0,\"value\":126}" +
                        "]}");

                // Gri takim rengi (nearby player = gri, ana oyuncu = kirmizi)
                String nearbyTeam = "rs_np_" + entityId;
                builder.writePacket("teams", action.getTimestamp(),
                        "{\"team\":\"" + nearbyTeam + "\"" +
                        ",\"mode\":0" +
                        ",\"name\":\"{\\\"text\\\":\\\"" + nearbyTeam + "\\\"}\"" +
                        ",\"prefix\":\"{\\\"text\\\":\\\"\\\"}\"" +
                        ",\"suffix\":\"{\\\"text\\\":\\\"\\\"}\"" +
                        ",\"friendlyFire\":true" +
                        ",\"nameTagVisibility\":\"always\"" +
                        ",\"collisionRule\":\"never\"" +
                        ",\"color\":7" + // GRAY
                        ",\"players\":[\"" + escapeJson(name) + "\"]}");

                // Equipment varsa gonder
                if (action.getEquipment() != null) {
                    convertNearbyEquipment(entityId, action, action.getTimestamp());
                }
                break;
            }

            case PLAYER_MOVE: {
                int entityId = entityManager.getEntityId(action.getPlayerUuid());
                if (entityId < 0) return;

                double newX = action.getX();
                double newY = action.getY();
                double newZ = action.getZ();
                int yawByte = WebReplayPacketBuilder.angleToByte(action.getYaw());
                int pitchByte = WebReplayPacketBuilder.angleToByte(action.getPitch());

                double[] lastPos = lastPositions.get(entityId);

                if (lastPos != null) {
                    double dx = newX - lastPos[0];
                    double dy = newY - lastPos[1];
                    double dz = newZ - lastPos[2];

                    if (Math.abs(dx) < 8 && Math.abs(dy) < 8 && Math.abs(dz) < 8) {
                        builder.writePacket("entity_move_look", action.getTimestamp(),
                                "{\"entityId\":" + entityId +
                                ",\"dX\":" + (short)(dx * 4096) +
                                ",\"dY\":" + (short)(dy * 4096) +
                                ",\"dZ\":" + (short)(dz * 4096) +
                                ",\"yaw\":" + yawByte +
                                ",\"pitch\":" + pitchByte +
                                ",\"onGround\":true}");
                    } else {
                        builder.writePacket("entity_teleport", action.getTimestamp(),
                                "{\"entityId\":" + entityId +
                                ",\"x\":" + newX + ",\"y\":" + newY + ",\"z\":" + newZ +
                                ",\"yaw\":" + yawByte + ",\"pitch\":" + pitchByte +
                                ",\"onGround\":true}");
                    }
                } else {
                    builder.writePacket("entity_teleport", action.getTimestamp(),
                            "{\"entityId\":" + entityId +
                            ",\"x\":" + newX + ",\"y\":" + newY + ",\"z\":" + newZ +
                            ",\"yaw\":" + yawByte + ",\"pitch\":" + pitchByte +
                            ",\"onGround\":true}");
                }

                lastPositions.put(entityId, new double[]{newX, newY, newZ});

                builder.writePacket("entity_head_rotation", action.getTimestamp(),
                        "{\"entityId\":" + entityId +
                        ",\"headYaw\":" + yawByte + "}");
                break;
            }

            case PLAYER_DISAPPEAR: {
                int entityId = entityManager.getEntityId(action.getPlayerUuid());
                if (entityId < 0) return;

                builder.writePacket("entity_destroy", action.getTimestamp(),
                        "{\"entityIds\":[" + entityId + "]}");

                builder.writePacket("player_remove", action.getTimestamp(),
                        "{\"players\":[\"" + action.getPlayerUuid() + "\"]}");

                entityFlags.remove(entityId);
                lastPositions.remove(entityId);
                entityManager.removeEntity(action.getPlayerUuid());
                break;
            }
        }
    }

    // ==================== TIER 2: ONEMLI AKSIYONLAR ====================

    private void convertDamage(DamageAction action) {
        int entityId = entityManager.resolveEntityId(action.getOwnerUUID());
        if (entityId < 0) return;

        // Hasar animasyonu
        builder.writePacket("animation", action.getTimestamp(),
                "{\"entityId\":" + entityId + ",\"animation\":1}");

        // Entity status - hurt
        builder.writePacket("entity_status", action.getTimestamp(),
                "{\"entityId\":" + entityId + ",\"entityStatus\":2}");
        // Ses: SoundAction olarak kayit sisteminden otomatik gelir
    }

    private void convertVelocity(VelocityAction action) {
        int entityId = entityManager.resolveEntityId(action.getOwnerUUID());
        if (entityId < 0) return;

        builder.writePacket("entity_velocity", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"velocityX\":" + WebReplayPacketBuilder.velocityToProtocol(action.getVelocityX()) +
                ",\"velocityY\":" + WebReplayPacketBuilder.velocityToProtocol(action.getVelocityY()) +
                ",\"velocityZ\":" + WebReplayPacketBuilder.velocityToProtocol(action.getVelocityZ()) +
                "}");
    }

    private void convertPose(PoseAction action) {
        int entityId = entityManager.resolveEntityId(action.getOwnerUUID());
        if (entityId < 0) return;

        int poseId;
        switch (action.getPoseType()) {
            case STANDING:      poseId = 0; break;
            case FALL_FLYING:   poseId = 1; break;
            case SLEEPING:      poseId = 2; break;
            case SWIMMING:      poseId = 3; break;
            case SPIN_ATTACK:   poseId = 4; break;
            case SNEAKING:      poseId = 5; break;
            case DYING:         poseId = 7; break;
            default:            poseId = 0; break;
        }

        builder.writePacket("entity_metadata", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"metadata\":[" +
                "{\"key\":6,\"type\":20,\"value\":" + poseId + "}" +
                "]}");
    }

    private void convertFire(FireAction action) {
        int entityId = entityManager.resolveEntityId(action.getOwnerUUID());
        if (entityId < 0) return;

        byte flags = setFlag(getEntityFlags(entityId), 0x01, action.isOnFire());
        setEntityFlags(entityId, flags);

        builder.writePacket("entity_metadata", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"metadata\":[" +
                "{\"key\":0,\"type\":0,\"value\":" + (flags & 0xFF) + "}" +
                "]}");
    }

    private void convertDeath(DeathAction action) {
        int entityId = entityManager.getMainPlayerEntityId();

        // Olum entity_status (olum animasyonu tetikler)
        builder.writePacket("entity_status", action.getTimestamp(),
                "{\"entityId\":" + entityId + ",\"entityStatus\":3}");

        // Olum pozu
        builder.writePacket("entity_metadata", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"metadata\":[" +
                "{\"key\":6,\"type\":20,\"value\":7}" + // DYING pose
                "]}");

        // Olum sesi
        builder.writePacket("sound_effect", action.getTimestamp(),
                "{\"sound\":{\"data\":{\"soundName\":\"minecraft:entity.player.death\"}}" +
                ",\"soundCategory\":0" +
                ",\"x\":" + (int)(lastPositions.containsKey(entityId) ? lastPositions.get(entityId)[0] * 8 : 0) +
                ",\"y\":" + (int)(lastPositions.containsKey(entityId) ? lastPositions.get(entityId)[1] * 8 : 512) +
                ",\"z\":" + (int)(lastPositions.containsKey(entityId) ? lastPositions.get(entityId)[2] * 8 : 0) +
                ",\"volume\":1.0,\"pitch\":1.0,\"seed\":[0,0]}");

        // Olum mesaji
        if (action.getDeathMessage() != null) {
            builder.writePacket("system_chat", action.getTimestamp(),
                    "{\"content\":" + toJsonTextComponent(action.getDeathMessage()) +
                    ",\"isActionBar\":false}");
        }
    }

    private void convertChat(ChatAction action) {
        if (action.isCommand()) return; // Komutlari gosterme

        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":" + toJsonTextComponent(action.getMessage()) +
                ",\"isActionBar\":false}");
    }

    private void convertSound(SoundAction action) {
        String soundName = action.getSoundName();
        if (soundName == null) return;

        // Ses adi donusumu:
        // 1. "minecraft:entity.player.hurt" -> aynen kullan (PacketEvents'ten)
        // 2. "ENTITY_PLAYER_HURT" -> Bukkit Sound enum ile dogru resource location'a cevir
        if (soundName.contains(":")) {
            // Zaten resource location formatinda (PacketEvents recording)
            soundName = soundName.toLowerCase();
        } else {
            // Bukkit Sound enum adi (SoundListener'dan) - getKey() ile dogru MC ismine cevir
            // replace("_", ".") YANLIS: "BLOCK_WOODEN_DOOR_OPEN" -> "block.wooden.door.open"
            // Dogru: "minecraft:block.wooden_door.open"
            try {
                org.bukkit.Sound bukkitSound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                soundName = bukkitSound.getKey().toString();
            } catch (Exception e) {
                // Enum bulunamazsa fallback (imperfect)
                soundName = "minecraft:" + soundName.toLowerCase().replace("_", ".");
            }
        }

        builder.writePacket("sound_effect", action.getTimestamp(),
                "{\"sound\":{\"data\":{\"soundName\":\"" + soundName + "\"}}" +
                ",\"soundCategory\":0" +
                ",\"x\":" + (int)(action.getX() * 8) +
                ",\"y\":" + (int)(action.getY() * 8) +
                ",\"z\":" + (int)(action.getZ() * 8) +
                ",\"volume\":" + action.getVolume() +
                ",\"pitch\":" + action.getPitch() +
                ",\"seed\":[0,0]" +
                "}");
    }

    private void convertExplosion(ExplosionAction action) {
        long ts = action.getTimestamp();

        // Patlama sesi
        builder.writePacket("sound_effect", ts,
                "{\"sound\":{\"data\":{\"soundName\":\"minecraft:entity.generic.explode\"}}" +
                ",\"soundCategory\":0" +
                ",\"x\":" + (int)(action.getX() * 8) +
                ",\"y\":" + (int)(action.getY() * 8) +
                ",\"z\":" + (int)(action.getZ() * 8) +
                ",\"volume\":2.0,\"pitch\":1.0,\"seed\":[0,0]}");

        // Patlama world_event (gorsel efekt)
        // world_event 2003 = explosion effect
        builder.writePacket("world_event", ts,
                "{\"effectId\":2003" +
                ",\"location\":{\"x\":" + (int) action.getX() +
                ",\"y\":" + (int) action.getY() +
                ",\"z\":" + (int) action.getZ() + "}" +
                ",\"data\":0,\"global\":false}");
    }

    private void convertTeleport(TeleportAction action) {
        int entityId = entityManager.getMainPlayerEntityId();
        builder.writePacket("entity_teleport", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"x\":" + action.getToX() +
                ",\"y\":" + action.getToY() +
                ",\"z\":" + action.getToZ() +
                ",\"yaw\":0,\"pitch\":0,\"onGround\":true" +
                "}");
    }

    private void convertPlayerState(PlayerStateAction action) {
        int entityId = entityManager.getMainPlayerEntityId();
        byte flags = getEntityFlags(entityId);

        switch (action.getStateType()) {
            case SNEAK:
                flags = setFlag(flags, 0x02, action.isEnabled());
                break;
            case SPRINT:
                flags = setFlag(flags, 0x08, action.isEnabled());
                break;
            case GLIDE:
                flags = setFlag(flags, 0x80, action.isEnabled());
                break;
            default:
                return;
        }
        setEntityFlags(entityId, flags);

        builder.writePacket("entity_metadata", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"metadata\":[" +
                "{\"key\":0,\"type\":0,\"value\":" + (flags & 0xFF) + "}" +
                "]}");
    }

    private void convertGameMode(GameModeAction action) {
        int mode;
        switch (action.getGameMode()) {
            case SURVIVAL:  mode = 0; break;
            case CREATIVE:  mode = 1; break;
            case ADVENTURE: mode = 2; break;
            case SPECTATOR: mode = 3; break;
            default: return;
        }

        builder.writePacket("game_state_change", action.getTimestamp(),
                "{\"reason\":3,\"gameMode\":" + mode + ".0}");
    }

    private void convertWeather(WeatherAction action) {
        switch (action.getWeatherType()) {
            case RAIN:
                builder.writePacket("game_state_change", action.getTimestamp(),
                        "{\"reason\":2,\"gameMode\":0}"); // Begin raining
                break;
            case CLEAR:
                builder.writePacket("game_state_change", action.getTimestamp(),
                        "{\"reason\":1,\"gameMode\":0}"); // End raining
                break;
            case THUNDER:
                builder.writePacket("game_state_change", action.getTimestamp(),
                        "{\"reason\":2,\"gameMode\":0}");
                builder.writePacket("game_state_change", action.getTimestamp(),
                        "{\"reason\":8,\"gameMode\":1.0}"); // Thunder level
                break;
        }
    }

    private void convertPlayerInfo(PlayerInfoAction action) {
        // Skin guncelleme (oyun ici skin degisikligi - nadir)
        // Ilk PlayerInfoAction zaten setup'ta islendi, burada sadece sonrakileri isle
    }

    // ==================== TIER 3: EK AKSIYONLAR ====================

    private void convertUseItem(UseItemAction action) {
        int entityId = entityManager.getMainPlayerEntityId();
        long ts = action.getTimestamp();
        byte flags = getEntityFlags(entityId);

        if (action.isStarted()) {
            // Item kullanma basladi: entity flag 0x10 (USING_ITEM) + hand metadata
            // Oyun icindeki ReplayActionPlayer ile birebir ayni
            flags = setFlag(flags, 0x10, true); // USING_ITEM flag
            setEntityFlags(entityId, flags);

            // Hand bilgisi: metadata index 8 = 0x01 (main) veya 0x02 (off)
            byte handByte = (byte) (action.isMainHand() ? 0x01 : 0x02);
            builder.writePacket("entity_metadata", ts,
                    "{\"entityId\":" + entityId +
                    ",\"metadata\":[" +
                    "{\"key\":0,\"type\":0,\"value\":" + (flags & 0xFF) + "}" +
                    ",{\"key\":8,\"type\":0,\"value\":" + handByte + "}" +
                    "]}");
        } else {
            // Item kullanma bitti: flag'i temizle
            flags = setFlag(flags, 0x10, false);
            setEntityFlags(entityId, flags);

            builder.writePacket("entity_metadata", ts,
                    "{\"entityId\":" + entityId +
                    ",\"metadata\":[" +
                    "{\"key\":0,\"type\":0,\"value\":" + (flags & 0xFF) + "}" +
                    "]}");

            // Tur bazli birakma sesi + animasyonu
            UseItemAction.UseType type = action.getUseType();
            if (type == UseItemAction.UseType.BOW_CHARGE) {
                writeActionSound(ts, "entity.arrow.shoot", 1.0f, 1.0f);
                builder.writePacket("animation", ts, "{\"entityId\":" + entityId + ",\"animation\":0}");
            } else if (type == UseItemAction.UseType.CROSSBOW_CHARGE) {
                writeActionSound(ts, "item.crossbow.shoot", 1.0f, 1.0f);
                builder.writePacket("animation", ts, "{\"entityId\":" + entityId + ",\"animation\":0}");
            } else if (type == UseItemAction.UseType.TRIDENT_CHARGE) {
                writeActionSound(ts, "item.trident.throw", 1.0f, 1.0f);
                builder.writePacket("animation", ts, "{\"entityId\":" + entityId + ",\"animation\":0}");
            } else if (type == UseItemAction.UseType.FOOD_EAT) {
                writeActionSound(ts, "entity.player.burp", 0.5f, 1.0f);
            } else if (type == UseItemAction.UseType.POTION_DRINK || type == UseItemAction.UseType.MILK_DRINK) {
                writeActionSound(ts, "entity.generic.drink", 0.5f, 1.0f);
            } else if (type == UseItemAction.UseType.SHIELD_BLOCK) {
                writeActionSound(ts, "item.shield.block", 1.0f, 1.0f);
            }
        }
    }

    private void convertProjectile(ProjectileAction action) {
        // Mermi entity'si olustur (sayac ile UUID cakismasini onle)
        int projectileEntityId = entityManager.getOrAssignEntityId(
                UUID.nameUUIDFromBytes(("projectile_" + action.getTimestamp() + "_" + entityCounter).getBytes()));

        // NMS ile dogru entity type ID'sini al (versiyona bagli)
        String nmsName;
        switch (action.getType()) {
            case ARROW:             nmsName = "arrow"; break;
            case SNOWBALL:          nmsName = "snowball"; break;
            case EGG:               nmsName = "egg"; break;
            case ENDER_PEARL:       nmsName = "ender_pearl"; break;
            case EXPERIENCE_BOTTLE: nmsName = "experience_bottle"; break;
            case SPLASH_POTION:
            case LINGERING_POTION:  nmsName = "potion"; break;
            case TRIDENT:           nmsName = "trident"; break;
            case FISHING_HOOK:      nmsName = "fishing_bobber"; break;
            case FIREWORK_ROCKET:   nmsName = "firework_rocket"; break;
            default:                nmsName = "arrow"; break;
        }
        int entityTypeId = getEntityTypeId(nmsName);
        if (entityTypeId < 0) entityTypeId = 4; // arrow fallback

        // Merminin baslangic konumu = ana oyuncunun son konumu
        double[] pos = lastPositions.get(entityManager.getMainPlayerEntityId());
        double x = pos != null ? pos[0] : 0;
        double y = pos != null ? pos[1] + 1.5 : 64; // goz hizasi
        double z = pos != null ? pos[2] : 0;

        String projectileUuid = UUID.nameUUIDFromBytes(
                ("proj_" + action.getTimestamp() + "_" + entityCounter).getBytes()).toString();
        entityCounter++;

        builder.writePacket("spawn_entity", action.getTimestamp(),
                "{\"entityId\":" + projectileEntityId +
                ",\"objectUUID\":\"" + projectileUuid + "\"" +
                ",\"type\":" + entityTypeId +
                ",\"x\":" + x +
                ",\"y\":" + y +
                ",\"z\":" + z +
                ",\"pitch\":" + WebReplayPacketBuilder.angleToByte(action.getPitch()) +
                ",\"yaw\":" + WebReplayPacketBuilder.angleToByte(action.getYaw()) +
                ",\"objectData\":" + entityManager.getMainPlayerEntityId() +
                ",\"velocityX\":" + WebReplayPacketBuilder.velocityToProtocol(action.getVelocityX()) +
                ",\"velocityY\":" + WebReplayPacketBuilder.velocityToProtocol(action.getVelocityY()) +
                ",\"velocityZ\":" + WebReplayPacketBuilder.velocityToProtocol(action.getVelocityZ()) +
                "}");

        // Kol sallanma animasyonu + ses
        builder.writePacket("animation", action.getTimestamp(),
                "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");

        // Mermi turune gore ses
        switch (action.getType()) {
            case ARROW: writeActionSound(action.getTimestamp(), "entity.arrow.shoot", 1.0f, 1.0f); break;
            case SNOWBALL: writeActionSound(action.getTimestamp(), "entity.snowball.throw", 0.5f, 0.4f); break;
            case EGG: writeActionSound(action.getTimestamp(), "entity.egg.throw", 0.5f, 0.4f); break;
            case ENDER_PEARL: writeActionSound(action.getTimestamp(), "entity.ender_pearl.throw", 0.5f, 0.4f); break;
            case SPLASH_POTION:
            case LINGERING_POTION: writeActionSound(action.getTimestamp(), "entity.splash_potion.throw", 0.5f, 0.4f); break;
            case TRIDENT: writeActionSound(action.getTimestamp(), "item.trident.throw", 1.0f, 1.0f); break;
            case FIREWORK_ROCKET: writeActionSound(action.getTimestamp(), "entity.firework_rocket.launch", 1.0f, 1.0f); break;
            default: break;
        }
    }

    private void convertEntitySpawn(EntitySpawnAction action) {
        if (action.getEntityUuid() == null) return;
        int entityId = entityManager.getOrAssignEntityId(action.getEntityUuid());

        int entityTypeId = getEntityTypeId(action.getEntityType());
        if (entityTypeId < 0) return; // Bilinmeyen entity tipi

        builder.writePacket("spawn_entity", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"objectUUID\":\"" + action.getEntityUuid() + "\"" +
                ",\"type\":" + entityTypeId +
                ",\"x\":" + action.getX() +
                ",\"y\":" + action.getY() +
                ",\"z\":" + action.getZ() +
                ",\"pitch\":" + WebReplayPacketBuilder.angleToByte(action.getPitch()) +
                ",\"yaw\":" + WebReplayPacketBuilder.angleToByte(action.getYaw()) +
                ",\"objectData\":0" +
                ",\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0" +
                "}");
    }

    private void convertEntityUpdate(EntityUpdateAction action) {
        if (action.getEntityUuid() == null) return;
        int entityId = entityManager.getEntityId(action.getEntityUuid());
        if (entityId < 0) return; // Spawn edilmemis entity

        builder.writePacket("entity_teleport", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"x\":" + action.getX() +
                ",\"y\":" + action.getY() +
                ",\"z\":" + action.getZ() +
                ",\"yaw\":" + WebReplayPacketBuilder.angleToByte(action.getYaw()) +
                ",\"pitch\":" + WebReplayPacketBuilder.angleToByte(action.getPitch()) +
                ",\"onGround\":true}");
    }

    private void convertPotionEffect(PotionEffectAction action) {
        int entityId = entityManager.getMainPlayerEntityId();
        long ts = action.getTimestamp();

        switch (action.getActionType()) {
            case ADD:
                int effectId = getPotionEffectId(action.getEffectType());
                if (effectId < 0) return;
                builder.writePacket("entity_effect", ts,
                        "{\"entityId\":" + entityId +
                        ",\"effectId\":" + effectId +
                        ",\"amplifier\":" + action.getAmplifier() +
                        ",\"duration\":" + action.getDuration() +
                        ",\"hideParticles\":" + !action.hasParticles() +
                        "}");
                break;
            case REMOVE:
                int removeId = getPotionEffectId(action.getEffectType());
                if (removeId < 0) return;
                builder.writePacket("remove_entity_effect", ts,
                        "{\"entityId\":" + entityId +
                        ",\"effectId\":" + removeId + "}");
                break;
            case CLEAR_ALL:
                // Bilinen etkileri kaldir (hepsini tek tek)
                break;
        }
    }

    private void convertInteractEntity(InteractEntityAction action) {
        // Ana oyuncunun kol sallanmasini goster
        int mainId = entityManager.getMainPlayerEntityId();
        int animId = (action.getType() == InteractEntityAction.InteractionType.LEFT_CLICK) ? 0 : 0;
        builder.writePacket("animation", action.getTimestamp(),
                "{\"entityId\":" + mainId + ",\"animation\":" + animId + "}");
    }

    private void convertItem(ItemAction action) {
        long ts = action.getTimestamp();
        if (action.getActionType() == ItemAction.ItemActionType.DROP) {
            // Item entity spawn et
            // NOT: ItemListener velocity olarak kaydetmis (x=velX, y=velY, z=velZ)
            int itemEntityId = entityManager.getOrAssignEntityId(
                    UUID.nameUUIDFromBytes(("item_" + ts + "_" + entityCounter).getBytes()));
            String uuid = UUID.nameUUIDFromBytes(
                    ("itm_" + ts + "_" + entityCounter).getBytes()).toString();
            entityCounter++;

            int itemTypeId = getEntityTypeId("item");
            if (itemTypeId < 0) itemTypeId = 55; // fallback

            // Oyuncunun son pozisyonundan spawn
            double[] pos = lastPositions.get(entityManager.getMainPlayerEntityId());
            double x = pos != null ? pos[0] : 0;
            double y = pos != null ? pos[1] + 1.3 : 64;
            double z = pos != null ? pos[2] : 0;

            // Velocity (ItemListener'dan)
            double velX = action.getX();
            double velY = action.getY();
            double velZ = action.getZ();

            builder.writePacket("spawn_entity", ts,
                    "{\"entityId\":" + itemEntityId +
                    ",\"objectUUID\":\"" + uuid + "\"" +
                    ",\"type\":" + itemTypeId +
                    ",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z +
                    ",\"pitch\":0,\"yaw\":0,\"objectData\":0" +
                    ",\"velocityX\":" + WebReplayPacketBuilder.velocityToProtocol(velX) +
                    ",\"velocityY\":" + WebReplayPacketBuilder.velocityToProtocol(velY) +
                    ",\"velocityZ\":" + WebReplayPacketBuilder.velocityToProtocol(velZ) +
                    "}");

            // ItemStack deserialize et ve item metadata gonder (gercek item gorunsun)
            if (action.getItemData() != null && !action.getItemData().isEmpty()) {
                try {
                    org.bukkit.inventory.ItemStack itemStack =
                            ItemSerializer.itemStackFromBase64(action.getItemData());
                    if (itemStack != null) {
                        int itemId = WebReplayItemMap.getItemId(itemStack.getType().name());
                        if (itemId >= 0) {
                            builder.writePacket("entity_metadata", ts,
                                    "{\"entityId\":" + itemEntityId +
                                    ",\"metadata\":[{\"key\":8,\"type\":7,\"value\":" +
                                    "{\"itemCount\":" + itemStack.getAmount() +
                                    ",\"itemId\":" + itemId +
                                    ",\"addedComponentCount\":0,\"removedComponentCount\":0" +
                                    ",\"components\":[],\"removeComponents\":[]}}]}");
                        }
                    }
                } catch (Exception e) {
                    // Deserialize edilemezse entity yine de gorunur (metadata olmadan)
                }
            }

            // Fizik simulasyonu: web client velocity simule etmiyor, biz trajectory hesaplariz
            // Minecraft item fizigi: gravity=0.04, drag=0.98 per tick
            double simX = x, simY = y, simZ = z;
            double simVx = velX, simVy = velY, simVz = velZ;
            for (int tick = 1; tick <= 20; tick++) {
                simX += simVx;
                simY += simVy;
                simZ += simVz;
                simVy -= 0.04; // yercekimi
                simVx *= 0.98; // surtunme
                simVy *= 0.98;
                simVz *= 0.98;
                // Yere dustuyse dur (baslangic Y'nin 3 blok altina inmesin)
                if (simVy < 0 && simY < y - 3) {
                    simY = y - 3;
                    break;
                }
                builder.writePacket("entity_teleport", ts + (tick * 50),
                        "{\"entityId\":" + itemEntityId +
                        ",\"x\":" + simX + ",\"y\":" + simY + ",\"z\":" + simZ +
                        ",\"yaw\":0,\"pitch\":0,\"onGround\":" + (simVy <= 0 && tick > 10) +
                        "}");
            }

            // Kol sallama animasyonu
            builder.writePacket("animation", ts,
                    "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");

        } else if (action.getActionType() == ItemAction.ItemActionType.PICKUP) {
            // Item toplama - collect paketi
            int entityId = entityManager.getMainPlayerEntityId();
            builder.writePacket("collect", ts,
                    "{\"collectedEntityId\":0" +
                    ",\"collectorEntityId\":" + entityId +
                    ",\"pickupItemCount\":" + action.getAmount() + "}");
        }
    }

    private void convertBucket(BucketAction action) {
        long ts = action.getTimestamp();
        int x = action.getBlockX();
        int y = action.getBlockY();
        int z = action.getBlockZ();

        if (action.getActionType() == BucketAction.ActionType.EMPTY) {
            // Kova bosaltma - sivi blogu yerlestir
            int stateId;
            switch (action.getBucketType()) {
                case WATER:       stateId = WebReplayBlockStateMap.getBlockStateId("WATER"); break;
                case LAVA:        stateId = WebReplayBlockStateMap.getBlockStateId("LAVA"); break;
                case POWDER_SNOW: stateId = WebReplayBlockStateMap.getBlockStateId("POWDER_SNOW"); break;
                default:          return;
            }
            builder.writePacket("block_change", ts,
                    "{\"location\":{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z +
                    "},\"type\":" + stateId + "}");
        } else if (action.getActionType() == BucketAction.ActionType.FILL) {
            // Kova doldurma - sivi blogu kaldir (air yap)
            builder.writePacket("block_change", ts,
                    "{\"location\":{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z +
                    "},\"type\":" + WebReplayBlockStateMap.getAirStateId() + "}");
        }
    }

    private void convertFallingBlock(FallingBlockAction action) {
        int entityId = entityManager.getOrAssignEntityId(
                UUID.nameUUIDFromBytes(("falling_" + action.getTimestamp() + "_" + entityCounter).getBytes()));

        int stateId = WebReplayBlockStateMap.getBlockStateId(action.getBlockType());
        String uuid = UUID.nameUUIDFromBytes(
                ("fb_" + action.getTimestamp() + "_" + entityCounter).getBytes()).toString();
        entityCounter++;

        int fbTypeId = getEntityTypeId("falling_block");
        if (fbTypeId < 0) fbTypeId = 40;
        builder.writePacket("spawn_entity", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"objectUUID\":\"" + uuid + "\"" +
                ",\"type\":" + fbTypeId +
                ",\"x\":" + action.getX() +
                ",\"y\":" + action.getY() +
                ",\"z\":" + action.getZ() +
                ",\"pitch\":0,\"yaw\":0" +
                ",\"objectData\":" + stateId +
                ",\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0}");
    }

    private void convertContainer(ContainerAction action) {
        long ts = action.getTimestamp();
        int actionId = action.isOpened() ? 1 : 0;

        // Block registry ID (state ID degil!) - web client animasyon icin buna bakar
        int blockId;
        if (action.getContainerType() == ContainerAction.ContainerType.ENDER_CHEST) {
            blockId = getBlockRegistryId("ENDER_CHEST");
        } else {
            blockId = getBlockRegistryId("CHEST");
        }

        builder.writePacket("block_action", ts,
                "{\"location\":{\"x\":" + action.getX() + ",\"y\":" + action.getY() + ",\"z\":" + action.getZ() + "}" +
                ",\"byte1\":1,\"byte2\":" + actionId +
                ",\"blockId\":" + blockId + "}");

        // Sandik/ender chest sesi
        if (action.isOpened()) {
            if (action.getContainerType() == ContainerAction.ContainerType.ENDER_CHEST) {
                writeActionSound(ts, "block.ender_chest.open", 0.5f, 1.0f);
            } else {
                writeActionSound(ts, "block.chest.open", 0.5f, 1.0f);
            }
        } else {
            writeActionSound(ts, "block.chest.close", 0.5f, 1.0f);
        }
    }

    private void convertBlockIgnite(BlockIgniteAction action) {
        int fireStateId = 2391;
        builder.writePacket("block_change", action.getTimestamp(),
                "{\"location\":{\"x\":" + action.getX() + ",\"y\":" + action.getY() + ",\"z\":" + action.getZ() +
                "},\"type\":" + fireStateId + "}");
        // Cakmak sesi
        if (action.getIgniteType() == BlockIgniteAction.IgniteType.FLINT_AND_STEEL) {
            writeActionSound(action.getTimestamp(), "item.flintandsteel.use", 1.0f, 1.0f);
        }
    }

    private void convertBed(BedAction action) {
        int entityId = entityManager.getMainPlayerEntityId();
        if (action.getActionType() == BedAction.ActionType.ENTER_BED) {
            // Uyuma pozu
            builder.writePacket("entity_metadata", action.getTimestamp(),
                    "{\"entityId\":" + entityId +
                    ",\"metadata\":[{\"key\":6,\"type\":20,\"value\":2}]}"); // SLEEPING pose
        } else {
            // Yataktan kalkma
            builder.writePacket("entity_metadata", action.getTimestamp(),
                    "{\"entityId\":" + entityId +
                    ",\"metadata\":[{\"key\":6,\"type\":20,\"value\":0}]}"); // STANDING pose
        }
    }

    private void convertPortal(PortalAction action) {
        int entityId = entityManager.getMainPlayerEntityId();
        builder.writePacket("entity_teleport", action.getTimestamp(),
                "{\"entityId\":" + entityId +
                ",\"x\":" + action.getToX() +
                ",\"y\":" + action.getToY() +
                ",\"z\":" + action.getToZ() +
                ",\"yaw\":0,\"pitch\":0,\"onGround\":true}");
    }

    private void convertFishing(FishingAction action) {
        long ts = action.getTimestamp();
        if (action.getState() == FishingAction.FishingState.CAST) {
            // Onceki hook varsa temizle
            if (lastHookEntityId >= 0) {
                builder.writePacket("entity_destroy", ts,
                        "{\"entityIds\":[" + lastHookEntityId + "]}");
            }

            // Olta atma - hook entity olustur
            int hookId = entityManager.getOrAssignEntityId(
                    UUID.nameUUIDFromBytes(("hook_" + ts + "_" + entityCounter).getBytes()));
            String uuid = UUID.nameUUIDFromBytes(("fh_" + ts + "_" + entityCounter).getBytes()).toString();
            entityCounter++;
            lastHookEntityId = hookId;

            builder.writePacket("spawn_entity", ts,
                    "{\"entityId\":" + hookId +
                    ",\"objectUUID\":\"" + uuid + "\"" +
                    ",\"type\":" + getEntityTypeId("fishing_bobber") +
                    ",\"x\":" + action.getHookX() +
                    ",\"y\":" + action.getHookY() +
                    ",\"z\":" + action.getHookZ() +
                    ",\"pitch\":0,\"yaw\":0" +
                    ",\"objectData\":" + entityManager.getMainPlayerEntityId() +
                    ",\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0}");

            builder.writePacket("animation", ts,
                    "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");
            writeActionSound(ts, "entity.fishing_bobber.throw", 0.5f, 0.4f);
        } else if (action.getState() == FishingAction.FishingState.REEL_IN ||
                   action.getState() == FishingAction.FishingState.CAUGHT) {
            // Hook entity'yi temizle
            if (lastHookEntityId >= 0) {
                builder.writePacket("entity_destroy", ts,
                        "{\"entityIds\":[" + lastHookEntityId + "]}");
                lastHookEntityId = -1;
            }
            builder.writePacket("animation", ts,
                    "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");
            writeActionSound(ts, "entity.fishing_bobber.retrieve", 1.0f, 0.4f);
            if (action.getState() == FishingAction.FishingState.CAUGHT) {
                writeActionSound(ts, "entity.fishing_bobber.splash", 0.25f, 1.0f);
            }
        }
    }

    private void convertVehicle(VehicleAction action) {
        int mainId = entityManager.getMainPlayerEntityId();
        long ts = action.getTimestamp();

        if (action.getActionType() == VehicleAction.ActionType.MOUNT) {
            // Arac entity'sini olustur
            int vehicleId = entityManager.getOrAssignEntityId(
                    UUID.nameUUIDFromBytes(("vehicle_" + ts + "_" + entityCounter).getBytes()));
            String uuid = UUID.nameUUIDFromBytes(("vh_" + ts + "_" + entityCounter).getBytes()).toString();
            entityCounter++;
            lastVehicleEntityId = vehicleId;

            int vehicleTypeId = getVehicleEntityTypeId(action.getVehicleType());
            builder.writePacket("spawn_entity", ts,
                    "{\"entityId\":" + vehicleId +
                    ",\"objectUUID\":\"" + uuid + "\"" +
                    ",\"type\":" + vehicleTypeId +
                    ",\"x\":" + action.getVehicleX() +
                    ",\"y\":" + action.getVehicleY() +
                    ",\"z\":" + action.getVehicleZ() +
                    ",\"pitch\":0,\"yaw\":0,\"objectData\":0" +
                    ",\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0}");

            // Oyuncuyu araca bindir
            builder.writePacket("set_passengers", ts,
                    "{\"entityId\":" + vehicleId +
                    ",\"passengers\":[" + mainId + "]}");
        } else if (action.getActionType() == VehicleAction.ActionType.DISMOUNT) {
            if (lastVehicleEntityId >= 0) {
                builder.writePacket("set_passengers", ts,
                        "{\"entityId\":" + lastVehicleEntityId + ",\"passengers\":[]}");
                builder.writePacket("entity_destroy", ts,
                        "{\"entityIds\":[" + lastVehicleEntityId + "]}");
                lastVehicleEntityId = -1;
            }
        } else if (action.getActionType() == VehicleAction.ActionType.PLACE) {
            // Arac yerlestirme (binmeden)
            int vehicleId = entityManager.getOrAssignEntityId(
                    UUID.nameUUIDFromBytes(("vehicle_" + ts + "_" + entityCounter).getBytes()));
            String uuid = UUID.nameUUIDFromBytes(("vh_" + ts + "_" + entityCounter).getBytes()).toString();
            entityCounter++;
            int vehicleTypeId = getVehicleEntityTypeId(action.getVehicleType());
            builder.writePacket("spawn_entity", ts,
                    "{\"entityId\":" + vehicleId +
                    ",\"objectUUID\":\"" + uuid + "\"" +
                    ",\"type\":" + vehicleTypeId +
                    ",\"x\":" + action.getVehicleX() +
                    ",\"y\":" + action.getVehicleY() +
                    ",\"z\":" + action.getVehicleZ() +
                    ",\"pitch\":0,\"yaw\":0,\"objectData\":0" +
                    ",\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0}");
        }
    }

    private static String[] getAlternativeEntityNames(String name) {
        switch (name) {
            case "fishing_bobber": return new String[]{"FISHING_HOOK", "FISHING_BOBBER"};
            case "potion":         return new String[]{"SPLASH_POTION", "POTION"};
            case "tnt":            return new String[]{"TNT", "PRIMED_TNT"};
            case "experience_bottle": return new String[]{"THROWN_EXP_BOTTLE", "EXPERIENCE_BOTTLE"};
            case "snow_golem":     return new String[]{"SNOWMAN", "SNOW_GOLEM"};
            case "mooshroom":      return new String[]{"MUSHROOM_COW", "MOOSHROOM"};
            default:               return new String[]{};
        }
    }

    private static int getVehicleEntityTypeId(VehicleAction.VehicleType type) {
        if (type == null) return getEntityTypeId("boat");
        String nmsName;
        switch (type) {
            case BOAT:      nmsName = "boat"; break;
            case MINECART:  nmsName = "minecart"; break;
            case HORSE:     nmsName = "horse"; break;
            case PIG:       nmsName = "pig"; break;
            case STRIDER:   nmsName = "strider"; break;
            case LLAMA:     nmsName = "llama"; break;
            case CAMEL:     nmsName = "camel"; break;
            default:        nmsName = "boat"; break;
        }
        int id = getEntityTypeId(nmsName);
        return id >= 0 ? id : 10;
    }

    // ==================== TIER 4: TUM KALAN AKSIYONLAR ====================

    private void convertEntityDye(EntityDyeAction action) {
        // Koyun/kurt boya degisikligi - chat bilgisi
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson(action.getEntityType() + " boyandi: " + action.getDyeColor()) +
                "\\\",\\\"color\\\":\\\"gray\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertHanging(HangingAction action) {
        long ts = action.getTimestamp();
        if (action.getActionType() == HangingAction.ActionType.PLACE) {
            int typeId = getEntityTypeId(action.getHangingType() == HangingAction.HangingType.PAINTING ? "painting" : "item_frame");
            if (typeId < 0) return;
            int entityId = entityManager.getOrAssignEntityId(
                    UUID.nameUUIDFromBytes(("hanging_" + ts + "_" + entityCounter).getBytes()));
            entityCounter++;
            builder.writePacket("spawn_entity", ts,
                    "{\"entityId\":" + entityId +
                    ",\"objectUUID\":\"" + UUID.nameUUIDFromBytes(("hg_" + ts + "_" + entityCounter).getBytes()) + "\"" +
                    ",\"type\":" + typeId +
                    ",\"x\":" + action.getX() + ",\"y\":" + action.getY() + ",\"z\":" + action.getZ() +
                    ",\"pitch\":0,\"yaw\":0,\"objectData\":0" +
                    ",\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0}");
        } else {
            // BREAK - entity yok et
            builder.writePacket("animation", ts,
                    "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");
        }
    }

    private void convertBreed(BreedAction action) {
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("Hayvan uremesi: " + action.getBabyEntityType()) +
                "\\\",\\\"color\\\":\\\"green\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertNoteBlock(NoteBlockAction action) {
        // Note block sesi
        builder.writePacket("block_action", action.getTimestamp(),
                "{\"location\":{\"x\":" + action.getBlockX() + ",\"y\":" + action.getBlockY() + ",\"z\":" + action.getBlockZ() + "}" +
                ",\"byte1\":" + action.getNote() + ",\"byte2\":0,\"blockId\":0}");
    }

    private void convertSign(SignAction action) {
        // Tabela icerigi chat olarak goster
        String[] lines = action.getLines();
        if (lines != null) {
            StringBuilder text = new StringBuilder();
            for (String line : lines) {
                if (line != null && !line.isEmpty()) {
                    if (text.length() > 0) text.append(" | ");
                    text.append(line);
                }
            }
            if (text.length() > 0) {
                builder.writePacket("system_chat", action.getTimestamp(),
                        "{\"content\":\"{\\\"text\\\":\\\"" +
                        escapeJson("[Tabela] " + text) +
                        "\\\",\\\"color\\\":\\\"yellow\\\",\\\"italic\\\":true}\"" +
                        ",\"isActionBar\":false}");
            }
        }
    }

    private void convertAnvil(AnvilAction action) {
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("[Ors] " + action.getActionType() + ": " + action.getItemAfter() + " (XP: " + action.getExpCost() + ")") +
                "\\\",\\\"color\\\":\\\"gray\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertBrew(BrewAction action) {
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("[Iksir Tezgahi] Malzeme: " + action.getIngredient()) +
                "\\\",\\\"color\\\":\\\"dark_purple\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertCraft(CraftAction action) {
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("[Craft] " + action.getCraftedItem() + " x" + action.getAmount()) +
                "\\\",\\\"color\\\":\\\"aqua\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertEnchant(EnchantAction action) {
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("[Buyuleme] " + action.getItemAfter() + " (XP: " + action.getExpLevelCost() + ")") +
                "\\\",\\\"color\\\":\\\"light_purple\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertUtilityBlock(UtilityBlockAction action) {
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("[" + action.getUtilityType() + "] " + action.getItemBefore() + " -> " + action.getItemAfter()) +
                "\\\",\\\"color\\\":\\\"gray\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertBookEdit(BookEditAction action) {
        String info = action.isSigned() ? "[Kitap Imzalandi] " + action.getTitle() + " - " + action.getAuthor()
                                        : "[Kitap Duzenlendi]";
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" + escapeJson(info) +
                "\\\",\\\"color\\\":\\\"gold\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertFarming(FarmingAction action) {
        long ts = action.getTimestamp();
        switch (action.getFarmingType()) {
            case BONE_MEAL:
                // Kemik unu efekti
                builder.writePacket("world_event", ts,
                        "{\"effectId\":1505,\"location\":{\"x\":" + action.getX() +
                        ",\"y\":" + action.getY() + ",\"z\":" + action.getZ() + "},\"data\":0,\"global\":false}");
                break;
            case HOE_TILL:
                // Toprak isleme - blok degisikligi
                int farmlandId = WebReplayBlockStateMap.getBlockStateId("FARMLAND");
                builder.writePacket("block_change", ts,
                        "{\"location\":{\"x\":" + action.getX() + ",\"y\":" + action.getY() + ",\"z\":" + action.getZ() +
                        "},\"type\":" + farmlandId + "}");
                break;
            case CROP_HARVEST:
                builder.writePacket("animation", ts,
                        "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");
                break;
            case FARMLAND_TRAMPLE:
                int dirtId = WebReplayBlockStateMap.getBlockStateId("DIRT");
                builder.writePacket("block_change", ts,
                        "{\"location\":{\"x\":" + action.getX() + ",\"y\":" + action.getY() + ",\"z\":" + action.getZ() +
                        "},\"type\":" + dirtId + "}");
                break;
        }
    }

    private void convertDecoration(DecorationAction action) {
        builder.writePacket("animation", action.getTimestamp(),
                "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("[Dekorasyon] " + action.getDecorationType()) +
                "\\\",\\\"color\\\":\\\"gray\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertRedstone(RedstoneAction action) {
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("[Redstone] " + action.getRedstoneType() + " @ " +
                        action.getX() + "," + action.getY() + "," + action.getZ()) +
                "\\\",\\\"color\\\":\\\"red\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertEntityCommand(EntityCommandAction action) {
        builder.writePacket("system_chat", action.getTimestamp(),
                "{\"content\":\"{\\\"text\\\":\\\"" +
                escapeJson("[Entity] " + action.getCommandType() + ": " + action.getEntityType()) +
                "\\\",\\\"color\\\":\\\"gray\\\",\\\"italic\\\":true}\"" +
                ",\"isActionBar\":false}");
    }

    private void convertArmorStand(ArmorStandAction action) {
        long ts = action.getTimestamp();
        if (action.getActionType() == ArmorStandAction.ActionType.PLACE) {
            int typeId = getEntityTypeId("armor_stand");
            if (typeId < 0) return;
            int entityId = entityManager.getOrAssignEntityId(
                    UUID.nameUUIDFromBytes(("armorstand_" + ts + "_" + entityCounter).getBytes()));
            String uuid = UUID.nameUUIDFromBytes(("as_" + ts + "_" + entityCounter).getBytes()).toString();
            entityCounter++;
            builder.writePacket("spawn_entity", ts,
                    "{\"entityId\":" + entityId +
                    ",\"objectUUID\":\"" + uuid + "\"" +
                    ",\"type\":" + typeId +
                    ",\"x\":" + action.getX() + ",\"y\":" + action.getY() + ",\"z\":" + action.getZ() +
                    ",\"pitch\":" + WebReplayPacketBuilder.angleToByte(action.getPitch()) +
                    ",\"yaw\":" + WebReplayPacketBuilder.angleToByte(action.getYaw()) +
                    ",\"objectData\":0" +
                    ",\"velocityX\":0,\"velocityY\":0,\"velocityZ\":0}");
        } else if (action.getActionType() == ArmorStandAction.ActionType.BREAK) {
            builder.writePacket("animation", ts,
                    "{\"entityId\":" + entityManager.getMainPlayerEntityId() + ",\"animation\":0}");
        }
    }

    // ==================== BLOCK & ENTITY TYPE ID MAPPING ====================

    // NMS block registry ID cache (block_name -> registry_id)
    private static final Map<String, Integer> blockRegistryIdCache = new HashMap<>();

    /**
     * NMS ile blok registry ID'sini alir (state ID degil!).
     * block_action paketi blockId alani icin gerekli (orn: chest animasyonu).
     */
    private static int getBlockRegistryId(String blockFieldName) {
        Integer cached = blockRegistryIdCache.get(blockFieldName);
        if (cached != null) return cached;

        try {
            Class<?> blocksClass = Class.forName("net.minecraft.world.level.block.Blocks");
            Object nmsBlock = blocksClass.getField(blockFieldName.toUpperCase()).get(null);

            Class<?> registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object blockRegistry = registriesClass.getDeclaredField("BLOCK").get(null);

            java.lang.reflect.Method getIdMethod = null;
            for (java.lang.reflect.Method m : blockRegistry.getClass().getMethods()) {
                if (m.getName().equals("getId") && m.getParameterCount() == 1
                        && m.getReturnType() == int.class) {
                    getIdMethod = m;
                    break;
                }
            }

            if (getIdMethod != null) {
                int id = (int) getIdMethod.invoke(blockRegistry, nmsBlock);
                blockRegistryIdCache.put(blockFieldName, id);
                return id;
            }
        } catch (Exception ignored) {}

        return 0;
    }

    // NMS entity type ID cache (entity_name -> protocol_id)
    private static final Map<String, Integer> entityTypeIdCache = new HashMap<>();

    /**
     * Entity type adi -> protokol entity type ID.
     * NMS reflection ile sunucu versiyonuna uyumlu ID doner.
     */
    private static int getEntityTypeId(String entityType) {
        if (entityType == null) return -1;
        String type = entityType.replace("minecraft:", "").toLowerCase();

        // Cache kontrol
        Integer cached = entityTypeIdCache.get(type);
        if (cached != null) return cached;

        // NMS ile al
        int id = getEntityTypeIdFromNms(type);
        if (id >= 0) {
            entityTypeIdCache.put(type, id);
            return id;
        }

        return -1;
    }

    /**
     * Potion effect adi -> 1.21.1 protokol effect ID (0-based registry).
     * Kaynak: PrismarineJS/minecraft-data/data/pc/1.20.5/effects.json
     */
    private static int getPotionEffectId(String effectType) {
        if (effectType == null) return -1;
        String type = effectType.replace("minecraft:", "").toLowerCase();
        switch (type) {
            case "speed":           return 0;
            case "slowness":        return 1;
            case "haste":           return 2;
            case "mining_fatigue":  return 3;
            case "strength":        return 4;
            case "instant_health":  return 5;
            case "instant_damage":  return 6;
            case "jump_boost":      return 7;
            case "nausea":          return 8;
            case "regeneration":    return 9;
            case "resistance":      return 10;
            case "fire_resistance": return 11;
            case "water_breathing": return 12;
            case "invisibility":    return 13;
            case "blindness":       return 14;
            case "night_vision":    return 15;
            case "hunger":          return 16;
            case "weakness":        return 17;
            case "poison":          return 18;
            case "wither":          return 19;
            case "health_boost":    return 20;
            case "absorption":      return 21;
            case "saturation":      return 22;
            case "glowing":         return 23;
            case "levitation":      return 24;
            case "luck":            return 25;
            case "unluck":          return 26;
            case "slow_falling":    return 27;
            case "conduit_power":   return 28;
            case "dolphins_grace":  return 29;
            case "bad_omen":        return 30;
            case "hero_of_the_village": return 31;
            case "darkness":        return 32;
            default:                return -1;
        }
    }

    // ==================== YARDIMCI METODLAR ====================

    private void convertNearbyEquipment(int entityId, NearbyPlayerAction action, long timestamp) {
        if (action.getEquipment() == null) return;

        StringBuilder equips = new StringBuilder("[");
        boolean first = true;

        for (Map.Entry<EquipmentAction.EquipmentSlot, EquipmentAction.ItemData> entry : action.getEquipment().entrySet()) {
            if (!first) equips.append(",");
            first = false;

            int slot = equipmentSlotToProtocol(entry.getKey());
            EquipmentAction.ItemData itemData = entry.getValue();
            String itemJson;

            if (itemData != null && itemData.getMaterial() != null) {
                int itemId = WebReplayItemMap.getItemId(itemData.getMaterial());
                if (itemId >= 0) {
                    itemJson = "{\"itemCount\":" + itemData.getAmount() +
                               ",\"itemId\":" + itemId +
                               ",\"addedComponentCount\":0,\"removedComponentCount\":0" +
                               ",\"components\":[],\"removeComponents\":[]}";
                } else {
                    itemJson = "{\"itemCount\":0}";
                }
            } else {
                itemJson = "{\"itemCount\":0}";
            }

            equips.append("{\"slot\":").append(slot).append(",\"item\":").append(itemJson).append("}");
        }
        equips.append("]");

        builder.writePacket("entity_equipment", timestamp,
                "{\"entityId\":" + entityId + ",\"equipments\":" + equips + "}");
    }

    private static int equipmentSlotToProtocol(EquipmentAction.EquipmentSlot slot) {
        if (slot == null) return 0;
        switch (slot) {
            case MAIN_HAND:  return 0;
            case OFF_HAND:   return 1;
            case BOOTS:      return 2;
            case LEGGINGS:   return 3;
            case CHESTPLATE: return 4;
            case HELMET:     return 5;
            default:         return 0;
        }
    }

    // NearbyPlayerAction EquipmentAction.EquipmentSlot kullanir, ayri overload gerekmiyor

    private static byte setFlag(byte flags, int mask, boolean enabled) {
        if (enabled) {
            return (byte) (flags | mask);
        } else {
            return (byte) (flags & ~mask);
        }
    }

    /**
     * Aksiyona bagli ses efekti gonderir. Konum ana oyuncunun son pozisyonundan alinir.
     */
    private void writeActionSound(long timestamp, String soundName, float volume, float pitch) {
        int entityId = entityManager.getMainPlayerEntityId();
        double[] pos = lastPositions.get(entityId);
        double x = pos != null ? pos[0] : 0;
        double y = pos != null ? pos[1] : 64;
        double z = pos != null ? pos[2] : 0;

        builder.writePacket("sound_effect", timestamp,
                "{\"sound\":{\"data\":{\"soundName\":\"minecraft:" + soundName + "\"}}" +
                ",\"soundCategory\":0" +
                ",\"x\":" + (int)(x * 8) +
                ",\"y\":" + (int)(y * 8) +
                ",\"z\":" + (int)(z * 8) +
                ",\"volume\":" + volume +
                ",\"pitch\":" + pitch +
                ",\"seed\":[0,0]}");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String toJsonTextComponent(String text) {
        return "\"{\\\"text\\\":\\\"" + escapeJson(text) + "\\\"}\"";
    }

    // ==================== TAKIM & KALP GOSTERGESI (Oyun ici ile ayni) ====================

    /**
     * NPC isim rengi (kirmizi) + kalp gostergesi (BELOW_NAME scoreboard).
     * Oyun icindeki ReplayNPCManager ile birebir ayni gorunum.
     */
    private void setupTeamAndHealth(String playerName, int entityId) {
        String teamName = "rs_" + entityId;

        // Takim olustur (kirmizi isim rengi)
        builder.writeSetupPacket("teams",
                "{\"team\":\"" + teamName + "\"" +
                ",\"mode\":0" + // CREATE
                ",\"name\":\"{\\\"text\\\":\\\"" + teamName + "\\\"}\"" +
                ",\"prefix\":\"{\\\"text\\\":\\\"\\\"}\"" +
                ",\"suffix\":\"{\\\"text\\\":\\\"\\\"}\"" +
                ",\"friendlyFire\":true" +
                ",\"nameTagVisibility\":\"always\"" +
                ",\"collisionRule\":\"never\"" +
                ",\"color\":12" + // RED (NamedTextColor index)
                ",\"players\":[\"" + escapeJson(playerName) + "\"]}");

        // Kalp gostergesi scoreboard (BELOW_NAME)
        builder.writeSetupPacket("scoreboard_objective",
                "{\"name\":\"rs_health\"" +
                ",\"action\":0" + // CREATE
                ",\"displayName\":\"{\\\"text\\\":\\\"\\u2764\\\",\\\"color\\\":\\\"red\\\"}\"" +
                ",\"type\":0}"); // INTEGER (HEARTS render type = 0 for web client)

        // Scoreboard'u BELOW_NAME pozisyonuna ata (position=2)
        builder.writeSetupPacket("scoreboard_display_objective",
                "{\"position\":2,\"name\":\"rs_health\"}");

        // Baslangic kalp degeri (20 = full health)
        builder.writeSetupPacket("scoreboard_score",
                "{\"itemName\":\"" + escapeJson(playerName) + "\"" +
                ",\"action\":0" + // CREATE_OR_UPDATE
                ",\"scoreName\":\"rs_health\"" +
                ",\"value\":20}");
    }

    /**
     * Kalp gostergesi guncelle (saglik degistiginde).
     */
    private void updateHealthScore(String playerName, float health, float absorption, long timestamp) {
        int score = (int) Math.ceil(health + absorption);
        builder.writePacket("scoreboard_score", timestamp,
                "{\"itemName\":\"" + escapeJson(playerName) + "\"" +
                ",\"action\":0" +
                ",\"scoreName\":\"rs_health\"" +
                ",\"value\":" + score + "}");
    }

    // ==================== HUD SISTEMI ====================

    /**
     * Boss Bar ilerleme cubugu kurar.
     */
    private void setupHUD(String name) {
        long durationSec = Math.max(1, (replayEndTime - replayStartTime) / 1000);
        String title = escapeJson(name) + " - Replay (" + durationSec + "s)";
        builder.writeSetupPacket("boss_bar",
                "{\"entityUUID\":\"00000000-0000-0000-0000-000000000001\"" +
                ",\"action\":0" +
                ",\"title\":\"{\\\"text\\\":\\\"" + title + "\\\"}\"" +
                ",\"health\":0.0" +
                ",\"color\":3" +
                ",\"dividers\":0" +
                ",\"flags\":0}");
    }

    /**
     * Her aksiyonda boss bar ilerlemesini gunceller.
     */
    private void updateHUD(ReplayAction action) {
        long ts = action.getTimestamp();

        // Boss bar'i her 500ms'de bir guncelle (performans: her LocationAction'da degil)
        if (action instanceof LocationAction) {
            if (ts - lastHUDUpdateTime < 500) return;
            lastHUDUpdateTime = ts;

            long elapsed = ts - replayStartTime;
            long totalDuration = Math.max(1, replayEndTime - replayStartTime);
            float progress = Math.min(1.0f, (float) elapsed / totalDuration);
            builder.writePacket("boss_bar", ts,
                    "{\"entityUUID\":\"00000000-0000-0000-0000-000000000001\"" +
                    ",\"action\":3,\"health\":" + progress + "}");
        }
    }
}
