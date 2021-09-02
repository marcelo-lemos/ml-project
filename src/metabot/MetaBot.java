package metabot;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ai.core.AI;
import ai.core.ParameterSpecification;
import config.ConfigManager;
import metabot.portfolio.BuildBarracks;
import rl.Sarsa;
import rts.GameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;
import utils.FileNameUtil;
import ai.abstraction.WorkerRush;
import ai.abstraction.LightRush;
import ai.abstraction.RangedRush;
import ai.abstraction.RangedDefense;
import ai.abstraction.HeavyRush;
import metabot.portfolio.Expand;
import ai.PassiveAI;


public class MetaBot extends AI {
    UnitTypeTable myUnitTypeTable = null;

    Logger logger;

    /**
     * Stores MetaBot-related configurations loaded from a .property file
     */
    private Properties config;

    /**
     * An array of AI's, which are used as 'sub-bots' to play the game. In our
     * academic wording, this is the portfolio of algorithms that play the game.
     */
    private Map<String, AI> portfolio;

    private Sarsa learningAgent;

    /**
     * Stores the choices made for debugging purposes
     */
    private List<String> choices;

    private List<Map<String, Double>> qValues;
    private List<Map<String, Float>> features;

    /**
     * Stores the player number to retrieve actions and determine match outcome
     */
    int myPlayerNumber;

    int stickyActions;
    int stickyCount = 0;

    int matchCount = 1;

    // BEGIN -- variables to feed the learning agent
    private GameState previousState;
    private GameState currentState;
    private AI choice;
    double reward;
    // END-- variables to feed the learning agent

    /**
     * Initializes MetaBot with default configurations
     * 
     * @param utt
     */
    public MetaBot(UnitTypeTable utt) {
        // calls the other constructor, specifying the default config file
        this(utt, "metabot.properties");
    }

    /**
     * Initializes MetaBot, loading the configurations from properties
     * 
     * @param utt
     * @param metaBotConfig
     */
    public MetaBot(UnitTypeTable utt, Properties metaBotConfig) {
        myUnitTypeTable = utt;
        config = metaBotConfig;

        logger = LogManager.getLogger(MetaBot.class);

        // Loads the configuration
        String members = config.getProperty("portfolio.members");

        if (members == null) {
            // logger.error("Error while loading configuration from '" + configPath+ "'.
            // Using defaults.", e);
            members = "WorkerRush, LightRush, RangedRush, HeavyRush, Expand, BuildBarracks";
        }

        setupPortifolio(members);

        // Creates the learning agent with the specified portfolio and loaded parameters
        learningAgent = new Sarsa(portfolio, config);

        if (config.containsKey("rl.bin_input")) {
            try {
                learningAgent.loadBin(config.getProperty("rl.bin_input"));
            } catch (IOException e) {
                logger.error("Error while loading weights from " + config.getProperty("rl.input"), e);
                logger.error("Weights initialized randomly.");
                e.printStackTrace();
            }
        }

        stickyActions = Integer.parseInt(config.getProperty("rl.sticky_actions", "100")) - 1;

        reset();
    }

    /**
     * Initializes MetaBot, loading the configurations from the specified file
     * 
     * @param utt
     * @param configPath
     */
    public MetaBot(UnitTypeTable utt, String configPath) {
        myUnitTypeTable = utt;

        logger = LogManager.getLogger(MetaBot.class);

        // loads the configuration
        String members;
        try {
            config = ConfigManager.loadConfig(configPath);
            members = config.getProperty("portfolio.members");
        } catch (IOException e) {
            logger.error("Error while loading configuration from '" + configPath + "'. Using defaults.", e);

            members = "WorkerRush, LightRush, RangedRush, HeavyRush, Expand, BuildBarracks";
        }

        setupPortifolio(members);

        // creates the learning agent with the specified portfolio and loaded parameters
        learningAgent = new Sarsa(portfolio, config);

        if (config.containsKey("rl.bin_input")) {
            try {
                learningAgent.loadBin(config.getProperty("rl.bin_input"));
            } catch (IOException e) {
                logger.error("Error while loading weights from " + config.getProperty("rl.input"), e);
                logger.error("Weights initialized randomly.");
                e.printStackTrace();
            }
        }

        // else if (config.containsKey("rl.workingdir")) {
        // String dir = config.getProperty("rl.workingdir");
        // if (dir.charAt(dir.length()-1) != '/') {
        // dir = dir + "/";
        // }
        // try {
        // learningAgent.loadBin(dir + "weights_" + myPlayerNumber + ".bin");
        // } catch (IOException e) {
        // logger.error("Error while loading weights from " + dir + "weights_" +
        // myPlayerNumber + ".bin", e);
        // logger.error("Weights initialized randomly.");
        // e.printStackTrace();
        // }
        // }

        reset();
    }

    /**
     * Saves the weight 'vector' to a file in the specified path by serializing the
     * weights HashMap. The file is overridden if already exists.
     * 
     * @param path
     * @throws IOException
     */
    // public void saveBin(String path) throws IOException {
    // learningAgent.saveBin(path);
    // }

