package metabot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ai.abstraction.HeavyRush;
import ai.abstraction.LightRush;
import ai.abstraction.RangedRush;
import ai.abstraction.WorkerRush;
import ai.core.AI;
import ai.core.ParameterSpecification;
import metabot.portfolio.BuildBarracks;
import metabot.portfolio.Expand;
import rts.GameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;

public class EpsilonLightRush extends AI {

    private UnitTypeTable unitTypeTable;

    private Random random;

    // AI portfolio
    private AI lightRush;
    private Map<String, AI> portfolio;

    // Epsilon value and decay
    private double epsilon = 0.3f;
    private double decayRate = 0.995f;
    private String decayType = "exponential";

    public EpsilonLightRush(UnitTypeTable unitTypeTable) {
        this.unitTypeTable = unitTypeTable;
        random = new Random();
        setupPortifolio();
    }

    public EpsilonLightRush(UnitTypeTable unitTypeTable, double epsilon, double decayRate, Random random) {
        this.unitTypeTable = unitTypeTable;
        this.random = random;
        setupPortifolio();
    }

    private void setupPortifolio() {
        lightRush = new LightRush(unitTypeTable);

        portfolio = new HashMap<>();
        portfolio.put("WorkerRush", new WorkerRush(unitTypeTable));
        portfolio.put("RangedRush", new RangedRush(unitTypeTable));
        portfolio.put("HeavyRush", new HeavyRush(unitTypeTable));
        portfolio.put("Expand", new Expand(unitTypeTable));
        portfolio.put("BuildBarracks", new BuildBarracks(unitTypeTable));
    }

    @Override
    public void reset() {
        for (AI ai : portfolio.values()) {
            ai.reset();
        }
    }

    @Override
    public void reset(UnitTypeTable unitTypeTable) {
        this.unitTypeTable = unitTypeTable;
        for (AI ai : portfolio.values()) {
            ai.reset(unitTypeTable);
        }
    }

    @Override
    public AI clone() {
        EpsilonLightRush epsilonLightRush = new EpsilonLightRush(unitTypeTable, epsilon, decayRate, random);
        return epsilonLightRush;
    }

    private AI selectAI() {
        if (random.nextFloat() < epsilon) {
            // Select random AI
            List<String> aiList = new ArrayList<String>(portfolio.keySet());
            String aiName = aiList.get(random.nextInt(aiList.size()));
            return portfolio.get(aiName);
        } else {
            // Select LightRush
            return lightRush;
        }
    }

    private PlayerAction getAIAction(AI ai, int player, GameState state) {
        try {
            return ai.getAction(player, state);
        } catch (Exception e) {
            PlayerAction playerAction = new PlayerAction();
            playerAction.fillWithNones(state, player, 1);
            return playerAction;
        }
    }

    @Override
    public PlayerAction getAction(int player, GameState state) {
        AI ai = selectAI();
        return getAIAction(ai, player, state);
    }

    private void decay() {
        if (decayType == "linear") {
            epsilon -= decayRate;
        } else if (decayType == "exponential") {
            epsilon *= decayRate;
        }
    }

    private void concludeEpisode() {
        decay();
    }

    @Override
    public void gameOver(int winner) {
        concludeEpisode();
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }

}
