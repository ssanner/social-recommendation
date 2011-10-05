package org.nicta.lr.recommender;


import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

public class NNRecommender extends Recommender
{
	private int K = 50;
	
	Map<Long, Set<Long>> userLinkSamples;
	
	public NNRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends)
	{
		super(linkLikes, userFeatures, linkFeatures, friends);
	}
	

	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		userLinkSamples = trainSamples;
	}

	
	public Map<Long, Map<Long, Double>> getPredictions(Map<Long, Set<Long>> testData)
	{
		Map<Long, Map<Long, Double>> predictions = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId : testData.keySet()) {
			HashMap<Long, Double> userPredictions = new HashMap<Long, Double>();
			predictions.put(userId, userPredictions);
			
			Double[] userFeature = userFeatures.get(userId);
			
			Set<Long> userTest = testData.get(userId);
			
			for (long testId : userTest) {
				Set<Long> likedUsers = linkLikes.get(testId);
				
				if (likedUsers == null) {
					likedUsers = new HashSet<Long>();
				}
				
				HashMap<Long, Double> kClosestDistance = new HashMap<Long, Double>();
					
				double biggestKDistance = 0;			
				long biggestId = 0;
					
				for (long user : likedUsers) {		
					if (user == userId || !userFeatures.containsKey(user)) continue;
					if (!userLinkSamples.get(user).contains(testId)) continue;
					
					Double[] likedFeature = userFeatures.get(user);
						
					double distance = getDistance(userFeature, likedFeature);
						
					if (kClosestDistance.size() < K) {
						kClosestDistance.put(user, distance);
							
						if (distance > biggestKDistance) {
							biggestKDistance = distance;
							biggestId = user;
						}
					}
					else if (distance < biggestKDistance) {
						kClosestDistance.remove(biggestId);
						kClosestDistance.put(user, distance);
							
						biggestKDistance = 0;
							
						//reset the biggest distance again
						for (long id : kClosestDistance.keySet()) {
							double d = kClosestDistance.get(id);
							
							if (d > biggestKDistance) {
								biggestKDistance = distance;
								biggestId = id;
							}
						}
					}
				}
					
					
				double prediction = 0;
					
				for (long neighborId : kClosestDistance.keySet()) {				
					prediction += 1 / kClosestDistance.get(neighborId);
				}
				
				userPredictions.put(testId, prediction);
				
			}
		}
		
		return predictions;
	}
	
	public Map<Long, Map<Long, Double>> recommend(Map<Long, Set<Long>> linksToRecommend)
	{	
		if (userMax == null) {
			try {
				userMax = getUserMax(linksToRecommend.keySet());
			}
			catch (SQLException ex) {
				ex.printStackTrace();
			}
		}
		
		HashMap<Long, Map<Long, Double>> recommendations = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId :linksToRecommend.keySet()) {
			System.out.println("For user: " + userId);
			Set<Long> userLinks = linksToRecommend.get(userId);
			Double[] userFeature = userFeatures.get(userId);
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
			
			int maxLinks = userMax.get(userId);
			
			for (long recommendId : userLinks) {
				Set<Long> likedUsers = linkLikes.get(recommendId);
				
				if (likedUsers == null) {
					System.out.println("Nobody liked");
					continue;
				}
				
				//Holds the k=10 nearest neighbors
				HashMap<Long, Double> kClosestDistance = new HashMap<Long, Double>();
				
				double biggestKDistance = 0;			
				long biggestId = 0;
				
				for (long user : likedUsers) {
					if (user == userId || !userFeatures.containsKey(user)) continue;
					
					Double[] likedFeature = userFeatures.get(user);
					
					double distance = getDistance(userFeature, likedFeature);
					
					if (kClosestDistance.size() < K) {
						kClosestDistance.put(user, distance);
						
						if (distance > biggestKDistance) {
							biggestKDistance = distance;
							biggestId = user;
						}
					}
					else if (distance < biggestKDistance) {
						kClosestDistance.remove(biggestId);
						kClosestDistance.put(user, distance);
						
						biggestKDistance = 0;
						
						//reset the biggest distance again
						for (long id : kClosestDistance.keySet()) {
							double d = kClosestDistance.get(id);
							
							if (d > biggestKDistance) {
								biggestKDistance = distance;
								biggestId = id;
							}
						}
					}
				}
				
				
				double prediction = 0;
				for (long neighborId : kClosestDistance.keySet()) {				
					prediction += 1 / kClosestDistance.get(neighborId);
				}
				
				System.out.println("Prediction: " + prediction + " Liked: " + likedUsers.size() + " Closest: " + kClosestDistance.size());
				//Recommend only if prediction score is greater or equal than the boundary
				//if (prediction > BOUNDARY) {
					//We recommend only a set number of links per day/run. 
					//If the recommended links are more than the max number, recommend only the highest scoring links.
					if (linkValues.size() < maxLinks) {
						linkValues.put(recommendId, prediction);
					}
					else {
						//Get the lowest scoring recommended link and replace it with the current link
						//if this one has a better score.
						long lowestKey = 0;
						double lowestValue = Double.MAX_VALUE;
						
						for (long id : linkValues.keySet()) {
							if (linkValues.get(id) < lowestValue) {
								lowestKey = id;
								lowestValue = linkValues.get(id);
							}
						}
		
						if (prediction > lowestValue) {
							linkValues.remove(lowestKey);
							linkValues.put(recommendId, prediction);
						}
					}
				//}
			}
		}
	
		return recommendations;
	}
	
	public static double getDistance(Double[] d1, Double[] d2)
	{
		double distance = 1;
		for (int x = 0; x < d1.length; x++) {
			distance += Math.pow(d1[x] - d2[x], 2);
		}
		return Math.sqrt(distance);
	}
	
	public void saveModel()
		throws SQLException
	{
		//do nothing
	}
	
	public void setK(int k)
	{
		K = k;
	}
}
