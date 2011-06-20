package org.nicta.fbproject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Date;

import org.nicta.social.LBFGS;

public class FeatureRecommender extends Recommender 
{
	public static void main(String[] args)
		throws Exception
	{
		FeatureRecommender test = new FeatureRecommender();
		test.test();
	}
	
	public void test()
		throws Exception
	{
		System.out.println("Start...");
		long startTime = System.currentTimeMillis();
		long lastUpdate = getLastUpdate("Feature");
		
		HashMap<Long, Double[]> users = getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = getLinkFeatures();
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = getLinkLikes(links.keySet());
		
		HashMap<Long, HashSet<Long>> friendships = getFriendships();
		System.out.println("Retrieved friends: " + friendships.size());
		
		HashMap<String, Integer> words = getMostCommonWords();
		System.out.println("Got " + words.size() + " words.");
		
		HashMap<Long, HashSet<String>> linkWords = getLinkWordFeatures(words.keySet());
		System.out.println("Link words: " + linkWords.size());
		
		//Double[][] userMatrix = FBMethods.getPrior(Constants.USER_FEATURE_COUNT + users.size());
		Double[][] userFeatureMatrix = getPrior(Constants.USER_FEATURE_COUNT);
		HashMap<Long, Double[]> userIdColumns = getMatrixIdColumns(users.keySet());
		
		//Double[][] linkMatrix = FBMethods.getPrior(Constants.LINK_FEATURE_COUNT + links.size() + words.size());
		Double[][] linkFeatureMatrix = getPrior(Constants.LINK_FEATURE_COUNT + words.size());
		HashMap<Long, Double[]> linkIdColumns = getMatrixIdColumns(links.keySet());
		
		HashMap<Long, HashSet<Long>> userLinkSamples = getUserLinksSample(users.keySet(), friendships);
		
		minimize(linkLikes, userFeatureMatrix, linkFeatureMatrix, users, links, userIdColumns, linkIdColumns, userLinkSamples);
		
		System.out.println("Getting links to recommend");
		HashMap<Long, HashSet<Long>> linksToRecommend = getLinksForRecommending(users.keySet(), friendships);
		System.out.println("Recommending...");
		HashMap<Long, HashMap<Long, Double>> recommendations = recommendLinks(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, 
																				users, links, linksToRecommend);
		System.out.println("Saving...");
		saveLinkRecommendations(recommendations, "lrFeatureRecommendations");
		
		System.out.println("Save matrices...");
		saveMatrices(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns);
		
		System.out.println("Load feature matrices");
		userFeatureMatrix = loadFeatureMatrix("lrUserMatrix", Constants.USER_FEATURE_COUNT);
		linkFeatureMatrix = loadFeatureMatrix("lrLinkMatrix", Constants.LINK_FEATURE_COUNT);
		
		System.out.println("Load id columns");
		userIdColumns = loadIdColumns("lrUserMatrix", Constants.USER_FEATURE_COUNT);
		linkIdColumns = loadIdColumns("lrLinkMatrix", Constants.LINK_FEATURE_COUNT);
		
		saveLastUpdate("Feature", startTime);
		
		System.out.println("Done");
	}
	
	public void minimize(HashMap<Long, HashSet<Long>> linkLikes, Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
					HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures,
					HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<Long, HashSet<Long>> userLinkSamples)
		throws Exception
	{
		boolean go = true;	
		int iterations = 0;
		int userVars = Constants.K * (Constants.USER_FEATURE_COUNT + userFeatures.size());
		int linkVars = Constants.K * (Constants.LINK_FEATURE_COUNT + linkFeatures.size());
		
		Object[] userKeys = userFeatures.keySet().toArray();
		Object[] linkKeys = linkFeatures.keySet().toArray();
		
		int[] iprint = {0,0};
		int[] iflag = {0};
		double[] diag = new double[userVars + linkVars];
		
		for (int x = 0; x < diag.length; x++) {
			diag[x] = 0;
		}

		double oldError = Double.MAX_VALUE;

		while (go) {
			iterations++;
			HashMap<Long, Double[]> userTraits = getTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
			HashMap<Long, Double[]> linkTraits = getTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);

			Double[][] userDerivative = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
			HashMap<Long, Double[]> userIdDerivative = new HashMap<Long, Double[]>();
			Double[][] linkDerivative = new Double[Constants.K][Constants.LINK_FEATURE_COUNT];
			HashMap<Long, Double[]> linkIdDerivative = new HashMap<Long, Double[]>();
			
			System.out.println("Iterations: " + iterations);

			//Get user derivatives
			for (int k = 0; k < Constants.K; k++) {
				for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
					userDerivative[k][l] = getErrorDerivativeOverUserAttribute(userFeatureMatrix, userFeatures, userIdColumns, userTraits, linkTraits, linkLikes, userLinkSamples, k, l);
				}
				
				for (long userId : userIdColumns.keySet()) {
					if (!userIdDerivative.containsKey(userId)) {
						userIdDerivative.put(userId, new Double[Constants.K]);
					}
					
					userIdDerivative.get(userId)[k] = getErrorDerivativeOverUserId(userFeatureMatrix, userFeatures, userTraits, linkTraits, userIdColumns, linkLikes, userLinkSamples, k, userId);
				}
			}

