package org.nicta.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class NormalizationTest extends MovieLens
{
	final int DIMENSION_COUNT = 5; 
	final Random RANDOM = new Random();
	final double STEP_CONVERGENCE = 1e-10;
	final double STEP_SIZE = 0.0001; //learning rate
	
	double lambdaU = 10;
	double lambdaV = 10;
	
	public void run(int k)
		throws Exception
	{	
		long start = System.currentTimeMillis();
		
		System.out.println("Get Data");
		Object[] data = getMovieUserRatingsAndUserMoviesData();
		HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = (HashMap<Integer, HashMap<Integer, Double>>)data[0];
		HashMap<Integer, HashSet<Integer>> userMovies = (HashMap<Integer, HashSet<Integer>>)data[1];
		
		HashSet<Integer[]> added = new HashSet<Integer[]>();
		
		double rmseSum = 0;
		for (int x = 0; x < k; x++) {
			System.out.println("Predict " + (x+1));
			HashMap<Integer[], Double> testData = getTestData(movieUserRatings, userMovies, added);
			
			
			double rmse = predict(movieUserRatings, userMovies, testData);
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

	
	public double predict(HashMap<Integer, HashMap<Integer, Double>> ratings, HashMap<Integer, HashSet<Integer>> userMovies, HashMap<Integer[], Double> testData)
	{
		//Fill priors
		HashMap<Integer, Double[]> userMatrix = getFixedPriors(userMovies.keySet());
		HashMap<Integer, Double[]> movieMatrix = getPriors(ratings.keySet());
		
		HashMap<Integer, HashMap<Integer, Double>> normalizedRatings = new HashMap<Integer, HashMap<Integer, Double>>();
		
		for (int movieId : ratings.keySet()) {
			HashMap<Integer, Double> norms = new HashMap<Integer, Double>();
			normalizedRatings.put(movieId, norms);
			HashMap<Integer, Double> unnormalized = ratings.get(movieId);
			
			for (int userId : unnormalized.keySet()) {
				norms.put(userId, unnormalized.get(userId));
			}
		}
		
		//Gradient Descent
		minimize(normalizedRatings, userMatrix, movieMatrix, testData);
		
		double se = 0;
		double ae = 0;
		
		int count = 0;
		for (Integer[] test : testData.keySet()) {
			count++;
			
			if (count % 1000 == 0) System.out.println("Run: " + count);
			
			int testUserId = test[0];
			int testMovieId = test[1];
			
			double testRating = testData.get(test);
			double prediction = dot(userMatrix.get(testUserId), movieMatrix.get(testMovieId));
			
			if (prediction > 5) prediction = 5;
			if (prediction < 1) prediction = 1;
			
			se += Math.pow((prediction - testRating), 2);
			ae += Math.abs(prediction - testRating);
		}
		
		double mse = se / (double)testData.size();
		mae += ae / (double)testData.size();
		
		return Math.sqrt(mse);
	}

	public void minimize(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer[], Double> evaluate)
	{
		double oldError = getError(userMatrix, movieMatrix, movieUserRatings);
		boolean converged = false;
		
		int iterations = 0;
		
		System.out.println("Error: " + oldError);
		
		double stepSize = STEP_SIZE;
		
		HashMap<Integer, Double[]> lastGoodUserMatrix = new HashMap<Integer, Double[]>(); 
		HashMap<Integer, Double[]> lastGoodMovieMatrix = new HashMap<Integer, Double[]>(); 
		
		double lastGoodError = 0;
		int count = 0;
		
		while (!converged && iterations <= 500) {
			
		
			HashMap<Integer, Double[]> updatedUserMatrix = new HashMap<Integer, Double[]>(); 
			HashMap<Integer, Double[]> updatedMovieMatrix = new HashMap<Integer, Double[]>(); 
			
			//Update user matrix
			for (int k : userMatrix.keySet()) {
				updatedUserMatrix.put(k, new Double[DIMENSION_COUNT]);
				
				for (int l = 0; l < DIMENSION_COUNT-1; l++) {
					double update = (stepSize * getErrorDerivativeOverUser(userMatrix, movieMatrix, movieUserRatings, k, l));
					
					updatedUserMatrix.get(k)[l] = userMatrix.get(k)[l] - update;
				}
				
				updatedUserMatrix.get(k)[DIMENSION_COUNT-1] = userMatrix.get(k)[DIMENSION_COUNT-1];
			}
			
			//Update movie matrix
			for (int q : movieMatrix.keySet()) {
				updatedMovieMatrix.put(q, new Double[DIMENSION_COUNT]);
				
				for (int l = 0; l < DIMENSION_COUNT; l++) {
					double update = (stepSize * getErrorDerivativeOverMovie(userMatrix, movieMatrix, movieUserRatings, q, l));
					
					updatedMovieMatrix.get(q)[l] = movieMatrix.get(q)[l] - update;
				}
				
				//updatedMovieMatrix.get(q)[DIMENSION_COUNT-1] = movieMatrix.get(q)[DIMENSION_COUNT-1];
			}
			
			double newError = getError(updatedUserMatrix, updatedMovieMatrix, movieUserRatings);
			double evalRMSE = calculateRMSE(evaluate, updatedUserMatrix, updatedMovieMatrix);
			
		
			if (newError < oldError) {
				System.out.println("Stepsize: " + stepSize + " Count: " + count);
				
				stepSize *= 2;
                count++;
                
                for (int k : userMatrix.keySet()) {
    				lastGoodUserMatrix.put(k, updatedUserMatrix.get(k));
    			}
    			for (int q : movieMatrix.keySet()) {
    				lastGoodMovieMatrix.put(q, updatedMovieMatrix.get(q));
    			}
    			
                lastGoodError = newError;
			}
			else {
				//Woops, overshot. Lower step size and try again
				if (count > 0) {
					count = 0;
					
					for (int k : userMatrix.keySet()) {
	    				userMatrix.put(k, lastGoodUserMatrix.get(k));
	    			}
	    			for (int q : movieMatrix.keySet()) {
	    				movieMatrix.put(q, lastGoodMovieMatrix.get(q));
	    			}
	    			oldError = lastGoodError;
	    			
	    			iterations++;
	    			System.out.println("Iterations: " + iterations);
	    			System.out.println("Error: " + oldError);
	    			//System.out.println("New Error: " + newError);
	    			//System.out.println("Diff: " + (oldError - newError));
	    			System.out.println("RMSE: " + evalRMSE);
	    			System.out.println("");
				}
				else {
					stepSize *= .5;
				}
			}
			
			//Once the learning rate is smaller than a certain size, just stop.
            //We get here after a few failures in the previous if statement.
            if (stepSize < STEP_CONVERGENCE) {
                converged = true;
            }
		}
	}
	
	// Hopefully I did the deriviations right
	public double getErrorDerivativeOverUser(HashMap<Integer, Double[]>userMatrix, HashMap<Integer, Double[]>movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int k, int l)
	{
		double klUser = userMatrix.get(k)[l];
		double errorDerivative = klUser * lambdaU;
		
		for (int j : movieMatrix.keySet()) {
			if (!movieUserRatings.get(j).containsKey(k)) continue;
			
			double v = movieMatrix.get(j)[l];
			double p = dot(userMatrix.get(k), movieMatrix.get(j));
			double r = movieUserRatings.get(j).get(k);
			
			errorDerivative += (r - p) * v * -1;
		}
		
		return errorDerivative;
	}
	
	
	public double getErrorDerivativeOverMovie(HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int q, int l)
	{
		double lqMovie = movieMatrix.get(q)[l];
		
		double errorDerivative = lqMovie * lambdaV;
		
		for (int i : userMatrix.keySet()) {
			if (!movieUserRatings.get(q).containsKey(i)) continue;
			
			double u = userMatrix.get(i)[l];
			double p = dot(userMatrix.get(i), movieMatrix.get(q));
			double r = movieUserRatings.get(q).get(i);
		
			errorDerivative += (r - p) * u * -1;
		}
		
		return errorDerivative;
	}
	
	
	public HashMap<Integer, Double[]> getPriors(Set<Integer> ids)
	{
		HashMap<Integer, Double[]> priors = new HashMap<Integer, Double[]>();
		
		for (int id : ids) {
			Double[] vector = new Double[DIMENSION_COUNT];
			
			for (int x = 0; x < DIMENSION_COUNT; x++) {
				vector[x] = RANDOM.nextGaussian();
			}
			
			priors.put(id, vector);
		}
		
		return priors;
	}
	
	public HashMap<Integer, Double[]> getFixedPriors(Set<Integer> ids)
	{
		HashMap<Integer, Double[]> priors = new HashMap<Integer, Double[]>();
		
		for (int id : ids) {
			Double[] vector = new Double[DIMENSION_COUNT];
			
			for (int x = 0; x < DIMENSION_COUNT-1; x++) {
				vector[x] = RANDOM.nextGaussian();
			}
			
			vector[DIMENSION_COUNT-1] = 1.0;
			
			priors.put(id, vector);
		}
		
		return priors;
	}
	
	public double getError(HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings)
	{
        double error = 0;
    	
    	//Get the square error
        for (int j : movieUserRatings.keySet()) {
        	HashMap<Integer, Double> userRatings = movieUserRatings.get(j);
        	
        	for (int i : userRatings.keySet()) {
        		double trueRating = userRatings.get(i);
        		double predictedRating = dot(userMatrix.get(i), movieMatrix.get(j));
        		
        		error += Math.pow(trueRating - predictedRating, 2);
        	}
        }
        
        
        //Get User and Movie norms for regularisation
        double userNorm = 0;
        double movieNorm = 0;
        
        for (int i : userMatrix.keySet()) {
        	for (int d = 0; d < DIMENSION_COUNT; d++) {
        		userNorm += Math.pow(userMatrix.get(i)[d], 2);
        	}
        }
        
        for (int j : movieMatrix.keySet()) {
        	for (int d = 0; d < DIMENSION_COUNT; d++) {
        		movieNorm += Math.pow(movieMatrix.get(j)[d], 2);
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
		new NormalizationTest().run(10);
	}
	
	public double calculateRMSE(HashMap<Integer[], Double> data,  HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix)
	{
		double se = 0;
		
		for (Integer[] test : data.keySet()) {
			int testUserId = test[0];
			int testMovieId = test[1];
			
			double testRating = data.get(test);
			double prediction = dot(userMatrix.get(testUserId), movieMatrix.get(testMovieId));
			
			if (prediction > 5) prediction = 5;
			if (prediction < 1) prediction = 1;
			
			se += Math.pow((prediction - testRating), 2);
		}
		
		double mse = se / (double)data.size();
		return Math.sqrt(mse);
	}
}
