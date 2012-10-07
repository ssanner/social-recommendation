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

	public final static String DATA_FILE = "active_all_1000_3.arff";
	//public final static String DATA_FILE = "passive.arff";
	public final static int    	NUM_FOLDS = 10;
	public static PrintWriter  	writer;
	public static Predictor[]  	predictors;

	public static int 			threshold = 5;
	public static int 			maxGroupsSize = 200;
	public static int 			groupsStep = 100;
	public static int 			maxFriendSize = 3;
	public static int			friendsStep = 1;

	public static boolean 	FRIENDS_FEATURE = false;
	public static boolean 	INTERACTIONS_FEATURE = false;
	public static boolean 	DEMOGRAPHICS_FEATURE = false; 
	public static boolean 	GROUPS_FEATURE = false;
	public static boolean 	PAGES_FEATURE = false;
	public static boolean	TRAITS_FEATURE = false;
	public static boolean 	OUTGOING_MESSAGES_FEATURE = false;
	public static boolean 	INCOMING_MESSAGES_FEATURE = false;
	
	public static int 		GROUPS_SIZE = 1000;
	public static int		PAGES_SIZE = 1000;
	public static int		OUTGOING_MESSAGES_SIZE = 1000;
	public static int		INCOMING_MESSAGES_SIZE = 1000;
	
	// best sizes for these types
	public static boolean	SIZES_OVERRIDE = false;
	public static int		NB_GROUPS_SIZE_OVERRIDE = 300;
	public static int		LR_GROUPS_SIZE_OVERRIDE = 800;
	public static int		SVM_GROUPS_SIZE_OVERRIDE = 700;
	public static int		SMB_GROUPS_SIZE_OVERRIDE = 100;
	
	public static int		NB_PAGES_SIZE_OVERRIDE = 500;
	public static int		LR_PAGES_SIZE_OVERRIDE = 900;
	public static int		SVM_PAGES_SIZE_OVERRIDE = 800;
	public static int		SMB_PAGES_SIZE_OVERRIDE = 100;

	public static int		NB_OUTGOING_SIZE_OVERRIDE = 300;
	public static int		LR_OUTGOING_SIZE_OVERRIDE = 800;
	public static int		SVM_OUTGOING_SIZE_OVERRIDE = 900;
	public static int		SMB_OUTGOING_SIZE_OVERRIDE = 100;
	
	public static int		NB_INCOMING_SIZE_OVERRIDE = 100;
	public static int		LR_INCOMING_SIZE_OVERRIDE = 800;
	public static int		SVM_INCOMING_SIZE_OVERRIDE = 900;
	public static int		SMB_INCOMING_SIZE_OVERRIDE = 100;
	/*
	 * set up predictors
	 */
	public Predictor[] setUp(){
		// SPS -- free parameters should always be apparent for tuning purposes
		Predictor friendLiked = new FriendLiked();
		
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
		/*Predictor liblinear1_maxent = new SVMLibLinear(SolverType.L2R_L2LOSS_SVC, C0.125d, eps0.001d, maxent true);
		Predictor liblinear2_maxent = new SVMLibLinear(SolverType.L1R_L2LOSS_SVC, C0.125d, eps0.001d, maxent true);
		Predictor liblinear3_maxent = new SVMLibLinear(SolverType.L2R_LR,         C0.125d, eps0.001d, maxent true);
		Predictor liblinear4_maxent = new SVMLibLinear(SolverType.L1R_LR,         C0.125d, eps0.001d, maxent true);*/

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
		//Predictor matchbox     = new SocialRecommender(Constants.FEATURE);
		Predictor soc_matchbox = new SocialRecommender(Constants.SOCIAL);
		//Predictor knn          = new SocialRecommender(Constants.NN);
		//Predictor cbf          = new SocialRecommender(Constants.CBF);

		Predictor[] predictors = new Predictor[] {
				//matchbox,
				//friendLiked,
				soc_matchbox,
				naiveBayes, 
				constPredTrue,
				//constPredFalse,
				liblinear1,
				liblinear2,
				liblinear3,
				liblinear4,
				logisticRegression_l1,
				logisticRegression_l1_maxent, 
				logisticRegression_l2, 
				libsvm
				/*,liblinear1_maxent,
				liblinear2_maxent,
				liblinear3_maxent,
				liblinear4_maxent			*/	 
		};

		return predictors;
	}

	/*
	 * launch tests on thresholding value
	 */
	public void launchThresholds() throws Exception{
		for (int i = 0; i <= threshold; i++){
			for (Predictor p : predictors){
				System.out.println("Running predictors on " + DATA_FILE + " using threshold size " + i);
				writer.println("Running predictors on " + DATA_FILE + " using threshold size " + i);
				p.runTests(DATA_FILE /* file to use */, NUM_FOLDS /* folds to use */, writer /* file to write */, i /* min test threshold */, 0 /* min friend size */);
			}
		}
	}

	/*
	 * launch group size comparisons
	 */
	public void launchSizeComparisons(String name) throws Exception{
		for (int i = 100; i <= maxGroupsSize; i += groupsStep){
			if (name.contains("groups")){
				Launcher.GROUPS_SIZE = i;
			} else if (name.contains("pages")){
				Launcher.PAGES_SIZE = i;
			} else if (name.contains("outgoing")) {
				Launcher.OUTGOING_MESSAGES_SIZE = i;
			} else if (name.contains("incoming")){
				Launcher.INCOMING_MESSAGES_SIZE = i;
			} else {
				System.err.println("No comparison set");
				return;
			}
			for (Predictor p : predictors){
				System.out.println("Running predictors on " + DATA_FILE + " using " +  name +  " size " + i);
				writer.println("Running predictors on " + DATA_FILE + " using " +  name +  " size " + i);
				p.runTests(DATA_FILE /* file to use */, NUM_FOLDS /* folds to use */, writer /* file to write */, 0 /* min test threshold */, 0 /* min friend size */);
			}
		}
	}

	/*
	 * launch tests on thresholding value
	 */
	public void launchFlag(String flag) throws Exception{
		for (int i = 0; i <= maxFriendSize; i += friendsStep){
			for (Predictor p : predictors){
				System.out.println("Running predictors on " + DATA_FILE + " using flag " + flag + " friend size " + i);
				writer.println("Running predictors on " + DATA_FILE + " using flag " + flag + " friend size " + i);
				p.runTests(DATA_FILE /* file to use */, NUM_FOLDS /* folds to use */, writer /* file to write */, 0 /* min test threshold */, i /* min friend size */);
			}
		}
	}
	
	public void setFlag(String flag){
		if (flag.contains("interaction")){
			Launcher.INTERACTIONS_FEATURE = true;
		} else if (flag.contains("demographics")){
			Launcher.DEMOGRAPHICS_FEATURE = true;
		} else if (flag.contains("traits")){
			Launcher.TRAITS_FEATURE = true;
		} else if (flag.contains("groups")){
			Launcher.GROUPS_FEATURE = true;
		} else if (flag.contains("pages")){
			Launcher.PAGES_FEATURE = true;
		} else if (flag.contains("outgoing")){
			Launcher.OUTGOING_MESSAGES_FEATURE = true;
		} else if (flag.contains("incoming")){
			Launcher.INCOMING_MESSAGES_FEATURE = true;
		}
	}

	public static void main(String[] args) throws Exception {
		Launcher launcher = new Launcher();
		predictors = launcher.setUp();
		System.out.println("Running predictors on " + DATA_FILE);

		Date dNow = new Date();
		SimpleDateFormat ft = new SimpleDateFormat ("dd_MM_yyyy");			
		
		String flag;
		//flag = "interactions";
		//flag = "demographics";
		//flag = "traits";
		//flag = "groups";
		//flag = "pages";
		flag = "outgoing";
		//flag = "incoming";
		
		String outName = flag + "_results_" + ft.format(dNow) + ".txt"; 
		writer = new PrintWriter(outName);
		
		//launcher.launchThresholds();
		//Launcher.SIZES_OVERRIDE = true;
		launcher.setFlag(flag);
		//launcher.launchFlag(flag);				
		launcher.launchSizeComparisons(flag);		
		
		System.out.println("Finished writing to file " + outName);
		writer.close();
	}

}
