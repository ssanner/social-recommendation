package org.nicta.social;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

/*
 * LATEST NEWS:
 * 
 * 1. Nearest neighbor works, but it's so slow. Using cosine similarity it takes about 10 secs on average to predict a single rating.
 *    Using Pearson correlation it takes about 15-20secs on average per rating. I need to find some way to speed that up.
 *    
 * 2. Speaking of Pearson, I'm using the sample correlation which according to Wikipedia is an estimate of the sample correlaton. However, it seems to be
 *    be performing a lot worse than cosine similarity. In fact, performance is just about the same as the average rating. I most likely have a bug somewhere.
 *    
 * 3. Now using RMSE as a performance metric.
 * 
 * 4. TODO: Matrix Factorization, K cross fold validation
 */
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
		
		while (testData.size()  < ratings.size() * .00001) {
			int randomIndex = (int)(Math.random() * list.size());
			testData.put(list.get(randomIndex), ratings.get(list.get(randomIndex)));
		}
		
		return testData;
	}
	
	public HashMap<Integer[], Integer> getTestNNData(HashMap<Integer, HashMap<Integer, Integer>> matrix, int largestMovieId, int count, HashMap<Integer, HashSet<Integer>> userMovies)
	{
		HashMap<Integer[], Integer> testData = new HashMap<Integer[], Integer>();
		
		while (testData.size()  < count * .00001) {
			int randomMovie = (int)(Math.random() * (largestMovieId+1));
			if (matrix.containsKey(randomMovie)) {
				HashMap<Integer, Integer> userRatings = matrix.get(randomMovie);
				
				Object[] users = userRatings.keySet().toArray();
				int randomUser = (int)(Math.random() * users.length);
				
				testData.put(new Integer[]{(Integer)users[randomUser], randomMovie}, userRatings.get(users[randomUser]));
			
				userRatings.remove(users[randomUser]);
				if (userRatings.size() == 0) matrix.remove(randomMovie);
				userMovies.get(users[randomUser]).remove(randomMovie);
			}
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
		int se = 0;
		
		for (Integer[] test : testData.keySet()) {
			int itemId = test[1];
			int average = ratingAverages.containsKey(itemId) ? ratingAverages.get(itemId) : totalAverage;
			int rate = testData.get(test);
			
			if (rate == average) {
				correct++;
			}
			else {
				wrong++;
			}
			
			se += Math.pow(rate - average, 2);
		}
		
		double mse = se / (double)testData.size();
		
		System.out.println("RMSE: " + Math.sqrt(mse));
		
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
	
	//Reference: http://public.research.att.com/~volinsky/netflix/BellKorICDM07.pdf
	public void nearestNeighbor()
		throws Exception
	{	
		System.out.println(Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().totalMemory());
		
		long start = System.currentTimeMillis();
		
		//The Matrix
		HashMap<Integer, HashMap<Integer, Integer>> movieUserRatings = new HashMap<Integer, HashMap<Integer, Integer>>();
		
		HashMap<Integer, HashSet<Integer>> userMovies = new HashMap<Integer, HashSet<Integer>>();
		
		BufferedReader ratingInput = new BufferedReader(new FileReader(ratingsSource));
		String line = ratingInput.readLine();
		
		int count = 0;
		int largestMovieId = 0;
		while (line != null) {
			count++;
			
			String[] tokens = line.split("::");
			int userId = Integer.parseInt(tokens[0]);
			int movieId = Integer.parseInt(tokens[1]);
			
			if (movieId > largestMovieId) largestMovieId = movieId;
			
			//Ratings go from 1 to 5 in 0.5 increments, but I hate floating point arithmetic so turn it into an int.
			int rating = Math.round(Float.parseFloat(tokens[2]) * 10);
			
			if (!movieUserRatings.containsKey(movieId)) {
				movieUserRatings.put(movieId, new HashMap<Integer, Integer>());
			}
			if (!userMovies.containsKey(userId)) {
				userMovies.put(userId, new HashSet<Integer>());
			}
			
			movieUserRatings.get(movieId).put(userId, rating);
			userMovies.get(userId).add(movieId);
			
			
			if (count % 1000000 == 0) {
				System.out.println(count + " loaded");
			}
			line = ratingInput.readLine();
			
		}
		ratingInput.close();
		
		System.out.println("Get test data");
		HashMap<Integer[], Integer> testData = getTestNNData(movieUserRatings, largestMovieId, count, userMovies);
		System.out.println("Size: " + testData.size());
		
		System.out.println(Runtime.getRuntime().freeMemory() + " " + Runtime.getRuntime().totalMemory());
		
		int correct = 0;
		int wrong = 0;
		double se = 0;
		
		int run = 0;
		int fmlCount = 0;
		
		for (Integer[] test : testData.keySet()) {
			long startRun = System.currentTimeMillis();
			
			System.out.println("Run: " + ++run);
			int testUserId = test[0];
			int testMovieId = test[1];
			int testRating = testData.get(test);
			
			//Get all movies rated by User
			HashSet<Integer> ratedMovies = userMovies.get(testUserId);
		
			//Get the movie vector
			if (! movieUserRatings.containsKey(testMovieId)) {
				System.out.println("FML");
				fmlCount++;
				continue;
			}
			HashMap<Integer, Integer> testMovieVector = movieUserRatings.get(testMovieId);
			
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
				
				HashMap<Integer, Integer> movieVector = movieUserRatings.get(movieId);
				userIds.addAll(movieVector.keySet());
				
				//Get the similarity to the movie vector
				//Current choices are cosine similarity and sample correlation (an estimate of the pearson correlation)
				//double similarity = getCosineSimilarity(testMovieVector, movieVector, userIds);
				double similarity = getSampleCorrelation(testMovieVector, movieVector, userIds);
				
				// k = 10
				if (kClosestSimilarities.size() < 10) {
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
			
			assert kClosestSimilarities.size() <= 10;
			
			//At this point we have the 10 most similar movies to the movie vector.
			//Now just need to follow the formula in the paper to get the predicted rating
			double numerator = 0;
			double denomenator = 0;
			
			for (int neighborId : kClosestSimilarities.keySet()) {
				//System.out.println("WTF: " + movieUserRatings.containsKey(neighborId));
				//System.out.println("WTF: " + movieUserRatings.get(neighborId));
				//System.out.println("WTF2: " +  movieUserRatings.get(neighborId).containsKey(testUserId));
				//System.out.println("WTF2: " +  movieUserRatings.get(neighborId).get(testUserId));
				
				double neighborSimilarity = kClosestSimilarities.get(neighborId);
				int neighborRating = movieUserRatings.get(neighborId).get(testUserId);
				
				numerator += neighborSimilarity * neighborRating;
				denomenator += neighborSimilarity;
			}
			
			int nnRating = (int)(numerator / denomenator);
			
			if (nnRating % 5 < 3) {
				nnRating = nnRating - (nnRating % 10);
			}
			else {
				nnRating = nnRating - (nnRating % 10) + 5;
			}
			
			if (testRating == nnRating) {
				correct++;
			}
			else {
				wrong++;
			}
			
			se += Math.pow(testRating - nnRating, 2);
			System.out.println("Run Time: " + ((System.currentTimeMillis() - startRun) / 1000));
		}
		
		double mse = se / (double)testData.size();
		System.out.println("RMSE: " + Math.sqrt(mse));
		
		System.out.println("Percentage: " + (((double)correct / (correct + wrong)) * 100) + "%");
		System.out.println("Correct: " + correct);
		System.out.println("Wrong: " + wrong);
		System.out.println("FML: " + fmlCount);
		System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000));
	}
	
	public double getCosineSimilarity(HashMap<Integer, Integer> vector1, HashMap<Integer, Integer> vector2, HashSet<Integer> keys)
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
		
		return similarity / (Math.sqrt(mag1) + Math.sqrt(mag2));
	}
	
	//This is used to estiomate the Pearson correlation according to http://en.wikipedia.org/wiki/Correlation_and_dependence
	public double getSampleCorrelation(HashMap<Integer, Integer> vector1, HashMap<Integer, Integer> vector2, HashSet<Integer> keys)
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
		
		return numerator / Math.sqrt(denominator1 * denominator2);
	}
}
