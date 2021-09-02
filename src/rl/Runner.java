package rl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ai.core.AI;
import config.ConfigManager;
import metabot.MetaBot;
import metabot.EpsilonLightRush;
import rts.GameSettings;
import rts.GameState;
import rts.PartiallyObservableGameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.Trace;
import rts.TraceEntry;
import rts.units.UnitTypeTable;
import utils.FileNameUtil;

/**
 * A class to run microRTS games to train and test MetaBot
 * @author anderson
 */
public class Runner {

    public static final int MATCH_ERROR = 2;
    public static final int DRAW = -1;
    public static final int P1_WINS = 0;
    public static final int P2_WINS = 1;

    private static final Logger logger = LogManager.getRootLogger();

    public static void main(String[] args) throws Exception {

        EpsilonLightRush ep;

        // Argument parser
        Options options = new Options();

        // Runner command line options
        options.addOption("c", "config", true, "config file");
        options.addOption("o", "output", true, "output file");

        // Player 1 command line options
        options.addOption("s1", "seed1", true, "player 1 seed number");
        options.addOption("d1", "directory1", true, "player 1 working directory");
        options.addOption("b1", "binprefix1", false, "player 1 save binary weights");
        options.addOption("h1", "humanprefix1", false, "player 1 save human weights");
        options.addOption("bi1", "bininput1", true, "player 1 binary input");
        options.addOption("sd", "stickyduration", true, "sticky actions duration");

        // Player 2 command line options
        options.addOption("s2", "seed2", true, "player 2 seed number");
        options.addOption("d2", "directory2", true, "player 2 working directory");
        options.addOption("b2", "binprefix2", false, "player 2 save binary weights");
        options.addOption("h2", "humanprefix2", false, "player 2 save human weights");
        options.addOption("bi2", "bininput2", true, "player 2 binary input");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);

        String configFile;
        if (cmd.hasOption("c")) {
            logger.info("Loading experiment configuration from {}", cmd.getOptionValue("c"));
            configFile = cmd.getOptionValue("c");
        } else {
            logger.info("Input not specified, reading from 'config/microrts.properties'");
            logger.info("args: " + Arrays.toString(args));
            configFile = "config/microrts.properties";
        }

        // Load properties from file
        Properties prop = new Properties();
        prop = ConfigManager.loadConfig(configFile);

        if (cmd.hasOption("o")) {
            logger.debug("Outputting to {}", cmd.getOptionValue("o"));
            prop.setProperty("runner.output", cmd.getOptionValue("o"));
        }

        // Load and shows game settings
        GameSettings settings = GameSettings.loadFromConfig(prop);
        logger.info(settings);

        UnitTypeTable utt = new UnitTypeTable(settings.getUTTVersion(), settings.getConflictPolicy());
        AI ai1 = loadAI(settings.getAI1(), utt, 1, prop, cmd);
        AI ai2 = loadAI(settings.getAI2(), utt, 2, prop, cmd);

        int numGames = Integer.parseInt(prop.getProperty("runner.num_games", "1"));

        for (int i = 0; i < numGames; i++) {

            // determines the trace output file. It is either null or the one calculated from the specified prefix
            String traceOutput = null;

            if (prop.containsKey("runner.trace_prefix")) {
                // finds the file name
                traceOutput = FileNameUtil.nextAvailableFileName(
                    prop.getProperty("runner.trace_prefix"), "trace.zip"
                );
            }

            Date begin = new Date(System.currentTimeMillis());
            int result = headlessMatch(ai1, ai2, settings, utt, traceOutput);
            Date end = new Date(System.currentTimeMillis());

            System.out.print(String.format("\rMatch %8d finished with result %3d.", i+1, result));
            // logger.info(String.format("Match %8d finished.", i+1));

            long duration = end.getTime() - begin.getTime();

            if (prop.containsKey("runner.output")) {
                try {
                    outputSummary(prop.getProperty("runner.output"), result, duration, begin, end);
                } catch(IOException ioe) {
                    logger.error("Error while trying to write summary to '" + prop.getProperty("runner.output") + "'", ioe);
                }
            }

            ai1.reset();
            ai2.reset();
        }

