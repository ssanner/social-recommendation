package org.nicta.social;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

// TODO: A good way to initialize LBFGS, vice versa?
//       Training column-by-column (effectively residual method)
//       EP?
public class BayesianFeatureMF extends MovieLens
{
	static final public double MIN_VARIANCE = 1e-4d;
	static final public boolean USE_ITEM_NORM = false;
	static final public double PRIOR_VARIANCE = 200d;
	static final public double BETA = 2.5d;
	static final public int BAYESIAN_UPDATE_ITER = 10;
	
	final int DIMENSION_COUNT = 5; 
	final Random RANDOM = new Random();
	final double STEP_CONVERGENCE = 1e-2;
	
	double lambdaU = 10;
	double lambdaV = 10; 
	double totalAverage;
	HashMap<Integer, Double> itemAverages;
	HashMap<Integer, Double> userAverages;
	
	public void run(int k)
		throws Exception
	{	
		long start = System.currentTimeMillis();
		
		System.out.println("Get Data");
		Object[] data = getMovieUserRatingsAndUserMoviesData();
		HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = (HashMap<Integer, HashMap<Integer, Double>>)data[0];
		HashMap<Integer, HashSet<Integer>> userMovies = (HashMap<Integer, HashSet<Integer>>)data[1];
		
		HashSet<Integer[]> added = new HashSet<Integer[]>();
		
		System.out.println("Got ratings");
		HashMap<Integer, Double[]> userFeatures = getUserFeatures();
		System.out.println("Got user features");
		HashMap<Integer, Double[]> movieFeatures = getMovieFeatures();
		System.out.println("Got movie features");
		
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
							HashMap<Integer[], Double> testData, HashMap<Integer, Double[]> userFeatures, HashMap<Integer, Double[]> movieFeatures)
		throws Exception
	{
		//Fill priors
		Double[][] userMatrix  = getPriorMean(USER_FEATURE_COUNT + USER_COUNT);
		Double[][] movieMatrix = getPriorMean(MOVIE_FEATURE_COUNT + MOVIE_COUNT);
		
		Double[][] userVar  = getPriorVar(USER_FEATURE_COUNT + USER_COUNT);
		Double[][] movieVar = getPriorVar(MOVIE_FEATURE_COUNT + MOVIE_COUNT);
		
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
				
				if (USE_ITEM_NORM)
					norms.put(userId, unnormalized.get(userId) - itemAverage);
				else
					norms.put(userId, unnormalized.get(userId) - userAverage);
			}
		}
		
		// Learning via Bayesian updates
		bayesianUpdate(normalizedRatings, userMatrix, movieMatrix, userVar, movieVar, userFeatures, movieFeatures, /*userTraits, movieTraits*/ testData, userMovies);

		HashMap<Integer, Double[]> userTraits = getTraitVectors(userMatrix, userFeatures);
		HashMap<Integer, Double[]> movieTraits = getTraitVectors(movieMatrix, movieFeatures);
		
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

			if (USE_ITEM_NORM)
				prediction += itemAverage;
			else
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

	public HashMap<Integer, Double[]> getTraitVectors(Double[][] matrix, HashMap<Integer, Double[]> features)
	{
		HashMap<Integer, Double[]> traitVectors = new HashMap<Integer, Double[]>();
		
		for (int id : features.keySet()) {
			Double[] feature = features.get(id);
			Double[] vector = new Double[DIMENSION_COUNT];
			
			for (int x = 0; x < DIMENSION_COUNT; x++) {
				vector[x] = 0.0;
				
				for (int y = 0; y < feature.length; y++) {
					vector[x] += matrix[x][y] * feature[y];
				}
				
				vector[x] += matrix[x][feature.length + (id-1)];
			}
			
			traitVectors.put(id, vector);
		}
		
		return traitVectors;
	}
	
	public Double[] getTraitVector(Double[][] matrix, Double[] feature, int id)
	{
		//Double[] feature = features.get(id);
		Double[] vector = new Double[DIMENSION_COUNT];
		
		for (int x = 0; x < DIMENSION_COUNT; x++) {
			vector[x] = 0.0;
			
			for (int y = 0; y < feature.length; y++) {
				vector[x] += matrix[x][y] * feature[y];
			}
			
			vector[x] += matrix[x][feature.length + (id-1)];
		}
			
		return vector;
	}
	
	public double predict(Double[] userTraitVector, Double[] movieTraitVector)
	{
		double prediction = 0;
		
		for (int x = 0; x < DIMENSION_COUNT; x++) {
			prediction += userTraitVector[x] * movieTraitVector[x];
		}
		
		return prediction;
	}
	
	public void bayesianUpdate(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings, Double[][] userMatrix, Double[][] movieMatrix, 
							Double[][] userVar, Double[][] movieVar,
							HashMap<Integer, Double[]> userFeatures, HashMap<Integer, Double[]> movieFeatures, 
							//HashMap<Integer, Double[]> userTraits, HashMap<Integer, Double[]> movieTraits,
							HashMap<Integer[], Double> testData, HashMap<Integer, HashSet<Integer>> userMovies)
		throws Exception
	{
		for (int iter = 0; iter < BAYESIAN_UPDATE_ITER; iter++) {
			
			System.out.println("Bayesian update iteration: " + iter);
			//iterations++;
					
			int num_ratings = 0;
	        for (int j : movieUserRatings.keySet()) {
	        	HashMap<Integer, Double> userRatings = movieUserRatings.get(j);
	        	
	        	for (int i : userRatings.keySet()) {
	        		double true_rating = userRatings.get(i);
	        		//double predicted_rating = predict(userTraits.get(i), movieTraits.get(j));	        		
	        		//error += Math.pow(trueRating - predictedRating, 2);
	        		
        			bayUpdateU(userMatrix, userVar, movieMatrix, movieVar, 
							userFeatures.get(i), i, movieFeatures.get(j), j, 
							true_rating);
	        			
					bayUpdateV(userMatrix, userVar, movieMatrix, movieVar, 
							userFeatures.get(i), i, movieFeatures.get(j), j, 
							true_rating);
					
					//if (++num_ratings % 1000 == 0) 
					//	System.out.println("Completed " + num_ratings + " updates!");
	        	}
	        }

			// Diagnostics
			System.out.println("Iterations: " + iter);
			HashMap<Integer, Double[]> userTraits = getTraitVectors(userMatrix, userFeatures);
			HashMap<Integer, Double[]> movieTraits = getTraitVectors(movieMatrix, movieFeatures);
			double error = getError(userMatrix, movieMatrix, userTraits, movieTraits, movieUserRatings);
			double evalRMSE = calculateRMSE(testData, userMatrix, movieMatrix, userTraits, movieTraits);
			double evalMAE  = calculateMAE(testData, userMatrix, movieMatrix, userTraits, movieTraits);
			System.out.println("New Error: " + error);
			System.out.println("RMSE: " + evalRMSE);
			System.out.println("MAE:  " + evalMAE);
			System.out.println();
		}
	}
	
	public void bayUpdateU(
			Double[][] userMatrix, Double[][] userVar, 
			Double[][] movieMatrix, Double[][] movieVar, 
			Double[] userFeatures, int user_id, 
			Double[] movieFeatures, int movie_id,
			double actual_rating)
	{
		// Get latent projections -- note that we do not update these after each U update
		//                           (this should introduce less ordering bias)
		Double[] mu_sk  = getTraitVector(userMatrix, userFeatures, user_id);
		Double[] var_sk = getTraitVector(userVar, userFeatures, user_id);
		Double[] tk = getTraitVector(movieMatrix, movieFeatures, movie_id);

		// Precompute shared messages
		double[] mu_zk = new double[DIMENSION_COUNT];
		double[] var_zk = new double[DIMENSION_COUNT];
		double sum_mu_zk = 0d;
		double sum_var_zk = 0d;
		for (int k = 0; k < DIMENSION_COUNT; k++) {
			mu_zk[k] = tk[k] * mu_sk[k];
			var_zk[k] = tk[k] * tk[k] * var_sk[k];
			
			sum_mu_zk += mu_zk[k];
			sum_var_zk += var_zk[k];
		}

		// Update each U[k][feature_index]
		for (int k = 0; k < DIMENSION_COUNT; k++) {
			double mu_sk_ret  = (actual_rating - (sum_mu_zk - mu_zk[k])) / tk[k];
			double var_sk_ret = (BETA*BETA + (sum_var_zk - var_zk[k])) / (tk[k] * tk[k]);
			
			// Get relevant dimensions for this user_id
			for (int l = 0; l < USER_FEATURE_COUNT + 1; l++) {
				int feature_index = -1;
				if (l < USER_FEATURE_COUNT) {
					if (userFeatures[l] == 0d)
						continue; // This feature is zero, no need to update

					if (userFeatures[l] != 1d) {
						System.out.println("ERROR: User feature[" + l + "] is " + userFeatures[l] + " -- not 0 or 1");
						System.exit(1);
					}
					feature_index = l;

				} else {
					// int userId = y - USER_FEATURE_COUNT + 1;
					feature_index = USER_FEATURE_COUNT + user_id - 1;
				}

				// Compute update -- should we delay these?  Probably won't matter
				// since this feature_index is only used *here* given the way that
				// updates are subtracted off.				
				double mu1  = userMatrix[k][feature_index];
				double var1 = userVar[k][feature_index];

				double mu2  = mu_sk_ret - (mu_sk[k] - userMatrix[k][feature_index]);
				double var2 = var_sk_ret + (var_sk[k] - userVar[k][feature_index]);

				userMatrix[k][feature_index] = ((mu1 * var2) + (mu2 * var1)) / (var1 + var2);
				userVar[k][feature_index] = 1d / ((1d / var1) + (1d / var2));
				
				//if (true) continue;
				if (Math.abs(userMatrix[k][feature_index]) > 100d ||
					Math.abs(userVar[k][feature_index]) > 100d ||
					Math.abs(userVar[k][feature_index]) < MIN_VARIANCE) {
					//System.err.println("WARNING:  Mu U[" + k + "][" + feature_index + "] = " + Math.abs(userMatrix[k][feature_index]));
					//System.err.println("         Var U[" + k + "][" + feature_index + "] = " + Math.abs(userVar[k][feature_index]));
					
					if (Math.abs(userVar[k][feature_index]) < MIN_VARIANCE)
						userVar[k][feature_index] = MIN_VARIANCE;
				}
			}
		}
	}
	
	public void bayUpdateV(
			Double[][] userMatrix, Double[][] userVar, 
			Double[][] movieMatrix, Double[][] movieVar, 
			Double[] userFeatures, int user_id, 
			Double[] movieFeatures, int movie_id,
			double actual_rating)
	{
		// Get latent projections -- note that we do not update these after each U update
		//                           (this should introduce less ordering bias)
		Double[] mu_tk  = getTraitVector(movieMatrix, movieFeatures, movie_id);
		Double[] var_tk = getTraitVector(movieVar, movieFeatures, movie_id);
		Double[] sk = getTraitVector(userMatrix, userFeatures, user_id);

		// Precompute shared messages
		double[] mu_zk = new double[DIMENSION_COUNT];
		double[] var_zk = new double[DIMENSION_COUNT];
		double sum_mu_zk = 0d;
		double sum_var_zk = 0d;
		for (int k = 0; k < DIMENSION_COUNT; k++) {
			mu_zk[k] = sk[k] * mu_tk[k];
			var_zk[k] = sk[k] * sk[k] * var_tk[k];
			
			sum_mu_zk += mu_zk[k];
			sum_var_zk += var_zk[k];
		}

		// Update each V[k][feature_index]
		for (int k = 0; k < DIMENSION_COUNT; k++) {
			double mu_tk_ret  = (actual_rating - (sum_mu_zk - mu_zk[k])) / sk[k];
			double var_tk_ret = (BETA*BETA + (sum_var_zk - var_zk[k])) / (sk[k] * sk[k]);
			
			// Get relevant dimensions for this movie_id
			for (int l = 0; l < MOVIE_FEATURE_COUNT + 1; l++) {
				int feature_index = -1;
				if (l < MOVIE_FEATURE_COUNT) {
					if (movieFeatures[l] == 0d)
						continue; // This feature is zero, no need to update

					if (movieFeatures[l] != 1d) {
						System.out.println("ERROR:Movie feature[" + l + "] is " + movieFeatures[l] + " -- not 0 or 1");
						System.exit(1);
					}
					feature_index = l;

				} else {
					feature_index = MOVIE_FEATURE_COUNT + movie_id - 1;
				}

				// Compute update -- should we delay these?  Probably won't matter
				// since this feature_index is only used *here* given the way that
				// updates are subtracted off.				
				double mu1  = movieMatrix[k][feature_index];
				double var1 = movieVar[k][feature_index];

				double mu2  = mu_tk_ret - (mu_tk[k] - movieMatrix[k][feature_index]);
				double var2 = var_tk_ret + (var_tk[k] - movieVar[k][feature_index]);

				movieMatrix[k][feature_index] = ((mu1 * var2) + (mu2 * var1)) / (var1 + var2);
				movieVar[k][feature_index] = 1d / ((1d / var1) + (1d / var2));
				
				if (Math.abs(movieMatrix[k][feature_index]) > 100d ||
					Math.abs(movieVar[k][feature_index]) > 100d ||
					Math.abs(movieVar[k][feature_index]) < MIN_VARIANCE) {
						//System.err.println("WARNING:  Mu V[" + k + "][" + feature_index + "] = " + Math.abs(movieMatrix[k][feature_index]));
						//System.err.println("         Var V[" + k + "][" + feature_index + "] = " + Math.abs(movieVar[k][feature_index]));
						
						if (Math.abs(movieVar[k][feature_index]) < MIN_VARIANCE)
							movieVar[k][feature_index] = MIN_VARIANCE;
				}
			}
		}
	}

	
	public Double[][] getPriorMean(int feature_count)
	{
		Double[][] prior = new Double[DIMENSION_COUNT][feature_count];
		
		for (int x = 0; x < DIMENSION_COUNT; x++) {
			for (int y = 0; y < feature_count; y++) {
				prior[x][y] = RANDOM.nextGaussian();
			}
		}
		
		return prior;
	}
	
	public Double[][] getPriorVar(int feature_count)
	{
		Double[][] priorVar = new Double[DIMENSION_COUNT][feature_count];
		
		for (int x = 0; x < DIMENSION_COUNT; x++) {
			for (int y = 0; y < feature_count; y++) {
				priorVar[x][y] = PRIOR_VARIANCE;
			}
		}
		
		return priorVar;
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
        	for (int y = 0; y < MOVIE_FEATURE_COUNT; y++) {
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
		//new BayesianFeatureMF().run(10);
		new BayesianFeatureMF().run(1);
	}
	
	public double calculateMAE(HashMap<Integer[], Double> data,
			Double[][] userMatrix, Double[][] movieMatrix,
			HashMap<Integer, Double[]> userTraits,
			HashMap<Integer, Double[]> movieTraits) {
		double se = 0;

		for (Integer[] test : data.keySet()) {
			int testUserId = test[0];
			int testMovieId = test[1];

			double testRating = data.get(test);
			double prediction = predict(userTraits.get(testUserId), movieTraits
					.get(testMovieId));

			double itemAverage = itemAverages.containsKey(testMovieId) ? itemAverages
					.get(testMovieId)
					: totalAverage;
			double userAverage = userAverages.containsKey(testUserId) ? userAverages
					.get(testUserId)
					: totalAverage;

			prediction += userAverage;

			if (prediction > 5)
				prediction = 5;
			if (prediction < 1)
				prediction = 1;

			se += Math.abs(prediction - testRating); //Math.pow((prediction - testRating), 2);
		}

		double mse = se / (double) data.size();
		return mse; //Math.sqrt(mse);
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
