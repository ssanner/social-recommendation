package org.nicta.lr.minimizer;

import java.util.HashMap;
import java.util.HashSet;

import org.nicta.lr.util.Constants;

public class MSRSocialMinimizer extends Minimizer 
{	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns,
			HashMap<Long, Double[]> users, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		double error = 0;

		for (long i : userTraits.keySet()) {
			Double[] iTrait = userTraits.get(i);
			
			for (long j : userTraits.keySet()) {
				if (i == j) continue;
				
				double sim = getFriendConnection(i, j, friendships);
				
				double norm = 0;
				Double[] jTrait = userTraits.get(j);
				
				for (int x = 0; x < jTrait.length; x++) {
					norm += Math.pow(iTrait[x] - jTrait[x], 2);
				}
				
				error += sim * norm;
			}
		}
		error *= Constants.BETA;
		
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
		
	public double getErrorDerivativeOverUserAttribute(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userIdColumns,
														HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits,
														HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples,
														int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * Constants.LAMBDA;
		
		for (long uid1 : userTraits.keySet()) {
			Double[] iTrait = userTraits.get(uid1);
			
			for (long uid2 : userFeatures.keySet()) {
				if (uid1 == uid2) continue;	
						
				Double[] jTrait = userTraits.get(uid2);
				double norm = 0;
				
				for (int z = 0; z < jTrait.length; z++) {
					norm += Math.pow(iTrait[z] - jTrait[z], 2);
				}
				
				double sim = getFriendConnection(uid1, uid2, friendships);
				errorDerivative += sim * (iTrait[x] - jTrait[x]) * userFeatures.get(uid1)[y] * userFeatures.get(uid2)[y] * -1;
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
												HashMap<Long, Double[]> userIdColumns, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
												HashMap<Long, HashSet<Long>> userLinkSamples, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * Constants.LAMBDA;

		//Double[] user1 = userFeatures.get(userId);
		
		Double[] userTrait = userTraits.get(userId);
		
		for (long uid2 : userFeatures.keySet()) {
			if (userId == uid2) continue;	
			
			//Double[] user2 = userFeatures.get(uid2);
			Double[] jTrait = userTraits.get(uid2);
			
			double sim = getFriendConnection(userId, uid2, friendships);
				
			errorDerivative += sim * (userTrait[k] - jTrait[k]) * userFeatures.get(userId)[k] * userFeatures.get(uid2)[k] * -1;
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
