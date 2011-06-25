package org.nicta.fbproject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Date;

import org.nicta.social.LBFGS;

public class SocialRecommender extends Recommender 
{
	public static void main(String[] args)
		throws Exception
	{
		SocialRecommender test = new SocialRecommender();
		test.recommend();
	}
	
	public void recommend()
		throws Exception
	{
		System.out.println("Loading Data..." + new Date());
		
		HashMap<Long, Double[]> users = getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = getLinkFeatures();
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = getLinkLikes(links.keySet());
		HashMap<Long, HashSet<Long>> friendships = getFriendships();
		
		Set<String> words = loadMostCommonWords();
		if (words.size() == 0) {
			words = getMostCommonWords();
		}
		HashMap<Long, HashSet<String>> linkWords = getLinkWordFeatures(words);

		Double[][] userFeatureMatrix = loadFeatureMatrix("lrUserMatrix", Constants.USER_FEATURE_COUNT, "Social");
		if (userFeatureMatrix == null) {
			userFeatureMatrix = getPrior(Constants.USER_FEATURE_COUNT);
		}
		Double[][] linkFeatureMatrix = loadFeatureMatrix("lrLinkMatrix", Constants.LINK_FEATURE_COUNT, "Social");
		if (linkFeatureMatrix == null) {
			linkFeatureMatrix = getPrior(Constants.LINK_FEATURE_COUNT);
		}
		HashMap<Long, Double[]>userIdColumns = loadIdColumns("lrUserMatrix", "Social");
		if (userIdColumns.size() == 0) {
			userIdColumns = getMatrixIdColumns(users.keySet());
		}
		
		HashMap<Long, Double[]>linkIdColumns = loadIdColumns("lrLinkMatrix", "Social");
		if (linkIdColumns.size() == 0) {
			linkIdColumns = getMatrixIdColumns(links.keySet());
		}
		
		HashMap<String, Double[]> wordColumns = loadWordColumns("lrWordColumns", "Social");
		if (wordColumns.size() == 0) {
			wordColumns = getWordColumns(words);
		}
		
		updateMatrixColumns(links.keySet(), linkIdColumns);
		updateMatrixColumns(users.keySet(), userIdColumns);
		
		HashMap<Long, HashSet<Long>> userLinkSamples = getUserLinksSample(users.keySet(), friendships);
		
		System.out.println("Minimizing...");
		minimize(linkLikes, userFeatureMatrix, linkFeatureMatrix, users, links, friendships, userIdColumns, linkIdColumns, userLinkSamples, wordColumns, linkWords, words);
		
		System.out.println("Recommending...");
		HashMap<Long, HashSet<Long>> linksToRecommend = getLinksForRecommending(friendships, "Social");
		HashMap<Long, HashMap<Long, Double>> recommendations = recommendLinks(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, 
																				users, links, linksToRecommend, linkWords, wordColumns);
		
		System.out.println("Saving...");
		saveLinkRecommendations(recommendations, "Social");
		saveMatrices(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, wordColumns, "Social");
		
		closeSqlConnection();
		
		System.out.println("Done");
	}
	
