package org.nicta.social;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;


public class MatlabPMF extends MovieLens
{
	private int train_n;
	final Random RANDOM = new Random();
	
	final String trainSource = "/Users/jino/Desktop/train.txt";
	final String probeSource = "/Users/jino/Desktop/probe.txt";
	
	public static void main(String[] args)
		throws Exception
	{
		new MatlabPMF().run();
	}
	
	public HashMap<Integer, HashMap<Integer, Double>> loadTrainVec()
		throws Exception
	{
		HashMap<Integer, HashMap<Integer, Double>> ratings = new HashMap<Integer, HashMap<Integer, Double>>();
		
		BufferedReader ratingInput = new BufferedReader(new FileReader(trainSource));
		String line = ratingInput.readLine();
		
		while (line != null) {
			String[] tokens = line.split("   ");
			//for (String s : tokens) {
			//	System.out.println("s: " + s);
			//}
			int userId = (int)Double.parseDouble(tokens[1]);
			int itemId = (int)Double.parseDouble(tokens[2]);
			double rating = Float.parseFloat(tokens[3]);
		
			//System.out.println("userId: " + userId + " itemId: " + itemId + " rating: " + rating);
			
			if (! ratings.containsKey(itemId)) {
				ratings.put(itemId, new HashMap<Integer, Double>());
			}
			
			ratings.get(itemId).put(userId, rating);
			
			line = ratingInput.readLine();
			
		}
		ratingInput.close();
		
		return ratings;
	}
	
	public HashMap<Integer[], Double> loadProbeVec()
		throws Exception
	{
		HashMap<Integer[], Double> ratings = new HashMap<Integer[], Double>();
		
		BufferedReader ratingInput = new BufferedReader(new FileReader(probeSource));
		String line = ratingInput.readLine();
		
		while (line != null) {
			String[] tokens = line.split("   ");
			int userId = (int)Double.parseDouble(tokens[1]);
			int itemId = (int)Double.parseDouble(tokens[2]);
			double rating = Float.parseFloat(tokens[3]);

			
			ratings.put(new Integer[]{userId, itemId}, rating);
			
			line = ratingInput.readLine();
			
		}
		ratingInput.close();
		
		return ratings;
	}
	
	public double getAverage(HashMap<Integer, HashMap<Integer, Double>> movieUserRatings)
	{
		double overallRating = 0;
		int n = 0;
		
		for (int movieId : movieUserRatings.keySet()) {
			HashMap<Integer, Double> userRatings = movieUserRatings.get(movieId);
			n += userRatings.size();
			
			for (int userId : userRatings.keySet()) {
				double rating = userRatings.get(userId);
				
				overallRating += rating;
			}
		}
		train_n = n;
		overallRating /= n;
		return overallRating;
	}
	
	public Double[][] getPrior(int row_n, int col_n)
	{
		Double[][] prior = new Double[row_n][col_n];
		
		for (int x = 0; x < row_n; x++) {
			for (int y = 0; y < col_n; y++) {
				prior[x][y] = RANDOM.nextGaussian() * 0.1;
			}
		}
		
		return prior;
	}
	
	public Double[][] getZeros(int row_n, int col_n)
	{
		Double[][] zeros = new Double[row_n][col_n];
		
		for (int x = 0; x < row_n; x++) {
			for (int y = 0; y < col_n; y++) {
				zeros[x][y] = 0.0;
			}
		}
		
		return zeros;
	}
	
