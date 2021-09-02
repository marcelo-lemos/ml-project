package rl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class EpsilonGreedy implements ExplorationStrategy {

    private double epsilon;
    private double decayRate;

    private Random random;

    private static final double PRECISION = 0.000001;

    public EpsilonGreedy(double epsilon, double decayRate, Random random) {
        this.epsilon = epsilon;
        this.decayRate = decayRate;
        this.random = random;
    }

    @Override
    public String selectAction(Map<String, Double> actionsValues) {
        if (random.nextDouble() < epsilon) {
            // Explore
            List<String> actions = new ArrayList<String>(actionsValues.keySet());
            return getRandomAction(actions);
        } else {
            // Exploit
            return getRandomBestAction(actionsValues);
        }
    }

    private String getRandomBestAction(Map<String, Double> actionsValues) {
        List<String> bestActions = getBestActions(actionsValues);
        return getRandomAction(bestActions);
    }

    /**
     * Returns a list containing all actions with max value.
     * @param   actionsValues a map with available actions and their respective values
     * @return  a list containing the IDs of all actions with max value.
     */
    private List<String> getBestActions(Map<String, Double> actionsValues) {
        double maxValue = Collections.max(actionsValues.values());

        List<String> bestActions = new ArrayList<String>();
        for (Map.Entry<String, Double> entry : actionsValues.entrySet()) {
            if (Math.abs(maxValue - entry.getValue()) < PRECISION) {
                bestActions.add(entry.getKey());
            }
        }
        return bestActions;
    }

    /**
     * 
     * @param actions
     * @return
     */
    private String getRandomAction(List<String> actions) {
        int randomIndex = random.nextInt(actions.size());
        return actions.get(randomIndex);
    }

    @Override
    public void concludeEpisode() {
        epsilon *= decayRate;
    }

}
