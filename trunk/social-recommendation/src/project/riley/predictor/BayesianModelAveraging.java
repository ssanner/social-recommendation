package project.riley.predictor;

import de.bwaldvogel.liblinear.SolverType;

public class BayesianModelAveraging {
	
	//http://en.wikipedia.org/wiki/Ensemble_learning#Bayesian_model_averaging
	
	static int 		NUM_FOLDS = 5;
	static String 	SOURCE_FILE = "active.arff";
	
	/*
	 * train the BMA
	 */
	public static double[] trainBMA(Predictor[] predictors){
		double z = Double.MIN_VALUE;
		double[] loglikelihood = new double[predictors.length];
		double[] weights = new double[predictors.length];						
		
		for (int i = 0; i < predictors.length; i++){
			Predictor model = predictors[i];
			double x = model.measures(model._testData._data)[0] / (double) model._testData._data.size() ;	// predictive accuracy of model
			loglikelihood[i] = model._trainData._data.size() * (x * Math.log(x) + (1-x) * Math.log(1-x));	// estimate log likelihood
			z = Math.max(z, loglikelihood[i]);
		}
		
		double totalWeight = 0.0;
		for (int i = 0; i < predictors.length; i++){														// update model weights
			weights[i] = Math.exp(loglikelihood[i]-z);
			totalWeight += weights[i];
		}
		
		for (int i = 0; i < predictors.length; i++)															// normalise model weights to sum to 1
			weights[i] /= totalWeight;
		
		return weights;		
	}
	
	/*
	 * test the BMA
	 */
	public static void testBMA(Predictor[] predictors){
		
		double[] totalWeights = new double[predictors.length];
		
		for (int i = 0; i < NUM_FOLDS; i++){
			
			String trainName = SOURCE_FILE + ".train." + (i+1);
			String testName  = SOURCE_FILE + ".test."  + (i+1);						
			
			for (Predictor predictor : predictors){
				predictor._trainData = new ArffData(trainName);
				predictor._testData  = new ArffData(testName);
				predictor.train();				
			}
			
			double[] results = trainBMA(predictors);					// results of the bma
			for (int j = 0; j < results.length; j++)
				totalWeights[j] += results[j];							// total weights sum
					
		}
		
		for (int i = 0; i < totalWeights.length; i++)
			totalWeights[i] /= NUM_FOLDS;								// average total weights over folds
		
		for (int i = 0; i < predictors.length; i++)			
			System.out.println(predictors[i].getName() + ":" + totalWeights[i]);		
		
	}
	
	public double sigmoid(double t){
		double k = 1.0;
		return 1.0 / (1.0 + Math.pow(Math.E,(k * -t)));
	}
	
	public static void main(String[] args) {
		Predictor naiveBayes = new NaiveBayes(1.0d);
		Predictor logisticRegression_l1 = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		Predictor logisticRegression_l2 = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d);
		Predictor logisticRegression_l1_maxent = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d, /*maxent*/ true);
		Predictor libsvm = new SVMLibSVM(/*C*/0.125d, /*eps*/0.1d);
		Predictor liblinear1 = new SVMLibLinear(SolverType.L2R_L2LOSS_SVC, /*C*/0.125d, /*eps*/0.001d);
		Predictor liblinear2 = new SVMLibLinear(SolverType.L1R_L2LOSS_SVC, /*C*/0.125d, /*eps*/0.001d);
		Predictor liblinear3 = new SVMLibLinear(SolverType.L2R_LR,         /*C*/0.125d, /*eps*/0.001d);
		Predictor liblinear4 = new SVMLibLinear(SolverType.L1R_LR,         /*C*/0.125d, /*eps*/0.001d);
		
		Predictor[] predictors = {naiveBayes,logisticRegression_l1,logisticRegression_l2,logisticRegression_l1_maxent,libsvm,liblinear1,liblinear2,liblinear3,liblinear4};
		testBMA(predictors);
	}
	
}
