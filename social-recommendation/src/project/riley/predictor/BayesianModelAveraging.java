package project.riley.predictor;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import org.nicta.lr.util.Constants;

import project.riley.predictor.ArffData.DataEntry;

public class BayesianModelAveraging extends Predictor {

	static Predictor[] predictors;
	static double[][][] weights;
	static Map<Long, Map<Long, Double>>[][] probabilities;

	/*
	 * set up required predictors
	 */
	public static Predictor[] setUp(){
		Predictor soc_matchbox = new SocialRecommender(Constants.SOCIAL);		
		Predictor lr_interactions = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor lr_demographics = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor lr_traits = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor lr_groups = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor lr_pages = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor lr_outgoing = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor lr_incoming = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);		
		Predictor lr_all = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);

		Predictor[] predictors = new Predictor[] {soc_matchbox,lr_interactions,lr_demographics,lr_traits,lr_groups,lr_pages,lr_outgoing,lr_incoming,lr_all};
		return predictors;
	}

	/*
	 * set up arff file based on flag
	 */
	public static ArffData getArff(int flag, int friend, String name){
		// 0 = none
		// 1 = interactions
		// 2 = demographics
		// 3 = traits
		// 4 = groups
		// 5 = pages
		// 6 = outgoing
		// 7 = incoming
		// 8 = all
		ArffData data = new ArffData();
		data.setThreshold(0);
		data.setFriendSize(friend);
		data.setFriends(false);
		data.setInteractions(((flag == 1 || flag == 8) ? true : false));
		data.setDemographics(((flag == 2 || flag == 8) ? true : false));
		data.setTraits(((flag == 3 || flag == 8) ? true : false));
		data.setGroups(((flag == 4 || flag == 8) ? true : false), Launcher.NB_GROUPS_SIZE_OVERRIDE);
		data.setPages(((flag == 5 || flag == 8) ? true : false), Launcher.NB_PAGES_SIZE_OVERRIDE);
		data.setOutgoingMessages(((flag == 6 || flag == 8) ? true : false), Launcher.NB_OUTGOING_SIZE_OVERRIDE);
		data.setIncomingMessages(((flag == 7 || flag == 8) ? true : false), Launcher.NB_INCOMING_SIZE_OVERRIDE);
		data.setFileName(name);

		return data;
	}

	// @author Scott Sanner (ssanner@gmail.com)
	// for a,b given log versions log_a, log_b returns log(a + b)
	public static double LogSum(double log_a, double log_b) {
		double max = Math.max(log_a, log_b);
		double sum = Math.exp(log_a - max) + Math.exp(log_b - max);
		return Math.log(sum) + max;
	}

	public static double sigmoid(double t){
		double k = 1.0;
		return 1.0 / (1.0 + Math.pow(Math.E,(k * -t)));
	}		

	@Override
	public void train() {
		predictors = setUp();		
		weights = new double[predictors.length][Launcher.NUM_FOLDS][2];		// weights for each predictor of wm_0 & wm_1 at each fold
			
		for (int i = 0; i < predictors.length; i++){						// initialise weights
			for (int j = 0; j < Launcher.NUM_FOLDS; j++){
				weights[i][j][0] = 1.0 / predictors.length;
				weights[i][j][1] = 1.0 / predictors.length;
			}
		}
		
		probabilities = new HashMap[predictors.length][Launcher.NUM_FOLDS];
		double[][] normals = new double[Launcher.NUM_FOLDS][2];				// normals

		for (int i = 0; i < predictors.length; i++){			

			System.out.println("BMA training " + predictors[i].getName());
			
			for (int j = 0; j < Launcher.NUM_FOLDS; j++){
				
				System.out.println("Fold " + j);
				
				String trainName = Launcher.DATA_FILE + ".train." + (j+1);
				String testName  = Launcher.DATA_FILE + ".test."  + (j+1);

				predictors[i]._trainData = getArff(i,0,trainName);
				predictors[i]._testData = getArff(i,0,testName);
				
				System.out.println(predictors[i]._trainData.getFlags());

				if (predictors[i] instanceof LogisticRegression){
					predictors[i].train();										
				} else {
					LinkRecommenderArff.setType(Constants.SOCIAL);		
					try {
						LinkRecommenderArff.runTests(Launcher.DATA_FILE, Launcher.NUM_FOLDS, new PrintWriter("a"), 0, 0);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				for (DataEntry de : predictors[i]._testData._data) {
					predictors[i].evaluate(de);								// populate probabilities 
				}	

				probabilities[i][j] = predictors[i].getProbabilities(); 	// uid -> (link_id,probability)

				for (DataEntry de : predictors[i]._testData._data) {		// calculate w_m
					Long uid = ((Double)de.getData(0)).longValue();
					Long link_id = ((Double)de.getData(1)).longValue();
					int friend_liked = de.friendLiked;

					/*System.out.println(predictors[i].getName() + " " + uid + " " + link_id + " " + friend_liked + " " + testName);
					for (Long link : probabilities[i][j].get(uid).keySet()) {
						System.out.println(uid + " " + link);
					}*/

					if (friend_liked == 0){
						weights[i][j][0] *= probabilities[i][j].get(uid).get(link_id);
						normals[j][0] = LogSum(normals[j][0], probabilities[i][j].get(uid).get(link_id));
					} else {
						weights[i][j][1] *= probabilities[i][j].get(uid).get(link_id);
						normals[j][1] = LogSum(normals[j][1], probabilities[i][j].get(uid).get(link_id));
					}
				}				
			}
		}		

		for (int i = 0; i < predictors.length; i++){				// normalise
			for (int j = 0; j < Launcher.NUM_FOLDS; j++){
				weights[i][j][0] /= normals[j][0];
				weights[i][j][1] /= normals[j][1];
				System.out.println(i + " " + j + " " + weights[i][j][0] + " " + weights[i][j][1]);
			}
		}

	}

	public static void main(String[] args) {
		BayesianModelAveraging bma = new BayesianModelAveraging();
		bma.train();
	}

	@Override
	public int evaluate(DataEntry de) {
		Long uid = (Long) de.getData(0);
		Long link_id = (Long) de.getData(1);
		int friend_liked = de.friendLiked;		
		int fold = current;
		double prob = 0.0;

		for (int i = 0; i < predictors.length; i++){
			double prediction = probabilities[i][fold].get(uid).get(link_id);
			double weight = (friend_liked == 0 ? weights[i][fold][0] : weights[i][fold][1]);
			prob = LogSum(prob, (prediction * weight));
		}

		return prob > 0.5 ? 0 : 1;
	}

	@Override
	public void clear() {
		predictors = null;
		weights = null;
		probabilities = null;
	}

	@Override
	public Map<Long, Map<Long, Double>> getProbabilities() {
		return null;
	}

	@Override
	public String getName() {
		return "Bayesian Model Averaging";
	}

}
