package org.nicta.social;

import java.util.HashMap;
import java.util.Random;

/*
 * Test class for learning how probabilistic matrix factorization works
 * 
 * Ported a Python code from http://blog.smellthedata.com/2009/06/netflix-prize-tribute-recommendation.html
 * where the author says that he basically just implemented the PMF paper.
 * 
 * So basically it generates a list of 30 * 100 (users) ratings from a randomly generated 10 dimensional user and item vectors.
 * From the these ratings, it generates user and item vectors of 5 dimensions each for each user and item. To get the predicted
 * rating of a user for a movie item, simply just get the dot product of the user vector and the item vector.
 * 
 * The trick is how these user and item 5d vectors are created.
 * 
 * Apparently it does this with update vectors that have the same number of rows and dimensions as the user and item vectors.
 * 
 * updates_o and updates_d are set to 0 matrices at the start of the update. Then for each rating triple in the training data (user, item, rating)
 * 
 *
 * i = ratingMap[x][0]; //i holds the userIndex
   j = ratingMap[x][1]; //j holds the itemIndex
   rating = ratings[x];
   weight = 1; 
   double r_hat = dot(users[i], items[j]);
    		
   updates_o[i][d] += items[j][d] * (rating - r_hat) * weight;
   updates_d[j][d] += users[i][d] * (rating - r_hat) * weight;
 *  
 *  
 *  
 *  This update matrices are then used to update the user and item vectors like so
 * 
 * double alpha = this.learning_rate;
   double beta = -1 * this.regularization_strength;

   new_users[i][d] = users[i][d] + alpha * (beta * users[i][d] + updates_o[i][d]);
   new_items[i][d] = items[i][d] + alpha * (beta * items[i][d] + updates_d[i][d]);
 *
 * 
 * These are then used to get the new likelihood:
 * 
 * i = ratingMap[x][0]; //i is the userIndex
   j = ratingMap[x][1]; //j is the itemIndex
   rating = ratings[x];
   weight = 1;
        	
   double r_hat = dot(users[i], items[j]);
   sq_error += weight * Math.pow(rating - r_hat, 2);
        
   double L2_norm = 0;
        
   for (int u = 0; u < num_users; u++) {
      for (int z = 0; z < latent_d; z++) {
         L2_norm += Math.pow(users[u][z], 2);	
      }
   }
        
   for (int u = 0; u < num_items; u++) {
      for (int z = 0; z < latent_d; z++) {
         L2_norm += Math.pow(items[u][z], 2);
      }
   }
         
   return -sq_error - (regularization_strength * L2_norm);
 * 
 * The program loops until convergence happens where this likelihood is as minimal as possible.
 * 
 */

public class MF
{
	int latent_d = 5; //number of dimensions of the user and item vector
	double learning_rate = 0.0001; //step size I think?
	double regularization_strength = 0.1; //Regularisation to avoid overfitting
	boolean converged; //flag to see if training is done
	
	//Fake data
	int num_users = 100;
	int num_items = 100;
	int num_ratings = 30;
    
	//Array that holds actual ratings
	double[] ratings;
	//2nd array is of length 2 and [0] holds the user id and [1] holds the item id.
	int[][] ratingMap;
	
	//latent uer vectors
	double[][] users;
	
	//latent item vectors
	double[][] items;
	
	double[][] new_users;
	double[][] new_items;
	
	Random random = new Random();
	
    public static void main(String[] args)
    {
    	new MF().mf();
    }

    /**
     * Dot product convenience method
     * @param d1
     * @param d2
     * @return
     */
    public double dot(double[] d1, double[] d2)
    {
    	double result = 0;

    	for (int x = 0; x < d1.length; x++) {
    		result += d1[x] * d2[x];
    	}

    	return result;
    }

