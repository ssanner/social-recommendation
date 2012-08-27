package project.riley.predictor;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.nicta.lr.util.Constants;

import de.bwaldvogel.liblinear.SolverType;

/*
 * Set up and launch classifiers
 */

public class Launcher {

	public final static String DATA_FILE = "active_all.arff";
	//public final static String DATA_FILE = "passive.arff";
	public static int threshold = 5;
	public final static int    NUM_FOLDS = 10;
	public static PrintWriter  writer;
	public static Predictor[]  predictors;
	public static boolean DEMOGRAPHICS = false;
	public static boolean GROUPS = false;
	public static boolean TRAITS = false;
	public static boolean CONVERSATION = false;

	/*
	 * set up predictors
	 */
	public Predictor[] setUp(){
		// SPS -- free parameters should always be apparent for tuning purposes
		Predictor constPredTrue  = new ConstantPredictor(true);
		Predictor constPredFalse = new ConstantPredictor(false);

		Predictor naiveBayes = new NaiveBayes(1.0d);
		Predictor logisticRegression_l1 = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor logisticRegression_l2 = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d);
		Predictor logisticRegression_l1_maxent = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d, /*maxent*/ true);
		Predictor libsvm = new SVMLibSVM(/*C*/0.125d, /*eps*/0.1d);
		Predictor liblinear1 = new SVMLibLinear(SolverType.L2R_L2LOSS_SVC, /*C*/0.125d, /*eps*/0.001d);
		Predictor liblinear2 = new SVMLibLinear(SolverType.L1R_L2LOSS_SVC, /*C*/0.125d, /*eps*/0.001d);
		Predictor liblinear3 = new SVMLibLinear(SolverType.L2R_LR,         /*C*/0.125d, /*eps*/0.001d);
		Predictor liblinear4 = new SVMLibLinear(SolverType.L1R_LR,         /*C*/0.125d, /*eps*/0.001d);
		//Predictor liblinear1_maxent = new SVMLibLinear(SolverType.L2R_L2LOSS_SVC, /*C*/0.125d, /*eps*/0.001d, /*maxent*/ true);
		//Predictor liblinear2_maxent = new SVMLibLinear(SolverType.L1R_L2LOSS_SVC, /*C*/0.125d, /*eps*/0.001d, /*maxent*/ true);
		//Predictor liblinear3_maxent = new SVMLibLinear(SolverType.L2R_LR,         /*C*/0.125d, /*eps*/0.001d, /*maxent*/ true);
		//Predictor liblinear4_maxent = new SVMLibLinear(SolverType.L1R_LR,         /*C*/0.125d, /*eps*/0.001d, /*maxent*/ true);

		// Note -- SPS, Riley TODO for experimental comparison:
		//
		// The following should exploit Joseph's uniform interface for recommenders,
		// see org.nicta.lr.LinkRecommenderArff... don't change this file, rather
		// encapsulate it in a local SocialRecommender class that sets it according
		// to the correct type and sets good default parameters (Joseph to inform).
		// See how Joseph does evaluation as a batch... you need to prepare a data
		// structure to evaluate and he returns the ratings... his code also has
		// a method for determining the best threshold for those ratings in order
		// to do classification.
		// 
		Predictor matchbox     = new SocialRecommender(Constants.FEATURE);
		Predictor soc_matchbox = new SocialRecommender(Constants.SOCIAL);
		//Predictor knn          = new SocialRecommender(Constants.NN);
		//Predictor cbf          = new SocialRecommender(Constants.CBF);

		Predictor[] predictors = new Predictor[] {
				matchbox,
				soc_matchbox,
				naiveBayes, 
				constPredTrue,
				constPredFalse,
				liblinear1,
				liblinear2,
				liblinear3,
				liblinear4,
				logisticRegression_l1,
				logisticRegression_l1_maxent, 
				logisticRegression_l2, 
				libsvm
				/*liblinear1_maxent,
				liblinear2_maxent,
				liblinear3_maxent,
				liblinear4_maxent, 
				 */ 
		};

		return predictors;
	}
	/*
	 * launch tests on demographics, group types and conversation conditions
	 */
	public void launchConditions() throws Exception{
		boolean[] conditions = {true, false};
		for (boolean condition_a : conditions){
			for (boolean condition_b : conditions){
				for (boolean condition_c : conditions){
					for (boolean condition_d : conditions){
						System.out.println("Running predictors on " + DATA_FILE + " with demographics flag " + condition_a + " groups flag " + condition_b + " traits flag " + condition_c + " and conversation flag " + condition_d);
						writer.println("Running predictors on " + DATA_FILE + " with demographics flag " + condition_a + " groups flag " + condition_b + " traits flag " + condition_c + " and conversation flag " + condition_d);
						for (Predictor p : predictors){
							p.runTests(DATA_FILE /* file to use */, NUM_FOLDS /* folds to use */, 0, /* test threshold */ writer /* file to write */, condition_a, condition_b, condition_c, condition_d);
						}
					}	
				}
			}	
		}
	}

	/*
	 * launch tests on thresholding value
	 */
	public void launchThresholds() throws Exception{
		for (int i = 0; i <= threshold; i++){
			for (Predictor p : predictors){
				p.runTests(DATA_FILE /* file to use */, NUM_FOLDS /* folds to use */, i /* test threshold */, writer /* file to write */, DEMOGRAPHICS, GROUPS, TRAITS, CONVERSATION);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Launcher launcher = new Launcher();
		predictors = launcher.setUp();
		System.out.println("Running predictors on " + DATA_FILE);

		//BayesianModelAveraging.setArff(new ArffData(DATA_FILE));

		Date dNow = new Date();
		SimpleDateFormat ft = new SimpleDateFormat ("dd_MM_yyyy");
		String outName = "results_" + ft.format(dNow) + ".txt"; 

		writer = new PrintWriter(outName);		

		launcher.launchThresholds();
		//launcher.launchConditions();

		System.out.println("Finished writing to file " + outName);
		writer.close();
	}

}
