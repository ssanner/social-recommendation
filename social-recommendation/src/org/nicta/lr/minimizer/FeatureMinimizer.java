package org.nicta.lr.minimizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.RecommenderUtil;

public class FeatureMinimizer extends Minimizer 
{
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<String, Double[]> wordColumns,
			HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections)
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

		for (int x = 0; x < Constants.K; x++) {
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

		for (int x = 0; x < Constants.K; x++) {
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
		for (String id : wordColumns.keySet()) {
			Double[] column = linkIdColumns.get(id);
			
			for (double val : column) {
				linkNorm += Math.pow(val, 2);
			}
		}
		
		userNorm *= Constants.LAMBDA;
		linkNorm *= Constants.LAMBDA;
		
		error += userNorm + linkNorm;

		return error / 2;
	}
	
	public double getErrorDerivativeOverUserAttribute(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> userIdColumns,
			HashMap<Long, Double[]> linkTraits, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections,
			int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * Constants.LAMBDA;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
			
			for (long linkId : links) {
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.get(linkId) != null && linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}

	public double getErrorDerivativeOverUserId(Double[][] userFeatureMatrix, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkTraits,
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, HashMap<Long, HashMap<Long, Double>> connections,
			int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * Constants.LAMBDA;

		Set<Long> links = predictions.get(userId).keySet();
		
		for (long linkId : links) {
			HashSet<Long> likes = linkLikes.get(linkId);

			double dst = linkTraits.get(linkId)[k] /* userFeatures.get(userId)[k]*/;
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst;
		}

		return errorDerivative * -1;
	}

	public double getErrorDerivativeOverLinkAttribute(Double[][] linkFeatureMatrix,
			HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkFeatures,
			HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashMap<Long, Double>> predictions, int x, int y)
	{	
		double errorDerivative = linkFeatureMatrix[x][y] * Constants.LAMBDA;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
				
			for (long linkId : links) {			
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}

	
	public double getErrorDerivativeOverLinkId(HashMap<Long, Double[]> linkIdColumns,
			HashMap<Long, Double[]> userTraits,
			HashMap<Long, HashSet<Long>> linkLikes, 
			HashMap<Long, HashMap<Long, Double>> predictions, 
			int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * Constants.LAMBDA;
		HashSet<Long> likes = linkLikes.get(linkId);
		
		for (Long userId : predictions.keySet()) {
			if (! predictions.get(userId).containsKey(linkId)) continue;
			
			//System.out.println("Contains: " + userTraits.containsKey(1669989910l));
			//System.out.println("User: " + userTraits.containsKey(userId) + " : " + userId);
			//System.out.println("Link: " + idColumn);
			
			double dst = userTraits.get(userId)[x] * idColumn[x];		
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes != null && likes.contains(userId)) r = 1;

			errorDerivative += (r - p) * dst;
		}
		
		return errorDerivative * -1;
	}
	
	public double getErrorDerivativeOverWord(HashMap<String, Double[]> wordColumns, HashMap<Long, Set<String>> linkWords, 
			HashMap<Long, Double[]> userTraits, HashMap<Long, HashSet<Long>> linkLikes,
			HashMap<Long, HashMap<Long, Double>> predictions,  
			int x, String word)
	{
		Double[] column = wordColumns.get(word);
		double errorDerivative = column[x] * Constants.LAMBDA;
		
		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
			
			for (long linkId : links) {
				double dst = userTraits.get(userId)[x] * column[x];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.get(linkId).contains(userId)) r = 1;

				errorDerivative += (r - p) * dst;
			}
		}

		return errorDerivative * -1;
	}
}