    /**
     * Generate fake data to run matrix factorization on
     * 
     * @return
     */
    public HashMap generateData()
    {
    	int latent_dimension = 10;
    	double noise = 0.25;
    	
    	double[][] users = new double[num_users][latent_dimension];
    	double[][] items = new double[num_items][latent_dimension];
    
    	//Generate the latent user and item vectors
    	for (int i = 0; i < num_users; i++) {
    		for (int x = 0; x < latent_dimension; x++) {
    			users[i][x] = random.nextGaussian();
    		}
    	}

    	for (int i = 0; i < num_items; i++) {
    		for (int x = 0; x < latent_dimension; x++) {
    			items[i][x] = random.nextGaussian();
    		}
    	}

    	//Then from the user vectors generate the ratings.
    	double[] ratings = new double[num_users * num_ratings];
    	int[][] ratingMap = new int[num_users * num_ratings][2];
    	
    	int ratingIndex = 0;
    	
    	for (int u = 0; u < num_users; u++) {
    		int count = 0;

    		while (count < num_ratings) {
    			int i = (int)(Math.random() * num_items);

    			double rating = dot(users[u], items[i]) + (noise * random.nextGaussian());
    		
    			ratingMap[ratingIndex][0] = u;
    			ratingMap[ratingIndex][1] = i;
    			ratings[ratingIndex] = rating;
    			
    			count++;
    			ratingIndex++;
    		}
    	}

    	//Just return the ratings. User and item latent vectors will have to be regenerated from scratch.
    	HashMap data = new HashMap();
    	data.put("ratings", ratings);
    	data.put("ratingMap", ratingMap);
    	
    	return data;
    }


    /**
     * Set things up, given the ratings
     * 
     * @param ratings
     * @param ratingMap
     */
    public void init(double[] ratings, int[][] ratingMap)
    {
        this.ratings = ratings;
        this.ratingMap = ratingMap;
        
        this.converged = false;

        //Create the user latent vectors.
        //new_users are for testing out updates first. Same for new_items
        this.users = new double[this.num_users][this.latent_d];
        this.new_users = new double[this.num_users][this.latent_d];
        
        //We place zero-mean spherical Gaussian priors on user and movie features according to the paper
        for (int x = 0; x < this.num_users; x++) {
        	for (int y = 0; y < this.latent_d; y++) {
        		users[x][y] = random.nextGaussian();
        		new_users[x][y] = random.nextGaussian();
        	}
        }
        
        //Create the item latent vectors
        this.items = new double[this.num_items][this.latent_d];
        this.new_items = new double[this.num_items][this.latent_d];
        
        //We place zero-mean spherical Gaussian priors on user and movie features according to the paper
        for (int x = 0; x < this.num_items; x++) {
        	for (int y = 0; y < this.latent_d; y++) {
        		items[x][y] = random.nextGaussian();
        		new_items[x][y] = random.nextGaussian();
        	}
        }   
    }
        
    /**
     * likelihood appears to be the sum-of-squared-errors objective function in the paper.
     * 
     * @param users
     * @param items
     * @return
     */
    public double likelihood(double[][] users, double[][] items)
    {
        if (users == null) {
            users = this.users;
        }
        
        if (items == null) {
            items = this.items;
        }
            
        double sq_error = 0;
        
        double rating;
    	int i, j;
    	
    	//This calculates the square error
        for (int x = 0; x < ratings.length; x++) {
   			i = ratingMap[x][0]; //i is the userIndex
   			j = ratingMap[x][1]; //j is the itemIndex
   			rating = ratings[x];
        	
        	double r_hat = dot(users[i], items[j]);
        	
        	sq_error += Math.pow(rating - r_hat, 2);
        }
        
        
        //Apparently hold the sum of the L2 norms of the 2 vectors.
        //But aren't we supposed to square root the L2 norm?
        double L2_norm = 0;
        
        for (int u = 0; u < num_users; u++) {
        	for (int z = 0; z < latent_d; z++) {
        		L2_norm += Math.pow(users[u][z], 2);	
        	}
        }
        
        for (int u = 0; u < num_items; u++) {
        	for (int z = 0; z < latent_d; z++) {
        		L2_norm += Math.pow(items[u][z], 2);
        	}
        }
        
        //Hang on, the Frobenius norm is the L2 norm when it's just a 1 column matrix? That's what I got from reading Wikipedia

        return -sq_error - (regularization_strength * L2_norm);
    }
        
    /**
     * Applies the updates on the new_users and new_item vectors
     * Back in update() we check the results and see it it's satisfactory or not.
     * 
     * Part of the gradient descent
     * 
     * @param updates_o
     * @param updates_d
     */
    public void try_updates(double[][] updates_o, double[][] updates_d)
    {
        double alpha = this.learning_rate;
        double beta = -1 * this.regularization_strength;

        
        for (int i = 0; i < num_users; i++) {
            for (int d = 0; d < latent_d; d++) {
                new_users[i][d] = users[i][d] + alpha * (beta * users[i][d] + updates_o[i][d]);
            }
        }
        
        for (int i = 0; i < num_items; i++) {
        	for (int d = 0; d < latent_d; d++) {
                new_items[i][d] = items[i][d] + alpha * (beta * items[i][d] + updates_d[i][d]);
        	}
        }
    }
       