			//Get movie derivatives
			for (int q = 0; q < Constants.K; q++) {
				for (int l = 0; l < Constants.LINK_FEATURE_COUNT; l++) {
					linkDerivative[q][l] = getErrorDerivativeOverLinkAttribute(linkFeatureMatrix, userTraits, linkTraits, linkFeatures, linkLikes, userLinkSamples, q, l);
				}
				
				for (long linkId : linkIdColumns.keySet()) {
					if (!linkIdDerivative.containsKey(linkId)) {
						linkIdDerivative.put(linkId, new Double[Constants.K]);
					}
									
					linkIdDerivative.get(linkId)[q] = getErrorDerivativeOverLinkId(linkFeatureMatrix, linkFeatures, linkIdColumns, userTraits, linkTraits, linkFeatures, linkLikes, userLinkSamples, q, linkId);
				}
			}


			double[] variables = new double[userVars + linkVars];
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
			
			double error = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, userFeatures, userTraits, linkTraits, linkLikes, userLinkSamples);
			//if (iterations % 20 == 0) {
				System.out.println("New Error: " + error + ", RMSE: " + calcRMSE(userTraits, linkTraits, linkLikes, userLinkSamples));
				System.out.println("");
			//}

			LBFGS.lbfgs(variables.length, 5, variables, error, derivatives,
					false, diag, iprint, Constants.STEP_CONVERGENCE,
					1e-15, iflag);

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
			if (iflag[0] == 0 || Math.abs(oldError - error) < Constants.STEP_CONVERGENCE) go = false;

			oldError = error;
		}
	}
	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns,
			HashMap<Long, Double[]> users, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		double error = 0;
			
		//Get the square error
		for (long i : userTraits.keySet()) {
			HashSet<Long> links = userLinkSamples.get(i);
			
			for (long j : links) {
				int liked = 0;
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				double predictedLike = dot(userTraits.get(i), linkTraits.get(j));
		
				error += Math.pow(liked - predictedLike, 2);
			}
		}
		
		//Get User and Movie norms for regularisation
		double userNorm = 0;
		double linkNorm = 0;

		for (int x = 0; x < Constants.K; x++) {
			for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
				userNorm += Math.pow(userFeatureMatrix[x][y], 2);
			}
		}
		for (long id : userIdColumns.keySet()) {
			Double[] column = userIdColumns.get(id);
			
			for (double val : column) {
				userNorm += Math.pow(val, 2);
			}
		}

		for (int x = 0; x < Constants.K; x++) {
			for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
				linkNorm += Math.pow(linkFeatureMatrix[x][y], 2);
			}
		}
		for (long id : linkIdColumns.keySet()) {
			Double[] column = linkIdColumns.get(id);
			
			for (double val : column) {
				linkNorm += Math.pow(val, 2);
			}
		}
			
		userNorm *= Constants.LAMBDA;
		linkNorm *= Constants.LAMBDA;

		System.out.println("Sq error: " + error + " uNorm: " + userNorm + " lNorm: " + linkNorm);
		
		error += userNorm + linkNorm;

		return error / 2;
	}
	
	public double getErrorDerivativeOverUserAttribute(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userIdColumns,
				HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
				HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples, int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * Constants.LAMBDA;

		for (long userId : userFeatures.keySet()) {
			HashSet<Long> links = userLinkSamples.get(userId);
			
			for (long linkId : links) {
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y];		
				double p = dot(userTraits.get(userId), linkTraits.get(linkId));
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst * -1;
			}
		}

		return errorDerivative;
	}


	public double getErrorDerivativeOverUserId(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
												HashMap<Long, Double[]> userIdColumns, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * Constants.LAMBDA;

		HashSet<Long> links = userLinkSamples.get(userId);
		
		for (long linkId : links) {
			HashSet<Long> likes = linkLikes.get(linkId);

			double dst = linkTraits.get(linkId)[k] /* userFeatures.get(userId)[k]*/;
			double p = dot(userTraits.get(userId), linkTraits.get(linkId));
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst * -1;
		}

		return errorDerivative;
	}

	public double getErrorDerivativeOverLinkAttribute(Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, Double[]> linkFeatures,
			HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples, int x, int y)
	{	
		double errorDerivative = linkFeatureMatrix[x][y] * Constants.LAMBDA;

		for (long userId : userTraits.keySet()) {
			HashSet<Long> links = userLinkSamples.get(userId);

			for (long linkId : links) {
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y];		
				double p = dot(userTraits.get(userId), linkTraits.get(linkId));
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst * -1;
			}
		}

		return errorDerivative;
	}

	
	public double getErrorDerivativeOverLinkId(Double[][] linkFeatureMatrix, HashMap<Long, Double[]> links, HashMap<Long, Double[]> linkIdColumns,
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, Double[]> linkFeatures,
			HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples, int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * Constants.LAMBDA;
		HashSet<Long> likes = linkLikes.get(linkId);
		
		for (long userId : userTraits.keySet()) {
			if (! userLinkSamples.get(userId).contains(linkId)) continue;
			
			double dst = userTraits.get(userId)[x] * idColumn[x];		
			double p = dot(userTraits.get(userId), linkTraits.get(linkId));
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst * -1;
		}
		
		return errorDerivative;
	}
}
