package org.nicta.social;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

public class MovieLensClassification 
{	
	//10M
	String moviesSource = "/Users/jino/Desktop/Honours/ml-data-10M100K/movies.dat";
	String ratingsSource = "/Users/jino/Desktop/Honours/ml-data-10M100K/ratings.dat";
	String tagsSource = "/Users/jino/Desktop/Honours/ml-data-10M100L/tags.dat";
	
	public static void main(String[] args)
		throws Exception
	{
		MovieLensClassification mlc = new MovieLensClassification();
		//mlc.average();
		mlc.nearestNeighbor();
	}
	
	public HashMap<Integer, Integer[]> getMovies()
		throws Exception
	{
		HashMap<Integer, Integer[]> itemMap = new HashMap<Integer, Integer[]>();
		BufferedReader itemInput = new BufferedReader(new FileReader(moviesSource));
		String line = itemInput.readLine();
		
		while (line != null) {
			String[] tokens = line.split("::");
			Integer[] item = new Integer[19];
			item[0] = tokens[5].equals("0") ? 0 : 1;
			item[1] = tokens[6].equals("0") ? 0 : 1;
			item[2] = tokens[7].equals("0") ? 0 : 1;
			item[3] = tokens[8].equals("0") ? 0 : 1;
			item[4] = tokens[9].equals("0") ? 0 : 1;
			item[5] = tokens[10].equals("0") ? 0 : 1;
			item[6] = tokens[11].equals("0") ? 0 : 1;
			item[7] = tokens[12].equals("0") ? 0 : 1;
			item[8] = tokens[13].equals("0") ? 0 : 1;
			item[9] = tokens[14].equals("0") ? 0 : 1;
			item[10] = tokens[15].equals("0") ? 0 : 1;
			item[11] = tokens[16].equals("0") ? 0 : 1;
			item[12] = tokens[17].equals("0") ? 0 : 1;
			item[13] = tokens[18].equals("0") ? 0 : 1;
			item[14] = tokens[19].equals("0") ? 0 : 1;
			item[15] = tokens[20].equals("0") ? 0 : 1;
			item[16] = tokens[21].equals("0") ? 0 : 1;
			item[17] = tokens[22].equals("0") ? 0 : 1;
			item[18] = tokens[23].equals("0") ? 0 : 1;
			
			itemMap.put(Integer.parseInt(tokens[0]), item);
			
			line = itemInput.readLine();
			
		}
		itemInput.close();
		
		return itemMap;
	}
	
	public HashMap<Integer[], Integer> getRatings()
		throws Exception
	{
		HashMap<Integer[], Integer> ratings = new HashMap<Integer[], Integer>();
		BufferedReader ratingInput = new BufferedReader(new FileReader(ratingsSource));
		String line = ratingInput.readLine();
		int count = 0;
		while (line != null) {
			String[] tokens = line.split("::");
			int userId = Integer.parseInt(tokens[0]);
			int itemId = Integer.parseInt(tokens[1]);
			int rating = Math.round(Float.parseFloat(tokens[2]) * 10);
		
			ratings.put(new Integer[]{userId, itemId}, rating);
			
			count++;
			if (count % 1000000 == 0) {
				System.out.println(count + " loaded");
			}
			line = ratingInput.readLine();
			
		}
		ratingInput.close();
		
		return ratings;
	}
	
	public HashMap<Integer, HashSet<Integer>> getMovieRatings(HashMap<Integer[], Integer> ratings, HashMap<Integer[], Integer> testData)
	{
		HashMap<Integer, HashSet<Integer>> trainingData = new HashMap<Integer, HashSet<Integer>>();
		
		for (Integer[] key : ratings.keySet()) {
			if (testData.containsKey(key)) continue;
			
			int rate = ratings.get(key);
			int itemId = key[1];
			
			if (!trainingData.containsKey(itemId)) {
				trainingData.put(itemId, new HashSet<Integer>());
			}
			
			trainingData.get(itemId).add(rate);
		}
		
		return trainingData;
	}
	
	public HashMap<Integer[], Integer> getTestData(HashMap<Integer[], Integer> ratings)
	{
		HashMap<Integer[], Integer> testData = new HashMap<Integer[], Integer>();
		
		List<Integer[]> list = new ArrayList<Integer[]>();
		list.addAll(ratings.keySet());
		
		while (testData.size()  < ratings.size() * .1) {
			int randomIndex = (int)(Math.random() * list.size());
			testData.put(list.get(randomIndex), ratings.get(list.get(randomIndex)));
		}
		
		return testData;
	}
	
