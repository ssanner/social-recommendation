package org.nicta.lr;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.RecommenderUtil;
import org.nicta.lr.util.UserUtil;

public class NNRecommender extends LinkRecommender
{
	private final int K = 150;
	private final double BOUNDARY = 0.5;
	
	public NNRecommender()
	{
		super(null);
	}
	
	public static void main(String[] args)
		throws Exception
	{
		NNRecommender nn = new NNRecommender();
		nn.recommend();
		//nn.crossValidate();
	}
	
	public void crossValidate()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(false);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(links.keySet());
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(users.keySet(), friendships, false);
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
		
		HashMap<Long, Integer[]> totalUserPrecision = new HashMap<Long, Integer[]>();
		
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
			
			int[] stats = nnPredict(linkLikes, friendships, users, links,  userLinkSamples, forTesting);
			
			int truePos = stats[0];
			int falsePos = stats[1];
			int trueNeg = stats[2];
			int falseNeg = stats[3];
			
			totalTruePos += truePos;
			totalFalsePos += falsePos;
			totalTrueNeg += trueNeg;
			totalFalseNeg += falseNeg;
			
			double accuracy = (double)(truePos + trueNeg) / (double)(truePos + falsePos + trueNeg + falseNeg);
			double precision = (double)truePos / (double)(truePos + falsePos);
			double recall = (double)truePos / (double)(truePos + falseNeg);
			double f1 = 2 * precision * recall / (precision + recall);
			
			
			//HashMap<Long, Integer[]> precisions = getPrecision(linkLikes, users, links, userLinkSamples, forTesting);
			/*
			for (long u : precisions.keySet()) {
				Integer[] precisionAt = precisions.get(u);
				
				if (!totalUserPrecision.containsKey(u)) {
					totalUserPrecision.put(u, new Integer[]{0, 0, 0});
				}
				
				Integer[] userPrecision = totalUserPrecision.get(u);
				for (int z = 0; z < userPrecision.length; z++) {
					userPrecision[z] += precisionAt[z];
				}
			}
			*/
			
			System.out.println("Stats for Run " + (x+1));
			System.out.println("True Pos: "+ truePos);
			System.out.println("False Pos: "+ falsePos);
			System.out.println("True Neg: "+ trueNeg);
			System.out.println("False Neg: "+ falseNeg);
			System.out.println("Accuracy: " + accuracy);
			System.out.println("Precision: " + precision);
			System.out.println("Recall: " + recall);
			System.out.println("F1: " + f1);
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
		
		double map[] = {0, 0, 0};
		
		for (long user : totalUserPrecision.keySet()) {
			Integer[] precisions = totalUserPrecision.get(user);
			
			for (int x = 0; x < precisions.length; x++) {
				map[x] += (double)precisions[x] / (double)10;
			}
		}
		
		for (int x = 0; x < map.length; x++) {
			map[x] /= (double)totalUserPrecision.size();
		}
		
		System.out.println("AVERAGE FOR 10 RUNS");
		System.out.println("Accuracy: " + accuracy);
		System.out.println("Precision: " + precision);
		System.out.println("Recall: " + recall);
		System.out.println("F1: " + f1);
		//System.out.println("MAP@1: " + map[0]);
		//System.out.println("MAP@2: " + map[1]);
		//System.out.println("MAP@3: " + map[2]);
		System.out.println("");
	}

	public void recommend()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(false);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(links.keySet());
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();	
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(users.keySet(), friendships, false);
		System.out.println("users: " + userLinkSamples.size());
		
		System.out.println("Recommending...");
		HashMap<Long, HashSet<Long>> linksToRecommend = getLinksForRecommending(friendships, "NN");
		HashMap<Long, HashMap<Long, Double>> recommendations = recommendLinks(linkLikes, friendships, users, links, userLinkSamples, linksToRecommend);
		
		System.out.println("Saving...");
		saveLinkRecommendations(recommendations, "NN");
		
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
			
			Set<Long> userIds = users.keySet();
			
			for (long testLinkId : testLinks) {
				//double testUserIdDbl = links.get(testLinkId)[3];
				//long testUserId = (long)testUserIdDbl;
				
				//if (friendships.containsKey(testUserId)) userIds.addAll(friendships.get(testUserId).keySet());
				
				for (long linkId : samples) {
					//double trainUserIdDbl = links.get(linkId)[3];
					//long trainUserId = (long)trainUserIdDbl;
					
					//if (friendships.containsKey(trainUserId)) userIds.addAll(friendships.get(trainUserId).keySet());
					
					double similarity = getCosineSimilarity(testLinkId, linkId, linkLikes, userIds);
					
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
	
	public HashMap<Long, HashMap<Long, Double>> recommendLinks(HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashMap<Long, Double>> friendships,
																HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures,
																HashMap<Long, HashSet<Long>> userLinkSamples, HashMap<Long, HashSet<Long>> linksToRecommend)
		throws SQLException
	{	
		HashMap<Long, HashMap<Long, Double>> recommendations = new HashMap<Long, HashMap<Long, Double>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		for (long userId :linksToRecommend.keySet()) {
			HashSet<Long> userLinks = linksToRecommend.get(userId);
			HashSet<Long> samples = userLinkSamples.get(userId);
			if (samples == null) continue;
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
			
			//Holds the k=10 nearest neighbors
			HashMap<Long, Double> kClosestSimilarities = new HashMap<Long, Double>();
			
			double smallestKSimilarity = 1;			
			long smallestId = 0;
			
			Set<Long> userIds = userFeatures.keySet();
			
			ResultSet result = statement.executeQuery("SELECT max_links FROM trackUserUpdates WHERE uid=" + userId);
			result.next();
			int maxLinks = result.getInt("max_links");
			
			for (long recommendId : userLinks) {
				for (long linkId : samples) {
					double similarity = getCosineSimilarity(recommendId, linkId, linkLikes, userIds);
					
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
				
				double prediction = 0;
				if (denomenator > 0) prediction = numerator / denomenator;
				System.out.println("Prediction: " + prediction);
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
		
						if (prediction < lowestValue) {
							linkValues.remove(lowestKey);
							linkValues.put(recommendId, prediction);
						}
					}
				//}
			}
		}
	
		return recommendations;
	}
	
	public double getCosineSimilarity(long testLinkId, long trainLinkId, HashMap<Long, HashSet<Long>> linkLikes, Set<Long> userIds)
	{
		double similarity = 0;
		double mag1 = 0;
		double mag2 = 0;
		
		HashSet<Long> testLikes = linkLikes.get(testLinkId);
		HashSet<Long> trainLikes = linkLikes.get(trainLinkId);
		
		if (testLikes == null || trainLikes == null) return 0;
		
		for (long userId : userIds) {
			int testVal = 0;
			int trainVal = 0;
			
			if (testLikes.contains(userId)) testVal = 1;
			if (trainLikes.contains(userId)) trainVal = 1;
			
			if (testVal != 0 && trainVal != 0) {
				similarity += testVal * trainVal;
				mag1 += Math.pow(testVal, 2);
				mag2 += Math.pow(trainVal, 2);
			}
		}
		
		if (mag1 == 0 || mag2 == 0) {
			return 0;
		}
		else {
			return similarity / ((Math.sqrt(mag1) * Math.sqrt(mag2)));
		}
	}
}
