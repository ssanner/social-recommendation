package org.nicta.lr.minimizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.nicta.lr.util.Constants;

public class SocialMinimizer extends Minimizer 
{	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<String, Double[]> wordColumns,
			HashMap<Long, Double[]> users, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		double error = 0;

		for (long i : userLinkSamples.keySet()) {
			for (long j : userLinkSamples.keySet()) {
				if (i == j) continue;
				
				double connection = getFriendConnection(i, j, friendships);
				
				double predictConnection = predictConnection(userFeatureMatrix, userIdColumns, users, i, j);
				error += Math.pow(connection - predictConnection, 2);
			}
		}
		
		error *= Constants.BETA;
		//System.out.println("Soc Error: " + error);
		
		//Get the square error
		for (long i : userLinkSamples.keySet()) {
			HashSet<Long> links = userLinkSamples.get(i);
			
			for (long j : links) {
				if (!linkTraits.containsKey(j)) continue;
				
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
														HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples,
														int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * Constants.LAMBDA;
		
		for (long uid1 : userLinkSamples.keySet()) {
			for (long uid2 : userLinkSamples.keySet()) {
				if (uid1 == uid2) continue;	
				
				Double[] user1 = userFeatures.get(uid1);
				Double[] user1Id = userIdColumns.get(uid1);
				Double[] user2 = userFeatures.get(uid2);
				Double[] user2Id = userIdColumns.get(uid2);
				
				double c = getFriendConnection(uid1, uid2, friendships);
				
				double p = predictConnection(userFeatureMatrix, userIdColumns, userFeatures, uid1, uid2);
				double duu = 2 * user1[y] * user2[y] * userFeatureMatrix[x][y];
				for (int z = 0; z < user1.length; z++) {
					if (z != y) {
						//System.out.println(x + " " + z + " " + user1.length + " " + user2.length);
						duu += user1[y] * user2[z] * userFeatureMatrix[x][z];
						duu += user1[z] * user2[y] * userFeatureMatrix[x][z];
					}
				}
				duu += user1Id[x] * user2[y];
				duu += user2Id[x] * user1[y];
				
				errorDerivative += Constants.BETA * (c - p) * duu;
			}
		}
		
		for (long userId : userLinkSamples.keySet()) {
			HashSet<Long> links = userLinkSamples.get(userId);
			
			for (long linkId : links) {
				//System.out.println("Link: " + linkId + " " + linkTraits.get(linkId));
				//System.out.println("User: " + userId + " " + userFeatures.get(userId));
				//System.out.println("Like: " + linkLikes.containsKey(linkId));
				if (!linkTraits.containsKey(linkId)) continue;
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y];		
				double p = dot(userTraits.get(userId), linkTraits.get(linkId));
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}
	
	
	public double getErrorDerivativeOverUserId(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
												HashMap<Long, Double[]> userIdColumns, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
												HashMap<Long, HashSet<Long>> userLinkSamples, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * Constants.LAMBDA;

		Double[] user1 = userFeatures.get(userId);
		
		for (long uid2 : userLinkSamples.keySet()) {
			if (userId == uid2) continue;	
			
			Double[] user2 = userFeatures.get(uid2);
				
			double c = getFriendConnection(userId, uid2, friendships);
			double p = predictConnection(userFeatureMatrix, userIdColumns, userFeatures, userId, uid2);
			double duu = 0;
			
			for (int z = 0; z < user1.length; z++) {
				duu += user2[z] * userFeatureMatrix[k][z];
			}
				
			errorDerivative += Constants.BETA * (c - p) * duu;
		}
		
		HashSet<Long> links = userLinkSamples.get(userId);
		
		for (long linkId : links) {
			if (!linkTraits.containsKey(linkId)) continue;
			
			HashSet<Long> likes = linkLikes.get(linkId);
		
			double dst = linkTraits.get(linkId)[k] /* userFeatures.get(userId)[k]*/;
			double p = dot(userTraits.get(userId), linkTraits.get(linkId));
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst;
		}
		
		return errorDerivative * -1;
	}

	public double getErrorDerivativeOverLinkAttribute(Double[][] linkFeatureMatrix,
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, Double[]> linkFeatures,
			HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples, int x, int y)
	{
		double errorDerivative = linkFeatureMatrix[x][y] * Constants.LAMBDA;

		for (long userId : userLinkSamples.keySet()) {
			HashSet<Long> links = userLinkSamples.get(userId);

			for (long linkId : links) {
				if (!linkTraits.containsKey(linkId)) continue;
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y];		
				double p = dot(userTraits.get(userId), linkTraits.get(linkId));
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}

	public double getErrorDerivativeOverLinkId(HashMap<Long, Double[]> linkIdColumns,
												HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
												HashMap<Long, HashSet<Long>> linkLikes, 
												HashMap<Long, HashSet<Long>> userLinkSamples, int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * Constants.LAMBDA;

		HashSet<Long> likes = linkLikes.get(linkId);
		
		for (long userId : userLinkSamples.keySet()) {
			if (! userLinkSamples.get(userId).contains(linkId)) continue;
			
			double dst = userTraits.get(userId)[x] * idColumn[x];		
			double p = dot(userTraits.get(userId), linkTraits.get(linkId));
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst;
		}
		
		return errorDerivative * -1;
	}
	
	
	public double getErrorDerivativeOverWord(HashMap<String, Double[]> wordColumns, HashMap<Long, Set<String>> linkWords, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes,
			HashMap<Long, HashSet<Long>> userLinkSamples, int x, String word)
	{
		Double[] column = wordColumns.get(word);
		double errorDerivative = column[x] * Constants.LAMBDA;

		for (long userId : userLinkSamples.keySet()) {
			HashSet<Long> links = userLinkSamples.get(userId);

			for (long linkId : links) {
				if (!linkTraits.containsKey(linkId)) continue;
				
				double dst = userTraits.get(userId)[x] * column[x];		
				double p = dot(userTraits.get(userId), linkTraits.get(linkId));
				double r = 0;

				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}
}
