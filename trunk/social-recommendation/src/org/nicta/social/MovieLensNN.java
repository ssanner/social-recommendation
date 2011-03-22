package org.nicta.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class MovieLensNN extends MovieLens
{
	private final int ALPHA = 2;
	private final int K = 50;
	
	public static void main(String[] args)
		throws Exception
	{
		new MovieLensNN().run(1);
	}
	
	//Reference: http://public.research.att.com/~volinsky/netflix/BellKorICDM07.pdf
	public void run(int k)
		throws Exception
	{		
		long start = System.currentTimeMillis();
		
		Object[] data = getMovieUserRatingsAndUserMoviesData();
		HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = (HashMap<Integer, HashMap<Integer, Double>>)data[0];
		HashMap<Integer, HashSet<Integer>> userMovies = (HashMap<Integer, HashSet<Integer>>)data[1];
		
		double rmseSum = 0;
		
		HashSet<Integer[]> added = new HashSet<Integer[]>();
		
		for (int x = 0; x < k; x++) {
			HashMap<Integer[], Double> testData = getTestData(movieUserRatings, userMovies, added);
			//HashMap<Integer[], Double> movieSimilarities = getSimilarities(movieUserRatings, userMovies);
			HashMap<Integer[], Double> movieSimilarities = new HashMap<Integer[], Double>();
			
			System.out.println("Predict " + (x+1));
			//normalize(movieUserRatings, userMovies);
			double rmse = predict(movieUserRatings, userMovies, testData, movieSimilarities);
			rmseSum += rmse;
			
			for (Integer[] key : testData.keySet()) {
				int userId = key[0];
				int movieId = key[1];
				double rating = testData.get(key);
				
				movieUserRatings.get(movieId).put(userId, rating);
				userMovies.get(userId).add(movieId);
			}
			
			System.out.println("RMSE of Run " + (x+1) + ": " + rmse);
		}
		
		System.out.println("Average RMSE: " + rmseSum / k);
		System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000));
	}
	
	//Remove global effects **NOT WORKING***
	public void normalize(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, HashSet<Integer>> userMovies)
	{
		//user effects
		for (int userId : userMovies.keySet()) {
			HashSet<Integer> movies = userMovies.get(userId);
			int nu = movies.size();
			
			double theta = 0;
			
			for (int movieId : movies) {
				double rating = movieUserRatings.get(movieId).get(userId);
				
				theta += rating;
			}
			theta *= (double)nu / (nu + ALPHA);
			
			for (int movieId : movies) {
				movieUserRatings.get(movieId).put(userId, theta);
			}
		}
	}
	
	public double predict(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, HashSet<Integer>> userMovies, HashMap<Integer[], Double> testData, HashMap<Integer[], Double> similarities)
	{
		/*
		 * Get averages first. Baseline ratings
		 */
		
		double total = 0;
		int totalCount = 0;
		
		HashMap<Integer, Double> ratingAverages = new HashMap<Integer, Double>();
		
		int noTrainCount = 0;
		
		for (int movieId : movieUserRatings.keySet()) {
			HashMap<Integer, Double> rates = movieUserRatings.get(movieId);
			
			if (rates.size() == 0) {
				noTrainCount++;
				continue;
			}

			double totalRate = 0;
			for (double rate : rates.values()) {
				totalRate += rate;
			}
			
			total += totalRate;
			totalCount += rates.size();
			
			double averageRating = totalRate / rates.size();
			
			ratingAverages.put(movieId, averageRating);
		}
		
		double totalAverage = total / totalCount;
		
		double se = 0;
		
		int run = 0;
		int nanCount = 0;
		int infiniteCount = 0;
		
		
		for (Integer[] test : testData.keySet()) {
			run++;
			/*if (run % 1000 == 0)*/ System.out.println("Test: " + run);
			
			int testUserId = test[0];
			int testMovieId = test[1];
			double testRating = testData.get(test);
			
			//Get all movies rated by User
			HashSet<Integer> ratedMovies = userMovies.get(testUserId);
			
			//Get the movie vector
			HashMap<Integer, Double> testMovieVector = movieUserRatings.get(testMovieId);
			
			//Holds the k=10 nearest neighbors
			HashMap<Integer, Double> kClosestSimilarities = new HashMap<Integer, Double>();
			
			double smallestKSimilarity = 1;			
			int smallestId = 0;
			
			//Used for getting the cosine similarity
			HashSet<Integer> userIds = new HashSet<Integer>();
			userIds.addAll(testMovieVector.keySet());
			
			//Get all users that rated those movies and their ratings
			for (int movieId : ratedMovies) {
				if (movieId == testMovieId || !movieUserRatings.containsKey(movieId)) continue;
				
				HashMap<Integer, Double> movieVector = movieUserRatings.get(movieId);
				userIds.addAll(movieVector.keySet());
				
				//Get the similarity to the movie vector
				double similarity = getCosineSimilarity(testMovieVector, movieVector, userIds);
				
				/*
				Integer[] key;
				if (movieId < testMovieId) {
					key = new Integer[]{movieId, testMovieId};
					
				}
				else {
					key = new Integer[]{testMovieId, movieId};
				}
					
				double similarity = similarities.containsKey(key) ? similarities.get(key) : 0;
				*/
				// k = 10
				if (kClosestSimilarities.size() < K) {
					kClosestSimilarities.put(movieId, similarity);
					
					if (similarity < smallestKSimilarity) {
						smallestKSimilarity = similarity;
						smallestId = movieId;
					}
				}
				else if (similarity > smallestKSimilarity) {
					kClosestSimilarities.remove(smallestId);
					kClosestSimilarities.put(movieId, similarity);
					
					smallestKSimilarity = 1;
					
					//reset the smallest similarity again
					for (int id : kClosestSimilarities.keySet()) {
						double s = kClosestSimilarities.get(id);
						
						if (s < smallestKSimilarity) {
							smallestKSimilarity = similarity;
							smallestId = id;
						}
					}
				}
			}
			
			//At this point we have the 10 or less most similar movies to the movie vector.
			//Now just need to follow the formula in the paper to get the predicted rating
			double numerator = 0;
			double denomenator = 0;
			
			for (int neighborId : kClosestSimilarities.keySet()) {				
				double neighborSimilarity = kClosestSimilarities.get(neighborId);
				double neighborRating = movieUserRatings.get(neighborId).get(testUserId);
				
				numerator += neighborSimilarity * neighborRating;
				denomenator += neighborSimilarity;
			}
			
			/*
			 * So far only cosine similarity is working properly.
			 * When I try to use pearson, it sometimes returns the similarity as a NaN. 
			 * When I set all NaN similarities to 0, I end up with infinite nnRatings.
			 * Am I still calculating the correlations incorrectly?.
			 */
			
			double nnRating = numerator / denomenator;
			
			if (Double.isNaN(nnRating)) {
				nnRating = ratingAverages.containsKey(testMovieId) ? ratingAverages.get(testMovieId) : totalAverage;
				nanCount++;
			}
			else if (Double.isInfinite(nnRating)) {
				nnRating = ratingAverages.containsKey(testMovieId) ? ratingAverages.get(testMovieId) : totalAverage;
				infiniteCount++;
			}
			
			se += Math.pow(testRating - nnRating, 2);
		}
		
		System.out.println("NaN: " + nanCount);
		System.out.println("Infinite: " + infiniteCount);
		System.out.println("No Training: " + noTrainCount);
		
		double mse = se / ((double)testData.size());
		return Math.sqrt(mse);
	}
	
	public double getCosineSimilarity(HashMap<Integer, Double> vector1, HashMap<Integer, Double> vector2, Set<Integer> keys)
	{
		double similarity = 0;
		double mag1 = 0;
		double mag2 = 0;
		
		for (int key : keys) {
			//Currently disregards vectors that have null columns in the similarity calculation.
			//Basically just treats them as 0.
			if (vector1.containsKey(key) && vector2.containsKey(key)) {
				similarity += vector1.get(key) * vector2.get(key);
				mag1 += Math.pow(vector1.get(key), 2);
				mag2 += Math.pow(vector2.get(key), 2);
			}
		}
		
		return similarity / ((Math.sqrt(mag1) * Math.sqrt(mag2)));
	}
	
	//This is used to estiomate the Pearson correlation according to http://en.wikipedia.org/wiki/Correlation_and_dependence
	public double getSampleCorrelation(HashMap<Integer, Double> vector1, HashMap<Integer, Double> vector2, Set<Integer> keys)
	{
		double vector1Mean = 0;
		double vector2Mean = 0;
		
		int sharedCount = 0;
		
		for (int key : keys) {
			//Currently disregards vectors that have null columns in the similarity calculation.
			//Basically just treats them as 0.
			if (vector1.containsKey(key) && vector2.containsKey(key)) {
				vector1Mean += vector1.get(key);
				vector2Mean += vector2.get(key);
				sharedCount++;
			}
		}
		
		vector1Mean /= sharedCount;
		vector2Mean /= sharedCount;
		
		double numerator = 0;
		double denominator1 = 0;
		double denominator2 = 0;
		
		for (int key : keys) {
			//Currently disregards vectors that have null columns in the similarity calculation.
			//Basically just treats them as 0.
			if (vector1.containsKey(key) && vector2.containsKey(key)) {
				numerator += ((vector1.get(key) - vector1Mean) * (vector2.get(key) - vector2Mean));
				denominator1 += Math.pow(vector1.get(key) - vector1Mean, 2);
				denominator2 += Math.pow(vector2.get(key) - vector2Mean, 2);
			}
		}
		
		double correlation = numerator / (Math.sqrt(denominator1) * Math.sqrt(denominator2));
		
		return correlation;
	}
	
	// Formula retrieved from http://algorithmsanalyzed.blogspot.com/2008/07/bellkor-algorithm-pearson-correlation.html
	public double getPearsonCorrelation(HashMap<Integer, Double> vector1, HashMap<Integer, Double> vector2, Set<Integer> keys)
	{
		double sum1 = 0;
		double sum2 = 0;
		double sumpr = 0;
		double sumsq1 = 0;
		double sumsq2 = 0;
		int n = 0;
		
		for (int key : keys) {
			if (vector1.containsKey(key) && vector2.containsKey(key)) {
				double x = vector1.get(key);
				double y = vector2.get(key);
				
				sum1 += x;
				sum2 += y;
				sumpr += x * y;
				sumsq1 += Math.pow(x, 2);
				sumsq2 += Math.pow(y, 2);
				
				n++;
			}
		}
		
		double numerator = sumpr - ((sum1 * sum2) / n);
		double denomenator = (sumsq1 - Math.pow(sum1, 2)/n) * (sumsq2 - Math.pow(sum2, 2) / n);
		
		return numerator / Math.sqrt(denomenator);
	}
	
	public HashMap<Integer[], Double> getSimilarities(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, HashSet<Integer>> userMovies)
	{
		HashMap<Integer[], Double> similarities = new HashMap<Integer[], Double>();
		Set<Integer> userIds = userMovies.keySet();
		Set<Integer> movieIds = movieUserRatings.keySet();
		
		int count1 = 0;
		for (int movieId1 : movieIds) {
			count1++;
			
			//Get the movie vector
			HashMap<Integer, Double> movieVector1 = movieUserRatings.get(movieId1);
			int count2 = 0;
			for (int movieId2 : movieIds) {
				count2++;
				//System.out.println(count1 + " : " + count2);
				
				if (movieId1 == movieId2) continue;
				
				HashMap<Integer, Double> movieVector2 = movieUserRatings.get(movieId2);
				
				double similarity = getCosineSimilarity(movieVector1, movieVector2, userIds);
				//double similarity = getSampleCorrelation(testMovieVector, movieVector, userIds);
				//double similarity = getPearsonCorrelation(testMovieVector, movieVector, userIds);
				
				System.out.println(similarity);
				
				if (movieId1 < movieId2) {
					similarities.put(new Integer[]{movieId1, movieId2}, similarity);
				}
				else {
					similarities.put(new Integer[]{movieId2, movieId1}, similarity);
				}
			}	
		}
		
		return similarities;
	}
}
