package com.reportsystem.spigot.license;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * KAREBLOK.TC - Encryption Utility
 * AES-256-CBC + HMAC-SHA256
 */
public class EncryptionUtil {

    private final byte[] aesKey;   // 32 bytes (AES-256)
    private final byte[] hmacKey;  // 32 bytes

    public EncryptionUtil(String licenseKey, String hwid) {
        byte[] seed = deriveKeySeed(licenseKey, hwid);
        // Ilk 32 byte AES, son 32 byte HMAC
        this.aesKey = Arrays.copyOfRange(seed, 0, 32);
        this.hmacKey = Arrays.copyOfRange(seed, 32, 64);
    }

    private static byte[] deriveKeySeed(String licenseKey, String hwid) {
        try {
            // Salt parcalara bolunmus (tek string olarak decompile'da gorunmez)
            char[] s = {0x4B, 0x42, 0x2E, 0x54, 0x43, 0x2D};
            char[] p = {0x52, 0x53, 0x2D, 0x32, 0x30, 0x32, 0x35};
            char[] e = {0x2D, 0x4C, 0x49, 0x43};
            String salt = new String(s) + new String(p) + new String(e);

            MessageDigest sha = MessageDigest.getInstance("SHA-512");
            sha.update(salt.getBytes(StandardCharsets.UTF_8));
            sha.update(licenseKey.getBytes(StandardCharsets.UTF_8));
            sha.update(hwid.getBytes(StandardCharsets.UTF_8));
            return sha.digest(); // 64 bytes
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * AES-256-CBC ile sifreler. Cikti: Base64(IV + ciphertext)
     */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new RuntimeException("Sifreleme hatasi", e);
        }
    }

    /**
     * AES-256-CBC ile cözer. Girdi: Base64(IV + ciphertext)
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] data = Base64.getDecoder().decode(encryptedText);
            byte[] iv = Arrays.copyOfRange(data, 0, 16);
            byte[] ciphertext = Arrays.copyOfRange(data, 16, data.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Sifre cozme hatasi", e);
        }
    }

    /**
     * HMAC-SHA256 hesaplar
     */
    public String computeHMAC(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC hatasi", e);
        }
    }

    /**
     * HMAC dogrulama (constant-time karsilastirma)
     */
    public boolean verifyHMAC(String data, String expectedHmac) {
        String computed = computeHMAC(data);
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                expectedHmac.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash hatasi", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
