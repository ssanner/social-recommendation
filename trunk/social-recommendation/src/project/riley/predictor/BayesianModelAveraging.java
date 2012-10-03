package project.riley.predictor;

import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Map;

import org.nicta.lr.util.Constants;

import project.riley.predictor.ArffData.DataEntry;

import util.Statistics;

public class BayesianModelAveraging {

	/*
	 * wtf is wrong with UserInfoHack
	 * run tests for pages/groups
	 * apply bma based on predictions
	 */
	
	//http://en.wikipedia.org/wiki/Ensemble_learning#Bayesian_model_averaging	
	
	/*
	 * set up required predictors
	 */
	public Predictor[] setUp(){
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
	public ArffData getArff(int flag, String name){
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
		data.setFriendSize(0);
		data.setFriends(true);
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
	
	/*
	 * train the BMA
	 */
	public double[] testBMA(){
		
		Predictor[] predictors = setUp();		
		
		for (int i = 0; i < predictors.length; i++){			
			
			for (int j = 0; j < Launcher.NUM_FOLDS; j++){
				String trainName = Launcher.DATA_FILE + ".train." + (j+1);
				String testName  = Launcher.DATA_FILE + ".test."  + (j+1);
				
				predictors[i]._trainData = getArff(i,trainName);
				predictors[i]._testData = getArff(i,testName);
				
				predictors[i].train();
				
				for (DataEntry de : predictors[i]._testData._data) {
					predictors[i].evaluate(de); 
				}	
				
				Map<Long, Map<Long, Double>> probabilities = predictors[i].getProbabilities();
				
			}
						
		}
		
	/*	double z = Double.MIN_VALUE;
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
			weights[i] /= totalWeight;*/

		return null;		
	}

	/*
	 * test the BMA
	 */
	/*public static void testBMA(){

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

	}*/
	
	//* @author Scott Sanner (ssanner@gmail.com)
	// for a,b given log versions log_a, log_b returns log(a + b)
	public double LogSum(double log_a, double log_b) {
		double max = Math.max(log_a, log_b);
		double sum = Math.exp(log_a - max) + Math.exp(log_b - max);
		return Math.log(sum) + max;
	}

	public double sigmoid(double t){
		double k = 1.0;
		return 1.0 / (1.0 + Math.pow(Math.E,(k * -t)));
	}		
	
	public static void main(String[] args) {
		BayesianModelAveraging bma = new BayesianModelAveraging();
		bma.testBMA();
	}

}
