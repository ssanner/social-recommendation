package org.nicta.lr.recommender;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.UserUtil;

public class BaselineRecommender extends Recommender
{
	String type;
	
	public BaselineRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends, String type)
	{	
		super(linkLikes, userFeatures, linkFeatures, friends);
		this.type = type;
	}
	
	public Map<Long, Double[]> getPrecisionRecall(Map<Long, Set<Long>> testData, int boundary)
	{
		return null;
	}
	
	public Map<Long, Double> getAveragePrecisions(Map<Long, Set<Long>> testData)
	{
		HashMap<Long, Double> averagePrecisions = new HashMap<Long, Double>();
		
		for (long userId : testData.keySet()) {
			Set<Long> testLinks = testData.get(userId);
			Map<Long, Double> friends = friendships.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			double total = 0;
			for (long friendId : friends.keySet()) {
				total += friends.get(friendId);
			}
			
			for (long friendId : friends.keySet()) {
				double val = friends.get(friendId);
				val /= total;
				friends.put(friendId, val);
			}
			
			for (long testId : testLinks) {
				double prediction = 0;
				
				for (long friendId : friends.keySet()) {
					if (linkLikes.containsKey(testId) && linkLikes.get(testId).contains(friendId)) {
						prediction += friends.get(friendId);
					}
				}
				
				scores.add(prediction);
				ids.add(testId);
			}
			
			Object[] sorted = sort(scores, ids);
			double ap = getUserAP(sorted, userId);
			averagePrecisions.put(userId, ap);
		}
		
		return averagePrecisions;
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		try {
			if (Constants.FIW == type) 
				friendships = UserUtil.getFriendInteractionMeasure(trainSamples.keySet());
		}
		catch (SQLException ex) {
			ex.printStackTrace();
		}
	}
	
	public void saveModel()
		throws SQLException
	{
		//do nothing
	}
	
	public Map<Long, Map<Long, Double>> recommend(Map<Long, Set<Long>> linksToRecommend)
	{
		//Will never be used for online recommendatons
		return null;
	}
}
