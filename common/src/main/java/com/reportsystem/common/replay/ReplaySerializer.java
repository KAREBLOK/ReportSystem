package com.reportsystem.common.replay;

import com.reportsystem.common.replay.actions.ReplayAction;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ReplaySerializer {

    /**
     * Replay action'larını sıkıştırılmış byte array'e dönüştürür
     */
    public static byte[] serialize(List<ReplayAction> actions, boolean compress) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (compress) {
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
                 ObjectOutputStream oos = new ObjectOutputStream(gzipOut)) {
                oos.writeObject(actions);
            }
        } else {
            try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
                oos.writeObject(actions);
            }
        }

        return baos.toByteArray();
    }

    /**
     * Byte array'den replay action'larını geri yükler
     */
    @SuppressWarnings("unchecked")
    public static List<ReplayAction> deserialize(byte[] data, boolean compressed) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);

        if (compressed) {
            try (GZIPInputStream gzipIn = new GZIPInputStream(bais);
                 ObjectInputStream ois = new ObjectInputStream(gzipIn)) {
                return (List<ReplayAction>) ois.readObject();
            }
        } else {
            try (ObjectInputStream ois = new ObjectInputStream(bais)) {
                return (List<ReplayAction>) ois.readObject();
            }
        }
    }

    /**
     * Veri boyutunu hesaplar (KB cinsinden)
     */
    public static double calculateSizeKB(byte[] data) {
        return data.length / 1024.0;
    }
}