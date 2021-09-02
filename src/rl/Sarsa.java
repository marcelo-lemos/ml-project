package rl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import ai.core.AI;
import features.Feature;
import features.FeatureExtractor;
import features.QuadrantModelFeatureExtractor;
import rts.GameState;

/**
 * Implements Sarsa(0)
 * 
 * TODO implement Sarsa(lambda) TODO allow loading and saving of weights
 * 
 * @author anderson
 *
 */
public class Sarsa {
    /**
     * Random number generator
     */
    Random random;

    /**
     * For feature calculation, the map will be divided in quadrantDivision x
     * quadrantDivision
     */
    private int quadrantDivision;

    /**
     * Learning rate
     */
    private double alpha;

    /**
     * Decay rate of alpha
     */
    private double alphaDecayRate;

    /**
     * Discount factor
     */
    private double gamma;

    /**
     * Eligibility trace
     */
    private double lambda;

    // private final static Logger logger = Logger.getLogger(Sarsa.class.getName());

    /**
     * Will return the feature values according to state
     */
    private FeatureExtractor featureExtractor;

    /**
     * The 'action' that this learning agent returns, i.e. an AI to perform the game
     * action on behalf of the plaer
     */
    AI nextChoice;

    /**
     * The weight 'vector' is the 'internal' Map from string (feature name) to float
     * (weight value) There's a weight vector per AI, hence the map from string (AI
     * name) to weight vectors
     */
    private Map<String, Map<String, Float>> weights;

    /**
     * An array of AI's, which are used as 'sub-bots' to play the game. In our
     * academic wording, this is the portfolio of algorithms that play the game.
     */
    private Map<String, AI> portfolio;

    private String weightInitMethod;

    private ExplorationStrategy explorationStrategy;

    /**
     * Loads the parameters from a specific Properties object
     * 
     * @param portfolio
     * @param config
     */
    public Sarsa(Map<String, AI> portfolio, Properties config) {
        random = new Random(Integer.parseInt(config.getProperty("rl.random.seed")));

        double epsilon = Double.parseDouble(config.getProperty("rl.epsilon.initial", "0.1"));
        double epsilonDecayRate = Double.parseDouble(config.getProperty("rl.epsilon.decay", "1.0"));
        explorationStrategy = new EpsilonGreedy(epsilon, epsilonDecayRate, random);

        alpha = Double.parseDouble(config.getProperty("rl.alpha.initial", "0.1"));
        alphaDecayRate = Double.parseDouble(config.getProperty("rl.alpha.decay", "1.0"));

        gamma = Double.parseDouble(config.getProperty("rl.gamma", "0.9"));

        lambda = Double.parseDouble(config.getProperty("rl.lambda", "0.0"));

        quadrantDivision = Integer.parseInt(config.getProperty("rl.feature.extractor.quadrant_division", "3"));

        weightInitMethod = config.getProperty("rl.weights.init_method", "fixed_interval");

        // if we want to use a different featureExtractor, must customize this call
        featureExtractor = new QuadrantModelFeatureExtractor(quadrantDivision);

        // weights are initialized in the first call to {@link #getAction} because we
        // require the game map
        weights = null;

        this.portfolio = portfolio;
    }

    /**
     * Initializes the weight vector (to be called at the first game frame) Requires
     * the game state because some features depend on map size
     * 
     * @param gs
     */
    public void initializeWeights(Set<String> aiNames, List<String> featureNames, float min, float max) {
        if (min > max) {
            throw new RuntimeException("Weights min (" + min + ") greater than max (" + max + ")");
        }

        float range = max - min;

        weights = new HashMap<>();
        for (String ai : aiNames) {
            Map<String, Float> aiWeights = new HashMap<>();

            for (String feature : featureNames) {
                aiWeights.put(feature, random.nextFloat() * range + min);
            }

            weights.put(ai, aiWeights);
        }
    }

    /**
     * Returns the AI for the given state and player.
     * 
     * @param state
     * @param player
     * @return
     */
    public AI act(GameState state, int player) {
        // nextChoice is null on the first call to this function, afterwards, it is
        // determined as a side-effect of 'learn'
        if (nextChoice == null) {
            nextChoice = epsilonGreedy(state, player);
        }

        return nextChoice;
    }

