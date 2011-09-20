package org.nicta.lr.recommender;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.Configuration;
import org.nicta.lr.util.UserUtil;

public class SocialRecommender extends MFRecommender
{	
	double beta = 1.0E-3;
	
	public SocialRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends)
	{
		super(linkLikes, userFeatures, linkFeatures, friends);
		
		K = 5;
		lambda = 1000;
		
		type = "social";
		friendships = friends;
		
		if (Configuration.DEPLOYMENT_TYPE == Constants.TEST || Configuration.INITIALIZE) {
			initializePriors(userFeatures.keySet(), linkFeatures.keySet());
		}
		else if (Configuration.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			try {
				loadData();
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		try {
			int linkCount = 0;
			
			//Remove users that do not have data yet.
			HashSet<Long> remove = new HashSet<Long>();
			for (long trainId : trainSamples.keySet()) {
				if (! userFeatures.containsKey(trainId)) {
					remove.add(trainId);
				}
				else {
					linkCount += trainSamples.get(trainId).size();
				}
			}
		
			for (long userId : remove) {
				trainSamples.remove(userId);
			}
			
			friendConnections = UserUtil.getFriendInteractionMeasure(trainSamples.keySet());
			//friendConnections = UserUtil.getFriendLikeSimilarity(userLinkSamples.keySet());
			//friendConnections = friendships;
			
			
			minimizeByThreadedLBFGS(trainSamples);
			//minimizeByLBFGS(trainSamples);
			//checkDerivative(trainSamples);
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkIdColumns,
			Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Double>> connections)
	{
		double error = 0;
	
		Object[] keys = connections.keySet().toArray();
		
		for (int a = 0; a < keys.length-1; a++) {
			Long i = (Long)keys[a];
			                 
			for (int b = a+1; b < keys.length; b++) {
				Long j = (Long)keys[b];
				
				double connection = getFriendConnection(i, j, friendships);
				double predictConnection = connections.get(i).containsKey(j) ? connections.get(i).get(j) : connections.get(j).get(i);
				error += Math.pow(connection - predictConnection, 2);
			}
		}
		
		error *= beta;
		
		for (long i : predictions.keySet()) {
			Set<Long> links = predictions.get(i).keySet();
			
			for (long j : links) {
				int liked = 0;
				
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				double predictedLike = predictions.get(i).get(j);
		
				error += Math.pow(liked - predictedLike, 2);
			}
		}

		//Get User and Link norms for regularisation
		double userNorm = 0;
		double linkNorm = 0;

		for (int x = 0; x < K; x++) {
			for (int y = 0; y < Configuration.USER_FEATURE_COUNT; y++) {
				userNorm += Math.pow(userFeatureMatrix[x][y], 2);
			}
		}
		for (long id : userIdColumns.keySet()) {
			Double[] column = userIdColumns.get(id);
			
			for (double val : column) {
				userNorm += Math.pow(val, 2);
			}
		}

		for (int x = 0; x < K; x++) {
			for (int y = 0; y < Configuration.LINK_FEATURE_COUNT; y++) {
				linkNorm += Math.pow(linkFeatureMatrix[x][y], 2);
			}
		}
		for (long id : linkIdColumns.keySet()) {
			Double[] column = linkIdColumns.get(id);
			
			for (double val : column) {
				linkNorm += Math.pow(val, 2);
			}
		}
			
		userNorm *= lambda;
		linkNorm *= lambda;

		error += userNorm + linkNorm;

		return error / 2;
	}
	
	public double getErrorDerivativeOverUserAttribute(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
														Map<Long, Map<Long, Double>> connections, int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * lambda;
		
		Object[] keys = connections.keySet().toArray();
		
		for (int a = 0; a < keys.length-1; a++) {
			Long uid1 = (Long)keys[a];
			                 
			for (int b = a+1; b < keys.length; b++) {
				Long uid2 = (Long)keys[b];
				
				Double[] user1 = userFeatures.get(uid1);
				Double[] user1Id = userIdColumns.get(uid1);
				Double[] user2 = userFeatures.get(uid2);
				Double[] user2Id = userIdColumns.get(uid2);
				
				double c = getFriendConnection(uid1, uid2, friendships);
				double p = connections.get(uid1).containsKey(uid2) ? connections.get(uid1).get(uid2) : connections.get(uid2).get(uid1);
				
				double duu = 2 * user1[y] * user2[y] * userFeatureMatrix[x][y];
				for (int z = 0; z < user1.length; z++) {
					if (z != y) {
						duu += user1[y] * user2[z] * userFeatureMatrix[x][z];
						duu += user1[z] * user2[y] * userFeatureMatrix[x][z];
					}
				}
				duu += user1Id[x] * user2[y];
				duu += user2Id[x] * user1[y];
				
				errorDerivative += beta * (c - p) * duu * -1;
			}
		}
		
		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
			
			for (long linkId : links) {
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst * -1;
			}
		}

		return errorDerivative;
	}
	
	
	public double getErrorDerivativeOverUserId(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
												Map<Long, Map<Long, Double>> connections, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * lambda;

		Double[] user1 = userFeatures.get(userId);
		
		for (long uid2 : connections.keySet()) {
			if (userId == uid2) continue;	
			
			Double[] user2 = userFeatures.get(uid2);
			
			Double[] user2Column = userIdColumns.get(uid2);
			
			double c = getFriendConnection(userId, uid2, friendships);
			double p = connections.get(userId).containsKey(uid2) ? connections.get(userId).get(uid2) : connections.get(uid2).get(userId);
			
			double duu = 0;
			
			for (int z = 0; z < user1.length; z++) {
				duu += user2[z] *  userFeatureMatrix[k][z];

			}
			
			duu += user2Column[k];
			
			errorDerivative += beta * (c - p) * duu * -1;
		}
		
		Set<Long> links = predictions.get(userId).keySet();
		for (long linkId : links) {
			if (!linkTraits.containsKey(linkId)) continue;
			
			Set<Long> likes = linkLikes.get(linkId);
		
			double dst = linkTraits.get(linkId)[k];
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst * -1;
		}
		
		return errorDerivative;
	}

	public double getErrorDerivativeOverLinkAttribute(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, int y)
	{
		double errorDerivative = linkFeatureMatrix[x][y] * lambda;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();

			for (long linkId : links) {
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst * -1;
			}
		}

		return errorDerivative;
	}

	public double getErrorDerivativeOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * lambda;

		Set<Long> likes = linkLikes.get(linkId);
		
		for (long userId : predictions.keySet()) {
			if (! predictions.get(userId).containsKey(linkId)) continue;
			
			double dst = userTraits.get(userId)[x] /* * idColumn[x]*/;		
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst * -1;
		}
		
		return errorDerivative;
	}
	
	
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
	public double getFriendConnection(Long uid1, Long uid2, Map<Long, Map<Long, Double>> friendships)
	{
		if ((friendships.containsKey(uid1) && friendships.get(uid1).containsKey(uid2))) {
			return friendships.get(uid1).get(uid2);
		}
		
		return 0;
	}	
	
	public void setBeta(double b)
	{
		beta = b;
	}
}
