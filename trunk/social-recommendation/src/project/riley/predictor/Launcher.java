package project.riley.predictor;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.nicta.lr.util.Constants;

import de.bwaldvogel.liblinear.SolverType;

/*
 * Set up and launch classifiers
 */

public class Launcher {

	public final static String DATA_FILE = "active_all_1000_2.arff";
	//public final static String DATA_FILE = "passive.arff";
	public static int 			threshold = 5;
	public final static int    	NUM_FOLDS = 10;
	public static int 			maxSize = 1000;
	public static int 			step = 100;
	public static PrintWriter  	writer;
	public static Predictor[]  	predictors;

	public static boolean 	FRIENDS_FEATURE = true;
	public static boolean 	INTERACTIONS_FEATURE = false;
	public static boolean 	DEMOGRAPHICS_FEATURE = false; 
	public static boolean 	GROUPS_FEATURE = false;
	public static int 		GROUPS_SIZE = 0;
	public static boolean 	PAGES_FEATURE = false;
	public static int		PAGES_SIZE = 0;
	public static boolean	TRAITS_FEATURE = false;
	public static boolean 	OUTGOING_MESSAGES_FEATURE = false;
	public static int		OUTGOING_MESSAGES_SIZE = 0;
	public static boolean 	INCOMING_MESSAGES_FEATURE = false;
	public static int		INCOMING_MESSAGES_SIZE = 0;

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
		Predictor liblinear1_maxent = new SVMLibLinear(SolverType.L2R_L2LOSS_SVC, /*C*/0.125d, /*eps*/0.001d, /*maxent*/ true);
		Predictor liblinear2_maxent = new SVMLibLinear(SolverType.L1R_L2LOSS_SVC, /*C*/0.125d, /*eps*/0.001d, /*maxent*/ true);
		Predictor liblinear3_maxent = new SVMLibLinear(SolverType.L2R_LR,         /*C*/0.125d, /*eps*/0.001d, /*maxent*/ true);
		Predictor liblinear4_maxent = new SVMLibLinear(SolverType.L1R_LR,         /*C*/0.125d, /*eps*/0.001d, /*maxent*/ true);

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
				libsvm,
				liblinear1_maxent,
				liblinear2_maxent,
				liblinear3_maxent,
				liblinear4_maxent				 
		};

		return predictors;
	}

	/*
	 * launch tests on thresholding value
	 */
	public void launchThresholds() throws Exception{
		for (int i = 0; i <= threshold; i++){
			for (Predictor p : predictors){
				System.out.println("Running predictors on " + DATA_FILE + " using threshold " + i);
				writer.println("Running predictors on " + DATA_FILE + " using threshold " + i);
				p.runTests(DATA_FILE /* file to use */, NUM_FOLDS /* folds to use */, writer /* file to write */, i /* test threshold */);
			}
		}
	}

	/*
	 * launch group size comparisons
	 */
	public void launchSizeComparisons(String name) throws Exception{
		for (int i = 0; i <= maxSize; i+=step)
			for (Predictor p : predictors){
				if (p.getName().contains("NaiveBayes") || p.getName().contains("LogisticRegression") || p.getName().contains("SVMLibSVM") || p.getName().contains("SVMLibLinear")){
					System.out.println("Running predictors on " + DATA_FILE + " using " +  name +  " size " + i);
					writer.println("Running predictors on " + DATA_FILE + " using " +  name +  " size " + i);
					p.runTests(DATA_FILE /* file to use */, NUM_FOLDS /* folds to use */, writer /* file to write */, 0 /* test threshold */);
				}
			}
	}

	/*
	 * launch tests on thresholding value
	 */
	public void launchFlag(String flag) throws Exception{
		for (Predictor p : predictors){
			System.out.println("Running predictors on " + DATA_FILE + " using flag " + flag);
			writer.println("Running predictors on " + DATA_FILE + " using flag " + flag);
			p.runTests(DATA_FILE /* file to use */, NUM_FOLDS /* folds to use */, writer /* file to write */, 0 /* test threshold */);
		}
	}

	public static void main(String[] args) throws Exception {
		Launcher launcher = new Launcher();
		predictors = launcher.setUp();
		System.out.println("Running predictors on " + DATA_FILE);

		Date dNow = new Date();
		SimpleDateFormat ft = new SimpleDateFormat ("dd_MM_yyyy");
		String outName = "pages_results_" + ft.format(dNow) + ".txt"; 

		writer = new PrintWriter(outName);		

		//launcher.launchThresholds();
		//launcher.launchFlag("friends");
		//launcher.launchFlag("interactions");				
		//launcher.launchFlag("demographics");				
		//launcher.launchFlag("traits");				

		//launcher.launchSizeComparisons("group");		
		//launcher.launchSizeComparisons("pages");
		//launcher.launchSizeComparisons("messages outgoing");
		//launcher.launchSizeComparisons("messages incoming");

		System.out.println("Finished writing to file " + outName);
		writer.close();
	}

}
