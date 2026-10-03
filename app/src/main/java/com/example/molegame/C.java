package com.example.molegame;

/**
 * Tile ids and tuning constants for the digging game.
 */
public final class C {

    private C() {}

    // ---- Tiles -------------------------------------------------------------
    public static final int EMPTY   = 0;   // excavated tunnel (air)
    public static final int SOIL    = 1;   // diggable earth
    public static final int ROCK    = 2;   // obstacle: cannot dig
    public static final int DIAMOND = 3;   // obstacle: cannot dig, reward bonus
    public static final int ROOT    = 4;   // collectible: tree root
    public static final int CARROT  = 5;   // collectible: carrot top in soil
    public static final int POTATO  = 6;   // collectible: potato in soil
    public static final int BUG     = 7;   // collectible: bug
    public static final int WATER   = 8;   // obstacle: water pocket, cannot dig
    public static final int BEDROCK = 9;   // unbreakable border

    public static boolean isDiggable(int t) {
        return t == SOIL || t == ROOT || t == CARROT || t == POTATO || t == BUG;
    }

    /** How much food a collectible tile restores. */
    public static float foodValue(int t) {
        switch (t) {
            case ROOT:    return 18f;
            case CARROT:  return 22f;
            case POTATO:  return 26f;
            case BUG:     return 16f;
            case DIAMOND: return 0f;
            default:      return 0f;
        }
    }

    public static boolean isCollectible(int t) {
        return t == ROOT || t == CARROT || t == POTATO || t == BUG || t == DIAMOND;
    }

    // ---- Grid / world ------------------------------------------------------
    /** Tile edge in pixels. Raised so characters and items read clearly on a phone. */
    public static final int TILE = 48;

    /** Sky band height in tiles - wide enough to show trees, park and corn fields. */
    public static final int SURFACE_ROWS = 16;
    /** Underground rows the mole can dig through. */
    public static final int ROWS = 20;
    public static final int H = SURFACE_ROWS + ROWS;
    public static final int W = 116;                // world width in tiles (~30s run)

    /**
     * Topmost row the mole may ever enter. The soil surface itself starts a couple of
     * rows deeper, so this earth cap guarantees he cannot dig up into the open sky.
     */
    public static final int GROUND_ROW = SURFACE_ROWS;

    // ---- Rules -------------------------------------------------------------
    public static final float MAX_POWER       = 100f;
    /**
     * Digging is expensive enough that a level cannot be finished on the starting
     * charge alone: the mole has to find and eat food on the way. Food is scarce, so
     * steering onto it matters, but the nap mechanic below means running dry only
     * costs a couple of seconds instead of ending the game.
     */
    public static final float DIG_COST        = 0.85f;   // power spent per tile dug
    public static final float MOVE_COST_IDLE  = 0.02f;   // tiny trickle while moving
    public static final float SPEED           = 4.2f;    // tiles per second - gentle pace
    public static final float START_X         = 3.5f;    // mole start position (tiles)
    public static final float START_Y         = 25f;
    public static final int   MOUSE_HOLE_X    = 104;     // finish column
    public static final int   MOUSE_HOLE_ROW  = 27;

    // Scenery bands (world x in tiles): dark forest -> city park -> countryside
    public static final float BAND_FOREST_END  = 52f;
    public static final float BAND_PARK_END    = 104f;
}
