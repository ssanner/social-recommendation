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
			HashMap<String, Double[]> wordColumns, HashMap<Long, HashSet<String>> linkWords, Set<String> words)
		throws Exception
	{
		boolean go = true;	
		int iterations = 0;
		int userVars = Constants.K * (Constants.USER_FEATURE_COUNT + userFeatures.size());
		int linkVars = Constants.K * (Constants.LINK_FEATURE_COUNT + linkFeatures.size() + words.size());
		
		Object[] userKeys = userFeatures.keySet().toArray();
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
		
			Double[][] userDerivative = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
			HashMap<Long, Double[]> userIdDerivative = new HashMap<Long, Double[]>();
			Double[][] linkDerivative = new Double[Constants.K][Constants.LINK_FEATURE_COUNT];
			HashMap<Long, Double[]> linkIdDerivative = new HashMap<Long, Double[]>();
			HashMap<String, Double[]> wordDerivative = new HashMap<String, Double[]>();
			
			System.out.println("Iterations: " + iterations);
		
			//Get user derivatives
			//System.out.println("Get user derivatives");
			for (int k = 0; k < Constants.K; k++) {
				for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
					userDerivative[k][l] = getErrorDerivativeOverUserAttribute(userFeatureMatrix, userFeatures, userIdColumns, userTraits, linkTraits, friendships, linkLikes, userLinkSamples, k, l);
				}
				
				for (long userId : userIdColumns.keySet()) {
					if (!userIdDerivative.containsKey(userId)) {
						userIdDerivative.put(userId, new Double[Constants.K]);
					}
					
					//If no link samples for user, do not change?
					//Did this for optmization, hopefully this doesn't screw up anything. Much.
					if (userLinkSamples.containsKey(userId)) {
						userIdDerivative.get(userId)[k] = getErrorDerivativeOverUserId(userFeatureMatrix, userFeatures, userTraits, linkTraits, userIdColumns, friendships, linkLikes, userLinkSamples, k, userId);
					}
					else {
						Double[] idColumn = userIdColumns.get(userId);
						userIdDerivative.get(userId)[k] = idColumn[k] * Constants.LAMBDA * -1;
						//userIdDerivative.get(userId)[k] = 0.0;
					}
				}
			}
		
			//Get link derivatives
			//System.out.println("Get link derivatives");
			for (int q = 0; q < Constants.K; q++) {
				//System.out.println("K: " + q);
				for (int l = 0; l < Constants.LINK_FEATURE_COUNT; l++) {
					linkDerivative[q][l] = getErrorDerivativeOverLinkAttribute(linkFeatureMatrix, userTraits, linkTraits, linkFeatures, linkLikes, userLinkSamples, q, l);
				}
				//System.out.println("Done features");
				for (long linkId : linkIdColumns.keySet()) {
					if (!linkIdDerivative.containsKey(linkId)) {
						linkIdDerivative.put(linkId, new Double[Constants.K]);
					}
									
					linkIdDerivative.get(linkId)[q] = getErrorDerivativeOverLinkId(linkIdColumns, userTraits, linkTraits, linkLikes, userLinkSamples, q, linkId);
				}
				//System.out.println("Done ids");
				for (String word : words) {
					if (!wordDerivative.containsKey(word)) {
						wordDerivative.put(word, new Double[Constants.K]);
					}
					
					wordDerivative.get(word)[q] = getErrorDerivativeOverWord(wordColumns, linkWords, userTraits, linkTraits, linkLikes, userLinkSamples, q, word);
				}
				//System.out.println("Done words");
			}
		
		
			double[] variables = new double[userVars + linkVars];
			//System.out.println("Variables: " + variables.length);
			
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
			
			//System.out.println("derivatives");
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
			
			//System.out.println("Foo");
			double error = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, wordColumns, userFeatures, userTraits, linkTraits, friendships, linkLikes, userLinkSamples);
			//System.out.println("Bar");
			rmse = RecommenderUtil.calcRMSE(userTraits, linkTraits, linkLikes, userLinkSamples);
			//System.out.println("Baz");
			
			System.out.println("New Error: " + error);// + ", RMSE: " + rmse);
			System.out.println("");
		
			LBFGS.lbfgs(variables.length, 5, variables, error, derivatives,
					false, diag, iprint, Constants.STEP_CONVERGENCE,
					1e-15, iflag);
		
			//System.out.println("Setting again");
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
	
	public abstract double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<String, Double[]> wordColumns,
			HashMap<Long, Double[]> users, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples);
	
	public abstract double getErrorDerivativeOverUserAttribute(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userIdColumns,
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples,
			int x, int y);
	
	public abstract double getErrorDerivativeOverUserId(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashSet<Long>> userLinkSamples, int k, long userId);
	
	public abstract double getErrorDerivativeOverLinkAttribute(Double[][] linkFeatureMatrix,
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, Double[]> linkFeatures,
			HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples, int x, int y);
	
	public abstract double getErrorDerivativeOverLinkId(HashMap<Long, Double[]> linkIdColumns,
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashSet<Long>> userLinkSamples, int x, long linkId);
	
	public abstract double getErrorDerivativeOverWord(HashMap<String, Double[]> wordColumns, HashMap<Long, HashSet<String>> linkWords, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes,
			HashMap<Long, HashSet<Long>> userLinkSamples, int x, String word);
	
}
