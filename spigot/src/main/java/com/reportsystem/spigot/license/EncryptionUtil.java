package com.reportsystem.spigot.license;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * KAREBLOK.TC - Encryption Utility
 *
 * AES-256 şifreleme/şifre çözme ve checksum hesaplama
 */
public class EncryptionUtil {

    private static final String ALGORITHM = "AES";

    // IMPORTANT: Bu key'i değiştir! Her proje için farklı olmalı
    private static final String SECRET_KEY = "KAREBLOK_REPORTSYSTEM_2025_SECRET_KEY_CHANGE_THIS";

    /**
     * Metni AES-256 ile şifreler
     *
     * @param plainText Şifrelenecek metin
     * @return Base64 encoded şifreli metin
     */
    public static String encrypt(String plainText) {
        try {
            SecretKeySpec secretKey = generateKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            throw new RuntimeException("Şifreleme hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Şifreli metni çözer
     *
     * @param encryptedText Base64 encoded şifreli metin
     * @return Orijinal metin
     */
    public static String decrypt(String encryptedText) {
        try {
            SecretKeySpec secretKey = generateKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            byte[] decrypted = cipher.doFinal(decoded);

            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Şifre çözme hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Secret key oluşturur (SHA-256)
     */
    private static SecretKeySpec generateKey() {
        try {
            byte[] key = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // AES-128 için 16 byte

            return new SecretKeySpec(key, ALGORITHM);

        } catch (Exception e) {
            throw new RuntimeException("Key oluşturma hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Bir string'in SHA-256 hash'ini hesaplar
     *
     * @param input Hash'lenecek string
     * @return Hex formatında hash
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            throw new RuntimeException("Hash hesaplama hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Test için main metod
     */
    public static void main(String[] args) {
        // Test
        String original = "RS-A3F2-B8D1-C4E9";
        System.out.println("Original: " + original);

        String encrypted = encrypt(original);
        System.out.println("Encrypted: " + encrypted);

        String decrypted = decrypt(encrypted);
        System.out.println("Decrypted: " + decrypted);

        System.out.println("\nMatch: " + original.equals(decrypted));
    }
}
