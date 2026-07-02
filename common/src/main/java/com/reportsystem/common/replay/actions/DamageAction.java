package com.reportsystem.common.replay.actions;

public class DamageAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum DamageType {
        FALL,           // Düşme
        ATTACK,         // Saldırı
        FIRE,           // Ateş/Lav
        DROWNING,       // Boğulma
        CONTACT,        // Kaktüs, sweet berry bush
        HOT_FLOOR,      // Magma block
        CAMPFIRE,       // Kamp ateşi
        WITHER,         // Wither rose, wither etkisi
        POISON,         // Zehir
        STARVATION,     // Açlık
        SUFFOCATION,    // Sıkışma
        VOID,           // Boşluk
        FLY_INTO_WALL,  // Elytra ile duvara çarpma
        CRAMMING,       // Mob sıkışması
        THORNS,         // Diken büyüsü
        MAGIC,          // Instant damage iksiri
        EXPLOSION,      // Patlama
        OTHER           // Diğer
    }

    private final DamageType damageType;
    private final double damageAmount;
    private final boolean playSound;

    public DamageAction(DamageType damageType, double damageAmount, boolean playSound) {
        super();
        this.damageType = damageType;
        this.damageAmount = damageAmount;
        this.playSound = playSound;
    }

    public DamageType getDamageType() { return damageType; }
    public double getDamageAmount() { return damageAmount; }
    public boolean shouldPlaySound() { return playSound; }
}