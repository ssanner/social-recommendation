package org.nicta.lr;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.RecommenderUtil;
import org.nicta.lr.util.UserUtil;

public class NNRecommender3 extends LinkRecommender
{
	private final int K = 1;
	private final double BOUNDARY = 0.5;
	
	public NNRecommender3()
	{
		super(null);
	}
	
	public static void main(String[] args)
		throws Exception
	{
		NNRecommender3 nn = new NNRecommender3();
		//nn.recommend();
		nn.crossValidate();
	}
	
	public void crossValidate()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(false);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(links.keySet());
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, false);
		System.out.println("Likes: " + linkLikes.size());
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(linkLikes, users.keySet(), friendships, linkUsers, false);
		System.out.println("Samples: " + userLinkSamples.size());
	
		RecommenderUtil.closeSqlConnection();
		
		HashMap<Long, HashSet<Long>> tested = new HashMap<Long, HashSet<Long>>();
		for (long userId : userLinkSamples.keySet()) {
			tested.put(userId, new HashSet<Long>());
		}
		
		double totalTruePos = 0;
		double totalFalsePos = 0;
		double totalTrueNeg = 0;
		double totalFalseNeg = 0;
		
		HashMap<Long, Double> averagePrecision = new HashMap<Long, Double>();
		HashMap<Long, Integer> precisionCount = new HashMap<Long, Integer>();
		
		for (int x = 0; x < 10; x++) {
			HashMap<Long, HashSet<Long>> forTesting = new HashMap<Long, HashSet<Long>>();
			
			for (long userId : userLinkSamples.keySet()) {
				HashSet<Long> userTesting = new HashSet<Long>();
				forTesting.put(userId, userTesting);
				
				HashSet<Long> samples = userLinkSamples.get(userId);
				HashSet<Long> userTested = tested.get(userId);
				
				
				Object[] sampleArray = samples.toArray();
				
				int addedCount = 0;
				
				while (addedCount < sampleArray.length * .1) {
					if (sampleArray.length == userTested.size()) break;
					
					int randomIndex = (int)(Math.random() * (sampleArray.length));
					Long randomLinkId = (Long)sampleArray[randomIndex];
					
					//System.out.println("Size: " + samples.size() + " Length: " + sampleArray.length + " Random: " + randomIndex + " User: " + userId + "userTested: " + userTested.size());
					if (!tested.get(userId).contains(randomLinkId) && ! userTesting.contains(randomLinkId)) {
						userTesting.add(randomLinkId);
						tested.get(userId).add(randomLinkId);
						samples.remove(randomLinkId);
						addedCount++;
					}
				}		
			}
			
			/*
			int[] stats = nnPredict(linkLikes, friendships, users, links,  userLinkSamples, forTesting);
			
			int truePos = stats[0];
			int falsePos = stats[1];
			int trueNeg = stats[2];
			int falseNeg = stats[3];
			
			totalTruePos += truePos;
			totalFalsePos += falsePos;
			totalTrueNeg += trueNeg;
			totalFalseNeg += falseNeg;
			*/
			
			HashMap<Long, Double> precisions = getAveragePrecision(linkLikes, users, links, friendships, userLinkSamples, forTesting);
			
			for (long userId : precisions.keySet()) {
				double ap = precisions.get(userId);
				if (ap == 0) continue;
				
				if (!averagePrecision.containsKey(userId)) {
					averagePrecision.put(userId, 0.0);
					precisionCount.put(userId, 0);
				}
				
				double average = averagePrecision.get(userId);
				average += ap;
				averagePrecision.put(userId, average);
				
				int count = precisionCount.get(userId);
				count++;
				precisionCount.put(userId, count);
			}
			
			System.out.println("Stats for Run " + (x+1));
			//System.out.println("True Pos: "+ truePos);
			//System.out.println("False Pos: "+ falsePos);
			//System.out.println("True Neg: "+ trueNeg);
			//System.out.println("False Neg: "+ falseNeg);
			System.out.println("");
			
			for (long userId : forTesting.keySet()) {
				HashSet<Long> tests = forTesting.get(userId);
				for (long linkId : tests) {
					userLinkSamples.get(userId).add(linkId);
				}
			}
		}
		
		double accuracy = (double)(totalTruePos + totalTrueNeg) / (double)(totalTruePos + totalFalsePos + totalTrueNeg + totalFalseNeg);
		double precision = (double)totalTruePos / (double)(totalTruePos + totalFalsePos);
		double recall = (double)totalTruePos / (double)(totalTruePos + totalFalseNeg);
		double f1 = 2 * precision * recall / (precision + recall);
		
		double map = 0;
		for (long userId : averagePrecision.keySet()) {
			double pre = averagePrecision.get(userId);
			//pre /= (double)precisionCount.get(userId);
			pre /= (double)10;
			
			map += pre;
		}
		map /= (double)averagePrecision.size();
		
		System.out.println("K=" + K);
		System.out.println("Accuracy: " + accuracy);
		System.out.println("Precision: " + precision);
		System.out.println("Recall: " + recall);
		System.out.println("F1: " + f1);
		System.out.println("MAP: " + map);
		System.out.println("");
	}

	public void recommend()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(true);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(links.keySet());
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, true);
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();	
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(linkLikes, users.keySet(), friendships, linkUsers, true);
		System.out.println("users: " + userLinkSamples.size());
		
		System.out.println("Recommending...");
		HashMap<Long, HashSet<Long>> friendLinksToRecommend = getFriendLinksForRecommending(friendships, "nn");
		HashMap<Long, HashMap<Long, Double>> friendRecommendations = recommendLinks(linkLikes, friendships, users, links, userLinkSamples, friendLinksToRecommend);
		
		HashMap<Long, HashSet<Long>> nonFriendLinksToRecommend = getNonFriendLinksForRecommending(friendships, "nn");
		HashMap<Long, HashMap<Long, Double>> nonFriendRecommendations = recommendLinks(linkLikes, friendships, users, links, userLinkSamples, nonFriendLinksToRecommend);
		
		System.out.println("Saving...");
		saveLinkRecommendations(friendRecommendations, nonFriendRecommendations, "nn");
		
		RecommenderUtil.closeSqlConnection();
		
		System.out.println("Done");
	}
	
	public int[] nnPredict(HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashMap<Long, Double>> friendships, 
							HashMap<Long, Double[]> users, HashMap<Long, Double[]> links, 
							HashMap<Long, HashSet<Long>> userLinkSamples, HashMap<Long, HashSet<Long>> testSamples)
	{	
		int nanCount = 0;
		int infiniteCount = 0;
		
		int truePos = 0;
		int falsePos = 0;
		int trueNeg = 0;
		int falseNeg = 0;
		
		for (long userId : testSamples.keySet()) {
			HashSet<Long> testLinks = testSamples.get(userId);
			HashSet<Long> samples = userLinkSamples.get(userId);
			
			//Holds the k=10 nearest neighbors
			HashMap<Long, Double> kClosestSimilarities = new HashMap<Long, Double>();
			
			double smallestKSimilarity = 1;			
			long smallestId = 0;
			
			for (long testLinkId : testLinks) {
				
				for (long linkId : samples) {					
					double similarity = 0;//getCosineSimilarity(testLinkId, linkId, linkLikes, userIds);
					
					if (kClosestSimilarities.size() < K) {
						kClosestSimilarities.put(linkId, similarity);
						
						if (similarity < smallestKSimilarity) {
							smallestKSimilarity = similarity;
							smallestId = linkId;
						}
					}
					else if (similarity > smallestKSimilarity) {
						kClosestSimilarities.remove(smallestId);
						kClosestSimilarities.put(linkId, similarity);
						
						smallestKSimilarity = 1;
						
						//reset the smallest similarity again
						for (long id : kClosestSimilarities.keySet()) {
							double s = kClosestSimilarities.get(id);
							
							if (s < smallestKSimilarity) {
								smallestKSimilarity = similarity;
								smallestId = id;
							}
						}
					}
				}
				
				double numerator = 0;
				double denomenator = 0;
				
				
				for (long neighborId : kClosestSimilarities.keySet()) {				
					double neighborSimilarity = kClosestSimilarities.get(neighborId);
					double neighborRating = 0;
					if (linkLikes.containsKey(neighborId) && linkLikes.get(neighborId).contains(userId)) neighborRating = 1;
					
					numerator += neighborSimilarity * neighborRating;
					denomenator += neighborSimilarity;
				}
				
				
				double nnRating = 0;
				if (denomenator > 0) nnRating = numerator / denomenator;
				
				if (nnRating >= BOUNDARY) nnRating = 1;
				else nnRating = 0;
				
				if (linkLikes.containsKey(testLinkId) && linkLikes.get(testLinkId).contains(userId)) {
					if (nnRating > 0) {
						truePos++;
					}
					else {
						falseNeg++;
					}
				}
				else {
					if (nnRating > 0) {
						falsePos++;
					}
					else {
						trueNeg++;
					}
				}
				
				//System.out.println("Predict: " + nnRating);
				
				if (Double.isNaN(nnRating)) {
					nanCount++;
				}
				else if (Double.isInfinite(nnRating)) {
					infiniteCount++;
				}
			}
		}
		
		return new int[]{truePos, falsePos, trueNeg, falseNeg};
	}
	
	public HashMap<Long, Double> getAveragePrecision(HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> userLinkSamples, HashMap<Long, HashSet<Long>> testSamples)
	{
		HashMap<Long, Double> userAP = new HashMap<Long, Double>();
		
		for (long userId : testSamples.keySet()) {
			HashSet<Long> testLinks = testSamples.get(userId);
			//HashSet<Long> samples = userLinkSamples.get(userId);
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
					
					double distance = RecommenderUtil.getDistance(userFeature, likedFeature);
					
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
			
			Object[] sorted = RecommenderUtil.sort(scores, ids);
			ArrayList<Double> sortedScores = (ArrayList<Double>)sorted[0];
			ArrayList<Long> sortedIds = (ArrayList<Long>)sorted[1];
			
			ArrayList<Double> precisions = new ArrayList<Double>();
			int pos = 0;
			for (int x = 0; x < sortedScores.size(); x++) {
				long linkId = sortedIds.get(x);
			
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					pos++;
					precisions.add((double)pos / (double)(x+1));
				}
			}
			
			double ap = 0;
			
			if (precisions.size() > 0) {
				for (double p : precisions) {
					ap += p;
				}
				
				ap /= (double)precisions.size();
			}
			
			userAP.put(userId, ap);
		}
		
		return userAP;
	}
	
	
	public HashMap<Long, HashMap<Long, Double>> recommendLinks(HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashMap<Long, Double>> friendships,
																HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures,
																HashMap<Long, HashSet<Long>> userLinkSamples, HashMap<Long, HashSet<Long>> linksToRecommend)
		throws SQLException
	{	
		HashMap<Long, HashMap<Long, Double>> recommendations = new HashMap<Long, HashMap<Long, Double>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		for (long userId :linksToRecommend.keySet()) {
			System.out.println("For user: " + userId);
			HashSet<Long> userLinks = linksToRecommend.get(userId);
			Double[] userFeature = userFeatures.get(userId);
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
			
			ResultSet result = statement.executeQuery("SELECT max_links FROM trackUserUpdates WHERE uid=" + userId);
			result.next();
			int maxLinks = result.getInt("max_links");
			
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
					
					double distance = RecommenderUtil.getDistance(userFeature, likedFeature);
					
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
}
