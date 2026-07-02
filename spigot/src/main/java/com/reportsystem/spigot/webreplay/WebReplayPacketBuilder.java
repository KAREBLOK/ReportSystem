package com.reportsystem.spigot.webreplay;

/**
 * .packets.txt formatinda paket satirlari olusturur.
 * Format: S play:paket_adi +zamanFarkMs {jsonVeri}
 */
public class WebReplayPacketBuilder {

    private final StringBuilder output;
    private long lastTimestamp = -1;

    public WebReplayPacketBuilder() {
        this.output = new StringBuilder(512 * 1024); // 512KB initial (terrain verisi buyuk olabilir)
    }

    /**
     * Header satirini yazar.
     */
    public void writeHeader(String minecraftVersion) {
        output.append("{\"formatVersion\":1,\"minecraftVersion\":\"")
              .append(minecraftVersion)
              .append("\"}\n");
    }

    /**
     * Bir server-to-client paketi yazar.
     * @param packetName minecraft-protocol paket adi (orn: entity_teleport)
     * @param timestamp  mutlak zaman damgasi (ms)
     * @param json       paket verisi JSON olarak
     */
    public void writePacket(String packetName, long timestamp, String json) {
        long diff = 0;
        if (lastTimestamp >= 0) {
            diff = Math.max(0, timestamp - lastTimestamp);
        }
        // Sadece ileri giden timestamp'lerde guncelle (sira disi aksiyonlarda drift onle)
        if (timestamp > lastTimestamp || lastTimestamp < 0) {
            lastTimestamp = timestamp;
        }

        output.append("S play:")
              .append(packetName)
              .append(" +")
              .append(diff)
              .append(' ')
              .append(json)
              .append('\n');
    }

    /**
     * Setup paketi yazar (diff=0, timestamp etkilenmez).
     */
    public void writeSetupPacket(String packetName, String json) {
        output.append("S play:")
              .append(packetName)
              .append(" +0 ")
              .append(json)
              .append('\n');
    }

    /**
     * Tamamlanmis .packets.txt icerigini dondurur.
     */
    public String build() {
        return output.toString();
    }

    /**
     * Angle byte hesaplar (derece -> protokol byte).
     */
    public static int angleToByte(float degrees) {
        return (int) (degrees * 256.0f / 360.0f) & 0xFF;
    }

    /**
     * Velocity degerini protokol formatina cevirir (blocks/tick * 8000).
     */
    public static int velocityToProtocol(double blocksPerTick) {
        return (int) (blocksPerTick * 8000.0);
    }
}
