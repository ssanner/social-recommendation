package org.nicta.lr.recommender;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
	
	public Map<Long, Double[]> getPrecisionRecall(Map<Long, Set<Long>> testData, int boundary)
	{
		HashMap<Long, Double[]> precisionRecalls = new HashMap<Long, Double[]>();
		
		HashSet<Long> combinedTest = new HashSet<Long>();
		for (long userId : testData.keySet()) {
			combinedTest.addAll(testData.get(userId));
		}
		
		
		for (long userId : testData.keySet()) {
			Set<Long> testLinks = testData.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			for (long testId : combinedTest) {
				Double[] feature = linkFeatures.get(testId);
				double prediction = feature[0] + feature[1];
				
				scores.add(prediction);
				ids.add(testId);
			}
			
			Object[] sorted = sort(scores, ids);
			List<Long> idLength = (List<Long>)sorted[1];
			
			int limit = boundary;
			if (idLength.size() < limit) limit = idLength.size();
		
			
			Long[] top = new Long[limit];
			for (int x = 0; x < top.length; x++) {
				top[x] = idLength.get(x);
			}
			
			
			double precision = getUserPrecision(top, userId);
			double recall = getUserRecall(top, userId, testData.get(userId));
			
			precisionRecalls.put(userId, new Double[]{precision, recall});
		}
		
		return precisionRecalls;
	}
	
	public Map<Long, Map<Long, Double>> getPredictions(Map<Long, Set<Long>> testData)
	{
		Map<Long, Map<Long, Double>> predictions = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId : testData.keySet()) {
			HashMap<Long, Double> userPredictions = new HashMap<Long, Double>();
			predictions.put(userId, userPredictions);
			
			Set<Long> testLinks = testData.get(userId);
			
			for (long testId : testLinks) {
				Double[] feature = linkFeatures.get(testId);
				double prediction = feature[0] + feature[1];
				
				userPredictions.put(testId, prediction);
			}
		}
		
		return predictions;
	}
	
	public void saveModel()
		throws SQLException
	{
		//do nothing
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
		
		Map<Long, Map<Long, Double>> recommendations = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId : linksToRecommend.keySet()) {
			Set<Long> userLinks = linksToRecommend.get(userId);
			recommendations.put(userId, recommendForUser(userId, userLinks, userMax.get(userId)));
		}
		
		return recommendations;
	}
	
	public Map<Long, Double> recommendForUser(Long userId, Set<Long> possibleLinks, int numberOfLinks)
	{
		Map<Long, Double> recommendations = new HashMap<Long, Double>();
		
		for (long linkId : possibleLinks) {
			Double[] feature = linkFeatures.get(linkId);
			double score = feature[0] + feature[1];
			
			//If the recommended links are more than the max number, recommend only the highest scoring links.
			if (recommendations.size() < numberOfLinks) {
				recommendations.put(linkId, score);
			}
			else {
				//Get the lowest scoring recommended link and replace it with the current link
				//if this one has a better score.
				long lowestKey = 0;
				double lowestValue = Double.MAX_VALUE;
					
				for (long id : recommendations.keySet()) {
					if (recommendations.get(id) < lowestValue) {
						lowestKey = id;
						lowestValue = recommendations.get(id);
					}
				}
					
				if (score > lowestValue) {
					recommendations.remove(lowestKey);
					recommendations.put(linkId, score);
				}
			}
		}
		
		return recommendations;
	}
}
