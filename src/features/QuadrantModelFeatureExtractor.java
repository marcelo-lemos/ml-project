package features;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rts.GameState;
import rts.units.Unit;
import rts.units.UnitType;

/**
 * Extract features from a microRTS {@link GameState} using the IJCAI-18 paper
 * model, where most features are related to material advantage in each map
 * quadrant. See:
 * 
 * Tavares, Anbalagan, Marcolino, and Chaimowicz. Algorithms or Actions? A Study
 * in Large-Scale Reinforcement Learning. In IJCAI 2018, pages 2717--2723.
 * 
 * @author anderson
 *
 */
public class QuadrantModelFeatureExtractor extends FeatureExtractor {
    int numQuadrants;

    public QuadrantModelFeatureExtractor(int numQuadrants) {
        this.numQuadrants = numQuadrants;

    }

    /**
     * Initializes the features from the given state. Feature values are zero, but
     * max and min are set accordingly
     * 
     * @param state
     * @return
     */
    private Map<String, Feature> initializeFeatures(GameState state) {
        // initializes the features (values will be set within getFeatures)
        Map<String, Feature> features = new HashMap<>();

        // adds the 'global' features
        features.put(FeatureNames.RESOURCES_OWN, new Feature(FeatureNames.RESOURCES_OWN, 0, 0, 20));
        features.put(FeatureNames.RESOURCES_OPP, new Feature(FeatureNames.RESOURCES_OPP, 0, 0, 20));
        features.put(FeatureNames.GAME_TIME, new Feature(FeatureNames.GAME_TIME, 0, 0, 3000));
        features.put(FeatureNames.BIAS, new Feature(FeatureNames.BIAS, 1, 0, 1));

        // adds the 'per-quadrant' features
        int horizQuadLength = state.getPhysicalGameState().getWidth() / numQuadrants;
        int vertQuadLength = state.getPhysicalGameState().getHeight() / numQuadrants;

        int tilesPerQuadrant = horizQuadLength * vertQuadLength;

        // FIXME hardcoded to the HP of the base (which is the highest in microRTS)
        int maxHitPoints = 10;

        // the first two for traverse the quadrants
        for (int horizQuad = 0; horizQuad < numQuadrants; horizQuad++) {
            for (int vertQuad = 0; vertQuad < numQuadrants; vertQuad++) {

                // the third for traverses the players
                for (int player = 0; player < 2; player++) {
                    String healthFeatName = FeatureNames.avgHealthPerQuad(horizQuad, vertQuad, player);

                    features.put(healthFeatName, new Feature(healthFeatName, 0, 0, 1));

                    // the fourth for traverses the unit types
                    for (UnitType type : state.getUnitTypeTable().getUnitTypes()) {
                        if (type.isResource)
                            continue; // ignores resources
                        String countFeatName = FeatureNames.unitsOfTypePerQuad(horizQuad, vertQuad, player, type);
                        features.put(countFeatName, new Feature(countFeatName, 0, 0, tilesPerQuadrant));
                    }
                }
            }
        }

        return features;
    }

    public List<String> getFeatureNames(GameState state) {
        List<String> featureNames = new ArrayList<>();

        // weights are initialized randomly within [-1, 1]
        featureNames.add(FeatureNames.RESOURCES_OWN);
        featureNames.add(FeatureNames.RESOURCES_OPP);
        featureNames.add(FeatureNames.GAME_TIME);
        featureNames.add(FeatureNames.BIAS);

        // adds the 'per-quadrant' features

        // the first two for traverse the quadrants
        for (int horizQuad = 0; horizQuad < numQuadrants; horizQuad++) {
            for (int vertQuad = 0; vertQuad < numQuadrants; vertQuad++) {

                // the third for traverses the players
                for (int player = 0; player < 2; player++) {
                    String healthFeatName = FeatureNames.avgHealthPerQuad(horizQuad, vertQuad, player);

                    featureNames.add(healthFeatName);

                    // the fourth for traverses the unit types
                    for (UnitType type : state.getUnitTypeTable().getUnitTypes()) {
                        if (type.isResource)
                            continue; // ignores resources
                        String countFeatName = FeatureNames.unitsOfTypePerQuad(horizQuad, vertQuad, player, type);
                        featureNames.add(countFeatName);
                    }
                }
            }
        }

        return featureNames;
    }