	public void minimize(HashMap<Long, HashSet<Long>> linkLikes, Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
					HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, HashMap<Long, HashSet<Long>> friendships,
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

		while (go) {
			iterations++;
			HashMap<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
			HashMap<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures, linkWords, wordColumns);

			Double[][] userDerivative = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
			HashMap<Long, Double[]> userIdDerivative = new HashMap<Long, Double[]>();
			Double[][] linkDerivative = new Double[Constants.K][Constants.LINK_FEATURE_COUNT];
			HashMap<Long, Double[]> linkIdDerivative = new HashMap<Long, Double[]>();
			HashMap<String, Double[]> wordDerivative = new HashMap<String, Double[]>();
			
			System.out.println("Iterations: " + iterations);

			//Get user derivatives
			for (int k = 0; k < Constants.K; k++) {
				for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
					userDerivative[k][l] = getErrorDerivativeOverUserAttribute(userFeatureMatrix, userFeatures, userIdColumns, userTraits, linkTraits, friendships, linkLikes, userLinkSamples, k, l);
				}
				
				for (long userId : userIdColumns.keySet()) {
					if (!userIdDerivative.containsKey(userId)) {
						userIdDerivative.put(userId, new Double[Constants.K]);
					}
					
					userIdDerivative.get(userId)[k] = getErrorDerivativeOverUserId(userFeatureMatrix, userFeatures, userTraits, linkTraits, userIdColumns, friendships, linkLikes, userLinkSamples, k, userId);
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
									
					linkIdDerivative.get(linkId)[q] = getErrorDerivativeOverLinkId(linkIdColumns, userTraits, linkTraits, linkLikes, userLinkSamples, q, linkId);
				}
				
				for (String word : words) {
					if (!wordDerivative.containsKey(word)) {
						wordDerivative.put(word, new Double[Constants.K]);
					}
					
					wordDerivative.get(word)[q] = getErrorDerivativeOverWord(wordColumns, linkWords, userTraits, linkTraits, linkLikes, userLinkSamples, q, word);
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
			for (Object word : wordKeys) {
				Double[] column = wordColumns.get(word);
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
			for (Object word : wordKeys) {
				Double[] column = wordDerivative.get(word);
				for (double d : column) {
					derivatives[index++] = d;
				}
			}
			
			double error = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, userFeatures, userTraits, linkTraits, friendships, linkLikes, userLinkSamples);
			System.out.println("New Error: " + error + ", RMSE: " + calcRMSE(userTraits, linkTraits, linkLikes, userLinkSamples));
			System.out.println("");

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
			for (Object id : wordKeys) {
				Double[] column = wordColumns.get(id);
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
			HashMap<Long, HashSet<Long>> friendships, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		double error = 0;

		for (long i : userTraits.keySet()) {
			for (long j : userTraits.keySet()) {
				if (i == j) continue;
				
				int connection = 0;
				
				if (areFriends(i, j, friendships)) {
					connection = 1;
				}
				
				double predictConnection = predictConnection(userFeatureMatrix, userIdColumns, users, i, j);
				error += Math.pow(connection - predictConnection, 2);
			}
		}
			
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

		error += userNorm + linkNorm;

		return error / 2;
	}
	
	public double predictConnection(Double[][] userMatrix, 
									HashMap<Long, Double[]> idColumns,
									HashMap<Long, Double[]> userFeatures,
									long i, long j)
	{
		Double[] iFeature = userFeatures.get(i);
		Double[] iColumn = idColumns.get(i);
		Double[] jFeature = userFeatures.get(j);
		Double[] jColumn = idColumns.get(j);
		
		Double[] xU = new Double[Constants.K];
		
		for (int x = 0; x < xU.length; x++) {
			xU[x] = 0.0;
			
			for (int y = 0; y < iFeature.length; y++) {
				xU[x] += iFeature[y] * userMatrix[x][y];
			}
			
			xU[x] += iColumn[x];
			
		}
		
		Double[] xUU = new Double[iFeature.length + 1];
		
		for (int x = 0; x < iFeature.length; x++) {
			xUU[x] = 0.0;
				
			for (int y = 0; y < Constants.K; y++) {
				xUU[x] += xU[y] * userMatrix[y][x];
			}
		}
		
		xUU[iFeature.length] = 0.0;
		
		for (int x = 0; x < Constants.K; x++) {
			xUU[iFeature.length] += xU[x] * jColumn[x];
		}
		
		double connection = 0;
		
		for (int x = 0; x < jFeature.length; x++) {
			connection += xUU[x] + jFeature[x];
		}
		connection += xUU[jFeature.length];
		
		return connection;
	}
	
	public double getErrorDerivativeOverUserAttribute(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userIdColumns,
														HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
														HashMap<Long, HashSet<Long>> friendships, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples,
														int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * Constants.LAMBDA;
		
		for (long uid1 : userTraits.keySet()) {
			for (long uid2 : userFeatures.keySet()) {
				if (uid1 == uid2) continue;	
				
				Double[] user1 = userFeatures.get(uid1);
				Double[] user1Id = userIdColumns.get(uid1);
				Double[] user2 = userFeatures.get(uid2);
				Double[] user2Id = userIdColumns.get(uid2);
				
				int c = 0;
				if (areFriends(uid1, uid2, friendships)) c = 1;
				double p = predictConnection(userFeatureMatrix, userIdColumns, userFeatures, uid1, uid2);
				double duu = 2 * user1[y] * user2[y] * userFeatureMatrix[x][y];
				for (int z = 0; z < user1.length; z++) {
					if (z != y) {
						//System.out.println(x + " " + z + " " + user1.length + " " + user2.length);
						duu += user1[y] * user2[z] * userFeatureMatrix[x][z];
						duu += user1[z] * user2[y] * userFeatureMatrix[x][z];
					}
				}
				duu += user1Id[y] * user2[y];
				duu += user2Id[y] * user1[y];
				
				errorDerivative += (c - p) * duu * -1;
			}
		}
		
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
												HashMap<Long, Double[]> userIdColumns, HashMap<Long, HashSet<Long>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
												HashMap<Long, HashSet<Long>> userLinkSamples, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * Constants.LAMBDA;

		Double[] user1 = userFeatures.get(userId);
		
		for (long uid2 : userFeatures.keySet()) {
			if (userId == uid2) continue;	
			
			Double[] user2 = userFeatures.get(uid2);
				
			int c = 0;
			if (areFriends(userId, uid2, friendships)) c = 1;
			double p = predictConnection(userFeatureMatrix, userIdColumns, userFeatures, userId, uid2);
			double duu = 0;
			
			for (int z = 0; z < user1.length; z++) {
				duu += user2[z] * userFeatureMatrix[k][z];
			}
				
			errorDerivative += (c - p) * duu * -1;
		}
		
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

	public double getErrorDerivativeOverLinkId(HashMap<Long, Double[]> linkIdColumns,
												HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
												HashMap<Long, HashSet<Long>> linkLikes, 
												HashMap<Long, HashSet<Long>> userLinkSamples, int x, long linkId)
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
	
	public double getErrorDerivativeOverWord(HashMap<String, Double[]> wordColumns, HashMap<Long, HashSet<String>> linkWords, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes,
			HashMap<Long, HashSet<Long>> userLinkSamples, int x, String word)
	{
		Double[] column = wordColumns.get(word);
		double errorDerivative = column[x] * Constants.LAMBDA;

		for (long userId : userTraits.keySet()) {
			HashSet<Long> links = userLinkSamples.get(userId);

			for (long linkId : links) {
				double dst = userTraits.get(userId)[x] * column[x];		
				double p = dot(userTraits.get(userId), linkTraits.get(linkId));
				double r = 0;

				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst * -1;
			}
		}

		return errorDerivative;
	}
}
