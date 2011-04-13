package org.nicta.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class NewPMF extends MovieLens
{
	final int DIMENSION_COUNT = 5; 
	final Random RANDOM = new Random();
	final double STEP_CONVERGENCE = 1e-5;
	final double STEP_SIZE = 0.0001; //learning rate
	
	double lambda = .001;
	double MEAN;
	double momentum = 0.8;
	int N;
	
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
	
	
	public double getMean(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings)
	{
		double total = 0;
		int n = 0;
		
		for (int movieId : movieUserRatings.keySet()) {
			HashMap<Integer, Double> ratings = movieUserRatings.get(movieId);
			
			for (int userId : ratings.keySet()) {
				total += ratings.get(userId);
				n++;
			}
		}
		N = n;
		
		return total / n;
	}
	
	public double predict(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, HashMap<Integer, HashSet<Integer>> userMovies, HashMap<Integer[], Double> testData)
	{
		//Fill priors
		HashMap<Integer, Double[]> userMatrix = getPriors(userMovies.keySet());
		HashMap<Integer, Double[]> movieMatrix = getPriors(movieUserRatings.keySet());
		
		double mean = getMean(movieUserRatings);
		MEAN = mean;
		
		for (int movieId : movieUserRatings.keySet()) {
			HashMap<Integer, Double> ratings = movieUserRatings.get(movieId);
			
			for (int userId : ratings.keySet()) {
				ratings.put(userId, ratings.get(userId) - mean);
			}
		}
		
		//Gradient Descent
		minimize(movieUserRatings, userMatrix, movieMatrix, testData);
		
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
			prediction += mean;
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
		HashMap<Integer[], Double> trainData = new HashMap<Integer[], Double>();
		
		for (int movieId : movieUserRatings.keySet()) {
			HashMap<Integer, Double> ratings = movieUserRatings.get(movieId);
			
			for (int userId : ratings.keySet()) {
				trainData.put(new Integer[]{userId, movieId}, ratings.get(userId));
			}
		}
		
		double oldError = getError(userMatrix, movieMatrix, movieUserRatings);
		boolean converged = false;
		
		int iterations = 0;
		
		System.out.println("Error: " + oldError);
		
		double stepSize = STEP_SIZE;
		
		while (!converged) {
			iterations++;
		
			HashMap<Integer, Double[]> updatedUserMatrix = new HashMap<Integer, Double[]>();
			for (int k : userMatrix.keySet()) {
				updatedUserMatrix.put(k, new Double[DIMENSION_COUNT]);
				
				for (int l = 0; l < DIMENSION_COUNT; l++) {
					updatedUserMatrix.get(k)[l] = userMatrix.get(k)[l];
				}
			}
			
			HashMap<Integer, Double[]> updatedMovieMatrix = new HashMap<Integer, Double[]>(); 
			for (int q : movieMatrix.keySet()) {
				updatedMovieMatrix.put(q, new Double[DIMENSION_COUNT]);
				
				for (int l = 0; l < DIMENSION_COUNT; l++) {
					updatedMovieMatrix.get(q)[l] = movieMatrix.get(q)[l];
				}
			}
			
			System.out.println("Iterations: " + iterations);
		
			for (int movieId : movieUserRatings.keySet()) {
				HashMap<Integer, Double> ratings = movieUserRatings.get(movieId);
				
				for (int userId : ratings.keySet()) {
					double trueR = ratings.get(userId);
					double predR = dot(userMatrix.get(userId), movieMatrix.get(movieId));
					
					double io = 2 * (predR - trueR);
					
					for (int y = 0; y < DIMENSION_COUNT; y++) {
						double dV = (io * userMatrix.get(userId)[y]) + (lambda * movieMatrix.get(movieId)[y]);
						double vInc = (momentum * movieMatrix.get(movieId)[y]) + (STEP_SIZE * dV / N);
						updatedMovieMatrix.get(movieId)[y] = movieMatrix.get(movieId)[y] - vInc;
						
						double dU = (io * movieMatrix.get(movieId)[y]) + (lambda * userMatrix.get(userId)[y]);
						double uInc = (momentum * userMatrix.get(userId)[y]) + (STEP_SIZE * dU / N);
						updatedUserMatrix.get(userId)[y] = userMatrix.get(userId)[y] - uInc;
					}
				}
			}
			
			double newError = getError(updatedUserMatrix, updatedMovieMatrix, movieUserRatings);
			double evalRMSE = calculateRMSE(evaluate, updatedUserMatrix, updatedMovieMatrix);
			double trainRMSE = calculateRMSE(trainData, updatedUserMatrix, updatedMovieMatrix);
			
			System.out.println("Old Error: " + oldError);
			System.out.println("New Error: " + newError);
			System.out.println("Diff: " + (oldError - newError));
			System.out.println("RMSE: " + evalRMSE);
			System.out.println("Train RMSE: " + trainRMSE);
			System.out.println("");
		
			if (newError < oldError) {
				stepSize *= 1.25;
                
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

	
	
	public HashMap<Integer, Double[]> getPriors(Set<Integer> ids)
	{
		HashMap<Integer, Double[]> priors = new HashMap<Integer, Double[]>();
		
		for (int id : ids) {
			Double[] vector = new Double[DIMENSION_COUNT];
			
			for (int x = 0; x < DIMENSION_COUNT; x++) {
				vector[x] = RANDOM.nextGaussian() * 0.1;
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
        		
        		error += Math.pow(predictedRating - trueRating, 2);
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
        
        //userNorm *= lambdaU;
        //movieNorm *= lambdaV;
        
        error += lambda * (userNorm + movieNorm) / 2;

        return error;
	}
	
	public static void main(String[] args)
		throws Exception
	{
		new NewPMF().run(10);
	}
	
	
	
	public double calculateRMSE(HashMap<Integer[], Double> data,  HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix)
	{
		double se = 0;
		
		for (Integer[] test : data.keySet()) {
			int testUserId = test[0];
			int testMovieId = test[1];
			
			double testRating = data.get(test);
			double prediction = dot(userMatrix.get(testUserId), movieMatrix.get(testMovieId));
			prediction += MEAN;
			if (prediction > 5) prediction = 5;
			if (prediction < 1) prediction = 1;
			
			se += Math.pow((prediction - testRating), 2);
		}
		
		double mse = se / (double)data.size();
		return Math.sqrt(mse);
	}
}
