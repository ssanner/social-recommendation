package org.nicta.lr.minimizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.nicta.lr.util.Constants;

public class LogisticSocialMinimizer extends Minimizer 
{	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<String, Double[]> wordColumns,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections)
	{
		double error = 0;

		Object[] keys = connections.keySet().toArray();
		
		for (int a = 0; a < keys.length-1; a++) {
			Long i = (Long)keys[a];
			                 
			for (int b = a+1; b < keys.length; b++) {
				Long j = (Long)keys[b];
				
		//for (long i : userIds) {
			//for (long j : userIds) {
				//if (i == j) continue;
				
				double connection = getFriendConnection(i, j, friendships);
				
				double predictConnection = connections.get(i).containsKey(j) ? connections.get(i).get(j) : connections.get(j).get(i);
				error += Math.pow(connection - predictConnection, 2);
			}
		}
		
		
		error *= Constants.BETA;
		//System.out.println("Soc Error: " + error);
		
		//Get the square error
		for (long i : predictions.keySet()) {
			Set<Long> links = predictions.get(i).keySet();
			
			for (long j : links) {
				int liked = 0;
				
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				
				double predictedLike = logistic(predictions.get(i).get(j));
		
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
			HashMap<Long, Double[]> linkTraits,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes,
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections,
			int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * Constants.LAMBDA;
		
		Object[] keys = connections.keySet().toArray();
		
		for (int a = 0; a < keys.length-1; a++) {
			Long uid1 = (Long)keys[a];
			                 
			for (int b = a+1; b < keys.length; b++) {
				Long uid2 = (Long)keys[b];
			//}
		//}
		//for (long uid1 : connections.keySet()) {
			//for (long uid2 : connections.keySet()) {
				//if (uid1 == uid2) continue;	
				
				Double[] user1 = userFeatures.get(uid1);
				Double[] user1Id = userIdColumns.get(uid1);
				Double[] user2 = userFeatures.get(uid2);
				Double[] user2Id = userIdColumns.get(uid2);
				
				double c = getFriendConnection(uid1, uid2, friendships);
				double p = connections.get(uid1).containsKey(uid2) ? connections.get(uid1).get(uid2) : connections.get(uid2).get(uid1);
				
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
		
		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
			
			for (long linkId : links) {
				double dot = predictions.get(userId).get(linkId);
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y] * logisticDerivative(dot);	
				double p = logistic(dot);
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}
	
	
	public double getErrorDerivativeOverUserId(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections,
			int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * Constants.LAMBDA;

		Double[] user1 = userFeatures.get(userId);
		
		for (long uid2 : connections.keySet()) {
			if (userId == uid2) continue;	
			
			Double[] user2 = userFeatures.get(uid2);
				
			double c = getFriendConnection(userId, uid2, friendships);
			double p = connections.get(userId).containsKey(uid2) ? connections.get(userId).get(uid2) : connections.get(uid2).get(userId);
			double duu = 0;
			
			for (int z = 0; z < user1.length; z++) {
				duu += user2[z] * userFeatureMatrix[k][z];
			}
				
			errorDerivative += Constants.BETA * (c - p) * duu;
		}
		
		Set<Long> links = predictions.get(userId).keySet();
		
		for (long linkId : links) {
			HashSet<Long> likes = linkLikes.get(linkId);
		
			double dot = predictions.get(userId).get(linkId);
			double dst = linkTraits.get(linkId)[k] * logisticDerivative(dot);
			double p = logistic(dot);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst;
		}
		
		return errorDerivative * -1;
	}

	public double getErrorDerivativeOverLinkAttribute(Double[][] linkFeatureMatrix,
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkFeatures,
			HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashMap<Long, Double>> predictions, int x, int y)
	{
		double errorDerivative = linkFeatureMatrix[x][y] * Constants.LAMBDA;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();

			for (long linkId : links) {
				double dot = predictions.get(userId).get(linkId);
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y] * logisticDerivative(dot);		
				double p = logistic(dot);
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}

	public double getErrorDerivativeOverLinkId(HashMap<Long, Double[]> linkIdColumns,
			HashMap<Long, Double[]> userTraits,
			HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, 
			int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * Constants.LAMBDA;

		HashSet<Long> likes = linkLikes.get(linkId);
		
		for (long userId : predictions.keySet()) {
			if (! predictions.get(userId).containsKey(linkId)) continue;
			
			double dot = predictions.get(userId).get(linkId);
			double dst = userTraits.get(userId)[x] * idColumn[x] * logisticDerivative(dot);		
			double p = logistic(dot);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst;
		}
		
		return errorDerivative * -1;
	}
	
	public double getErrorDerivativeOverWord(HashMap<String, Double[]> wordColumns, HashMap<Long, Set<String>> linkWords, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, HashSet<Long>> linkLikes,
			HashMap<Long, HashMap<Long, Double>> predictions,  
			int x, String word)
	{
		Double[] column = wordColumns.get(word);
		double errorDerivative = column[x] * Constants.LAMBDA;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();

			for (long linkId : links) {
				double dot = predictions.get(userId).get(linkId);
				double dst = userTraits.get(userId)[x] * column[x] * logisticDerivative(dot);		
				double p = logistic(dot);
				double r = 0;

				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}
	
	public double logistic(double d)
	{
		return (double)1 / (1 + Math.exp(-d));
	}
	
	public double logisticDerivative(double d)
	{
		return logistic(d) * (1 - logistic(d));
	}
}
