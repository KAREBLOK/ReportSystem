package com.reportsystem.spigot.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;

import java.util.List;

/**
 * Sunucu surumune gore Player entity metadata index'lerini doner.
 *
 * Mojang 1.21.5 ile birlikte Player entity'nin metadata layout'unu yeniden
 * numaralandirdi. Eski 17 nolu "displayed skin parts" alani 1.21.11 client'inda
 * bir Float field'a denk geliyor; eski kod hardcoded 17 gonderdigi icin
 * 1.21.5+ client'lari "Ag Protokolu Hatasi" ile sunucudan atiliyordu.
 *
 * Bu sinif kullanildigi her yerde index'leri dinamik secer, bilinmeyen veya
 * desteklenmeyen sürümlerde guvenli sekilde paketi atlar.
 */
public final class MetadataIndices {

    private MetadataIndices() {}

    /**
     * Player Mode Customisation (displayed skin parts) wire index'i.
     * Donus null ise bu surumde gondermek guvensiz, atlanmali.
     */
    public static Integer skinPartsIndex() {
        ServerVersion v;
        try {
            v = PacketEvents.getAPI().getServerManager().getVersion();
        } catch (Throwable ignored) {
            return 17; // PacketEvents henuz initialize olmadiysa eski davranis
        }
        if (v.isOlderThanOrEquals(ServerVersion.V_1_21_4)) {
            return 17;
        }
        // 1.21.5+ icin Mojang layout'u kaydi; dogru index henuz dogrulanmadigi
        // icin paketi atliyoruz. NPC default skin layer ayarlariyla (hepsi acik
        // gosterimi yerine bos) spawn olur ama disconnect yasanmaz.
        return null;
    }

    /**
     * "Skin parts" metadata entry'sini listeye ekler. Sunucu surumu desteklenmiyorsa
     * hicbir sey yapmaz.
     */
    public static void addSkinParts(List<EntityData<?>> metadata, byte value) {
        Integer idx = skinPartsIndex();
        if (idx != null) {
            metadata.add(new EntityData<>(idx, EntityDataTypes.BYTE, value));
        }
    }
}