	public void run()
		throws Exception
	{
		//rand('state',0); 
		//randn('state',0); 

		//if restart==1 
		double epsilon = 50; //% Learning rate 
		double lambda = .01; //% Regularization parameter 
		double momentum=0.8; 

		int epoch = 1; 
		int maxepoch = 50; 

		int numbatches= 9; //% Number of batches  
		//int num_m = 3952;  //% Number of movies 
		//int num_p = 6040;  //% Number of users 
		int num_feat = 10; //% Rank 10 decomposition 
		
		int num_m = MOVIE_COUNT;
		int num_p = USER_COUNT;  //% Number of users 
		
		///load moviedata % Triplets: {user_id, movie_id, rating} 	
		Object[] data = getMovieUserRatingsAndUserMoviesData();
		HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = (HashMap<Integer, HashMap<Integer, Double>>)data[0];
		//HashMap<Integer, HashMap<Integer, Double>> movieUserRatings = loadTrainVec();
		HashMap<Integer, HashSet<Integer>> userMovies = (HashMap<Integer, HashSet<Integer>>)data[1];
		HashMap<Integer[], Double> testData = getTestData(movieUserRatings, userMovies, new HashSet<Integer[]>());
		//HashMap<Integer[], Double> testData = loadProbeVec();	
		
		System.out.println("Data loaded");
		
		double[][] probe_vec = new double[testData.size()][3];
		int i = 0;
		for (Integer[] key : testData.keySet()) {
			probe_vec[i][0] = key[0];
			probe_vec[i][1] = key[1];
			probe_vec[i][2] = testData.get(key);
			i++;
		}
		System.out.println("probe_vec processed");
		
		double[][] train_vec = new double[RATING_COUNT - probe_vec.length][3];
		i = 0;
		System.out.println("probe_vec: " + probe_vec.length);
		
		for (int movieId : movieUserRatings.keySet()) {
			for (int userId : movieUserRatings.get(movieId).keySet()) {
				train_vec[i][0] = userId;
				train_vec[i][1] = movieId;
				train_vec[i][2] = movieUserRatings.get(movieId).get(userId);
				i++;
			}
		}
		System.out.println("train_vec processed");
		
		double mean_rating = getAverage(movieUserRatings); //mean(train_vec(:,3)); 
		System.out.println("Got average: " + mean_rating);
		
		//int pairs_tr = train_n; //% training data 
		int pairs_pr = testData.size(); // % validation data 

		
		Double[][] w1_M1     = getPrior(num_m, num_feat); //0.1*randn(num_m, num_feat); % Movie feature vectors
		Double[][] w1_P1     = getPrior(num_p, num_feat); //0.1*randn(num_p, num_feat); % User feature vecators
		Double[][] w1_M1_inc = getZeros(num_m, num_feat); //zeros(num_m, num_feat);
		Double[][] w1_P1_inc = getZeros(num_p, num_feat); //zeros(num_p, num_feat);


		int[] aa_p = null;
		int[] aa_m = null;
		double[] rating = null;
		
		double[] err_train = new double[maxepoch];
		double[] err_valid = new double[maxepoch];
		//int N=100000; //% number training triplets per batch 
		int N=10000;
		
		for (epoch = 0; epoch < maxepoch; epoch++) {
			//rr = randperm(pairs_tr);
			//train_vec = train_vec(rr,:);
			//clear rr 

			for (int batch = 0; batch < numbatches; batch++) {
				//System.out.println("epoch " + epoch + " batch " + batch);
				

				int start = batch * N;
				int end = (batch+1) * N;
				
				//System.out.println("Start: " + start);
				//System.out.println("End: " + end);
				//System.out.println("train_vec: " + train_vec.length);
				
				//double(train_vec((batch-1)*N+1:batch*N,1));
				aa_p = new int[end - start]; 
				for (int x = 0; x < aa_p.length; x++) {
					aa_p[x] = (int)train_vec[x + start][0];
				}
					
				//double(train_vec((batch-1)*N+1:batch*N,2))
				aa_m = new int[end - start];
				for (int x = 0; x < aa_m.length; x++) {
					aa_m[x] = (int)train_vec[x + start][1];
				}
				
				//double(train_vec((batch-1)*N+1:batch*N,3));
				rating = new double[end - start];
				for (int x = 0; x < rating.length; x++) {
					rating[x] = train_vec[x + start][2];
				}
				
				//rating = rating-mean_rating; % Default prediction is the mean rating. 
				for (int x = 0; x < rating.length; x++) {
					rating[x] -= mean_rating;
				}
				

				/*%%%%%%%%%%%%%% Compute Predictions %%%%%%%%%%%%%%%%%*/
				//pred_out = sum(w1_M1(aa_m,:).*w1_P1(aa_p,:),2);
				double[] pred_out = new double[aa_m.length];
				for (int x = 0; x < pred_out.length; x++) {
					pred_out[x] = dot(w1_M1[aa_m[x]-1], w1_P1[aa_p[x]-1]);
					//System.out.println("pred_out: " + pred_out[x]);
				}
				
				
				//f = sum( (pred_out - rating).^2 + ...
				//		0.5*lambda*( sum( (w1_M1(aa_m,:).^2 + w1_P1(aa_p,:).^2),2)));
				double f = 0;
				for (int x = 0; x < pred_out.length; x++) {
					f += Math.pow(rating[x] - pred_out[x], 2);
					
					double regularization = 0;
					for (int y = 0; y < num_feat; y++) {
						regularization += Math.pow(w1_M1[aa_m[x]-1][y], 2);
						regularization += Math.pow(w1_P1[aa_p[x]-1][y], 2);
					}
					
					f += 0.5*lambda*regularization;
				}
				//System.out.println("f: " + f);
				
				/*%%%%%%%%%%%%%% Compute Gradients %%%%%%%%%%%%%%%%%%%*/
				//IO = repmat(2*(pred_out - rating),1,num_feat);
				double[] IO = new double[pred_out.length];
				for (int x = 0; x < pred_out.length; x++) {
					IO[x] = 2 * (pred_out[x] - rating[x]);
					//System.out.println("IO[x]: " + IO[x]);
				}
				
				//Ix_m=IO.*w1_P1(aa_p,:) + lambda*w1_M1(aa_m,:);
				//Ix_p=IO.*w1_M1(aa_m,:) + lambda*w1_P1(aa_p,:);
				//System.out.println("aa_m: " + aa_m.length);
				//System.out.println("aa_p: " + aa_p.length);
				double[][] Ix_p = new double[aa_p.length][num_feat];
				double[][] Ix_m = new double[aa_p.length][num_feat];
				for (int x = 0; x < aa_p.length; x++) {
					for (int y = 0; y < num_feat; y++) {
						Ix_m[x][y] = IO[x] * w1_P1[aa_p[x]-1][y] + lambda * w1_M1[aa_m[x]-1][y];
						Ix_p[x][y] = IO[x] * w1_M1[aa_m[x]-1][y] + lambda * w1_P1[aa_p[x]-1][y];
						//System.out.print(Ix_p[x][y] + " ");
					}
					//System.out.println("");
				}
				
				double[][] dw1_M1 = new double[num_m][num_feat]; //zeros(num_m,num_feat);
				for (int x = 0; x < num_m; x++) {
					for (int y = 0; y < num_feat; y++) {
						dw1_M1[x][y] = 0;
					}
				}
				double[][] dw1_P1 = new double[num_p][num_feat]; //zeros(num_p,num_feat);
				for (int x = 0; x < num_p; x++) {
					for (int y = 0; y < num_feat; y++) {
						dw1_P1[x][y] = 0;
					}
				}
				
				//System.out.println("num_m: " + num_m);
				//System.out.println("num_p: " + num_p);
				
				for (int x = 0; x < N; x++) {
					//dw1_M1(aa_m(ii),:) =  dw1_M1(aa_m(ii),:) +  Ix_m(ii,:);
					for (int y = 0; y < num_feat; y++) {
						dw1_M1[aa_m[x]-1][y] = dw1_M1[aa_m[x]-1][y] + Ix_m[x][y];
					}
				}
				
				for (int x = 0; x < N; x++) {
					//dw1_P1(aa_p(ii),:) =  dw1_P1(aa_p(ii),:) +  Ix_p(ii,:);	
					for (int y = 0; y < num_feat; y++) {
						dw1_P1[aa_p[x]-1][y] = dw1_P1[aa_p[x]-1][y] + Ix_p[x][y];
					}
				}
				
				/*%%%% Update movie and user features %%%%%%%%%%%*/
				//w1_M1_inc = momentum*w1_M1_inc + epsilon*dw1_M1/N;
				for (int x = 0; x < w1_M1_inc.length; x++) {
					for (int y = 0; y < num_feat; y++) {
						w1_M1_inc[x][y] = momentum * w1_M1_inc[x][y] + (epsilon * dw1_M1[x][y]/N);
					}
					
				}
				//w1_M1 =  w1_M1 - w1_M1_inc;
				for (int x = 0; x < w1_M1.length; x++) {
					for (int y = 0; y < num_feat; y++) {
						w1_M1[x][y] -=  w1_M1_inc[x][y];
					}
				}
				
				//w1_P1_inc = momentum*w1_P1_inc + epsilon*dw1_P1/N;
				for (int x = 0; x < w1_P1_inc.length; x++) {
					for (int y = 0; y < num_feat; y++) {
						w1_P1_inc[x][y] = momentum*w1_P1_inc[x][y] + epsilon*dw1_P1[x][y]/N;
					}
				}
				
				//w1_P1 =  w1_P1 - w1_P1_inc;
				for (int x = 0; x < w1_P1.length; x++) {
					for (int y = 0; y < num_feat; y++) {
						w1_P1[x][y] =  w1_P1[x][y] - w1_P1_inc[x][y];
					}
				}
			}

			/*%%%%%%%%%%%%%% Compute Predictions after Paramete Updates %%%%%%%%%%%%%%%%%*/
			//pred_out = sum(w1_M1(aa_m,:).*w1_P1(aa_p,:),2);
			double[] pred_out = new double[aa_m.length];
			for (int x = 0; x < pred_out.length; x++) {
				pred_out[x] = dot(w1_M1[aa_m[x]-1], w1_P1[aa_p[x]-1]);
			}
			
			
			//f_s = sum( (pred_out - rating).^2 + ...
	        //		0.5*lambda*( sum( (w1_M1(aa_m,:).^2 + w1_P1(aa_p,:).^2),2)));
			double f_s = 0;
			for (int x = 0; x < pred_out.length; x++) {
				f_s += Math.pow(rating[x] - pred_out[x], 2);
				
				double regularization = 0;
				for (int y = 0; y < num_feat; y++) {
					regularization += Math.pow(w1_M1[aa_m[x]-1][y], 2);
					regularization += Math.pow(w1_P1[aa_p[x]-1][y], 2);
				}
				
				f_s += 0.5*lambda*regularization;
			}
			
			err_train[epoch] = Math.sqrt(f_s/N);

			/*%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%*/
			/*%%% Compute predictions on the validation set %%%%%%%%%%%%%%%%%%%%%%*/ 
			int NN = pairs_pr;

			//aa_p = double(probe_vec(:,1));
			aa_p = new int[probe_vec.length]; 
			for (int x = 0; x < aa_p.length; x++) {
				aa_p[x] = (int)probe_vec[x][0];
			}
				
			//aa_m = double(probe_vec(:,2));
			aa_m = new int[probe_vec.length];
			for (int x = 0; x < aa_m.length; x++) {
				aa_m[x] = (int)probe_vec[x][1];
			}
			
			//rating = double(probe_vec(:,3));
			rating = new double[probe_vec.length];
			for (int x = 0; x < rating.length; x++) {
				rating[x] = probe_vec[x][2];
			}
			
			//pred_out = sum(w1_M1(aa_m,:).*w1_P1(aa_p,:),2) + mean_rating;
			pred_out = new double[aa_m.length];
			for (int x = 0; x < pred_out.length; x++) {
				pred_out[x] = dot(w1_M1[aa_m[x]-1], w1_P1[aa_p[x]-1]) + mean_rating;
			}
			
			//ff = find(pred_out>5); pred_out(ff)=5; % Clip predictions 
			//ff = find(pred_out<1); pred_out(ff)=1;
			for (int x = 0; x < pred_out.length; x++) {
				if (pred_out[x] > 5) pred_out[x] = 5;
				if (pred_out[x] < 1) pred_out[x] = 1;
			}
			
			//err_valid(epoch) = sqrt(sum((pred_out- rating).^2)/NN);
			double err = 0;
			for (int x = 0; x < pred_out.length; x++) {
				err += Math.pow(pred_out[x] - rating[x], 2);
			}
			err_valid[epoch] = Math.sqrt(err / NN);
			
			System.out.println("epoch " + epoch + " Training RMSE " + err_train[epoch] + "  Test RMSE " + err_valid[epoch]);
			/*%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%*/

			//if ((rem(epoch,10))==0) {
				//save pmf_weight w1_M1 w1_P1
			//}
		}
	}
}
