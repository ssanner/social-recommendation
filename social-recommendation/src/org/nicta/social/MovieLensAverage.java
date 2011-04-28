package org.nicta.social;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class MovieLensAverage extends MovieLens
{
	public static void main(String[] args)
		throws Exception
	{
		new MovieLensAverage().run(10);
	}
	
	
	public double[] predict(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, HashSet<Integer>> userMovies, HashMap<Integer[], Double> testData)
		throws Exception
	{		
		double total = 0;
		int totalCount = 0;
		
		HashMap<Integer, Double> itemAverages = new HashMap<Integer, Double>();
		
		for (int itemId : movieUserRatings.keySet()) {
			HashMap<Integer, Double> rates = movieUserRatings.get(itemId);
			
			double totalRate = 0;
			
			for (int userId : rates.keySet()) {
				totalRate += rates.get(userId);
			}
			
			total += totalRate;
			totalCount += rates.size();
			
			double averageRating = totalRate / rates.size();
			
			itemAverages.put(itemId, averageRating);
		}
		double totalAverage = total / totalCount;
		
		
		HashMap<Integer, Double> userAverages = new HashMap<Integer, Double>();
		
		for (int userId : userMovies.keySet()) {
			HashSet<Integer> movies = userMovies.get(userId);
			
			double totalRate = 0;
	
			for (int movieId : movies) {
				totalRate += movieUserRatings.get(movieId).get(userId);
			}
			
			double averageRating = totalRate / movies.size();
			
			userAverages.put(userId, averageRating);
		}
		
		

		double totalSE = 0;
		double userSE = 0;
		double itemSE = 0;
		double userItemSE = 0;
		
		for (Integer[] test : testData.keySet()) {
			int itemId = test[1];
			int userId = test[0];
			double rate = testData.get(test);
				
			double itemAverage = itemAverages.containsKey(itemId) ? itemAverages.get(itemId) : totalAverage;
			double userAverage = userAverages.containsKey(userId) ? userAverages.get(userId) : totalAverage;
			double userItemAverage = userAverage + itemAverage - totalAverage;
			
			//System.out.println("Total Average: " + totalAverage);
			//System.out.println("Item Average: " + itemAverage);
			//System.out.println("User Average: " + userAverage);
			//System.out.println("User-Item Average: " + userItemAverage);
			//System.out.println("");
			
			totalSE += Math.pow(rate - totalAverage, 2);
			userSE += Math.pow(rate - userAverage, 2);
			itemSE += Math.pow(rate - itemAverage, 2);
			userItemSE += Math.pow(rate - userItemAverage, 2);
		}
		
		double totalMSE = totalSE / (double)testData.size();
		double userMSE = userSE / (double)testData.size();
		double itemMSE = itemSE / (double)testData.size();
		double userItemMSE = userItemSE / (double)testData.size();
		
		return new double[]{Math.sqrt(totalMSE), Math.sqrt(itemMSE), Math.sqrt(userMSE), Math.sqrt(userItemMSE)};
	}
	
	public void run(int k)
		throws Exception
	{
		long start = System.currentTimeMillis();
		
		Object[] data = getMovieUserRatingsAndUserMoviesData();
		HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = (HashMap<Integer, HashMap<Integer, Double>>)data[0];
		HashMap<Integer, HashSet<Integer>> userMovies = (HashMap<Integer, HashSet<Integer>>)data[1];
		
		System.out.println("Getting Test data");
		
		//HashMap<Integer[], Double>[] tests = new HashMap[k];
		HashSet<Integer[]> added = new HashSet<Integer[]>();
		
		System.out.println("Run cross-fold");
		
		double totalAverageSum = 0;
		double itemAverageSum = 0;
		double userAverageSum = 0;
		double userItemAverageSum = 0;
		
		for (int x = 0; x < k; x++) {
			HashMap<Integer[], Double> testData = getTestData(movieUserRatings, userMovies, added);
			
			double[] rmse = predict(movieUserRatings, userMovies, testData);
			//System.out.println("RMSE of run " + (x+1) + ": " + rmse);
			totalAverageSum += rmse[0];
			itemAverageSum += rmse[1];
			userAverageSum += rmse[2];
			userItemAverageSum += rmse[3];
			
			for (Integer[] key : testData.keySet()) {
				int userId = key[0];
				int movieId = key[1];
				double rating = testData.get(key);
				
				movieUserRatings.get(movieId).put(userId, rating);
				userMovies.get(userId).add(movieId);
			}
		}
		
		System.out.println("Total Average RMSE: " + (totalAverageSum / k));
		System.out.println("Item Average RMSE: " + (itemAverageSum / k));
		System.out.println("User Average RMSE: " + (userAverageSum / k));
		System.out.println("User-Item Average RMSE: "  + (userItemAverageSum / k));
		
		System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000));
	}
}
