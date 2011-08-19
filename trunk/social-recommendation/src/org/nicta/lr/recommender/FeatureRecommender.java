package org.nicta.lr.recommender;

import java.util.Set;
import java.util.Map;

import org.nicta.lr.util.Constants;

public class FeatureRecommender extends MFRecommender
{
	public FeatureRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures)
	{
		super(linkLikes, userFeatures, linkFeatures, null);
		
		lambda = 100;
		type = "feature";
		
		if (Constants.DEPLOYMENT_TYPE == Constants.TEST || Constants.INITIALIZE) {
			initializePriors(userFeatures.keySet(), linkFeatures.keySet());
		}
		else if (Constants.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			try {
				loadData();
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		minimizeByLBFGS(trainSamples);
	}
	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
							Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkIdColumns,
							Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Double>> connections)
	{
		double error = 0;
		
		//Get the square error
		for (long i : predictions.keySet()) {
			Set<Long> links = predictions.get(i).keySet();
			
			for (long j : links) {
				int liked = 0;
				if (linkLikes.get(j).contains(i)) liked = 1;
				
				double predictedLike = predictions.get(i).get(j);
		
				error += Math.pow(liked - predictedLike, 2);
			}
		}
		
		//Get User and Movie norms for regularisation
		double userNorm = 0;
		double linkNorm = 0;

		for (int x = 0; x < K; x++) {
			for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
				userNorm += Math.pow(userFeatureMatrix[x][y], 2);
			}
		}
		for (long id : userIdColumns.keySet()) {
			Double[] column = userIdColumns.get(id);
			
			for (double val : column) {
				userNorm += Math.pow(val, 2);
			}
		}

		for (int x = 0; x < K; x++) {
			for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
				linkNorm += Math.pow(linkFeatureMatrix[x][y], 2);
			}
		}
		for (long id : linkIdColumns.keySet()) {
			Double[] column = linkIdColumns.get(id);
			
			for (double val : column) {
				linkNorm += Math.pow(val, 2);
			}
		}
		
		userNorm *= lambda;
		linkNorm *= lambda;
		
		error += userNorm + linkNorm;

		return error / 2;
	}
	
	public double getErrorDerivativeOverUserAttribute(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
														Map<Long, Map<Long, Double>> connections, int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * lambda;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
			
			for (long linkId : links) {
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst * -1;
			}
		}

		return errorDerivative;
	}

	public double getErrorDerivativeOverUserId(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
												Map<Long, Map<Long, Double>> connections, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * lambda;

		Set<Long> links = predictions.get(userId).keySet();
		
		for (long linkId : links) {
			Set<Long> likes = linkLikes.get(linkId);

			double dst = linkTraits.get(linkId)[k] /* userFeatures.get(userId)[k]*/;
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst * -1;
		}

		return errorDerivative;
	}

	public double getErrorDerivativeOverLinkAttribute(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, int y)
	{	
		double errorDerivative = linkFeatureMatrix[x][y] * lambda;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
				
			for (long linkId : links) {			
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst * -1;
			}
		}

		return errorDerivative;
	}
	
	public double getErrorDerivativeOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * lambda;
		Set<Long> likes = linkLikes.get(linkId);
		
		for (Long userId : predictions.keySet()) {
			if (! predictions.get(userId).containsKey(linkId)) continue;
			
			double dst = userTraits.get(userId)[x] /* idColumn[x]*/;		
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst * -1;
		}
		
		return errorDerivative;
	}
}
