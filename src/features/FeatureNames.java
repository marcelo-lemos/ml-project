package features;

import rts.units.UnitType;

public class FeatureNames {

    public static final String UNIT_COUNT = "unit_count"; // to be concatenated with quadrant, player and unit type
    public static final String AVG_HEALTH = "avg_health"; // to be concatenated with quadrant and player

    public static final String RESOURCES_OWN = "resources_own";
    public static final String RESOURCES_OPP = "resources_opp";
    public static final String GAME_TIME = "game_time";
    public static final String BIAS = "bias"; // the 'independent term' whose value is always 1

    /**
     * Returns the feature name for unit count, given the quadrant, unit owner and
     * unit type
     * 
     * @param xQuad
     * @param yQuad
     * @param owner
     * @param type
     * @return
     */
    public static String unitsOfTypePerQuad(int xQuad, int yQuad, int owner, UnitType type) {
        // feature name: unit_quad-x-y-owner-type
        return String.format(UNIT_COUNT + "-%d-%d-%d-%s", xQuad, yQuad, owner, type.name);
    }

    /**
     * Returns the corresponding feature name for average unit health, given the
     * quadrant and player
     * 
     * @param xQuad
     * @param yQuad
     * @param player
     * @return
     */
    public static String avgHealthPerQuad(int xQuad, int yQuad, int player) {
        return String.format(AVG_HEALTH + "-%d-%d-%d", xQuad, yQuad, player);
    }

}
