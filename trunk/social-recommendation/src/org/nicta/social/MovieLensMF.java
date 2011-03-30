package org.nicta.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class MovieLensMF extends MovieLens
{
	final int DIMENSION_COUNT = 3; 
	final Random RANDOM = new Random();
	final double STEP_CONVERGENCE = 1e-5;
	final double K = 5; //Range of rating
	final double STEP_SIZE = 0.0001; //learning rate
	
	
	double lambdaU = 10; // optimal for non-boundedness was 1000. For bounded it's 10
	double lambdaV = 10; 
	
	//HashMap<Integer, HashMap<Integer, Double>> boundedRatings;
	
	
	public void run(int k)
		throws Exception
	{
		long start = System.currentTimeMillis();
		
		System.out.println("Get Data");
		Object[] data = getMovieUserRatingsAndUserMoviesData();
		HashMap<Integer, HashMap<Integer, Double>> movieUserRatingsData = (HashMap<Integer, HashMap<Integer, Double>>)data[0];
		HashMap<Integer, HashSet<Integer>> userMovies = (HashMap<Integer, HashSet<Integer>>)data[1];
		
		//boundedRatings = boundRatings(movieUserRatingsData);
		
		HashSet<Integer[]> added = new HashSet<Integer[]>();
		
		double rmseSum = 0;
		for (int x = 0; x < k; x++) {
			System.out.println("Predict " + (x+1));
			HashMap<Integer[], Double> testData = getTestData(movieUserRatingsData, userMovies, added);
			
			HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = boundRatings(movieUserRatingsData);
			
			double rmse = predict(movieUserRatings, userMovies, testData);
			rmseSum += rmse;
		
			//Reset
			for (Integer[] key : testData.keySet()) {
				int userId = key[0];
				int movieId = key[1];
				double rating = testData.get(key);
				
				movieUserRatingsData.get(movieId).put(userId, rating);
				userMovies.get(userId).add(movieId);
			}
			
			System.out.println("RMSE of Run " + (x+1) + ": " + rmse);
		}
		
		System.out.println("Average RMSE: " + rmseSum / k);
		System.out.println("Time: " + ((System.currentTimeMillis() - start) / 1000));
	}
	
	public double predict(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, HashSet<Integer>> userMovies, HashMap<Integer[], Double> testData)
	{
		//Fill priors
		HashMap<Integer, Double[]> userMatrix = getPriors(userMovies.keySet());
		HashMap<Integer, Double[]> movieMatrix = getPriors(movieUserRatings.keySet());
		
		//Gradient Descent
		minimize(movieUserRatings, userMatrix, movieMatrix);
		
		double se = 0;
		
		int count = 0;
		for (Integer[] test : testData.keySet()) {
			count++;
			
			if (count % 1000 == 0) System.out.println("Run: " + count);
			
			int testUserId = test[0];
			int testMovieId = test[1];
			
			double testRating = testData.get(test);
			double prediction = boundPrediction(dot(userMatrix.get(testUserId), movieMatrix.get(testMovieId)));
			prediction *= K;
			//double prediction = dot(userMatrix.get(testUserId), movieMatrix.get(testMovieId));
			
			se += Math.pow((prediction - testRating), 2);
		}
		
		double mse = se / (double)testData.size();
		return Math.sqrt(mse);
	}

	public void minimize(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix)
	{
		double oldError = getBoundedError(userMatrix, movieMatrix, movieUserRatings);
		boolean converged = false;
		
		int iterations = 0;
		
		System.out.println("Error: " + oldError);
		
		double stepSize = STEP_SIZE;
		
		while (!converged) {
			iterations++;
		
			HashMap<Integer, Double[]> updatedUserMatrix = new HashMap<Integer, Double[]>(); 
			HashMap<Integer, Double[]> updatedMovieMatrix = new HashMap<Integer, Double[]>(); 
			
			System.out.println("Iterations: " + iterations);
		
			//Update user matrix
			for (int k : userMatrix.keySet()) {
				updatedUserMatrix.put(k, new Double[DIMENSION_COUNT]);
				
				for (int l = 0; l < DIMENSION_COUNT; l++) {
					updatedUserMatrix.get(k)[l] = userMatrix.get(k)[l] - (stepSize * getNewErrorDerivativeOverUserBounded(userMatrix, movieMatrix, movieUserRatings, k, l));
				}
			}
			
			//Update movie matrix
			for (int q : movieMatrix.keySet()) {
				updatedMovieMatrix.put(q, new Double[DIMENSION_COUNT]);
				
				for (int l = 0; l < DIMENSION_COUNT; l++) {
					updatedMovieMatrix.get(q)[l] = movieMatrix.get(q)[l] - (stepSize * getNewErrorDerivativeOverMovieBounded(userMatrix, movieMatrix, movieUserRatings, q, l));
				}
			}
			
			double newError = getBoundedError(updatedUserMatrix, updatedMovieMatrix, movieUserRatings);
	
			System.out.println("Old Error: " + oldError);
			System.out.println("New Error: " + newError);
			System.out.println("Diff: " + (oldError - newError));
			System.out.println("");
		
			// For some reason making the step size dynamic increases the RMSE.
			// Over-fitting maybe?
			if (newError < oldError) {
				stepSize *= 1.25;

                //Stop once we hit a limit of the speed of changes
                if (oldError - newError < .1) {
                    //converged = true;
                }
                
                
                for (int k : userMatrix.keySet()) {
    				userMatrix.put(k, updatedUserMatrix.get(k));
    			}
    			for (int q : movieMatrix.keySet()) {
    				movieMatrix.put(q, updatedMovieMatrix.get(q));
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
	
	// Hopefully I did the deriviations right
	public double getErrorDerivativeOverUser(HashMap<Integer, Double[]>userMatrix, HashMap<Integer, Double[]>movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int k, int l)
	{
		double klUser = userMatrix.get(k)[l];
		
		double errorDerivative = klUser * lambdaU;
		
		for (int j : movieMatrix.keySet()) {
			if (!movieUserRatings.get(j).containsKey(k)) continue;
			
			errorDerivative += klUser * Math.pow(movieMatrix.get(j)[l], 2);
			errorDerivative -= movieUserRatings.get(j).get(k) * movieMatrix.get(j)[l];
		}
		
		return errorDerivative;
	}
	
	public double getErrorDerivativeOverUserBounded(HashMap<Integer, Double[]>userMatrix, HashMap<Integer, Double[]>movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int k, int l)
	{
		double klUser = userMatrix.get(k)[l];
		
		double errorDerivative = klUser * lambdaU;
		
		for (int j : movieMatrix.keySet()) {
			if (!movieUserRatings.get(j).containsKey(k)) continue;
			
			double exp = Math.exp(-(klUser * movieMatrix.get(j)[l]));
			double exp2 = exp + 1;
			double gx2 = exp / Math.pow(exp2, 2);
			double gx3 = gx2 / exp2;
			
			errorDerivative += gx3;
			errorDerivative -= movieUserRatings.get(j).get(k) * gx2;
		}
		
		return errorDerivative;
	}
	
	public double getNewErrorDerivativeOverUserBounded(HashMap<Integer, Double[]>userMatrix, HashMap<Integer, Double[]>movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int k, int l)
	{
		double klUser = userMatrix.get(k)[l];
		
		double errorDerivative = klUser * lambdaU;
		
		
		for (int j : movieMatrix.keySet()) {
			if (!movieUserRatings.get(j).containsKey(k)) continue;
			
			double u = klUser;
			double v = movieMatrix.get(j)[l];
			//double e = Math.exp(-(klUser * v));
			double e = Math.exp(-dot(userMatrix.get(k), movieMatrix.get(j)));
			double r = movieUserRatings.get(j).get(k);
			double g = 1 / (1 + e);
			
			errorDerivative += (r - g) * g * (1 - g) * u * v;
		}
		
		//System.out.println("D User: " + errorDerivative);
		return errorDerivative;
	}
	
	public double getErrorDerivativeOverMovie(HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int q, int l)
	{
		double lqMovie = movieMatrix.get(q)[l];
		
		double errorDerivative = lqMovie * lambdaV;
		
		for (int i : userMatrix.keySet()) {
			if (!movieUserRatings.get(q).containsKey(i)) continue;
			
			errorDerivative += lqMovie * Math.pow(userMatrix.get(i)[l], 2);
			errorDerivative -= movieUserRatings.get(q).get(i) * userMatrix.get(i)[l];
		}
		
		return errorDerivative;
	}
	
	public double getErrorDerivativeOverMovieBounded(HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int q, int l)
	{
		double lqMovie = movieMatrix.get(q)[l];
		
		double errorDerivative = lqMovie * lambdaV;
		
		for (int i : userMatrix.keySet()) {
			if (!movieUserRatings.get(q).containsKey(i)) continue;
			
			double exp = Math.exp(-(lqMovie * userMatrix.get(i)[l]));
			double exp2 = exp + 1;
			double gx2 = exp / Math.pow(exp2, 2);
			double gx3 = gx2 / exp2;
			
			errorDerivative += gx3;
			errorDerivative -= movieUserRatings.get(q).get(i) * gx2;
		}
		
		return errorDerivative;
	}
	
	public double getNewErrorDerivativeOverMovieBounded(HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int q, int l)
	{
		double lqMovie = movieMatrix.get(q)[l];
		
		double errorDerivative = lqMovie * lambdaV;
		
		for (int i : userMatrix.keySet()) {
			if (!movieUserRatings.get(q).containsKey(i)) continue;
			
			double u = userMatrix.get(i)[l];
			//double e = Math.exp(-(lqMovie * u));
			double e = Math.exp(-dot(userMatrix.get(i), movieMatrix.get(q)));
			double r = movieUserRatings.get(q).get(i);
			double v = lqMovie;
			double g = 1 / (1 + e);
		
			
			errorDerivative += (r - g) * g * (1 - g) * u * v;
		}

		//System.out.println("D Movie: " + errorDerivative);
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
	
	public double getBoundedError(HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings)
	{
        double error = 0;
    	
    	//Get the square error
        for (int j : movieUserRatings.keySet()) {
        	HashMap<Integer, Double> userRatings = movieUserRatings.get(j);
        	
        	for (int i : userRatings.keySet()) {
        		double trueRating = userRatings.get(i);
        		double predictedRating = boundPrediction(dot(userMatrix.get(i), movieMatrix.get(j)));
        		
        		error += Math.pow(trueRating - predictedRating, 2);
        	}
        }
        
        //System.out.println("sq e: " + error);
        
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
        
        //System.out.println("u: " + userNorm);
        //System.out.println("m: " + movieNorm);
        
        userNorm *= lambdaU;
        movieNorm *= lambdaV;
        
        error += userNorm + movieNorm;

        return error / 2;
	}
	
	public static void main(String[] args)
		throws Exception
	{
		new MovieLensMF().run(5);
	}
	
	public double boundPrediction(double prediction)
	{
		return 1 / (1 + Math.exp(-prediction));
	}
	
	public double boundRating(double rating)
	{
		return (rating - 1) / (K - 1);
	}
	
	public HashMap<Integer, HashMap<Integer, Double>> boundRatings(HashMap<Integer, HashMap<Integer, Double>> data)
	{
		HashMap<Integer, HashMap<Integer, Double>> boundedRatings = new HashMap<Integer, HashMap<Integer, Double>>();
		
		for (int movieId : data.keySet()) {
			HashMap<Integer, Double> ratings = data.get(movieId);
			HashMap<Integer, Double> bounded = new HashMap<Integer, Double>();
			boundedRatings.put(movieId, bounded);
			
			for (int userId : ratings.keySet()) {
				double newRatings = (ratings.get(userId) - 1) / (K - 1);
				bounded.put(userId, newRatings);
			}
		}
		
		return boundedRatings;
	}
}