    public Map<String, Feature> getRawFeatures(GameState state, int player) {

        // receives the features with no values yet (their max and min are initialized,
        // though)
        Map<String, Feature> features = initializeFeatures(state);

        // gets the opponent's index:
        int opponent = 1 - player;

        // divides the map in quadrants
        int horizQuadLength = state.getPhysicalGameState().getWidth() / numQuadrants;
        int vertQuadLength = state.getPhysicalGameState().getHeight() / numQuadrants;

        // for each quadrant, counts the number of units of each type per player
        for (int horizQuad = 0; horizQuad < numQuadrants; horizQuad++) {
            for (int vertQuad = 0; vertQuad < numQuadrants; vertQuad++) {

                // arrays counting the sum of hit points and number of units owned by each
                // player
                float hpSum[] = new float[2];
                int unitCount[] = new int[2];

                // a collection of units in this quadrant:
                Collection<Unit> unitsInQuad = state.getPhysicalGameState().getUnitsInRectangle(
                        horizQuad * horizQuadLength, vertQuad * vertQuadLength, horizQuadLength, vertQuadLength);

                // initializes the unit count of each type and player as zero
                // also initializes the sum of HP of units owned per player as zero
                for (int p = 0; p < 2; p++) { // p for each player
                    // unitCountPerQuad.put(p, new HashMap<>());
                    hpSum[p] = 0;
                    unitCount[p] = 0;

                    /*
                     * for(UnitType type : state.getUnitTypeTable().getUnitTypes()){ //type for each
                     * unit type //unitCountPerQuad.get(p).put(type, 0); if(type.isResource)
                     * continue; //ignores resources
                     * features.put(FeatureNames.unitTypeCountPerQuad(horizQuad, vertQuad, p, type),
                     * 0.0f);
                     * 
                     * }
                     */
                }

                // traverses the list of units in quadrant, incrementing their feature count
                for (Unit u : unitsInQuad) {
                    if (u.getType().isResource)
                        continue; // ignores resources

                    unitCount[u.getPlayer()]++;
                    hpSum[u.getPlayer()] += u.getHitPoints() / (float)u.getType().hp;

                    String name = FeatureNames.unitsOfTypePerQuad(horizQuad, vertQuad, u.getPlayer(), u.getType());

                    // counts and increment the number of the given unit in the current quadrant
                    Feature typeCountPerQuadrant = features.get(name);
                    typeCountPerQuadrant.setValue(1 + typeCountPerQuadrant.getValue());
                    // features.put(name, features.get(name) + 1 );
                }

                // computes the average HP of units owned by each player
                for (int p = 0; p < 2; p++) { // p for each player
                    float avgHP = unitCount[p] != 0 ? hpSum[p] / unitCount[p] : 0;
                    Feature avgHealthInQuad = features.get(FeatureNames.avgHealthPerQuad(horizQuad, vertQuad, p));
                    avgHealthInQuad.setValue(avgHP);
                    // features.put(FeatureNames.avgHealthPerQuad(horizQuad, vertQuad, p), avgHP);
                }

            }
        }

        // sets the resources owned by the players
        features.get(FeatureNames.RESOURCES_OWN).setValue((float) state.getPlayer(player).getResources());
        features.get(FeatureNames.RESOURCES_OPP).setValue((float) state.getPlayer(opponent).getResources());

        // sets game time
        features.get(FeatureNames.GAME_TIME).setValue((float) state.getTime());

        return features;

    }

}
