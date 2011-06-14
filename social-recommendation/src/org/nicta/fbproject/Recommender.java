package org.nicta.fbproject;

import java.util.HashMap;
import java.util.HashSet;

public abstract class Recommender 
{
	public double calcRMSE(HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes)
	{
		double error = 0;
		
		//Get the square error
		for (long i : userTraits.keySet()) {
			for (long j : linkTraits.keySet()) {
				int liked = 0;
				
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				double predictedLike = FBMethods.dot(userTraits.get(i), linkTraits.get(j));
				if (liked == 1) System.out.println ("Like: " + liked + " Predicted: " + predictedLike);
				if (predictedLike < 0) predictedLike = 0;
				if (predictedLike > 1) predictedLike = 1;
				
				error += Math.pow(liked - predictedLike, 2);
			}
		}

		return Math.sqrt(error / (userTraits.size() * linkTraits.size()));
	}
	
	public HashMap<Long, Double[]> getTraitVectors(Double[][] matrix, 
													HashMap<Long, Double[]> idColumns,
													HashMap<Long, Double[]> features)
	{
		HashMap<Long, Double[]> traitVectors = new HashMap<Long, Double[]>();
		
		for (long id : features.keySet()) {
			Double[] feature = features.get(id);
			Double[] vector = new Double[FBConstants.K];
			Double[] idColumn = idColumns.get(id);
		
			for (int x = 0; x < FBConstants.K; x++) {
				vector[x] = 0.0;
		
				for (int y = 0; y < feature.length; y++) {
					vector[x] += matrix[x][y] * feature[y];
				}
		
				vector[x] += idColumn[x];
			}
		
			traitVectors.put(id, vector);
		}
		
		return traitVectors;
	}
}