	//Not sure yet if I need this
	public HashMap<Integer[], Integer> getTrainingData(HashMap<Integer[], Integer> ratings, HashMap<Integer[], Integer> testData)
	{
		HashMap<Integer[], Integer> trainingData = new HashMap<Integer[], Integer>();
		
		for (Integer[] key : ratings.keySet()) {
			if (testData.containsKey(key)) continue;
	
			trainingData.put(key, ratings.get(key));
		}
		
		return trainingData;
	}
	
	public void average()
		throws Exception
	{	
		long start = System.currentTimeMillis();
		
		System.out.println("Getting ratings");
		HashMap<Integer[], Integer> ratings = getRatings();
		
		System.out.println("Getting Test data");
		HashMap<Integer[], Integer> testData = getTestData(ratings);
		
		System.out.println("Getting training data");
		HashMap<Integer, HashSet<Integer>> trainingData = getMovieRatings(ratings, testData);
	
		
		System.out.println("Getting averages");
		int total = 0;
		int totalCount = 0;
		
		HashMap<Integer, Integer> ratingAverages = new HashMap<Integer, Integer>();
		
		for (int itemId : trainingData.keySet()) {
			HashSet<Integer> rates = trainingData.get(itemId);
			int totalRate = 0;
			for (double rate : rates) {
				totalRate += rate;
			}
			
			total += totalRate;
			totalCount += rates.size();
			
			int averageRating = totalRate / rates.size();
			if (averageRating % 5 < 3) {
				averageRating = averageRating - (averageRating % 10);
			}
			else  {
				averageRating = averageRating - (averageRating % 10) + 5;
			}	
			
			ratingAverages.put(itemId, averageRating);
		}
		
		int totalAverage = total / totalCount;
		
		if (totalAverage % 5 < 3) {
			totalAverage = totalAverage - (totalAverage % 10);
		}
		else {
			totalAverage = totalAverage - (totalAverage % 10) + 5;
		}
		
		int correct = 0;
		int wrong = 0;
		
		for (Integer[] test : testData.keySet()) {
			int itemId = test[1];
			double average = ratingAverages.containsKey(itemId) ? ratingAverages.get(itemId) : totalAverage;
			double rate = testData.get(test);
			
			if (rate == average) {
				correct++;
			}
			else {
				wrong++;
			}
		}
		
		System.out.println("Percentage: " + (((double)correct / (correct + wrong)) * 100) + "%");
		System.out.println("Correct: " + correct);
		System.out.println("Wrong: " + wrong);
		System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000));
	}
	
	public HashMap<Integer, HashMap<Integer, Integer>> getUserMovieRatings(HashMap<Integer[], Integer> ratings, HashMap<Integer[], Integer> testData)
	{
		HashMap<Integer, HashMap<Integer, Integer>> userMovieRatings = new HashMap<Integer, HashMap<Integer, Integer>>();
		
		int count = 0;
		
		for (Integer[] key : ratings.keySet()) {
			count++;
			if (count % 1000000 == 0) System.out.println(count + " processed");
			
			if (testData.containsKey(key)) continue;
			
			int rate = ratings.get(key);
			int itemId = key[1];
			int userId = key[0];
			
			if (!userMovieRatings.containsKey(userId)) {
				userMovieRatings.put(userId, new HashMap<Integer, Integer>());
			}
			
			userMovieRatings.get(userId).put(itemId, rate);
		}
		
		return userMovieRatings;
	}
	
	public HashMap<Integer, HashMap<Integer, Integer>> getMovieUserRatings(HashMap<Integer[], Integer> ratings, HashMap<Integer[], Integer> testData)
	{
		HashMap<Integer, HashMap<Integer, Integer>> movieUserRatings = new HashMap<Integer, HashMap<Integer, Integer>>();
		
		int count = 0;
		
		for (Integer[] key : ratings.keySet()) {
			count++;
			if (count % 1000000 == 0) System.out.println(count + " processed");
			
			if (testData.containsKey(key)) continue;
			
			int rate = ratings.get(key);
			int itemId = key[1];
			int userId = key[0];
			
			if (!movieUserRatings.containsKey(itemId)) {
				movieUserRatings.put(itemId, new HashMap<Integer, Integer>());
			}
			
			movieUserRatings.get(itemId).put(userId, rate);
		}
		
		return movieUserRatings;
	}
	
	public void nearestNeighbor()
		throws Exception
	{	
		long start = System.currentTimeMillis();
		
		System.out.println("Get ratings");
		HashMap<Integer[], Integer> ratings = getRatings();
		
		System.out.println("Get test data");
		HashMap<Integer[], Integer> testData = getTestData(ratings);
		System.out.println("Size: " + testData.size());
		
		System.out.println("Get user movie ratings");
		HashMap<Integer, HashMap<Integer, Integer>> userMovieRatings = getUserMovieRatings(ratings, testData);
		
		System.out.println("Get movie user ratings");
		HashMap<Integer, HashMap<Integer, Integer>> movieUserRatings = getMovieUserRatings(ratings, testData);
		
		int correct = 0;
		int wrong = 0;
		
		System.out.println("Run");
		int count = 0;
		for (Integer[] test : testData.keySet()) {
			System.out.println("Run: " + ++count);
			int testUserId = test[0];
			int testMovieId = test[1];
			int testRating = testData.get(test);
			
			HashMap<Integer, Integer> movieRatings = userMovieRatings.get(testUserId);
			HashMap<Integer, Integer> testMovieUserRatings = movieUserRatings.get(testMovieId);
			
			
			HashMap<Integer, Double> kClosestSimilarities = new HashMap<Integer, Double>();
			
			double smallestKSimilarity = 1;			
			int smallestId = 0;
			
			HashSet<Integer> userIds = new HashSet<Integer>();
			userIds.addAll(testMovieUserRatings.keySet());
			
			for (int movieId : movieRatings.keySet()) {
				if (movieId == testMovieId) continue;
				
				HashMap<Integer, Integer> userRatings = movieUserRatings.get(movieId);
				
				userIds.addAll(userRatings.keySet());
				
				double cosineSimilarity = getCosineSimilarity(testMovieUserRatings, userRatings, userIds);
			
				// k = 10
				if (kClosestSimilarities.size() < 10) {
					kClosestSimilarities.put(movieId, cosineSimilarity);
					
					if (cosineSimilarity < smallestKSimilarity) {
						smallestKSimilarity = cosineSimilarity;
						smallestId = movieId;
					}
				}
				else if (cosineSimilarity > smallestKSimilarity) {
					kClosestSimilarities.remove(smallestId);
					kClosestSimilarities.put(movieId, cosineSimilarity);
					
					smallestKSimilarity = 1;
					for (int id : kClosestSimilarities.keySet()) {
						double similarity = kClosestSimilarities.get(id);
						
						if (similarity < smallestKSimilarity) {
							smallestKSimilarity = cosineSimilarity;
							smallestId = id;
						}
					}
				}
			}
			
			double numerator = 0;
			double denomenator = 0;
			
			for (int neighborId : kClosestSimilarities.keySet()) {
				double neighborSimilarity = kClosestSimilarities.get(neighborId);
				int neighborRating = movieRatings.get(neighborId);
				
				numerator += neighborSimilarity * neighborRating;
				denomenator += neighborSimilarity;
			}
			
			int neighborRating = (int)(numerator / denomenator);
			
			if (neighborRating % 5 < 3) {
				neighborRating = neighborRating - (neighborRating % 10);
			}
			else {
				neighborRating = neighborRating - (neighborRating % 10) + 5;
			}
			
			if (testRating == neighborRating) {
				correct++;
			}
			else {
				wrong++;
			}
		}
		
		System.out.println("Percentage: " + (((double)correct / (correct + wrong)) * 100) + "%");
		System.out.println("Correct: " + correct);
		System.out.println("Wrong: " + wrong);
		System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000));
	}
	
	public double getCosineSimilarity(HashMap<Integer, Integer> vector1, HashMap<Integer, Integer> vector2, HashSet<Integer> keys)
	{
		double similarity = 0;
		double mag1 = 0;
		double mag2 = 0;
		
		for (int key : keys) {
			if (vector1.containsKey(key) && vector2.containsKey(key)) {
				similarity += vector1.get(key) * vector2.get(key);
				mag1 += Math.pow(vector1.get(key), 2);
				mag2 += Math.pow(vector2.get(key), 2);
			}
		}
		
		return similarity / (Math.sqrt(mag1) + Math.sqrt(mag2));
	}
}
