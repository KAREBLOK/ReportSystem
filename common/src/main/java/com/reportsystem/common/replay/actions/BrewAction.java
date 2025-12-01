package com.reportsystem.common.replay.actions;

/**
 * Brewing stand kullanımı (potion brewing)
 */
public class BrewAction extends ReplayAction {

    private final int x, y, z;
    private final String ingredient;   // Brewing ingredient (BLAZE_POWDER, NETHER_WART, etc.)
    private final String[] resultPotions; // Serialized ItemStacks of result potions

    public BrewAction(int x, int y, int z, String ingredient, String[] resultPotions) {
        super();
        this.x = x;
        this.y = y;
        this.z = z;
        this.ingredient = ingredient;
        this.resultPotions = resultPotions != null ? resultPotions : new String[3];
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public String getIngredient() { return ingredient; }
    public String[] getResultPotions() { return resultPotions; }
}
