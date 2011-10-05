package org.nicta.lr.component;

import java.util.Map;
import java.util.Set;

public class Objective
{
	public double getValue(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes)
	{
		double error = 0;
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> links = predictions.get(userId);
			
			for (long linkId : links.keySet()) {
				int liked = 0;
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) liked = 1;

				double prediction = links.get(linkId);
				
				error += Math.pow(liked - prediction, 2);
			}
		}
		
		return error / 2;
	}
	
	public double getDerivativeOverUserAttribute(Map<Long, Double[]> linkTraits, Map<Long, Double[]> userFeatures,
													Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes,
													int x, int y)
	{
		double derivative = 0;
		
		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
			
			for (long linkId : links) {
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				derivative += (r - p) * dst * -1;
			}
		}
		
		return derivative;
	}
	
	public double getErrorDerivativeOverUserId(Map<Long, Double[]> linkTraits, Map<Long, Set<Long>> linkLikes,
												Map<Long, Map<Long, Double>> predictions, int k, long userId)
	{
		double derivative = 0;
		
		Set<Long> links = predictions.get(userId).keySet();
		for (long linkId : links) {
			Set<Long> likes = linkLikes.get(linkId);

			double dst = linkTraits.get(linkId)[k];
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			derivative += (r - p) * dst * -1;
		}

		return derivative;
	}

	public double getErrorDerivativeOverLinkAttribute(Map<Long, Double[]> userTraits, Map<Long, Double[]> linkFeatures, Map<Long, Set<Long>> linkLikes,
														Map<Long, Map<Long, Double>> predictions, int x, int y)
	{	
		double derivative = 0;
		
		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();

			for (long linkId : links) {			
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) r = 1;

				derivative += (r - p) * dst * -1;
			}
		}

		return derivative;
	}

	public double getErrorDerivativeOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Set<Long>> linkLikes,
												Map<Long, Map<Long, Double>> predictions, int x, long linkId)
	{
		double derivative = 0;
		Set<Long> likes = linkLikes.get(linkId);

		for (Long userId : predictions.keySet()) {
			if (! predictions.get(userId).containsKey(linkId)) continue;

			double dst = userTraits.get(userId)[x];		
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			derivative += (r - p) * dst * -1;
		}

		return derivative;
	}
	
	public double getDerivativeOverWeights(Map<Long, Set<Long>> linkLikes, Map<Long, Map<Long, Map<Integer, Double>>> featureMaps, Map<Long, Map<Long, Double>> predictions, int w)
	{
		double derivative = 0;
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> links = predictions.get(userId);
			
			Map<Long, Map<Integer, Double>> userFeatureMaps = featureMaps.get(userId);
			
			for (long linkId : links.keySet()) {
				Map<Integer, Double> featureMap = userFeatureMaps.get(linkId);
				if (!featureMap.containsKey(w)) continue;
				
				double val = links.get(linkId);
				
				double trueVal = 0;
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					trueVal = 1;
				}
				
				double d = (trueVal - val) * featureMap.get(w) * -1;
				
				derivative += d;
			}
		}
		
		return derivative;
	}
}