    public void apply_updates()
    {
        for (int i = 0; i < num_users; i++) {
            for (int d = 0; d < latent_d; d++) {
                users[i][d] = new_users[i][d];
            }
        }

        for (int i = 0; i < num_items; i++) {
            for (int d = 0; d < latent_d; d++) {
                items[i][d] = new_items[i][d];
            }
        }
    }
    
    /**
     * Updates the user and item latent vectors.
     * This method is repeatedly called until convergence happens.
     * 
     * This seems to be the Gradient Descent method.
     * 
     * @return
     */
    public boolean update()
    {
    	//wtf are these. and why are they labeled o and d?
    	//they have the same dimensions as the users and items latent vectors.
    	double[][] updates_o = new double[num_users][latent_d];
    	double[][] updates_d = new double[num_items][latent_d];
    	
    	//Make them both zero matrices first.
    	for (int x = 0; x < num_users; x++) {
    		for (int y = 0; y < latent_d; y++) {
    			updates_o[x][y] = 0;
    		}
    	}
    	
    	for (int x = 0; x < num_items; x++) {
    		for (int y = 0; y < latent_d; y++) {
    			updates_d[x][y] = 0;
    		}
    	}
    	
    	
    	double rating;
    	int i, j;
    	
    	for (int x = 0; x < ratings.length; x++) {
    		i = ratingMap[x][0]; //i holds the userIndex
    		j = ratingMap[x][1]; //j holds the itemIndex
    		rating = ratings[x];
    		
    		// r_hat is the predicted rating
    		double r_hat = dot(users[i], items[j]);
    		
    		
    		//Why are we crossing the i's and j's?
    		//(rating-r_hat) is the error
    		for (int d = 0; d < latent_d; d++) {
    			updates_o[i][d] += items[j][d] * (rating - r_hat) /* weight*/;
    			updates_d[j][d] += users[i][d] * (rating - r_hat) /* weight*/;
    		}
    	}
    		
        while (!converged) {
        	//initial likelihood of the user and item vectors.
            double initial_lik = likelihood(null, null);

            System.out.println("  setting learning rate =" + this.learning_rate);
            
            //Check first if the updates are satisfactory.
            try_updates(updates_o, updates_d);

            //Then we get the new likelihood (or error) once the updates have been applied
            double final_lik = likelihood(new_users, new_items);

            //Satisfactory means that the next likelihood is smaller than what it is now
            //Hence, the gradient descent
            if (final_lik > initial_lik) {
            	//if so, apply the updates and make the step size bigger
                apply_updates();
                this.learning_rate *= 1.25;

                //Stop once we hit a limit of the speed of changes
                if (final_lik - initial_lik < .1) {
                    this.converged = true;
                }
               
                break;
            }
            else {
            	//Else, make the learning rate/step size smaller.
                this.learning_rate *= .5;
            }
            
            //Once the learning rate is smaller than a certain size, just stop.
            //We only get here after a few failures in the previous if statement.
            if (this.learning_rate < 1e-10) {
                this.converged = true;
            }
        }

        //Why are returning the reverse of converged?
        //It's false only when we apply updates and the minimum hasn't been reached yet. (So we're returning true)
        //It's true otherwise on all occasions we arrive here (So we're returning false)
        return (!this.converged);
    }
        
    public void print_latent_vectors()
    {
        System.out.println("Users");
        for (int i = 0; i < num_users; i++) {
            System.out.print(i + ": ");
            
            for (int d = 0; d < latent_d; d++) {
                System.out.print(users[i][d] + ", ");
            }
            
            System.out.println("");
        }
            
        System.out.println("Items");
        for (int i = 0; i < num_items; i++) {
            System.out.print(i + ": ");
            
            for (int d = 0; d < latent_d; d++) {
                System.out.print(items[i][d] + ", ");
            }
            
            System.out.println("");
        }
    }
           
    /**
     * Main method
     */
    public void mf()
    {
    	HashMap data = generateData();
    	double[] ratings = (double[])data.get("ratings");
    	int[][] ratingMap = (int[][])data.get("ratingMap");

  
    	init(ratings, ratingMap);
 
    	//What the hell does this loop do?
    	//Basically what we want to do is to remake the user and item vectors right, from the ratings.
    	while (update()) { //Aha! I understand now why we're returning the negative of converged.
    					   //The loop continues as long as we can keep applying updates and
    		               //not reach the minimum yet.
    		
    		//Line does nothing, apparently, just here so we can print out the likelihood.
    		double lik = likelihood(null, null);
			System.out.println("L=" + lik);
    	}
   
    	print_latent_vectors();
    }
}