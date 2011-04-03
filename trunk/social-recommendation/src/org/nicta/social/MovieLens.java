package org.nicta.social;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;

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

public abstract class MovieLens 
{
	/* 10M MovieLens dataset */
	/*
	final String ratingsSource = "/Users/jino/Desktop/Honours/ml-data-10M100K/ratings.dat";
	final String separator = "::";
	final int MOVIE_COUNT = 10681;
	final int USER_COUNT = 71567;
	final int RATING_COUNT = 10000054;
	final int LARGEST_MOVIE_ID = 64999;
	*/
	
	/* 1M MovieLens dataset */
	/*
	final String ratingsSource = "/Users/jino/Desktop/Honours/million-ml-data/ratings.dat";
	final String separator = "::";
	final int MOVIE_COUNT = 3952;
	final int USER_COUNT = 6040;
	final int RATING_COUNT = 1000209;
	final int LARGEST_MOVIE_ID = 3952;
	*/
	
	/* 100K Movielens */
	
	final String ratingsSource = "/Users/jino/Desktop/Honours/ml-data_0/u.data";
	final String separator = "\\t";
	final int MOVIE_COUNT = 1682;
	final int USER_COUNT = 943;
	final int RATING_COUNT = 100000;
	final int LARGEST_MOVIE_ID = 1682;
	
	double mae;
	
	/**
     * Dot product convenience method
     * @param d1
     * @param d2
     * @return
     */
    public double dot(Double[] d1, Double[] d2)
    {
    	double result = 0;

    	for (int x = 0; x < d1.length; x++) {
    		result += d1[x] * d2[x];
    	}

    	return result;
    }
    
	public HashMap<Integer[], Double> getTestData(HashMap<Integer, HashMap<Integer, Double>> matrix, HashMap<Integer, HashSet<Integer>> userMovies, HashSet<Integer[]> added)
	{
		HashMap<Integer[], Double> testData = new HashMap<Integer[], Double>();
		
		while (testData.size()  < RATING_COUNT * .1) {
			
			int randomMovie = (int)(Math.random() * (LARGEST_MOVIE_ID + 1));
			if (matrix.containsKey(randomMovie)) {
				HashMap<Integer, Double> userRatings = matrix.get(randomMovie);
				if (userRatings.size() == 0) {
					continue;
				}
				
				int randomUser = (int)(Math.random() * userRatings.size());
					
				Object[] users = userRatings.keySet().toArray();
				
				Integer[] key = {(Integer)users[randomUser], randomMovie};
				
				if (! added.contains(key)) { 
					testData.put(key, userRatings.get(users[randomUser]));
					added.add(key);
					
					userRatings.remove(users[randomUser]);
					userMovies.get(users[randomUser]).remove(randomMovie);
				}
			}
			
		}
		
		return testData;
	}
	
	/**
	 * Returns the rating data as a HashMap
	 * 
	 * Key is a 2-length Integer array with key[0]=user_id and key[1]=movie_id. The value returned is the rating.
	 * 
	 * @return HashMap<Integer[], Integer>
	 * @throws Exception
	 */
	public HashMap<Integer[], Double> getRatings()
		throws Exception
	{
		HashMap<Integer[], Double> ratings = new HashMap<Integer[], Double>();
		BufferedReader ratingInput = new BufferedReader(new FileReader(ratingsSource));
		String line = ratingInput.readLine();
		int count = 0;
		while (line != null) {
			String[] tokens = line.split(separator);
			int userId = Integer.parseInt(tokens[0]);
			int itemId = Integer.parseInt(tokens[1]);
			double rating = Float.parseFloat(tokens[2]);
		
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
	
	/**
	 * Returns a HashMap of a movie and it's ratings.
	 * 
	 * The key is the movie_id. The value returns is a HashSet<Integer> containing all of the movie's ratings.
	 * 
	 * @param ratings
	 * @param testData
	 * @return HashMap<Integer, HashSet<Integer>>
	 */
	public HashMap<Integer, HashSet<Double>> getMovieRatingsMap(HashMap<Integer[], Double> ratings, HashMap<Integer[], Double> testData)
	{
		HashMap<Integer, HashSet<Double>> trainingData = new HashMap<Integer, HashSet<Double>>();
		
		for (Integer[] key : ratings.keySet()) {
			if (testData.containsKey(key)) continue;
			
			double rate = ratings.get(key);
			int itemId = key[1];
			
			if (!trainingData.containsKey(itemId)) {
				trainingData.put(itemId, new HashSet<Double>());
			}
			
			trainingData.get(itemId).add(rate);
		}
		
		return trainingData;
	}
	
	public Object[] getMovieUserRatingsAndUserMoviesData()
		throws Exception
	{
		//The Matrix
		HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = new HashMap<Integer, HashMap<Integer, Double>>();
		
		HashMap<Integer, HashSet<Integer>> userMovies = new HashMap<Integer, HashSet<Integer>>();
		
		BufferedReader ratingInput = new BufferedReader(new FileReader(ratingsSource));
		String line = ratingInput.readLine();
		
		int count = 0;
		while (line != null) {
			count++;
			
			String[] tokens = line.split(separator);
			int userId = Integer.parseInt(tokens[0]);
			int movieId = Integer.parseInt(tokens[1]);
			
			double rating = Float.parseFloat(tokens[2]);
			
			if (!movieUserRatings.containsKey(movieId)) {
				movieUserRatings.put(movieId, new HashMap<Integer, Double>());
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
		
		return new Object[]{movieUserRatings, userMovies};
	}
	
	/**
	 * Starting point every prediction run
	 * @throws Exception
	 */
	//public abstract void run() throws Exception; //So imaginative
	
	/*******************************************************************************
	 * The next 3 methods aren't actually being currently used. I'm crazy that way.*
	 *******************************************************************************/
	
	
	public HashMap<Integer[], Integer> getTrainingData(HashMap<Integer[], Integer> ratings, HashMap<Integer[], Integer> testData)
	{
		HashMap<Integer[], Integer> trainingData = new HashMap<Integer[], Integer>();
		
		for (Integer[] key : ratings.keySet()) {
			if (testData.containsKey(key)) continue;
	
			trainingData.put(key, ratings.get(key));
		}
		
		return trainingData;
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
}