    public void resetChoice() {
        nextChoice = null;
    }

    /**
     * Returns an action using epsilon-greedy for the given state (i.e., a random
     * action with probability epsilon, and the greedy action otherwise)
     * 
     * @param state
     * @param player
     * @return
     */
    private AI epsilonGreedy(GameState state, int player) {
        // initializes weights on first frame
        if (weights == null) {
            float weightsMin;
            float weightsMax;

            switch (weightInitMethod) {
                case "fixed_interval":
                    // TODO: remove magic numbers
                    weightsMin = -1;
                    weightsMax = 1;
                    break;
                case "parameterized":
                    int featureCount = featureExtractor.getFeatureNames(state).size();
                    weightsMin = -1 / (float) Math.sqrt(featureCount);
                    weightsMax = 1 / (float) Math.sqrt(featureCount);
                    break;
                default:
                    throw new RuntimeException("Invalid weight initialization method: " + weightInitMethod);
            }

            initializeWeights(portfolio.keySet(), featureExtractor.getFeatureNames(state), weightsMin, weightsMax);
        }


        // will choose the action for this state

        Map<String, Double> qValues = getQValues(state, player);

        String choiceName = explorationStrategy.selectAction(qValues);

        return portfolio.get(choiceName);
    }

    /**
     * Receives an experience tuple (s, a, r, s') and updates the action-value
     * function As a side effect of Sarsa, the next action a' is chosen here.
     * 
     * @param state     s
     * @param choice    a
     * @param reward    r
     * @param nextState s'
     * @param done      whether this is the end of the episode
     * @param player    required to extract the features of this state
     */
    public void learn(GameState state, AI choice, double reward, GameState nextState, boolean done, int player) {

        // ensures all variables are valid (they won't be in the initial state)
        if (state == null || choice == null) {
            return;
        }

        if (!done) {
            // determines the next choice
            nextChoice = epsilonGreedy(nextState, player);

            // applies the update rule with s, a, r, s', a'
            sarsaLearning(state, choice.getClass().getSimpleName(), reward, done, nextState,
                    nextChoice.getClass().getSimpleName(), player);
        } else {
            sarsaLearning(state, choice.getClass().getSimpleName(), reward, done, null, null, player);
        }

        if (done) {
            // decays alpha and epsilon
            alpha *= alphaDecayRate;
            explorationStrategy.concludeEpisode();
        }

    }

    /**
     * Updates the weight vector of the current action (choice) using the Sarsa
     * rule: delta = r + gamma * Q(s',a') - Q(s,a) w_i <- w_i + alpha*delta*f_i
     * (where w_i is the i-th weight and f_i the i-th feature)
     * 
     * @param state      s in Sarsa equation
     * @param choice     a in Sarsa equation
     * @param reward     r in Sarsa equation
     * @param nextState  s' in Sarsa equation
     * @param nextChoice a' in Sarsa equation
     * @param player     required to extract the features for the states
     */
    private void sarsaLearning(GameState state, String choice, double reward, boolean done, GameState nextState,
            String nextChoice, int player) {
        // checks if s' and a' are ok (s and a will always be ok, we hope)
        // if(nextState == null || nextChoice == null) return;

        Map<String, Feature> stateFeatures = featureExtractor.getFeatures(state, player);

        double futureQ;
        if (done) {
            futureQ = 0;
        } else {
            Map<String, Feature> nextStateFeatures = featureExtractor.getFeatures(nextState, player);
            futureQ = Math.max(-1, Math.min(1, qValue(nextStateFeatures, nextChoice)));
        }

        double q = qValue(stateFeatures, choice);

        // the temporal-difference error (delta in Sarsa equation)
        double delta = reward + gamma * futureQ - q;

        for (String featureName : stateFeatures.keySet()) {
            // retrieves the weight value, updates it and stores the updated value
            double oldWeightValue = weights.get(choice).get(featureName);
            double newWeightValue = oldWeightValue + alpha * delta * stateFeatures.get(featureName).getValue();
            weights.get(choice).put(featureName, (float) newWeightValue);
        }
    }

