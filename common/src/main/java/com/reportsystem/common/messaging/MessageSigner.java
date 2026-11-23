package com.reportsystem.common.messaging;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plugin message imzalama / doğrulama (HMAC-SHA256) — spigot, bungeecord ve velocity ortak kullanır.
 *
 * Neden: Backend sunucuya bir plugin message'ın proxy'den mi yoksa değiştirilmiş bir client'tan mı
 * geldiği Spigot tarafında ayırt edilemez. Ortak gizli anahtar ile imzalayarak sahte mesajlar
 * (sahte replay başlatma, sahte ceza sonucu, sahte yetkili bildirimi) reddedilir.
 *
 * TEL FORMATI (sürüm 1) — kanal verisinin TAMAMI şu zarftır:
 * <pre>
 *   byte    version  = 1
 *   long    timestamp (epoch ms, UTC)
 *   long    nonce     (rastgele; aynı mesajın tekrar oynatılmasını engeller)
 *   byte[32] mac      = HMAC-SHA256(secret, version | timestamp | nonce | kanalAdı(UTF-8) | payload)
 *   byte[]  payload   = eski formatla aynı içerik (writeUTF alt kanal adı + alanlar)
 * </pre>
 * Kanal adı MAC'e dahildir: "reportsystem:channel" için imzalı mesaj "reportsystem:notification"
 * üzerinde kabul edilmez. Zaman damgası MAX_AGE_MS (30 sn) dışındaysa mesaj reddedilir;
 * bu yüzden proxy ve backend makinelerinin saati senkron olmalıdır (NTP). MAC karşılaştırması
 * sabit zamanlıdır (MessageDigest.isEqual). Aynı nonce, geçerlilik penceresi boyunca ikinci kez kabul edilmez.
 */
public final class MessageSigner {

    public static final int VERSION = 1;
    public static final long MAX_AGE_MS = 30_000L;
    private static final int MAC_LEN = 32;
    private static final String ALGO = "HmacSHA256";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_SEEN = 10_000;

    private final byte[] key;
    private final Map<Long, Long> seenNonces = new ConcurrentHashMap<>();

    public MessageSigner(String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("message-secret boş olamaz");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** 256 bit rastgele, hex biçiminde yeni gizli anahtar üretir (64 karakter). */
    public static String generateSecret() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder(64);
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    /** payload'ı zarfa sarıp imzalar. */
    public byte[] sign(String channel, byte[] payload) {
        long ts = System.currentTimeMillis();
        long nonce = RANDOM.nextLong();
        byte[] mac = mac(channel, ts, nonce, payload);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(1 + 16 + MAC_LEN + payload.length);
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(VERSION);
            out.writeLong(ts);
            out.writeLong(nonce);
            out.write(mac);
            out.write(payload);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e); // ByteArrayOutputStream'de oluşmaz
        }
    }

    /**
     * Zarfı doğrular.
     *
     * @return doğrulanmış payload; imza/sürüm/zaman/nonce geçersizse null
     */
    public byte[] verify(String channel, byte[] data) {
        if (data == null || data.length < 1 + 16 + MAC_LEN) {
            return null;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            if (in.readUnsignedByte() != VERSION) {
                return null;
            }
            long ts = in.readLong();
            long nonce = in.readLong();
            byte[] mac = new byte[MAC_LEN];
            in.readFully(mac);
            byte[] payload = new byte[in.available()];
            in.readFully(payload);

            byte[] expected = mac(channel, ts, nonce, payload);
            if (!MessageDigest.isEqual(expected, mac)) {
                return null;
            }
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > MAX_AGE_MS) {
                return null;
            }
            if (!rememberNonce(nonce, now)) {
                return null; // tekrar oynatma
            }
            return payload;
        } catch (IOException e) {
            return null;
        }
    }

    /** Nonce ilk kez görülüyorsa true. Süresi dolanlar temizlenir; boyut sınırlıdır. */
    private boolean rememberNonce(long nonce, long now) {
        if (seenNonces.size() > MAX_SEEN) {
            purge(now);
        }
        return seenNonces.putIfAbsent(nonce, now + MAX_AGE_MS * 2) == null;
    }

    private void purge(long now) {
        Iterator<Map.Entry<Long, Long>> it = seenNonces.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue() < now) {
                it.remove();
            }
        }
        if (seenNonces.size() > MAX_SEEN) {
            seenNonces.clear(); // saldırı altında bellek şişmesin; imza zaten zaman penceresiyle sınırlı
        }
    }

    private byte[] mac(String channel, long ts, long nonce, byte[] payload) {
        try {
            Mac m = Mac.getInstance(ALGO);
            m.init(new SecretKeySpec(key, ALGO));
            ByteArrayOutputStream head = new ByteArrayOutputStream(32);
            DataOutputStream h = new DataOutputStream(head);
            h.writeByte(VERSION);
            h.writeLong(ts);
            h.writeLong(nonce);
            m.update(head.toByteArray());
            m.update(channel.getBytes(StandardCharsets.UTF_8));
            m.update(payload);
            return m.doFinal();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("HMAC hesaplanamadı", e);
        }
    }
}
