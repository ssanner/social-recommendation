package org.nicta.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class MovieLensFeatureMFBernoulli extends MovieLens
{
	final int DIMENSION_COUNT = 5; 
	final Random RANDOM = new Random();
	final double STEP_CONVERGENCE = 1e-7;
	final double STEP_SIZE = 0.00001; //learning rate
	final double MOMENTUM = 0.5;
	
	double lambdaU = 1;
	double lambdaV = 1; 
	double totalAverage;
	HashMap<Integer, Double> itemAverages;
	HashMap<Integer, Double> userAverages;
	
	int USER_FEATURE_COUNT = 22; //sex + 21 occupations
			
	public void run(int k)
		throws Exception
	{	
		long start = System.currentTimeMillis();
		
		System.out.println("Get Data");
		Object[] data = getMovieUserRatingsAndUserMoviesData();
		HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = (HashMap<Integer, HashMap<Integer, Double>>)data[0];
		HashMap<Integer, HashSet<Integer>> userMovies = (HashMap<Integer, HashSet<Integer>>)data[1];
		
		HashSet<Integer[]> added = new HashSet<Integer[]>();
		
		HashMap<Integer, HashSet<String>> userFeatures = getBernoulliUserFeatures();
		HashMap<Integer, HashSet<String>> movieFeatures = getBernoulliMovieFeatures();
		
		double rmseSum = 0;
		for (int x = 0; x < k; x++) {
			System.out.println("Predict " + (x+1));
			HashMap<Integer[], Double> testData = getTestData(movieUserRatings, userMovies, added);
			
			
			double rmse = getRMSE(movieUserRatings, userMovies, testData, userFeatures, movieFeatures);
			rmseSum += rmse;
		
			//Reset
			for (Integer[] key : testData.keySet()) {
				int userId = key[0];
				int movieId = key[1];
				double rating = testData.get(key);
				
				movieUserRatings.get(movieId).put(userId, rating);
				userMovies.get(userId).add(movieId);
			}
			
			System.out.println("RMSE of Run " + (x+1) + ": " + rmse);
		}
		
		System.out.println("Average MAE: " + mae / k);
		System.out.println("Average RMSE: " + rmseSum / k);
		System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000));
	}
	
	public double getRMSE(HashMap<Integer, HashMap<Integer, Double>> ratings, HashMap<Integer, HashSet<Integer>> userMovies, 
							HashMap<Integer[], Double> testData, HashMap<Integer, HashSet<String>> userFeatures, HashMap<Integer, HashSet<String>> movieFeatures)
	{
		//Fill priors
		Double[][] userMatrix = getPrior(USER_FEATURE_COUNT + USER_COUNT);
		Double[][] movieMatrix = getPrior(MOVIE_FEATURE_COUNT + MOVIE_COUNT);
		
		HashMap<Integer, Double[]> userTraits = getUserTraitVectors(userMatrix, userFeatures);
		HashMap<Integer, Double[]> movieTraits = getMovieTraitVectors(movieMatrix, movieFeatures);
		
		getAverages(ratings, userMovies);
		
		HashMap<Integer, HashMap<Integer, Double>> normalizedRatings = new HashMap<Integer, HashMap<Integer, Double>>();
		
		//normalize
		for (int movieId : ratings.keySet()) {
			HashMap<Integer, Double> norms = new HashMap<Integer, Double>();
			normalizedRatings.put(movieId, norms);
			HashMap<Integer, Double> unnormalized = ratings.get(movieId);
			
			for (int userId : unnormalized.keySet()) {
				double itemAverage = itemAverages.containsKey(movieId) ? itemAverages.get(movieId) : totalAverage;
				double userAverage = userAverages.containsKey(userId) ? userAverages.get(userId) : totalAverage;
				double userItemAverage = userAverage + itemAverage - totalAverage;
				
				norms.put(userId, unnormalized.get(userId) - userAverage);
			}
		}
		
		//Gradient Descent
		minimize(normalizedRatings, userMatrix, movieMatrix, userFeatures, movieFeatures, userTraits, movieTraits, testData);
		
		double se = 0;
		double ae = 0;
		
		int count = 0;
		for (Integer[] test : testData.keySet()) {
			count++;
			
			if (count % 1000 == 0) System.out.println("Run: " + count);
			
			int testUserId = test[0];
			int testMovieId = test[1];
			
			double testRating = testData.get(test);
			double prediction = predict(userTraits.get(testUserId), movieTraits.get(testMovieId));
			
			double itemAverage = itemAverages.containsKey(testMovieId) ? itemAverages.get(testMovieId) : totalAverage;
			double userAverage = userAverages.containsKey(testUserId) ? userAverages.get(testUserId) : totalAverage;
			double userItemAverage = userAverage + itemAverage - totalAverage;
			prediction += userAverage;
			
			if (prediction > 5) prediction = 5;
			if (prediction < 1) prediction = 1;
			
			se += Math.pow((prediction - testRating), 2);
			ae += Math.abs(prediction - testRating);
		}
		
		double mse = se / (double)testData.size();
		mae += ae / (double)testData.size();
		
		return Math.sqrt(mse);
	}

	public HashMap<Integer, Double[]> getUserTraitVectors(Double[][] matrix, HashMap<Integer, HashSet<String>> features)
	{
		HashMap<Integer, Double[]> traitVectors = new HashMap<Integer, Double[]>();
		
		for (int id : features.keySet()) {
			HashSet<String> feature = features.get(id);
			Double[] vector = new Double[DIMENSION_COUNT];
			
			for (int x = 0; x < DIMENSION_COUNT; x++) {
				vector[x] = 0.0;
				
				if (feature.contains("M")) {
					vector[x] += matrix[x][0];
				}
				
				for (int y = 0; y < OCCUPATION10K.length; y++) {
					if (feature.contains(OCCUPATION10K[y])) {
						vector[x] += matrix[x][y+1];
						break;
					}
				}
				
				vector[x] += matrix[x][USER_FEATURE_COUNT + (id-1)];
			}
			
			traitVectors.put(id, vector);
		}
		
		return traitVectors;
	}
	
	public HashMap<Integer, Double[]> getMovieTraitVectors(Double[][] matrix, HashMap<Integer, HashSet<String>> features)
	{
		HashMap<Integer, Double[]> traitVectors = new HashMap<Integer, Double[]>();
		
		for (int id : features.keySet()) {
			HashSet<String> feature = features.get(id);
			Double[] vector = new Double[DIMENSION_COUNT];
			
			for (int x = 0; x < DIMENSION_COUNT; x++) {
				vector[x] = 0.0;
				
				for (int y = 0; y < GENRES.length; y++) {
					if (feature.contains(GENRES[y])) {
						vector[x] += matrix[x][y];
						break;
					}
				}
				
				vector[x] += matrix[x][MOVIE_FEATURE_COUNT + (id-1)];
			}
			
			traitVectors.put(id, vector);
		}
		
		return traitVectors;
	}
	
	public double predict(Double[] userTraitVector, Double[] movieTraitVector)
	{
		double prediction = 0;
		
		for (int x = 0; x < DIMENSION_COUNT; x++) {
			prediction += userTraitVector[x] * movieTraitVector[x];
		}
		
		return prediction;
	}
	
	public void minimize(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, Double[][] userMatrix, Double[][] movieMatrix, 
							HashMap<Integer, HashSet<String>> userFeatures, HashMap<Integer, HashSet<String>> movieFeatures, 
							HashMap<Integer, Double[]> userTraits, HashMap<Integer, Double[]> movieTraits,
							HashMap<Integer[], Double> testData)
	{
		double oldError = getError(userMatrix, movieMatrix, userTraits, movieTraits, movieUserRatings);
		boolean converged = false;
		
		int iterations = 0;
		
		System.out.println("Error: " + oldError);
		
		double stepSize = STEP_SIZE;
		
		Double[][] oldUserDerivative = new Double[DIMENSION_COUNT][USER_FEATURE_COUNT + USER_COUNT];
		Double[][] oldMovieDerivative = new Double[DIMENSION_COUNT][MOVIE_FEATURE_COUNT + MOVIE_COUNT];
		
		for (int x = 0; x < DIMENSION_COUNT; x++) {
			for (int y = 0; y < USER_FEATURE_COUNT + USER_COUNT; y++) {
				oldUserDerivative[x][y] = 0.0;
			}
			
			for (int y = 0; y < MOVIE_FEATURE_COUNT + MOVIE_COUNT; y++) {
				oldMovieDerivative[x][y] = 0.0;
			}
		}
		
		while (!converged && iterations <= 500) {
			iterations++;
		
			Double[][] updatedUserMatrix = new Double[DIMENSION_COUNT][USER_FEATURE_COUNT + USER_COUNT]; 
			Double[][] updatedMovieMatrix = new Double[DIMENSION_COUNT][MOVIE_FEATURE_COUNT + MOVIE_COUNT]; 
			
			Double[][] userDerivative = new Double[DIMENSION_COUNT][USER_FEATURE_COUNT + USER_COUNT];
			Double[][] movieDerivative = new Double[DIMENSION_COUNT][MOVIE_FEATURE_COUNT + MOVIE_COUNT];
			
			System.out.println("Iterations: " + iterations);
		
			//Update user matrix
			System.out.println("Update User Matrix");
			for (int k = 0; k < DIMENSION_COUNT; k++) {
				System.out.println("Dimension: " + k);
				for (int l = 0; l < USER_FEATURE_COUNT + USER_COUNT; l++) {
					//System.out.print(l + " ");
					double update = (stepSize * getErrorDerivativeOverUser(userMatrix, userFeatures, userTraits, movieTraits, movieUserRatings, k, l)) + (MOMENTUM * oldUserDerivative[k][l]);
					userDerivative[k][l] = update;
					
					updatedUserMatrix[k][l] = userMatrix[k][l] - update;
				}
				//System.out.println("");
			}
			
			//Update movie matrix
			System.out.println("Update Movie Matrix");
			for (int q = 0; q < DIMENSION_COUNT; q++) {
				System.out.println("Dimension: " + q);
				for (int l = 0; l < MOVIE_FEATURE_COUNT + MOVIE_COUNT; l++) {
					//System.out.print(l + " ");
					double update = (stepSize * getErrorDerivativeOverMovie(movieMatrix, movieFeatures, userTraits, movieTraits, movieUserRatings, q, l)) + (MOMENTUM * oldMovieDerivative[q][l]);
					movieDerivative[q][l] = update;
					
					updatedMovieMatrix[q][l] = movieMatrix[q][l] - update;
				}
				//System.out.println("");
			}
			
			System.out.println("Get new trait vectors");
			HashMap<Integer, Double[]> updateUserTraits = getUserTraitVectors(updatedUserMatrix, userFeatures);
			HashMap<Integer, Double[]> updateMovieTraits = getMovieTraitVectors(updatedMovieMatrix, movieFeatures);
			
			System.out.println("Get new error");
			double newError = getError(updatedUserMatrix, updatedMovieMatrix, updateUserTraits, updateMovieTraits, movieUserRatings);
			double evalRMSE = calculateRMSE(testData, updatedUserMatrix, updatedMovieMatrix, userTraits, movieTraits);
			
			System.out.println("Old Error: " + oldError);
			System.out.println("New Error: " + newError);
			System.out.println("Diff: " + (oldError - newError));
			System.out.println("RMSE: " + evalRMSE);
			System.out.println("");
		
			if (newError < oldError) {
				stepSize *= 1.25;
                
                for (int k = 0; k < DIMENSION_COUNT; k++) {
    				userMatrix[k] = updatedUserMatrix[k];
    				oldUserDerivative[k] = userDerivative[k];
    			}
    			for (int q = 0; q < DIMENSION_COUNT; q++) {
    				movieMatrix[q] = updatedMovieMatrix[q];
    				oldMovieDerivative[q] = movieDerivative[q];
    			}
    			
    			for (int userId : updateUserTraits.keySet()) {
    				userTraits.put(userId, updateUserTraits.get(userId));
    			}
    			for (int movieId : updateMovieTraits.keySet()) {
    				movieTraits.put(movieId, updateMovieTraits.get(movieId));
    			}
    			
                oldError = newError;
			}
			else {
				//Woops, overshot. Lower step size and try again
				stepSize *= .5;
			}
			
			//Once the learning rate is smaller than a certain size, just stop.
            //We get here after a few failures in the previous if statement.
            if (stepSize < STEP_CONVERGENCE) {
                converged = true;
            }
		}
	}
	
	// Hopefully I did the deriviations right. Again
	public double getErrorDerivativeOverUser(Double[][] userMatrix, HashMap<Integer, HashSet<String>> userFeatures,
												HashMap<Integer, Double[]> userTraits, HashMap<Integer, Double[]> movieTraits,
												HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int x, int y)
	{
		double errorDerivative = userMatrix[x][y] * lambdaU;
		
		for (int movieId : movieUserRatings.keySet()) {
			HashMap<Integer, Double> userRatings = movieUserRatings.get(movieId);
			
			for (int userId : userRatings.keySet()) {
				//System.out.println(movieTraits.get(movieId).length + " " + userFeatures.get(userId).length + " " + x + " " + y);
				
				if ((y == 0) || (y > 0 && y < USER_FEATURE_COUNT && userFeatures.get(userId).contains(OCCUPATION10K[y-1]))
						|| (y >= USER_FEATURE_COUNT && userFeatures.get(userId).contains("" + movieId))) {
					
					double dst = movieTraits.get(movieId)[x];			
					double p = predict(userTraits.get(userId), movieTraits.get(movieId));
					double r = userRatings.get(userId);
				
					errorDerivative += (r - p) * dst * -1;
				}
			}
		}
		
		return errorDerivative;
	}
	
	
	public double getErrorDerivativeOverMovie(Double[][] movieMatrix, HashMap<Integer, HashSet<String>> movieFeatures,
												HashMap<Integer, Double[]> userTraits, HashMap<Integer, Double[]> movieTraits,
												HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int x, int y)
	{
		double errorDerivative = movieMatrix[x][y] * lambdaV;
		
		for (int movieId : movieUserRatings.keySet()) {
			HashMap<Integer, Double> userRatings = movieUserRatings.get(movieId);
			
			for (int userId : userRatings.keySet()) {
				if ((y < MOVIE_FEATURE_COUNT && movieFeatures.get(movieId).contains(GENRES[y])) 
						|| (y >= MOVIE_FEATURE_COUNT && movieFeatures.get(movieId).contains("" + y)))
				{
					double dst = userTraits.get(userId)[x];// * movieFeatures.get(movieId)[y];
					double p = predict(userTraits.get(userId), movieTraits.get(movieId));
					double r = movieUserRatings.get(movieId).get(userId);
			
					errorDerivative += (r - p) * dst * -1;
				}
			}
		}
		
		return errorDerivative;
	}
	
	
	public Double[][] getPrior(int feature_count)
	{
		Double[][] prior = new Double[DIMENSION_COUNT][feature_count];
		
		for (int x = 0; x < DIMENSION_COUNT; x++) {
			for (int y = 0; y < feature_count; y++) {
				prior[x][y] = RANDOM.nextGaussian();
			}
		}
		
		return prior;
	}
	
	public double getError(Double[][] userMatrix, Double[][] movieMatrix, 
							HashMap<Integer, Double[]> userTraits, HashMap<Integer, Double[]> movieTraits, 
							HashMap<Integer, HashMap<Integer, Double>> movieUserRatings)
	{
        double error = 0;
    	
    	//Get the square error
        for (int j : movieUserRatings.keySet()) {
        	HashMap<Integer, Double> userRatings = movieUserRatings.get(j);
        	
        	for (int i : userRatings.keySet()) {
        		double trueRating = userRatings.get(i);
        		double predictedRating = predict(userTraits.get(i), movieTraits.get(j));
        		
        		error += Math.pow(trueRating - predictedRating, 2);
        	}
        }
        
        //Get User and Movie norms for regularisation
        double userNorm = 0;
        double movieNorm = 0;
        
        for (int x = 0; x < DIMENSION_COUNT; x++) {
        	for (int y = 0; y < USER_FEATURE_COUNT; y++) {
        		userNorm += Math.pow(userMatrix[x][y], 2);
        	}
        }
        
        for (int x = 0; x < DIMENSION_COUNT; x++) {
        	for (int y = 0; y < DIMENSION_COUNT; y++) {
        		movieNorm += Math.pow(movieMatrix[x][y], 2);
        	}
        }
        
        userNorm *= lambdaU;
        movieNorm *= lambdaV;
        
        error += userNorm + movieNorm;

        return error / 2;
	}
	
	public static void main(String[] args)
		throws Exception
	{
		new MovieLensFeatureMFBernoulli().run(10);
	}
	
	
	
	public double calculateRMSE(HashMap<Integer[], Double> data,  Double[][] userMatrix, Double[][] movieMatrix, 
									HashMap<Integer, Double[]> userTraits, HashMap<Integer, Double[]> movieTraits)
	{
		double se = 0;
		
		for (Integer[] test : data.keySet()) {
			int testUserId = test[0];
			int testMovieId = test[1];
			
			double testRating = data.get(test);
			double prediction = predict(userTraits.get(testUserId), movieTraits.get(testMovieId));
			
			double itemAverage = itemAverages.containsKey(testMovieId) ? itemAverages.get(testMovieId) : totalAverage;
			double userAverage = userAverages.containsKey(testUserId) ? userAverages.get(testUserId) : totalAverage;
			double userItemAverage = userAverage + itemAverage - totalAverage;
			
			prediction += userAverage;
			
			if (prediction > 5) prediction = 5;
			if (prediction < 1) prediction = 1;
			
			se += Math.pow((prediction - testRating), 2);
		}
		
		double mse = se / (double)data.size();
		return Math.sqrt(mse);
	}
	
	public void getAverages(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, HashSet<Integer>> userMovies)
	{		
		double total = 0;
		int totalCount = 0;
		
		itemAverages = new HashMap<Integer, Double>();
		
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
		totalAverage = total / totalCount;
		
		
		userAverages = new HashMap<Integer, Double>();
		
		for (int userId : userMovies.keySet()) {
			HashSet<Integer> movies = userMovies.get(userId);
			
			double totalRate = 0;
	
			for (int movieId : movies) {
				totalRate += movieUserRatings.get(movieId).get(userId);
			}
			
			double averageRating = totalRate / movies.size();
			
			userAverages.put(userId, averageRating);
		}	
	}
}