    /**
     * Returns the dot product of features and their respective weights
     * 
     * @param features
     * @param weights
     * @return
     */
    private double dotProduct(Map<String, Feature> features, Map<String, Float> weights) {
        float product = 0.0f;
        for (String featureName : features.keySet()) {
            product += features.get(featureName).getValue() * weights.get(featureName);
        }
        return product;
    }

    /**
     * Returns the Q-value of a choice (action), for a given set of features
     * 
     * @param features
     * @param choice
     * @return
     */
    private double qValue(Map<String, Feature> features, String choice) {
        double value = dotProduct(features, weights.get(choice));
        return Math.max(-1, Math.min(1, value));
    }

    public Map<String, Float> getFeatures(GameState state, int player) {
        Map<String, Feature> raw = featureExtractor.getFeatures(state, player);

        Map<String, Float> features = new HashMap<String, Float>();
        for (String f : raw.keySet()) {
            features.put(f, raw.get(f).getValue());
        }
        return features;
    }

    public Map<String, Double> getQValues(GameState state, int player) {
        Map<String, Feature> stateFeatures = featureExtractor.getFeatures(state, player);

        Map<String, Double> qValues = new HashMap<String, Double>();
        for (String ai : portfolio.keySet()) {
            Double q = qValue(stateFeatures, ai);
            qValues.put(ai, q);
        }

        return qValues;
    }

    /**
     * Returns the Q-value of a state (including player information) and choice
     * (action)
     * 
     * @param state
     * @param player
     * @param choice
     * @return
     */
    private double qValue(GameState state, int player, String choice) {
        return qValue(featureExtractor.getFeatures(state, player), choice);
    }

    /**
     * Saves the weights in human-readable (csv) format. Creates one file for each
     * portfolio member and appends a line with the weights separated by comma. The
     * order of weights is as given by weights.get(portfolioMember).values()
     * 
     * @param prefix
     * @throws IOException
     */
    public void saveHuman(String prefix) throws IOException {
        if (weights == null) {
            throw new RuntimeException("Attempted to save non-initialized weights");
        }

        // creates a file for each AI in the portfolio (they're the keys of the weights
        // map)
        // if the file already exists, the weights will be appended
        for (String aiName : weights.keySet()) {
            File f = new File(prefix + "_" + aiName + ".csv");
            FileWriter writer;

            if (!f.exists()) { // creates a new file and writes the header
                writer = new FileWriter(f, false); // must be after the test, because it creates the file upon
                                                   // instantiation
                writer.write("#" + String.join(",", weights.get(aiName).keySet()) + "\n");
                writer.close();
            }

            // appends one line with each weight value separated by a comma
            writer = new FileWriter(f, true);
            String line = "";
            for (double value : weights.get(aiName).values()) {
                line += "" + value + ", ";
            }
            line = line.replaceAll(",$", ""); // removes the trailing comma

            writer.write(line + "\n");

            writer.close();
        }
    }

    /**
     * Saves the weight 'vector' to a file in the specified path by serializing the
     * weights HashMap. The file is overridden if already exists.
     * 
     * @param path
     * @throws IOException
     */
    public void saveBin(String path) throws IOException {
        if (weights == null) {
            throw new RuntimeException("Attempted to save non-initialized weights");
        }

        FileOutputStream fos = new FileOutputStream(path);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(weights);
        oos.close();
        fos.close();
    }

    /**
     * Loads the weight 'vector' from a file in the specified path by de-serializing
     * the weights HashMap
     * 
     * @param path
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    public void loadBin(String path) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        ObjectInputStream ois = new ObjectInputStream(fis);
        try {
            weights = (Map<String, Map<String, Float>>) ois.readObject();
        } catch (ClassNotFoundException e) {
            System.err.println("Error while attempting to load weights.");
            e.printStackTrace();
        }
        ois.close();
        fis.close();
    }

}
