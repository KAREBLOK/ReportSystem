package com.reportsystem.common.replay.actions;

/**
 * Note block sesi
 */
public class NoteBlockAction extends ReplayAction {
    private static final long serialVersionUID = 1L;

    private final int blockX, blockY, blockZ;
    private final String instrument;
    private final int note; // 0-24

    public NoteBlockAction(int blockX, int blockY, int blockZ, String instrument, int note) {
        super();
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.instrument = instrument;
        this.note = note;
    }

    public int getBlockX() { return blockX; }
    public int getBlockY() { return blockY; }
    public int getBlockZ() { return blockZ; }
    public String getInstrument() { return instrument; }
    public int getNote() { return note; }
}
