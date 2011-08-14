package org.nicta.lr.minimizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.RecommenderUtil;
import org.nicta.lr.util.UserUtil;
import org.nicta.social.LBFGS;

public abstract class Minimizer 
{	
	/**
	 * Convenience method for getting 'friendship' values.
	 * 
	 * Friendship values should be bounded between 0 and 1. Current assumption is values should be equal bidirectionally.
	 * 
	 * @param uid1
	 * @param uid2
	 * @param friendships
	 * @return
	 */
	public double getFriendConnection(Long uid1, Long uid2, HashMap<Long, HashMap<Long, Double>> friendships)
	{
		if ((friendships.containsKey(uid1) && friendships.get(uid1).containsKey(uid2))) {
			return friendships.get(uid1).get(uid2);
		}
		
		return 0;
	}	
	
	/**
	 * Calculates the dot product between 2 vectors
	 * 
	 * @param vec1
	 * @param vec2
	 * @return
	 */
	public double dot(Double[] vec1, Double[] vec2)
	{
		double prod = 0;
		
		for (int x = 0; x < vec1.length; x++) {
			prod += vec1[x] * vec2[x];
		}
		
		return prod;
	}
	
	public double minimize(HashMap<Long, HashSet<Long>> linkLikes, Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, HashMap<Long, HashMap<Long, Double>> friendships,
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<Long, HashSet<Long>> userLinkSamples,
			HashMap<String, Double[]> wordColumns, HashMap<Long, Set<String>> linkWords, Set<String> words)
		throws Exception
	{
		boolean go = true;	
		int iterations = 0;
		//int userVars = Constants.K * (Constants.USER_FEATURE_COUNT + userFeatures.size());
		int userVars = Constants.K * (Constants.USER_FEATURE_COUNT + userLinkSamples.size());
		int linkVars = Constants.K * (Constants.LINK_FEATURE_COUNT + linkFeatures.size() + words.size());
		
		Object[] userKeys = userLinkSamples.keySet().toArray();
		Object[] linkKeys = linkFeatures.keySet().toArray();
		Object[] wordKeys = wordColumns.keySet().toArray();
		
		int[] iprint = {0,0};
		int[] iflag = {0};
		double[] diag = new double[userVars + linkVars];
		
		for (int x = 0; x < diag.length; x++) {
			diag[x] = 0;
		}
		
		double oldError = Double.MAX_VALUE;
		double rmse = 0;
		
		while (go) {
			iterations++;
			HashMap<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
			
			HashMap<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures, linkWords, wordColumns);

			System.out.println("Getting Connections");
			HashMap<Long, HashMap<Long, Double>> connections = RecommenderUtil.getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
			System.out.println("Getting Predictions");
			HashMap<Long, HashMap<Long, Double>> predictions = RecommenderUtil.getPredictions(userTraits, linkTraits, userLinkSamples);
			
			Double[][] userDerivative = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
			HashMap<Long, Double[]> userIdDerivative = new HashMap<Long, Double[]>();
			Double[][] linkDerivative = new Double[Constants.K][Constants.LINK_FEATURE_COUNT];
			HashMap<Long, Double[]> linkIdDerivative = new HashMap<Long, Double[]>();
			HashMap<String, Double[]> wordDerivative = new HashMap<String, Double[]>();
			
			System.out.println("Iterations: " + iterations);
		
			//Get user derivatives
			System.out.println("Get user derivatives");
			for (int k = 0; k < Constants.K; k++) {
				System.out.println("K: " + k);
				for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
					userDerivative[k][l] = getErrorDerivativeOverUserAttribute(userFeatureMatrix, userFeatures, userIdColumns, linkTraits, friendships, linkLikes, predictions, connections, k, l);
				}
				
				for (long userId : userLinkSamples.keySet()) {
					if (!userIdDerivative.containsKey(userId)) {
						userIdDerivative.put(userId, new Double[Constants.K]);
					}
					
					userIdDerivative.get(userId)[k] = getErrorDerivativeOverUserId(userFeatureMatrix, userFeatures, linkTraits, userIdColumns, friendships, linkLikes, predictions, connections, k, userId);
				}
			}
			
