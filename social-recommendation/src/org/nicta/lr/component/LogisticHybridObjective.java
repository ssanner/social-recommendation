package org.nicta.lr.component;

import java.util.Map;
import java.util.Set;

public class LogisticHybridObjective extends Objective
{
	public double getDerivativeOverUserAttribute(Map<Long, Double[]> linkTraits, Map<Long, Double[]> userFeatures,
			Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes,
			int x, int y)
	{
		double derivative = 0;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();

			for (long linkId : links) {
				double p = predictions.get(userId).get(linkId);
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y];	
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				derivative += (r - p) * p * (1 - p) * dst * -1;
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
			if (!linkTraits.containsKey(linkId)) continue;

			Set<Long> likes = linkLikes.get(linkId);

			double p = predictions.get(userId).get(linkId);
			double dst = linkTraits.get(linkId)[k];
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			derivative += (r - p) * p * (1 - p) * dst * -1;
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
				double p = predictions.get(userId).get(linkId);
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y];		
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				derivative += (r - p) * p * (1 - p) * dst * -1;
			}
		}

		return derivative;
	}

	public double getErrorDerivativeOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Set<Long>> linkLikes,
			Map<Long, Map<Long, Double>> predictions, int x, long linkId)
	{
		double derivative = 0;
		Set<Long> likes = linkLikes.get(linkId);

		for (long userId : predictions.keySet()) {
			if (! predictions.get(userId).containsKey(linkId)) continue;

			double p = predictions.get(userId).get(linkId);
			double dst = userTraits.get(userId)[x];		
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			derivative += (r - p) * p * (1 - p) * dst * -1;
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
				
				double d = (trueVal - val) * val * (1 - val) * featureMap.get(w) * -1;
				
				derivative += d;
			}
		}
		
		return derivative;
	}
}
