package test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import features.Feature;
import features.QuadrantModelFeatureExtractor;
import rts.GameState;
import rts.PhysicalGameState;
import rts.units.UnitTypeTable;

public class TestQuadrantModelFeatureExtractor {

	@Before
	public void setUp() throws Exception {
	}
	
	@Test
	/**
	 * Test if the set of feature names is correct
	 */
	public void testGetFeatureNames() {
		
		/*
		 * - Copy the microRTS map file 'basesWorkers24x24.xml' to the test folder
		 * - Create an instance of microRTS GameState, loading the map you stored.
		 * - The feature extractor must extract 130 features (that's the length we'll test)
		 * - Within the set of features, you must find the following names:
		 * -- "resources_own", "resources_opp", "game_time", "bias" (as per the FeatureNames class)
		 * 
		 * - Moreover, we must test the existence of feature names with the following pattern:
		 * -- "unit_count-x-y-p-t", where: 
		 * --- x and y are in the set {0, 1, 2} (they are quadrant indexes)
		 * --- p is in the set {0, 1} (player index)
		 * --- t is the name of a unit type, except resources (they are in the UnitTypeTable class of microRTS)
		 * 
		 * - Finally, we must thest the existence of feature names with the following pattern:
		 * -- "avg_health-x-y-p", where:
		 * --- x and y are in the set {0, 1, 2} (they are quadrant indexes)
		 * --- p is in the set {0, 1} (player index)
		 */
		UnitTypeTable types = new UnitTypeTable(UnitTypeTable.VERSION_ORIGINAL_FINETUNED);
		GameState state = null;
		try {
			state = new GameState(PhysicalGameState.load("maps/test/basesWorkers24x24.xml", types),types);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load game state");
		}
		
		QuadrantModelFeatureExtractor featureExtractor = new QuadrantModelFeatureExtractor(3);
		
		List<String> featureNames = featureExtractor.getFeatureNames(state);
		assertTrue(featureNames.contains("resources_own"));
		assertEquals(130, featureNames.size()); //is giving 130
		
		
		fail("Not yet implemented"); // TODO
	}

	@Test
	/**
	 * Test if the method is returning a feature vector with the 
	 * correct length as well as the correct values for a given state
	 */
	public void testGetRawFeatures() {
		/*
		 * Copy a microRTS map file to the test folder
		 * 
		 * Create an instance of microRTS GameState, loading the map you stored.
		 * Manually count the feature values and check whether the 
		 * feature extractor is loading them correctly
		 */
		UnitTypeTable types = new UnitTypeTable(UnitTypeTable.VERSION_ORIGINAL_FINETUNED);
		GameState state = null;
		try {
			state = new GameState(PhysicalGameState.load("maps/test/basesWorkers24x24.xml", types),types);
		} catch (Exception e) {
			e.printStackTrace();
			fail("Failed to load game state");
		}
		
		QuadrantModelFeatureExtractor featureExtractor = new QuadrantModelFeatureExtractor(3);
		
		Map<String, Feature> features = featureExtractor.getFeatures(state, 0);
		assertEquals(130, features.size()); 
		fail("Not yet implemented"); // TODO
	}

	

}