			//Get link derivatives
			System.out.println("Get link derivatives");
			for (int q = 0; q < Constants.K; q++) {
				System.out.println("K: " + q);
				for (int l = 0; l < Constants.LINK_FEATURE_COUNT; l++) {
					linkDerivative[q][l] = getErrorDerivativeOverLinkAttribute(linkFeatureMatrix, userTraits, linkFeatures, linkLikes, predictions, q, l);
				}
				System.out.println("Done features");
				for (long linkId : linkIdColumns.keySet()) {
					if (!linkIdDerivative.containsKey(linkId)) {
						linkIdDerivative.put(linkId, new Double[Constants.K]);
					}
									
					linkIdDerivative.get(linkId)[q] = getErrorDerivativeOverLinkId(linkIdColumns, userTraits, linkLikes, predictions, q, linkId);
				}
				System.out.println("Done ids");
				
				/*
				for (String word : words) {
					if (!wordDerivative.containsKey(word)) {
						wordDerivative.put(word, new Double[Constants.K]);
					}
					
					wordDerivative.get(word)[q] = getErrorDerivativeOverWord(wordColumns, linkWords, linkTraits, linkLikes, predictions, q, word);
				}
				*/
				//System.out.println("Done words");
			}
		
			double[] variables = new double[userVars + linkVars];
			System.out.println("Variables: " + variables.length);
			