    /**
     * Saves the weights in human-readable (csv) format. Creates one file for each
     * portfolio member and appends a line with the weights separated by comma. The
     * order of weights is as given by weights.get(portfolioMember).values()
     *
     * @param prefix
     * @throws IOException
     */
    // public void saveHuman(String prefix) throws IOException {
    // learningAgent.saveHuman(prefix);
    // }

    /**
     * Loads the weight 'vector' from a file in the specified path by de-serializing
     * the weights HashMap
     * 
     * @param path
     * @throws IOException
     */
    // public void loadBin(String path) throws IOException {
    // learningAgent.loadBin(path);
    // }

    private void setupPortifolio(String members) {
        String[] memberNames = members.split(",");
        logger.trace("Portfolio members: ", String.join(",", memberNames));

        // loads the portfolio according to the file specification
        portfolio = new HashMap<>();

        // TODO get rid of this for-switch and do something like
        // https://stackoverflow.com/a/6094609/1251716
        for (String name : memberNames) {
            name = name.trim();

            if (name.equalsIgnoreCase("WorkerRush")) {
                portfolio.put("WorkerRush", new WorkerRush(myUnitTypeTable));
            } else if (name.equalsIgnoreCase("LightRush")) {
                portfolio.put("LightRush", new LightRush(myUnitTypeTable));
            } else if (name.equalsIgnoreCase("RangedRush")) {
                portfolio.put("RangedRush", new RangedRush(myUnitTypeTable));
            } else if (name.equalsIgnoreCase("RangedDefense")) {
                portfolio.put("RangedDefense", new RangedDefense(myUnitTypeTable));
            } else if (name.equalsIgnoreCase("HeavyRush")) {
                portfolio.put("HeavyRush", new HeavyRush(myUnitTypeTable));
            } else if (name.equalsIgnoreCase("Expand")) {
                portfolio.put("Expand", new Expand(myUnitTypeTable));
            } else if (name.equalsIgnoreCase("BuildBarracks")) {
                portfolio.put("BuildBarracks", new BuildBarracks(myUnitTypeTable));
            } else if (name.equalsIgnoreCase("PassiveAI")) {
                portfolio.put("PassiveAI", new PassiveAI(myUnitTypeTable));
            }

            else
                throw new RuntimeException("Unknown portfolio member '" + name + "'");
        }
    }

    public void preGameAnalysis(GameState gs, long milliseconds) throws Exception {

    }

    public void preGameAnalysis(GameState gs, long milliseconds, String readWriteFolder) throws Exception {

    }

    /**
     * Resets the portfolio with the new unit type table
     */
    public void reset(UnitTypeTable utt) {
        myUnitTypeTable = utt;
        myPlayerNumber = -1;
        stickyCount = 0;
        first = true;
        for (AI ai : portfolio.values()) {
            ai.reset(utt);
        }

        reset();

    }

    /**
     * Is called at the beginning of every game. Resets all AIs in the portfolio and
     * my internal variables. It does not reset the weight vector
     */
    public void reset() {
        for (AI ai : portfolio.values()) {
            ai.reset();
        }

        choice = null;
        previousState = null;
        currentState = null;
        myPlayerNumber = -1;
        stickyCount = 0;
        first = true;
        learningAgent.resetChoice();

        choices = new ArrayList<>(3000);
        qValues = new ArrayList<>();
        features = new ArrayList<>();

    }

    boolean first = true;

    public PlayerAction getAction(int player, GameState state) {

        // sets to a valid number on the first call
        if (myPlayerNumber == -1) {
            myPlayerNumber = player;
        }

        // verifies if the number I set previously holds
        if (myPlayerNumber != player) {
            throw new RuntimeException(
                    "Called with wrong player number " + player + ". Was expecting: " + myPlayerNumber);
        }

        // makes the learning agent learn
        if (stickyCount == 0) {
            stickyCount = stickyActions;
            previousState = currentState;
            currentState = state.clone();
            if (previousState != null)
                reward = 0;
            if (state.gameover()) {
                if (state.winner() == player)
                    reward = 1;
                if (state.winner() == 1 - player)
                    reward = -1;
                else
                    reward = 0;
            }
            learningAgent.learn(previousState, choice, reward, currentState, currentState.gameover(), player);

        } else {
            stickyCount--;
        }
        // selected is the AI that will perform our action, let's try it:
        choice = learningAgent.act(state, player);

        choices.add(choice.getClass().getSimpleName());

        if (matchCount > 1 || !first) {
            first = false;
            Map<String, Double> qs = learningAgent.getQValues(state, player);
            qValues.add(qs);

            Map<String, Float> feat = learningAgent.getFeatures(state, player);
            features.add(feat);
        } else {
            first = false;
        }

        try {
            return choice.getAction(player, state);
        } catch (Exception e) {
            logger.error("Exception while getting action in frame #" + state.getTime() + " from "
                    + choice.getClass().getSimpleName(), e);
            logger.error("Defaulting to empty action");
            e.printStackTrace();

            PlayerAction pa = new PlayerAction();
            pa.fillWithNones(state, player, 1);
            return pa;
        }
    }

