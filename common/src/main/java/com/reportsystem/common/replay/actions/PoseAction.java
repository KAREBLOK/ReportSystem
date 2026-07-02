package com.reportsystem.common.replay.actions;

public class PoseAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    public enum PoseType {
        STANDING,       // Normal duruş
        FALL_FLYING,    // Elytra ile uçma
        SLEEPING,       // Yatakta yatma
        SWIMMING,       // Yüzme pozu
        SPIN_ATTACK,    // Trident spin attack
        SNEAKING,       // Eğilme (alternatif)
        LONG_JUMPING,   // Uzun atlama (kedi/tilki)
        DYING,          // Ölme animasyonu
        CROAKING,       // Kurbağa
        USING_TONGUE,   // Kurbağa dil
        SITTING,        // Oturma (kurt/kedi)
        ROARING,        // Kükreme
        SNIFFING,       // Koklama
        EMERGING,       // Warden çıkışı
        DIGGING         // Armadillo kazma
    }

    private final PoseType poseType;

    public PoseAction(PoseType poseType) {
        super();
        this.poseType = poseType;
    }

    public PoseType getPoseType() { return poseType; }
}