			int index = 0;
			
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
					variables[index++] = userFeatureMatrix[x][y];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdColumns.get(userId);
				for (double d : column) {
					variables[index++] = d;
				}
			}
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
					variables[index++] = linkFeatureMatrix[x][y];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdColumns.get(linkId);
				for (double d : column) {
					variables[index++] = d;
				}
			}
			for (Object word : wordKeys) {
				Double[] column = wordColumns.get(word);
				for (double d : column) {
					variables[index++] = d;
				}
				
			}
			
			
			System.out.println("derivatives");
			double[] derivatives = new double[userVars + linkVars];
			index = 0;
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
					derivatives[index++] = userDerivative[x][y];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdDerivative.get(userId);
				for (double d : column) {
					derivatives[index++] = d;
				}
			}
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
					derivatives[index++] = linkDerivative[x][y];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdDerivative.get(linkId);
				for (double d : column) {
					derivatives[index++] = d;
				}
			}
			for (Object word : wordKeys) {
				Double[] column = wordDerivative.get(word);
				for (double d : column) {
					derivatives[index++] = d;
				}
			}
			
			double error = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, wordColumns, friendships, linkLikes, predictions, connections);
			
			System.out.println("New Error: " + error);
			System.out.println("");
		
			LBFGS.lbfgs(variables.length, 5, variables, error, derivatives,
					false, diag, iprint, Constants.STEP_CONVERGENCE,
					1e-15, iflag);
		
			System.out.println("Setting again");
			index = 0;
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
					userFeatureMatrix[x][y] = variables[index++];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdColumns.get(userId);
				for (int d = 0; d < column.length; d++) {
					column[d] = variables[index++];
				}
			}
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
					linkFeatureMatrix[x][y] = variables[index++];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdColumns.get(linkId);
				for (int d = 0; d < column.length; d++) {
					column[d] = variables[index++];
				}
			}
			for (Object id : wordKeys) {
				Double[] column = wordColumns.get(id);
				for (int d = 0; d < column.length; d++) {
					column[d] = variables[index++];
				}
			}
			
			if (iflag[0] == 0 || Math.abs(oldError - error) < Constants.STEP_CONVERGENCE) go = false;
		
			oldError = error;
		}
		
		return rmse;
	}
	
	public double minimize2(HashMap<Long, HashSet<Long>> linkLikes, Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, HashMap<Long, HashMap<Long, Double>> friendships,
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<Long, HashSet<Long>> userLinkSamples,
			HashMap<String, Double[]> wordColumns, HashMap<Long, Set<String>> linkWords, Set<String> words)
		throws Exception
	{
		boolean converged = false;	
		int iterations = 0;
		
		double stepSize = Constants.STEP_SIZE;
		int count = 0;
		double lastGoodError = 0;
		
		Double[][] lastGoodUserMatrix = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
		Double[][] lastGoodLinkMatrix = new Double[Constants.K][Constants.LINK_FEATURE_COUNT]; 
		HashMap<Long, Double[]> lastGoodUserIdColumns = new HashMap<Long, Double[]>();
		HashMap<Long, Double[]> lastGoodLinkIdColumns = new HashMap<Long, Double[]>();
		HashMap<String, Double[]> lastGoodWordColumns = new HashMap<String, Double[]>();
		
		HashMap<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		HashMap<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures, linkWords, wordColumns);
		HashMap<Long, HashMap<Long, Double>> connections = RecommenderUtil.getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
		HashMap<Long, HashMap<Long, Double>> predictions = RecommenderUtil.getPredictions(userTraits, linkTraits, userLinkSamples);
		
		double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, wordColumns, friendships, linkLikes, predictions, connections);
		
		while (!converged && iterations <= 500) {
			Double[][] updatedUserMatrix = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
			Double[][] updatedLinkMatrix = new Double[Constants.K][Constants.LINK_FEATURE_COUNT]; 
			HashMap<Long, Double[]> updatedUserIdColumns = new HashMap<Long, Double[]>();
			HashMap<Long, Double[]> updatedLinkIdColumns = new HashMap<Long, Double[]>();
			HashMap<String, Double[]> updatedWordColumns = new HashMap<String, Double[]>();
		
			//Get user derivatives
			System.out.println("Get user derivatives");
			for (int k = 0; k < Constants.K; k++) {
				System.out.println("K: " + k);
				for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
					double update = stepSize * getErrorDerivativeOverUserAttribute(userFeatureMatrix, userFeatures, userIdColumns, linkTraits, friendships, linkLikes, predictions, connections, k, l);
					updatedUserMatrix[k][l] = userFeatureMatrix[k][l] - update;
				}
				
				for (long userId : userLinkSamples.keySet()) {
					if (!updatedUserIdColumns.containsKey(userId)) {
						updatedUserIdColumns.put(userId, new Double[Constants.K]);
					}
					
					double update = stepSize * getErrorDerivativeOverUserId(userFeatureMatrix, userFeatures, linkTraits, userIdColumns, friendships, linkLikes, predictions, connections, k, userId);
					updatedUserIdColumns.get(userId)[k] = userIdColumns.get(userId)[k] - update;
				}
			}
			
			//Get link derivatives
			System.out.println("Get link derivatives");
			for (int q = 0; q < Constants.K; q++) {
				System.out.println("K: " + q);
				for (int l = 0; l < Constants.LINK_FEATURE_COUNT; l++) {
					double update = stepSize * getErrorDerivativeOverLinkAttribute(linkFeatureMatrix, userTraits, linkFeatures, linkLikes, predictions, q, l);
					updatedLinkMatrix[q][l] = linkFeatureMatrix[q][l] - update;
				}
				System.out.println("Done features");
				
				for (long linkId : linkIdColumns.keySet()) {
					if (!updatedLinkIdColumns.containsKey(linkId)) {
						updatedLinkIdColumns.put(linkId, new Double[Constants.K]);
					}
					
					double update = stepSize * getErrorDerivativeOverLinkId(linkIdColumns, userTraits, linkLikes, predictions, q, linkId);
					updatedLinkIdColumns.get(linkId)[q] = linkIdColumns.get(linkId)[q] = update;
				}
				System.out.println("Done ids");
				
				for (String word : words) {
					if (!updatedWordColumns.containsKey(word)) {
						updatedWordColumns.put(word, new Double[Constants.K]);
					}
					
					double update = stepSize * getErrorDerivativeOverWord(wordColumns, linkWords, linkTraits, linkLikes, predictions, q, word);
					updatedWordColumns.get(word)[q] = wordColumns.get(word)[q] - update;
				}
				System.out.println("Done words");
			}
			
			userTraits = UserUtil.getUserTraitVectors(updatedUserMatrix, updatedUserIdColumns, userFeatures);
			linkTraits = LinkUtil.getLinkTraitVectors(updatedLinkMatrix, updatedLinkIdColumns, linkFeatures, linkWords, updatedWordColumns);
			connections = RecommenderUtil.getConnections(updatedUserMatrix, updatedUserIdColumns, userFeatures, userLinkSamples);
			predictions = RecommenderUtil.getPredictions(userTraits, linkTraits, userLinkSamples);
			
			double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, wordColumns, friendships, linkLikes, predictions, connections);

			if (newError < oldError) {
				//System.out.println("Stepsize: " + stepSize + " Count: " + count);
				
				stepSize *= 2;
                count++;
                
                lastGoodUserMatrix = updatedUserMatrix;
                lastGoodUserIdColumns = updatedUserIdColumns;
                lastGoodLinkMatrix = updatedLinkMatrix;
                lastGoodLinkIdColumns = updatedLinkIdColumns;
                lastGoodWordColumns = updatedWordColumns;
    			
                lastGoodError = newError;
			}
			else {
				if (count > 0) {
					count = 0;
					
					for (int k = 0; k < Constants.K; k++) {
						userFeatureMatrix[k] = lastGoodUserMatrix[k];
						linkFeatureMatrix[k] = lastGoodLinkMatrix[k];
					}
					
					for (long userId : userLinkSamples.keySet()) {
						userIdColumns.put(userId, lastGoodUserIdColumns.get(userId));
					}
					
					for (long linkId : linkIdColumns.keySet()) {
						linkIdColumns.put(linkId, lastGoodLinkIdColumns.get(linkId));
					}
					
					for (String word : wordColumns.keySet()) {
						wordColumns.put(word, lastGoodWordColumns.get(word));
					}
					
	    			oldError = lastGoodError;
	    			
	    			iterations++;
	    			System.out.println("Iterations: " + iterations);
	    			System.out.println("Error: " + oldError);
	    			System.out.println("");
				}
				else {
					stepSize *= .5;
				}
			}
			
			//Once the learning rate is smaller than a certain size, just stop.
            //We get here after a few failures in the previous if statement.
            if (stepSize < Constants.STEP_CONVERGENCE) {
                converged = true;
            }
		}
		
		return oldError;
	}
	
	public abstract double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<String, Double[]> wordColumns,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections);
	
	public abstract double getErrorDerivativeOverUserAttribute(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userIdColumns,
			HashMap<Long, Double[]> linkTraits,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes,
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections,
			int x, int y);
	
	public abstract double getErrorDerivativeOverUserId(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections,
			int k, long userId);
	
	public abstract double getErrorDerivativeOverLinkAttribute(Double[][] linkFeatureMatrix,
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkFeatures,
			HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashMap<Long, Double>> predictions, int x, int y);
	
	public abstract double getErrorDerivativeOverLinkId(HashMap<Long, Double[]> linkIdColumns,
			HashMap<Long, Double[]> userTraits,
			HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, 
			int x, long linkId);
	
	public abstract double getErrorDerivativeOverWord(HashMap<String, Double[]> wordColumns, HashMap<Long, Set<String>> linkWords, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, HashSet<Long>> linkLikes,
			HashMap<Long, HashMap<Long, Double>> predictions,  
			int x, String word);
	
}
