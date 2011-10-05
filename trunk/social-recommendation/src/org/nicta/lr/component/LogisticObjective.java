package org.nicta.lr.component;

import java.util.Map;
import java.util.Set;

public class LogisticObjective extends Objective
{
	public double getValue(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes)
	{
		double error = 0;
		
		for (long i : predictions.keySet()) {
			Set<Long> links = predictions.get(i).keySet();
			
			for (long j : links) {
				int liked = 0;
				
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				double predictedLike = logistic(predictions.get(i).get(j));
				
				error += Math.pow(liked - predictedLike, 2);
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
				double dot = predictions.get(userId).get(linkId);
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y] * logisticDerivative(dot);	
				double p = logistic(dot);
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
			if (!linkTraits.containsKey(linkId)) continue;

			Set<Long> likes = linkLikes.get(linkId);

			double dot = predictions.get(userId).get(linkId);
			double dst = linkTraits.get(linkId)[k] * logisticDerivative(dot);
			double p = logistic(dot);
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
				double dot = predictions.get(userId).get(linkId);
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y] * logisticDerivative(dot);		
				double p = logistic(dot);
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

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

		for (long userId : predictions.keySet()) {
			if (! predictions.get(userId).containsKey(linkId)) continue;

			double dot = predictions.get(userId).get(linkId);
			double dst = userTraits.get(userId)[x] * logisticDerivative(dot);		
			double p = logistic(dot);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			derivative += (r - p) * dst * -1;
		}

		return derivative;
	}
	
	public double logistic(double d)
	{
		return (double)1 / (1 + Math.exp(-d));
	}
	
	public double logisticDerivative(double d)
	{
		return logistic(d) * (1 - logistic(d));
	}
}
