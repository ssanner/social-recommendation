package org.nicta.lr.recommender;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BaselineGlobalRecommender extends Recommender
{	
	public BaselineGlobalRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures)
	{
		super(linkLikes, userFeatures, linkFeatures, null);
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		//do nothing
	}
	
	public Map<Long, Double> getAveragePrecisions(Map<Long, Set<Long>> testData)
	{
		HashMap<Long, Double> averagePrecisions = new HashMap<Long, Double>();
		
		for (long userId : testData.keySet()) {
			Set<Long> testLinks = testData.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			for (long testId : testLinks) {
				Double[] feature = linkFeatures.get(testId);
				double prediction = feature[0] + feature[1];
				
				scores.add(prediction);
				ids.add(testId);
			}
			
			Object[] sorted = sort(scores, ids);
			double ap = getUserAP(sorted, userId);
			averagePrecisions.put(userId, ap);
		}
		
		return averagePrecisions;
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