        System.out.println(); // adds a trailing \n to the match count written in the loop.
        logger.info("Executed " + numGames + " matches.");
    }

    /**
     * Runs a match between two AIs with the specified settings, without the GUI.
     * Saves the trace to re-play the match if traceOutput is not null
     * @param ai1
     * @param ai2
     * @param config
     * @param types
     * @param traceOutput
     * @return
     * @throws Exception
     */
    public static int headlessMatch(
            AI ai1,
            AI ai2,
            GameSettings config,
            UnitTypeTable types,
            String traceOutput
            ) throws Exception {
        PhysicalGameState pgs;
        Logger logger = LogManager.getRootLogger();
        try {
            pgs = PhysicalGameState.load(config.getMapLocation(), types);
        } catch (Exception e) {
            logger.error("Error while loading map from file: " + config.getMapLocation(), e);
            //e.printStackTrace();
            logger.error("Aborting match execution...");
            return MATCH_ERROR;
        }

        GameState state = new GameState(pgs, types);

        // creates the trace logger
        Trace replay = new Trace(types);

        boolean gameover = false;

        while (!gameover && state.getTime() < config.getMaxCycles()) {

            // initializes state equally for the players
            GameState player1State = state;
            GameState player2State = state;

            // places the fog of war if the state is partially observable
            if (config.isPartiallyObservable()) {
                player1State = new PartiallyObservableGameState(state, 0);
                player2State = new PartiallyObservableGameState(state, 1);
            }

            // retrieves the players' actions
            PlayerAction player1Action = ai1.getAction(0, player1State);
            PlayerAction player2Action = ai2.getAction(1, player2State);

            // creates a new trace entry, fills the actions and stores it
            TraceEntry thisFrame = new TraceEntry(state.getPhysicalGameState().clone(), state.getTime());
            if (!player1Action.isEmpty()) {
                thisFrame.addPlayerAction(player1Action.clone());
            }
            if (!player2Action.isEmpty()) {
                thisFrame.addPlayerAction(player2Action.clone());
            }
            replay.addEntry(thisFrame);


            // issues the players' actions
            state.issueSafe(player1Action);
            state.issueSafe(player2Action);

            // runs one cycle of the game
            gameover = state.cycle();
        }
        ai1.gameOver(state.winner());
        ai2.gameOver(state.winner());

        //traces the final state
        replay.addEntry(new TraceEntry(state.getPhysicalGameState().clone(), state.getTime()));

        // writes the trace (replay)
        if (traceOutput != null){
			// creates missing parent directories if needed
			File f = new File(traceOutput);
    		if (f.getParentFile() != null) {
    			  f.getParentFile().mkdirs();
			}
    		
    		// ensures that traceOutput ends with a .zip
    		if (! traceOutput.endsWith(".zip")) {
    			traceOutput += ".zip";
    		}
    		
    		// writes the zipped trace file (much smaller)
    		replay.toZip(traceOutput);
    		
		}

        return state.winner();
    }

    public static void outputSummary(
            String path,
            int result,
            long duration,
            Date start,
            Date finish
            ) throws IOException {
        File f = new File(path);
        FileWriter writer;
        Logger logger = LogManager.getRootLogger();
        logger.debug("Attempting to write the output summary to {}", path);

        if (!f.exists()) { // creates a new file and writes the header
            logger.debug("File didn't exist, creating and writing header");
            writer = new FileWriter(f, false); //must be after the test, because it creates the file upon instantiation
            writer.write("#result,duration(ms),initial_time,final_time\n");
            writer.close();
        }

        // appends one line with each weight value separated by a comma
        writer = new FileWriter(f, true);
        writer.write(String.format("%d,%d,%s,%s\n", result, duration, start, finish));
        logger.debug("Successfully wrote to {}", path);

        writer.close();
    }

    /**
     * Loads an {@link AI} according to its name, using the provided UnitTypeTable.
     * If the AI is {@link MetaBot}, loads it with the configuration file specified in
     * entry 'metabot.config' of the received {@link Properties}
     * @param aiName
     * @param utt
     * @param playerNumber
     * @return
     * @throws NoSuchMethodException
     * @throws SecurityException
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InvocationTargetException
     */
    public static AI loadAI(
            String aiName,
            UnitTypeTable utt,
            int playerNumber,
            Properties config,
            CommandLine cmd
            ) throws NoSuchMethodException,
            SecurityException,
            ClassNotFoundException,
            InstantiationException,
            IllegalAccessException,
            IllegalArgumentException,
            InvocationTargetException {
        AI ai;

        Logger logger = LogManager.getRootLogger();
        logger.info("Loading {}", aiName);

        // (custom) loads MetaBot with its configuration file
        if (aiName.equalsIgnoreCase("metabot.MetaBot")) {

            String configKey = String.format("player%d.config", playerNumber);
            if(config.containsKey(configKey)){
                String configPath = config.getProperty(configKey);
                Properties metaBotConfig;
                try {
                    // Load Metabot config file
                    metaBotConfig = ConfigManager.loadConfig(configPath);

                    // Update config with command line arguments
                    String opt = String.format("s%d", playerNumber);
                    if (cmd.hasOption(opt)) {
                        String value = cmd.getOptionValue(opt);
                        logger.info("Updating player {} seed to {}", playerNumber, value);
                        metaBotConfig.setProperty("rl.random.seed", value);
                    }
                    
                    opt = String.format("d%d", playerNumber);
                    if (cmd.hasOption(opt)) {
                        String value = cmd.getOptionValue(opt);
                        logger.info("Updating player {} working directory to {}", playerNumber, value);
                        metaBotConfig.setProperty("rl.workingdir", value);
                    }

                    opt = String.format("b%d", playerNumber);
                    if (cmd.hasOption(opt)) {
                        logger.info("Setting player {} 'save binary weights' to true", playerNumber, cmd.getOptionValue(opt));
                        metaBotConfig.setProperty("rl.save_weights_bin", "true");
                    }

                    opt = String.format("h%d", playerNumber);
                    if (cmd.hasOption(opt)) {
                        logger.info("Setting player {} 'save human weights' to true", playerNumber, cmd.getOptionValue(opt));
                        metaBotConfig.setProperty("rl.save_weights_human", "true");
                    }

                    opt = String.format("bi%d", playerNumber);
                    if (cmd.hasOption(opt)) {
                        String value = cmd.getOptionValue(opt);
                        logger.info("Setting player {} binary input to {}", playerNumber, value);
                        metaBotConfig.setProperty("rl.bin_input", value);
                    }

                    if (cmd.hasOption("sd")) {
                        String value = cmd.getOptionValue("sd");
                        logger.info("Setting sticky actions duration to {}", value);
                        metaBotConfig.setProperty("rl.sticky_actions", value);
                    } else {
                        logger.info("Nope");
                    }

                    // Load AI
                    ai = new MetaBot(utt, metaBotConfig);
                } catch (IOException e) {
                    logger.error("Error while loading configuration from '" + configPath + "'. Using defaults.", e);
                    ai = new MetaBot(utt);
                }
            }
            else {
                ai = new MetaBot(utt);
            }

        } else { // (default) loads the AI according to its name
            Constructor<?> cons1 = Class.forName(aiName).getConstructor(UnitTypeTable.class);
            ai = (AI)cons1.newInstance(utt);
        }
        return ai;
    }
}