    public void gameOver(int winner) throws Exception {
        if (winner == -1)
            reward = 0; // game not finished (timeout) or draw
        else if (winner == myPlayerNumber)
            reward = 1; // I won
        else
            reward = -1; // I lost

        learningAgent.learn(currentState, choice, reward, null, true, myPlayerNumber);

        // tests whether the output prefix has been specified to save the weights
        // (binary)
        // if (config.containsKey("rl.output.binprefix")) {
        // String filename = FileNameUtil.nextAvailableFileName(
        // config.getProperty("rl.output.binprefix"), "weights"
        // );
        // learningAgent.saveBin(filename);
        // }

        // tests whether the output prefix has been specified to save the weights
        // (human-readable)
        // if (config.containsKey("rl.output.humanprefix")) {
        // learningAgent.saveHuman(config.getProperty("rl.output.humanprefix"));
        // }

        if (config.containsKey("rl.save_weights_bin")) {
            if (config.getProperty("rl.save_weights_bin").equalsIgnoreCase("True")) {
                String dir = config.getProperty("rl.workingdir", "weights/");
                if (dir.charAt(dir.length() - 1) != '/') {
                    dir = dir + "/";
                }

                learningAgent.saveBin(dir + "weights_" + myPlayerNumber + ".bin");
            }
        }

        if (config.containsKey("rl.save_weights_human")) {
            if (config.getProperty("rl.save_weights_human").equalsIgnoreCase("True")) {
                String dir = config.getProperty("rl.workingdir", "weights/");
                if (dir.charAt(dir.length() - 1) != '/') {
                    dir = dir + "/";
                }

                learningAgent.saveHuman(dir + "weights_" + myPlayerNumber);
            }
        }

        // check if it needs to save the choices
        // if (config.containsKey("output.choices_prefix")) {

        //     String dir = config.getProperty("rl.workingdir");
        //     if (dir.charAt(dir.length() - 1) != '/') {
        //         dir = dir + "/" + config.getProperty("output.choices_prefix");
        //     } else {
        //         dir = dir + config.getProperty("output.choices_prefix");
        //     }

        //     // finds the file name
        //     String filename = FileNameUtil.nextAvailableFileName(dir,
        //             "choices");

        //     // saves the weights
        //     FileWriter writer = new FileWriter(filename);
        //     writer.write(String.join("\n", choices));
        //     writer.close();
        // }

        // if (config.containsKey("output.qvalues_prefix")) {

        //     String dir = config.getProperty("rl.workingdir");
        //     if (dir.charAt(dir.length() - 1) != '/') {
        //         dir = dir + "/" + config.getProperty("output.qvalues_prefix");
        //     } else {
        //         dir = dir + config.getProperty("output.qvalues_prefix");
        //     }

        //     // finds the file name
        //     String filename = FileNameUtil.nextAvailableFileName(dir,
        //             "csv");

        //     // saves the weights
        //     FileWriter writer = new FileWriter(filename);
        //     writer.write("Match,Frame,Action,Q-Value\n");
        //     for (int i = 1; i <= qValues.size(); i++) {
        //         for (String key : qValues.get(i - 1).keySet()) {
        //             if (matchCount == 1) {
        //                 writer.write(String.format(Locale.US, "%d,%d,%s,%f\n", matchCount, i + 1, key, qValues.get(i - 1).get(key)));
        //             } else {
        //                 writer.write(String.format(Locale.US, "%d,%d,%s,%f\n", matchCount, i, key, qValues.get(i - 1).get(key)));
        //             }
        //         }
        //     }
        //     writer.close();
        // }

        // if (config.containsKey("output.features_prefix")) {

        //     String dir = config.getProperty("rl.workingdir");
        //     if (dir.charAt(dir.length() - 1) != '/') {
        //         dir = dir + "/" + config.getProperty("output.features_prefix");
        //     } else {
        //         dir = dir + config.getProperty("output.features_prefix");
        //     }

        //     // finds the file name
        //     String filename = FileNameUtil.nextAvailableFileName(dir,
        //             "csv");

        //     // saves the weights
        //     FileWriter writer = new FileWriter(filename);
        //     writer.write("Match,Frame,Feature,Value\n");
        //     for (int i = 1; i <= features.size(); i++) {
        //         for (String key : features.get(i - 1).keySet()) {
        //             if (matchCount == 1) {
        //                 writer.write(String.format(Locale.US, "%d,%d,%s,%f\n", matchCount, i + 1, key, features.get(i - 1).get(key)));
        //             } else {
        //                 writer.write(String.format(Locale.US, "%d,%d,%s,%f\n", matchCount, i, key, features.get(i - 1).get(key)));
        //             }
        //         }
        //     }
        //     writer.close();
        // }
        matchCount++;
    }

    public AI clone() {
        // FIXME copy features, weights and other attributes!
        return new MetaBot(myUnitTypeTable);
    }

    // This will be called by the microRTS GUI to get the
    // list of parameters that this bot wants exposed
    // in the GUI.
    public List<ParameterSpecification> getParameters() {
        return new ArrayList<>();
    }

}
