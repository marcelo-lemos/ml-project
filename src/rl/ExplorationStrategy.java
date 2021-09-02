package rl;

import java.util.Map;

public interface ExplorationStrategy {

    public String selectAction(Map<String, Double> actionsValues);

    public void concludeEpisode();

}
