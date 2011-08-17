package org.nicta.lr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.RecommenderUtil;
import org.nicta.lr.util.UserUtil;

public class BaselineGlobalRecommender extends LinkRecommender
{
	public static void main(String[] args)
		throws Exception
	{
		new BaselineGlobalRecommender().crossValidate();
	}
	
	public BaselineGlobalRecommender()
	{
		super(null);
	}
	
	public void crossValidate()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(true);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(links.keySet());
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, true);
		HashMap<Long, HashMap<Long, Double>> friends = UserUtil.getFriendships();
		
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(linkLikes, users.keySet(), friends, linkUsers, true);
		System.out.println("Samples: " + userLinkSamples.size());
		
		RecommenderUtil.closeSqlConnection();
		
		HashMap<Long, HashSet<Long>> tested = new HashMap<Long, HashSet<Long>>();
		for (long userId : userLinkSamples.keySet()) {
			tested.put(userId, new HashSet<Long>());
		}
		
		HashMap<Long, Double> averagePrecision = new HashMap<Long, Double>();
		HashMap<Long, Integer> precisionCount = new HashMap<Long, Integer>();
		
		//for (int x = 0; x < 10; x++) {
			HashMap<Long, HashSet<Long>> forTesting = new HashMap<Long, HashSet<Long>>();
			
			for (long userId : userLinkSamples.keySet()) {
				HashSet<Long> userTesting = new HashSet<Long>();
				forTesting.put(userId, userTesting);
				
				HashSet<Long> samples = userLinkSamples.get(userId);
				HashSet<Long> userTested = tested.get(userId);
				
				
				Object[] sampleArray = samples.toArray();
				
				int addedCount = 0;
				int likeCount = 0;
				
				while (addedCount < sampleArray.length * .2 || addedCount < 2) {
					if (sampleArray.length == userTested.size()) break;
					
					int randomIndex = (int)(Math.random() * (sampleArray.length));
					Long randomLinkId = (Long)sampleArray[randomIndex];
					
					//System.out.println("Size: " + samples.size() + " Length: " + sampleArray.length + " Random: " + randomIndex + " User: " + userId + "userTested: " + userTested.size());
					if (!tested.get(userId).contains(randomLinkId) && ! userTesting.contains(randomLinkId)) {
						
						if (likeCount == 0) {
							if (!linkLikes.get(randomLinkId).contains(userId)) {
								continue;
							}
							else {
								likeCount++;
							}
						}
						else {		
							if (linkLikes.get(randomLinkId).contains(userId)) {
								int remainingLike = 0;
								for (long remainingId : samples) {
									if (linkLikes.get(remainingId).contains(userId)) remainingLike++;
								}
								
								if (remainingLike == 1) {
									continue;
								}
								else {
									likeCount++;
								}
							}
						}
						
						userTesting.add(randomLinkId);
						tested.get(userId).add(randomLinkId);
						samples.remove(randomLinkId);
						addedCount++;
					}
				}		
			}
			
			HashMap<Long, Double> precisions = getAveragePrecision(linkLikes, forTesting, links);
			
			for (long userId : precisions.keySet()) {
				double ap = precisions.get(userId);
				//if (ap == 0) continue;
				
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
			
			for (long userId : forTesting.keySet()) {
				HashSet<Long> tests = forTesting.get(userId);
				for (long linkId : tests) {
					userLinkSamples.get(userId).add(linkId);
				}
			}
		//}
		
		double map = 0;
		for (long userId : averagePrecision.keySet()) {
			double pre = averagePrecision.get(userId);
			//pre /= (double)10;
			
			map += pre;
		}
		map /= (double)averagePrecision.size();
		
		double standardDev = 0;
		for (long userId : averagePrecision.keySet()) {
			double pre = averagePrecision.get(userId);
			standardDev += Math.pow(pre - map, 2);
		}
		standardDev /= (double)averagePrecision.size();
		standardDev = Math.sqrt(standardDev);
		double standardError = standardDev / Math.sqrt(averagePrecision.size());
		
		System.out.println("MAP: " + map);
		System.out.println("SD: " + standardDev);
		System.out.println("SE: " + standardError);
		System.out.println("");
	}
	
	public HashMap<Long, Double> getAveragePrecision(HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> testSamples, HashMap<Long, Double[]> linkFeatures)
	{
		HashMap<Long, Double> userAP = new HashMap<Long, Double>();
		
		for (long userId : testSamples.keySet()) {
			HashSet<Long> testLinks = testSamples.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			for (long testId : testLinks) {
				Double[] feature = linkFeatures.get(testId);
				double prediction = feature[0] + feature[1];
				
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
}
