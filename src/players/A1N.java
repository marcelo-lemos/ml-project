package players;

import java.util.Arrays;

import ai.RandomBiasedAI;
import ai.CMAB.CmabNaiveMCTS;
import ai.abstraction.HeavyRush;
import ai.abstraction.LightRush;
import ai.abstraction.RangedRush;
import ai.abstraction.WorkerRush;
import ai.evaluation.SimpleSqrtEvaluationFunction3;
import rts.units.UnitTypeTable;

/**
 * Encapsulates the creation of a standard instance of A1N
 * @author artavares
 *
 */
public class A1N extends CmabNaiveMCTS {

	public A1N (UnitTypeTable types) {
		super (
			100, -1, 50, 10, 0.3f, 0.0f, 0.4f, 0, new RandomBiasedAI(types),
			new SimpleSqrtEvaluationFunction3(), true, "CmabCombinatorialGenerator", types, 
			Arrays.asList(new WorkerRush(types), new LightRush(types), new RangedRush(types), new HeavyRush(types)), 
			"A1N"
        );
	}
}
