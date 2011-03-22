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
	
	public HashMap<Integer[], Double> getTestData(HashMap<Integer[], Double> ratings, HashSet<Integer[]> added)
	{
		HashMap<Integer[], Double> testData = new HashMap<Integer[], Double>();
		
		List<Integer[]> list = new ArrayList<Integer[]>();
		list.addAll(ratings.keySet());
		
		while (testData.size()  < ratings.size() * .1 - 1) {
			int randomIndex = (int)(Math.random() * list.size());
			if (!added.contains(list.get(randomIndex))) {
				testData.put(list.get(randomIndex), ratings.get(list.get(randomIndex)));
				added.add(list.get(randomIndex));
			}
		}
		
		return testData;
	}
	
	public double predict(HashMap<Integer, HashSet<Double>> trainingData, HashMap<Integer[], Double> testData)
		throws Exception
	{		
		double total = 0;
		int totalCount = 0;
		
		HashMap<Integer, Double> ratingAverages = new HashMap<Integer, Double>();
		
		for (int itemId : trainingData.keySet()) {
			HashSet<Double> rates = trainingData.get(itemId);
			double totalRate = 0;
			for (double rate : rates) {
				totalRate += rate;
			}
			
			total += totalRate;
			totalCount += rates.size();
			
			double averageRating = totalRate / rates.size();
			
			ratingAverages.put(itemId, averageRating);
		}
		
		double totalAverage = total / totalCount;

		int se = 0;
		
		for (Integer[] test : testData.keySet()) {
			int itemId = test[1];
			double average = ratingAverages.containsKey(itemId) ? ratingAverages.get(itemId) : totalAverage;
			double rate = testData.get(test);
			
			se += Math.pow(rate - average, 2);
		}
		
		double mse = se / (double)testData.size();
		return Math.sqrt(mse);
	}
	
	public void run(int k)
		throws Exception
	{
		long start = System.currentTimeMillis();
		
		System.out.println("Getting ratings");
		HashMap<Integer[], Double> ratings = getRatings();
		System.out.println("Ratings: " + ratings.size());
		
		System.out.println("Getting Test data");
		
		//HashMap<Integer[], Double>[] tests = new HashMap[k];
		HashSet<Integer[]> added = new HashSet<Integer[]>();
		
		System.out.println("Run cross-fold");
		
		double rmseSum = 0;
		for (int x = 0; x < k; x++) {
			HashMap<Integer[], Double> testData = getTestData(ratings, added);
			HashMap<Integer, HashSet<Double>> trainingData = getMovieRatingsMap(ratings, testData);
			double rmse = predict(trainingData, testData);
			System.out.println("RMSE of run " + (x+1) + ": " + rmse);
			rmseSum += rmse;
		}
		System.out.println("\nAverage RMSE: " + (rmseSum / k));
		
		System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000));
	}
}
