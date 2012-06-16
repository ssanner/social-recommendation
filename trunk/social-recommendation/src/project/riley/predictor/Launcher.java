package project.riley.predictor;

import java.io.IOException;

import org.nicta.lr.util.Constants;

import de.bwaldvogel.liblinear.SolverType;

/*
 * Set up and launch classifiers
 */

public class Launcher {

	public final static String DATA_FILE = "active.arff";
	//public final static String DATA_FILE = "passive.arff";
	public final static int    NUM_FOLDS = 10;
	
	public static void main(String[] args) throws IOException {
		
		// SPS -- free parameters should always be apparent for tuning purposes
		Predictor naiveBayes = new NaiveBayes(1.0d);
		Predictor logisticRegression_l1 = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor logisticRegression_l2 = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d);
		Predictor libsvm = new SVMLibSVM(/*C*/0.5d, /*eps*/0.1d);
		Predictor liblinear1 = new SVMLibLinear(SolverType.L2R_LR,         /*C*/0.5d, /*eps*/0.001d);
		Predictor liblinear2 = new SVMLibLinear(SolverType.L2R_L2LOSS_SVC, /*C*/0.5d, /*eps*/0.001d);
		Predictor liblinear3 = new SVMLibLinear(SolverType.L1R_L2LOSS_SVC, /*C*/0.5d, /*eps*/0.001d);
		Predictor liblinear4 = new SVMLibLinear(SolverType.L1R_LR,         /*C*/0.5d, /*eps*/0.001d);

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
		//Predictor soc_matchbox = new SocialMatchBox(Constants.SOCIAL);
		//Predictor knn          = new SocialMatchBox(Constants.NN);
		//Predictor cbf          = new SocialMatchBox(Constants.CBF);
		
		Predictor[] predictors = new Predictor[] { 
				naiveBayes, 
				liblinear1/*,
				svm, 
				logisticRegression_l1, 
				logisticRegression_l2*/ };
		
		for (Predictor p : predictors)
			p.runTests(DATA_FILE, NUM_FOLDS);
	}
	
}
