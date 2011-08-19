package org.nicta.lr.recommender;


import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;

public class NNRecommender extends Recommender
{
	private final int K = 5;
	
	Map<Long, Set<Long>> userLinkSamples;
	
	public NNRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends)
	{
		super(linkLikes, userFeatures, linkFeatures, friends);
	}
	

	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		userLinkSamples = trainSamples;
	}
	
	public Map<Long, Double> getAveragePrecisions(Map<Long, Set<Long>> testData)
	{
		HashMap<Long, Double> averagePrecisions = new HashMap<Long, Double>();
		
		for (long userId : testData.keySet()) {
			Set<Long> testLinks = testData.get(userId);
			Double[] userFeature = userFeatures.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			for (long testId : testLinks) {
				Set<Long> likedUsers = linkLikes.get(testId);
				if (likedUsers == null) {
					scores.add(0.0);
					ids.add(testId);
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
				
				
				double numerator = 0;
				double denomenator = 0;
				
				for (long neighborId : kClosestDistance.keySet()) {				
					double neighborSimilarity = kClosestDistance.get(neighborId);
					double neighborRating = 0;
					if (linkLikes.containsKey(neighborId) && linkLikes.get(neighborId).contains(userId)) neighborRating = 1;
					
					numerator += neighborSimilarity * neighborRating;
					denomenator += neighborSimilarity;
				}
				
				double prediction = 0;
				if (denomenator > 0) prediction = numerator / denomenator;
				
				scores.add(prediction);
				ids.add(testId);
			}
	
			Object[] sorted = sort(scores, ids);
			double ap = getUserAP(sorted, userId);
			averagePrecisions.put(userId, ap);
		}
		
		return averagePrecisions;
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
}
