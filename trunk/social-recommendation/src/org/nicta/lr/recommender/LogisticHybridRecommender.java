package org.nicta.lr.recommender;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.component.LogisticHybridObjective;

public class LogisticHybridRecommender extends HybridRecommender
{
	public LogisticHybridRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends, String type)
	{
		super(linkLikes, userFeatures, linkFeatures, friends, type);
		
		objective = new LogisticHybridObjective();
	}
	
	public Map<Long, Map<Long, Double>> combinePredictions(Map<Long, Map<Long, Double>> weightPredictions, Map<Long, Map<Long, Double>> mfPredictions)
	{
		Map<Long, Map<Long, Double>> predictions = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId : weightPredictions.keySet()) {
			Map<Long, Double> weightPreds = weightPredictions.get(userId);
			Map<Long, Double> mfPreds = mfPredictions.get(userId);
			
			Map<Long, Double> combined = new HashMap<Long, Double>();
			predictions.put(userId, combined);
			
			for (long linkId : weightPreds.keySet()) {
				double val = logistic(weightPreds.get(linkId)) + logistic(mfPreds.get(linkId));
				combined.put(linkId, val);
			}
		}
		
		return predictions;
	}
	
	public double logistic(double d)
	{
		return (double)1 / (1 + Math.exp(-d));
	}
}
