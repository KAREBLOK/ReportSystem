package com.reportsystem.common.punishment;

/**
 * Ceza süresini LiteBans komut biçimine çevirir (H-12).
 * Kesme yok: saniye yukarı yuvarlanır, tam dakikaysa "Nm", değilse "Ns" (LiteBans: s, m, h, d, w, mo, y).
 */
public final class DurationFormat {

    /**
     * T-1: geçici ceza süresinin üst sınırı (100 yıl). Bunu aşan süre bu değere kırpılır;
     * böylece {@code now + duration} taşıp negatif bitişe (cezanın anında bitmesine) dönüşmez.
     */
    public static final long MAX_DURATION_MS = 100L * 365L * 86_400_000L;

    private DurationFormat() {
    }

    /** Pozitif süreyi {@link #MAX_DURATION_MS} ile sınırlar; ≤0 (kalıcı/geçersiz) değerlere dokunmaz. */
    public static long clampDuration(long millis) {
        return millis > MAX_DURATION_MS ? MAX_DURATION_MS : millis;
    }

    /**
     * value × unitMillis; pozitif taşma/üst sınır aşımı {@link #MAX_DURATION_MS}'e kırpılır.
     * Negatif taşmada {@link ArithmeticException} atar (çağıran geçersiz süre sayar).
     */
    public static long fromUnits(long value, long unitMillis) {
        if (value > 0 && unitMillis > 0 && value > MAX_DURATION_MS / unitMillis) {
            return MAX_DURATION_MS;
        }
        return clampDuration(Math.multiplyExact(value, unitMillis));
    }

    /** Geçici ceza bitişi: {@code now + clampDuration(duration)} — taşmaz (now gerçek epoch ms). */
    public static long expiryFrom(long now, long durationMillis) {
        return now + clampDuration(durationMillis);
    }

    public static String toLiteBans(long millis) {
        millis = clampDuration(millis);
        if (millis <= 0) {
            return "1s";
        }
        long seconds = millis / 1000 + (millis % 1000 == 0 ? 0 : 1);
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }
}
