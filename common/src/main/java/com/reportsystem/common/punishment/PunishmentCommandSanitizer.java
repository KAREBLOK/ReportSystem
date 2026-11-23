package com.reportsystem.common.punishment;

import java.util.regex.Pattern;

/**
 * Konsol ceza komutuna (LiteBans / AdvancedBan) giden oyuncu metni için TEK nokta [SEC-02].
 *
 * <p>Sebep metni raporlayanın serbest yazdığı metindir; ceza eklentisi komut satırını kendi
 * ayrıştırıcısıyla işler (süre, {@code -s} gibi bayraklar). Bu sınıf iki ihtimale karşı da
 * (ayrıştırıyor / ayrıştırmıyor) güvenli tarafta kalır:
 * <ul>
 *   <li>kontrol / renk karakterleri atılır, beyaz liste dışı her şey silinir,</li>
 *   <li>her sözcüğün başındaki {@code -} silinir (bayrak sanılmasın),</li>
 *   <li>rakamla başlayan sebebin başına {@code Reason: } eklenir (kalıcı cezada ilk sözcük süre sanılmasın),</li>
 *   <li>en fazla {@value #MAX_REASON_LENGTH} karakter.</li>
 * </ul>
 *
 * <p>Hedeflenen ayrıştırıcı davranışları (araştırmacı doğrulaması, 21 Kas 2026):
 * <ul>
 *   <li>AdvancedBan: {@code -s} bayrağı ilk 4 argümanda (büyük/küçük harf duyarsız) aranıp silinir;
 *       süre yalnız /tempban ve /tempmute'ta hedeften hemen sonraki argümandan okunur; sebep
 *       {@code ~} veya {@code @} ile başlarsa layout (şablon) adı sayılır.</li>
 *   <li>LiteBans: {@code /ban <user> [flags] [duration] [server:*] [template] [reason]}; bayraklar
 *       {@code -s -S -p -I -g --sender=X --sender-uuid --hide}; süre {@code 1d}, {@code 1.5h},
 *       {@code 5 days}. Sebep ortasında süre/bayrak ayrıştırılıp ayrıştırılmadığı belgede net değil.</li>
 * </ul>
 * Karşılıkları: {@code ~ @ : * =} gibi karakterler beyaz liste dışı olduğu için her yerden silinir
 * (şablon adı ve {@code server:*} kalıpları bozulur); sözcük başındaki {@code -}/{@code --} silinir
 * (bayrak olmaz); rakamla başlayan sebebe {@code Reason: } eklenir (süre sanılmaz).
 * Not: LiteBans'ta sebepten önce {@code --} (bayrak sonu) konmadı; {@code -s} komutun sonunda
 * olduğundan {@code --} ile birlikte komut sırası değişmek zorunda kalırdı ve LiteBans'ın bu
 * biçimi doğrulanmadı.
 *
 * Spigot, BungeeCord ve Velocity sağlayıcılarının hepsi bunu kullanır; kopya temizleyici yazma.
 */
public final class PunishmentCommandSanitizer {

    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_REASON_LENGTH = 200;

    /** Sebep için izin verilen karakterler: harf, rakam, TR harfleri, boşluk ve . , _ - */
    private static final Pattern REASON_DISALLOWED = Pattern.compile(
            "[^a-zA-Z0-9çÇğĞıİöÖşŞüÜ .,_\\-]");
    /** Oyuncu adı: Floodgate/Bedrock öneki için nokta da serbest. */
    private static final Pattern NAME_VALID = Pattern.compile("[A-Za-z0-9_.]{1,32}");
    private static final Pattern CONTROL_OR_COLOR = Pattern.compile("[\\x00-\\x1F\\x7F§&]");
    private static final Pattern MULTI_SPACE = Pattern.compile(" {2,}");
    /** Satır başında veya boşluktan sonra gelen bir-veya-çok tire. */
    private static final Pattern WORD_LEADING_DASH = Pattern.compile("(^| )-+");

    private PunishmentCommandSanitizer() {
    }

    /**
     * Oyuncu adı DOĞRULAYICISI: tam olarak {@code ^[A-Za-z0-9_.]{1,32}$} ise aynen döner, aksi halde
     * {@code null}. Geçersiz karakter SİLİNMEZ (silmek adı başka bir gerçek oyuncunun adına
     * dönüştürüp yanlış kişinin cezalanmasına yol açardı) [CRA-02]. Çağıran {@code null}'da komutu
     * HİÇ çalıştırmamalı ve {@code false} dönmelidir. Floodgate öneki yalnız {@code .} ise geçer;
     * {@code *} gibi önekler bilinçli olarak reddedilir (komutta joker olabilir).
     */
    public static String name(String in) {
        if (in == null || in.isEmpty() || in.length() > MAX_NAME_LENGTH) return null;
        return NAME_VALID.matcher(in).matches() ? in : null;
    }

    /** Geçersiz adı loga güvenle yazmak için: kontrol karakterleri atılır, 32 karaktere kısaltılır [SEC-05]. */
    public static String logSafe(String in) {
        if (in == null) return "null";
        String s = CONTROL_OR_COLOR.matcher(in).replaceAll("?");
        return s.length() > MAX_NAME_LENGTH ? s.substring(0, MAX_NAME_LENGTH) + "..." : s;
    }

    /** Boş parçaları atıp tek boşlukla birleştirir (boş sebepte çift boşluk kalmaz). */
    public static String command(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
        }
        return sb.toString();
    }

    /** Sebep metni; bkz. sınıf açıklaması. Dönüş değeri boş olabilir. */
    public static String reason(String in) {
        if (in == null) return "";
        String s = CONTROL_OR_COLOR.matcher(in).replaceAll("");
        s = REASON_DISALLOWED.matcher(s).replaceAll("");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        // Tire silme boşlukları değiştirebilir (ör. "a - b" -> "a  b"); sonra yeniden temizle.
        s = WORD_LEADING_DASH.matcher(s).replaceAll("$1");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        if (!s.isEmpty() && s.charAt(0) >= '0' && s.charAt(0) <= '9') {
            s = "Reason: " + s;
        }
        if (s.length() > MAX_REASON_LENGTH) {
            s = s.substring(0, MAX_REASON_LENGTH).trim();
        }
        return s;
    }
}
