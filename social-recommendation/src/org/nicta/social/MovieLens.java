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
	final String ratingsSource = "/Users/jino/Desktop/FBProject/million-ml-data/ratings.dat";
	final String userSource = "/Users/jino/Desktop/FBProject/million-ml-data/users.dat";
	final String itemSource = "/Users/jino/Desktop/FBProject/million-ml-data/movies.dat";
	final String separator = ",";
	final int MOVIE_COUNT = 3952;
	final int USER_COUNT = 6040;
	final int RATING_COUNT = 1000209;
	final int LARGEST_MOVIE_ID = 3952;
	final String FEATURE_SEPARATOR = "::";
	final int USER_FEATURE_COUNT = 3;
	final int MOVIE_FEATURE_COUNT = 19;
	*/
	
	/* 100K Movielens */
	final String ratingsSource = "/Users/jino/Desktop/FBProject/ml-data_0/u.data";
	final String userSource = "/Users/jino/Desktop/FBProject/ml-data_0/u.user";
	final String itemSource = "/Users/jino/Desktop/FBProject/ml-data_0/u.item";
	final String separator = ",";
	final int MOVIE_COUNT = 1682;
	final int USER_COUNT = 943;
	final int RATING_COUNT = 100000;
	final int LARGEST_MOVIE_ID = 1682;
	final int USER_FEATURE_COUNT = 3;
	final int MOVIE_FEATURE_COUNT = 19;
	final String FEATURE_SEPARATOR = "\\|";
	
	double mae;
	final double RATING_RANGE = 5; //Range of rating
	
	final String[] OCCUPATION10K = {
		"administrator",
		"artist",
		"doctor",
		"educator",
		"engineer",
		"entertainment",
		"executive",
		"healthcare",
		"homemaker",
		"lawyer",
		"librarian",
		"marketing",
		"none",
		"other",
		"programmer",
		"retired",
		"salesman",
		"scientist",
		"student",
		"technician",
		"writer"
	};
	
	final String[] GENRES = {
		"unknown",
		"Action",
		"Adventure",
		"Animation",
		"Children's",
		"Comedy",
		"Crime",
		"Documentary",
		"Drama",
		"Fantasy",
		"Film-Noir",
		"Horror",
		"Musical",
		"Mystery",
		"Romance",
		"Sci-Fi",
		"Thriller",
		"War",
		"Western"
	};
	
	/**
     * Dot product convenience method
     * @param d1
     * @param d2
     * @return
     */
    public double dot(Double[] d1, Double[] d2)
    {
    	double result = 0;

    	//System.out.println(d1.length + " " + d2.length);
    	
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
				if (userRatings.size() == 1) {
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
	
	
	public HashMap<Integer, Double[]> getUserFeatures()
		throws Exception
	{
		HashMap<Integer, Double[]> userFeatures = new HashMap<Integer, Double[]>();
		
		BufferedReader reader = new BufferedReader(new FileReader(userSource));
		String line = reader.readLine();
		
		while (line != null) {
			String[] tokens = line.split(FEATURE_SEPARATOR);
			Double[] features = new Double[USER_FEATURE_COUNT];
			
			if (RATING_COUNT == 100000) {
				features[0] = Double.parseDouble(tokens[1]) / 100;
				features[1] = tokens[2].equals("M") ? 0.0 : 1.0;
			
				for (int x = 0; x < OCCUPATION10K.length; x++) {
					if (OCCUPATION10K[x].equals(tokens[3])) {
						features[2] = (double)x / OCCUPATION10K.length;
					}
				}
			}
			else if (RATING_COUNT == 1000209) {
				features[0] = tokens[1].equals("M") ? 0.0 : 1.0;
				features[1] = Double.parseDouble(tokens[2]) / 60;
				features[2] = Double.parseDouble(tokens[3]) / 20;
			}
			
			userFeatures.put(Integer.parseInt(tokens[0]), features);
			line = reader.readLine();
		}
		reader.close();
		
		return userFeatures;
	}
	
	public HashMap<Integer, Double[]> getMovieFeatures()
		throws Exception
	{
		HashMap<Integer, Double[]> movieFeatures = new HashMap<Integer, Double[]>();
		
		BufferedReader reader = new BufferedReader(new FileReader(itemSource));
		String line = reader.readLine();
		
		int index = 0;
		while (line != null) {
			Double[] features = new Double[MOVIE_FEATURE_COUNT];
			String[] tokens = line.split(FEATURE_SEPARATOR);
			
			if (RATING_COUNT == 100000) {
				for (int x = 5; x < tokens.length; x++) {
					features[x-5] = Double.parseDouble(tokens[x]);
				}
			}
			else if (RATING_COUNT == 1000209) {
				String[] genres = tokens[2].split("\\|");
				for (int x = 0; x < GENRES.length; x++) {
					boolean found = false;
					for (int y = 0; y < genres.length; y++) {
						if (genres[y].equals(GENRES[x])) {
							features[x] = 1.0;
							found = true;
							break;
						}
					}
					
					if (!found) features[x] = 0.0;
				}
			}
			
			movieFeatures.put(Integer.parseInt(tokens[0]), features);
			line = reader.readLine();
			index++;
		}
		reader.close();
		
		return movieFeatures;
	}
	
	public HashMap<Integer, HashSet<String>> getBernoulliUserFeatures()
		throws Exception
	{
		HashMap<Integer, HashSet<String>> userFeatures = new HashMap<Integer, HashSet<String>>();
		
		BufferedReader reader = new BufferedReader(new FileReader(userSource));
		String line = reader.readLine();
		
		while (line != null) {
			String[] tokens = line.split(FEATURE_SEPARATOR);
			HashSet<String> features = new HashSet<String>();
			
			//features[0] = Double.parseDouble(tokens[1]) / 100;
			
			features.add(tokens[2]); //sex
			features.add(tokens[3]); //occupation
			features.add(tokens[0]); //id
			
			userFeatures.put(Integer.parseInt(tokens[0]), features);
			line = reader.readLine();
		}
		reader.close();
		
		return userFeatures;
	}
	
	public HashMap<Integer, HashSet<String>> getBernoulliMovieFeatures()
		throws Exception
	{
		HashMap<Integer, HashSet<String>> movieFeatures = new HashMap<Integer, HashSet<String>>();
		
		BufferedReader reader = new BufferedReader(new FileReader(itemSource));
		String line = reader.readLine();
		
		
		while (line != null) {
			HashSet<String> features = new HashSet<String>();
			String[] tokens = line.split(FEATURE_SEPARATOR);
			
			for (int x = 5; x < tokens.length; x++) {
				if (tokens[x].equals("1")) {
					features.add(GENRES[x - 5]); //genre
				}
			}
			features.add(tokens[0]); //id
			
			movieFeatures.put(Integer.parseInt(tokens[0]), features);
			line = reader.readLine();
		}
		reader.close();
		
		return movieFeatures;
	}
}
