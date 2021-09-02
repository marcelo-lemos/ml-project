package players;

import java.util.Arrays;

import ai.abstraction.HeavyRush;
import ai.abstraction.LightRush;
import ai.abstraction.RangedRush;
import ai.abstraction.WorkerRush;
import ai.asymmetric.GAB.SandBox.GABScriptChoose;
import rts.units.UnitTypeTable;

/**
 * Encapsulates the creation of a standard instance of GAB
 * @author artavares
 *
 */
public class GAB extends GABScriptChoose {
	public GAB(UnitTypeTable types) {
		super(
			types, 200, 1, 2, 
			Arrays.asList(new WorkerRush(types), new LightRush(types), new RangedRush(types), new HeavyRush(types)),
			"GAB"
		);
	}
}
