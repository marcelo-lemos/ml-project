package metabot;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import ai.core.AI;
import rts.GameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MetaBotGym {

    Logger logger;

    private UnitTypeTable utt;

    List<AI> portfolio;

    public MetaBotGym(UnitTypeTable utt, Properties config) {
        logger = LogManager.getLogger(MetaBotGym.class);

        this.utt = utt;

        String[] members = config
                .getProperty("portfolio.members", "WorkerRush, LightRush, RangedRush, HeavyRush, Expand, BuildBarracks")
                .split(",");
        setUpPortfolio(members);
    }

    private void setUpPortfolio(String[] members) {
        logger.trace("Portfolio members: ", String.join(",", members));

        portfolio = new ArrayList<AI>();

        for (String memberName : members) {
            try {
                AI ai = (AI) Class.forName(memberName).getConstructor(UnitTypeTable.class).newInstance(utt);
                portfolio.add(ai);
            } catch (Exception e) {
                logger.error("Exception while loading ai " + memberName, e);
                throw new RuntimeException(e);
            }
        }
    }

    public PlayerAction getAction(int player, GameState state, int aiIndex) {
        AI ai = portfolio.get(aiIndex);
        try {
            return ai.getAction(player, state);
        } catch (Exception e) {
            logger.error("Exception while getting action in frame #" + state.getTime() + " from "
                    + ai.getClass().getSimpleName(), e);
            logger.error("Defaulting to empty action");
            e.printStackTrace();

            PlayerAction playerAction = new PlayerAction();
            playerAction.fillWithNones(state, player, 1);
            return playerAction;
        }
    }

    public void reset() {
        reset(utt);
    }

    public void reset(UnitTypeTable utt) {
        for (AI ai : portfolio) {
            ai.reset(utt);
        }
    }

}
