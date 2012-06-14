package org.nicta.lr;

import project.riley.predictor.ArffData;
import project.riley.predictor.ArffData.DataEntry;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import org.nicta.lr.recommender.Recommender;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.UserUtil;

public class LinkRecommenderArff extends LinkRecommender
{
	//Use as much of the old code as possible.
	//But make a new class and method as I don't trust myself yet to edit the old code and not break anything
	public void run(String trainFile, String testFile, String type)
		throws Exception
	{
		this.type = type;
		
		Set<Long> linkIds = new HashSet<Long>();
		Set<Long> userIds = new HashSet<Long>();
		Map<Long, Set<Long>> linkLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();
		
		//Read train data
		ArffData trainArff = new ArffData(trainFile);
		for (DataEntry de : trainArff._data) {
			//System.out.println(de.toString());
			long userId = ((Double)de.getData(0)).longValue();
			long linkId = ((Double)de.getData(1)).longValue();
			boolean like = (Integer)de.getData(2) == 1;
			
			//System.out.println(userId + " " + linkId + " " + like);
			
			linkIds.add(linkId);
			userIds.add(userId);
			
			if (!trainData.containsKey(userId)) {
				trainData.put(userId, new HashSet<Long>());
			}
			trainData.get(userId).add(linkId);
			
			if (like) {
				if (!linkLikes.containsKey(linkId)) {
					linkLikes.put(linkId, new HashSet<Long>());
				}					
				linkLikes.get(linkId).add(userId);
			}
		}
		
		Map<Long, Set<Long>> testLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> testData = new HashMap<Long, Set<Long>>();
		
		//Read test data
		ArffData testArff = new ArffData(testFile);
		for (DataEntry de : testArff._data) {
			//System.out.println(de.toString());
			long userId = ((Double)de.getData(0)).longValue();
			long linkId = ((Double)de.getData(1)).longValue();
			boolean like = (Integer)de.getData(2) == 1;
			
			//System.out.println(userId + " " + linkId + " " + like);
			
			linkIds.add(linkId);
			userIds.add(userId);
			
			if (!testData.containsKey(userId)) {
				testData.put(userId, new HashSet<Long>());
			}
			testData.get(userId).add(linkId);
			
			if (like) {
				if (!testLikes.containsKey(linkId)) {
					testLikes.put(linkId, new HashSet<Long>());
				}					
				testLikes.get(linkId).add(userId);
			}
		}
		
		//Read features
		Map<Long, Double[]> users = UserUtil.getUserFeatures(userIds);
		Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linkIds);
		
		//Remove ids that aren't actually links after all
		int num_invalid_link_ids = 0;
		for (Long userId : trainData.keySet()) {
			Set<Long> remove = new HashSet<Long>();
			
			for (Long linkId : trainData.get(userId)) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
					num_invalid_link_ids++;
				}
			}
			
			trainData.get(userId).removeAll(remove);
		}
		for (Long userId : testData.keySet()) {
			Set<Long> remove = new HashSet<Long>();
			
			for (Long linkId : testData.get(userId)) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
					num_invalid_link_ids++;
				}
			}
			
			testData.get(userId).removeAll(remove);
		}
		System.out.println("Discarded " + num_invalid_link_ids + " invalid link IDs.");
		
		//Code below is basically doing the same as LinkRecommender
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
		
		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		recommender.train(trainData);
		
		Map<Long, Map<Long, Double>> predictions = recommender.getPredictions(testData);
		Map<Long, Map<Long, Double>> trainPredictions = recommender.getPredictions(trainData);
		double threshold = getOptimalThreshold(trainPredictions, linkLikes);
		getTotalSimpleMetrics(predictions, testLikes, threshold);
	}
	
	public static void main(String[] args)
		throws Exception
	{
		//String trainFile = "/Users/jnoel/Desktop/arff/datak10_train.arff";
		//String testFile = "/Users/jnoel/Desktop/arff/datak10_test.arff";
		//String trainFile = "/Users/jnoel/Desktop/arff/datak100_train.arff";
		//String testFile = "/Users/jnoel/Desktop/arff/datak100_test.arff";
		String trainFile = "/Users/jnoel/Desktop/arff/datak1000_train.arff";
		String testFile = "/Users/jnoel/Desktop/arff/datak1000_test.arff";
		//String type = Constants.FEATURE;
		String type = Constants.SOCIAL;
		
		new LinkRecommenderArff().run(trainFile, testFile, type);
	}
}
