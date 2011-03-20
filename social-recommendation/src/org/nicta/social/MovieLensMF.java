package org.nicta.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Random;

public class MovieLensMF extends MovieLens
{
	final int DIMENSION_COUNT = 3; //Ratio of data/params should be over 10. Now we're useing the 100k dataset
	final Random RANDOM = new Random();
	final double ERROR_CONVERGENCE = .1;
	final double STEP_CONVERGENCE = 1e-10;
	final double K = 5; //Range of rating
	
	double stepSize = 0.0001; //learning rate
	double lambdaU = 100; // optimal for non-boundedness was 1000. For bounded it seems to be 100
	double lambdaV = 100; 
	
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
			movieUserRatings.clone();
			HashMap<Integer[], Double> testData = getTestData(movieUserRatings, userMovies, added);
			double rmse = predict(movieUserRatings, userMovies, testData);
			rmseSum += rmse;
		
			for (Integer[] key : testData.keySet()) {
				int userId = key[0];
				int movieId = key[1];
				double rating = testData.get(key);
				
				movieUserRatings.get(movieId).put(userId, rating);
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
			
			//Bound the range of prediction
			//prediction = 1 / (1 + Math.exp(-prediction));
			//testRating = (testRating - 1) / (K - 1);
			
			prediction *= K;
			//testRating *= K;
				
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
		
		while (!converged) {
			iterations++;
		
			//HashMap<Integer, Double[]> updatedUserMatrix = new HashMap<Integer, Double[]>(); 
			//HashMap<Integer, Double[]> updatedMovieMatrix = new HashMap<Integer, Double[]>(); 
			
			System.out.println("Iterations: " + iterations);
		
			//Update user matrix
			for (int k : userMatrix.keySet()) {
				//updatedUserMatrix.put(k, new Double[DIMENSION_COUNT]);
				
				for (int l = 0; l < DIMENSION_COUNT; l++) {
					userMatrix.get(k)[l] -= stepSize * getErrorDerivativeOverUserBoundedWrong(userMatrix, movieMatrix, movieUserRatings, k, l);
					//updatedUserMatrix.get(k)[l] = userMatrix.get(k)[l] - (stepSize * getErrorDerivativeOverUser(userMatrix, movieMatrix, movieUserRatings, k, l));
				}
			}
			
			//Update movie matrix
			for (int q : movieMatrix.keySet()) {
				//updatedMovieMatrix.put(q, new Double[DIMENSION_COUNT]);
				
				for (int l = 0; l < DIMENSION_COUNT; l++) {
					movieMatrix.get(q)[l] -= stepSize * getErrorDerivativeOverMovieBoundedWrong(userMatrix, movieMatrix, movieUserRatings, q, l);
					//updatedMovieMatrix.get(q)[l] = movieMatrix.get(q)[l] - (stepSize * getErrorDerivativeOverMovie(userMatrix, movieMatrix, movieUserRatings, q, l));
				}
			}
			
			System.out.println("Get New Error");
			double newError = getBoundedError(userMatrix, movieMatrix, movieUserRatings);
			//double newError = getError(updatedUserMatrix, updatedMovieMatrix, movieUserRatings);
			
			System.out.println("Old Error: " + oldError);
			System.out.println("New Error: " + newError);
			System.out.println("Diff: " + (oldError - newError));
			System.out.println("");
		
			// For some reason making the step size dynamic increases the RMSE.
			// Over-fitting maybe?
			if (newError < oldError) {
				//stepSize *= 1.25;

                //Stop once we hit a limit of the speed of changes
                if (oldError - newError < .1) {
                    //converged = true;
                }
                
                //userMatrix = updatedUserMatrix;
                //movieMatrix = updatedMovieMatrix;
                
                //oldError = newError;
			}
			else {
				//Woops, overshot. Lower step size and try again
				//userMatrix = updatedUserMatrix;
                //movieMatrix = updatedMovieMatrix;
				break;
				//stepSize *= .5;
			}
			
			//Once the learning rate is smaller than a certain size, just stop.
            //We get here after a few failures in the previous if statement.
            if (stepSize < STEP_CONVERGENCE) {
                converged = true;
            }
            
            oldError = newError;
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
		
		//System.out.println("error derivative:" + errorDerivative);
		return errorDerivative;
	}
	
	//This is with the bounds
	public double getErrorDerivativeOverUserBoundedWrong(HashMap<Integer, Double[]>userMatrix, HashMap<Integer, Double[]>movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int k, int l)
	{
		double klUser = userMatrix.get(k)[l];
		
		double errorDerivative = klUser * lambdaU;
		
		for (int j : movieMatrix.keySet()) {
			if (!movieUserRatings.get(j).containsKey(k)) continue;
			
			double exp = Math.exp(-(klUser * movieMatrix.get(j)[l]));
			double gx = exp / Math.pow(1 + exp, 2);
			
			errorDerivative += gx;
			errorDerivative -= boundRating(movieUserRatings.get(j).get(k)) * gx;
		}
		
		//System.out.println("error derivative:" + errorDerivative);
		return errorDerivative;
	}
	
	public double getErrorDerivativeOverUserBounded(HashMap<Integer, Double[]>userMatrix, HashMap<Integer, Double[]>movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int k, int l)
	{
		double klUser = userMatrix.get(k)[l];
		
		double errorDerivative = klUser * lambdaU;
		
		for (int j : movieMatrix.keySet()) {
			if (!movieUserRatings.get(j).containsKey(k)) continue;
			
			double exp = Math.exp((klUser * movieMatrix.get(j)[l]));
			double gx2 = exp / Math.pow(1 + exp, 2);
			double gx3 = exp / Math.pow(1 + exp, 3);
			
			errorDerivative += exp / gx2;
			errorDerivative -= boundRating(movieUserRatings.get(j).get(k)) * gx3;
		}
		
		//System.out.println("error derivative:" + errorDerivative);
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
	
	public double getErrorDerivativeOverMovieBoundedWrong(HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int q, int l)
	{
		double lqMovie = movieMatrix.get(q)[l];
		
		double errorDerivative = lqMovie * lambdaV;
		
		for (int i : userMatrix.keySet()) {
			if (!movieUserRatings.get(q).containsKey(i)) continue;
			
			double exp = Math.exp(-(lqMovie * userMatrix.get(i)[l]));
			double gx = exp / Math.pow(1 + exp, 2);
			
			errorDerivative += gx;
			errorDerivative -= boundRating(movieUserRatings.get(q).get(i)) * gx;
		}
		
		return errorDerivative;
	}
	
	public double getErrorDerivativeOverMovieBounded(HashMap<Integer, Double[]> userMatrix, HashMap<Integer, Double[]> movieMatrix, HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, int q, int l)
	{
		double lqMovie = movieMatrix.get(q)[l];
		
		double errorDerivative = lqMovie * lambdaV;
		
		for (int i : userMatrix.keySet()) {
			if (!movieUserRatings.get(q).containsKey(i)) continue;
			
			double exp = Math.exp((lqMovie * userMatrix.get(i)[l]));
			double gx2 = exp / Math.pow(1 + exp, 2);
			double gx3 = exp / Math.pow(1 + exp, 3);
			
			errorDerivative += gx2;
			errorDerivative -= boundRating(movieUserRatings.get(q).get(i)) * gx3;
			
			if (Double.isNaN(errorDerivative)) {
				System.out.println("FRAK");
				System.out.println(exp);
				System.out.println(gx2);
				System.out.println(gx3);
				System.out.println(userMatrix.get(i)[l]);
				System.out.println(lqMovie);
				System.exit(1);
			}
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
        		movieNorm += Math.pow(userMatrix.get(i)[d], 2);
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
        		double trueRating = boundRating(userRatings.get(i));
        		double predictedRating = boundPrediction(dot(userMatrix.get(i), movieMatrix.get(j)));
        		
        		error += Math.pow(trueRating - predictedRating, 2);
        	}
        }
        
        
        //Get User and Movie norms for regularisation
        double userNorm = 0;
        double movieNorm = 0;
        
        for (int i : userMatrix.keySet()) {
        	for (int d = 0; d < DIMENSION_COUNT; d++) {
        		movieNorm += Math.pow(userMatrix.get(i)[d], 2);
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
		new MovieLensMF().run(10);
	}
	
	public double boundPrediction(double prediction)
	{
		return 1 / (1 + Math.exp(-prediction));
	}
	
	public double boundRating(double rating)
	{
		return (rating - 1) / (K - 1);
	}
}
