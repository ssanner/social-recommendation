package org.nicta.lr.recommender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.Map;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.UserUtil;
import org.nicta.lr.util.SQLUtil;
import org.nicta.social.LBFGS;

public abstract class MFRecommender extends Recommender 
{
	String type = "";
	
	Double[][] userFeatureMatrix;
	Double[][] linkFeatureMatrix;
	Map<Long, Double[]>userIdColumns;
	Map<Long, Double[]>linkIdColumns;
	
	Map<Long, Map<Long, Double>> friendConnections;
	
	public MFRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friendships)
	{
		super(linkLikes, userFeatures, linkFeatures, friendships);
	}
	
	public void initializePriors(Set<Long> users, Set<Long> links)
	{
		userFeatureMatrix = getPrior(Constants.USER_FEATURE_COUNT);
		linkFeatureMatrix = getPrior(Constants.LINK_FEATURE_COUNT);
		userIdColumns = getMatrixIdColumns(userFeatures.keySet());
		linkIdColumns = getMatrixIdColumns(linkFeatures.keySet());
	}
	
	public void loadData()
		throws SQLException
	{
		userFeatureMatrix = loadFeatureMatrix("lrUserMatrix", Constants.USER_FEATURE_COUNT, type);
		linkFeatureMatrix = loadFeatureMatrix("lrLinkMatrix", Constants.LINK_FEATURE_COUNT, type);
		userIdColumns = loadIdColumns("lrUserMatrix", type);
		linkIdColumns = loadIdColumns("lrLinkMatrix", type);
	}
	
	/**
	 * For creation of the latent matrices.
	 * 
	 * @param featureCount
	 * @return
	 */
	public Double[][] getPrior(int featureCount)
	{
		Random random = new Random();
		
		Double[][] prior = new Double[Constants.K][featureCount];
		
		for (int x = 0; x < Constants.K; x++) {
			for (int y = 0; y < featureCount; y++) {
				prior[x][y] = random.nextGaussian();
				//prior[x][y] = 0.0;
			}
		}
		
		return prior;
	}
	

	public double minimizeByLBFGS(Map<Long, Set<Long>> userLinkSamples)
	{
		boolean go = true;	
		int iterations = 0;
		
		int userVars = Constants.K * (Constants.USER_FEATURE_COUNT + userLinkSamples.size());
		int linkVars = Constants.K * (Constants.LINK_FEATURE_COUNT + linkFeatures.size());
		
		Object[] userKeys = userLinkSamples.keySet().toArray();
		Object[] linkKeys = linkFeatures.keySet().toArray();
		
		int[] iprint = {0,0};
		int[] iflag = {0};
		double[] diag = new double[userVars + linkVars];
		
		for (int x = 0; x < diag.length; x++) {
			diag[x] = 0;
		}
		
		double oldError = Double.MAX_VALUE;
		double rmse = 0;
		
		while (go) {
			iterations++;
			
			Map<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
			
			Map<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);

			System.out.println("Getting Connections");
			Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
			System.out.println("Getting Predictions");
			Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
			
			Double[][] userDerivative = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
			HashMap<Long, Double[]> userIdDerivative = new HashMap<Long, Double[]>();
			Double[][] linkDerivative = new Double[Constants.K][Constants.LINK_FEATURE_COUNT];
			HashMap<Long, Double[]> linkIdDerivative = new HashMap<Long, Double[]>();
			
			System.out.println("Iterations: " + iterations);
		
			//Get user derivatives
			System.out.println("Get user derivatives");
			for (int k = 0; k < Constants.K; k++) {
				System.out.println("K: " + k);
				for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
					userDerivative[k][l] = getErrorDerivativeOverUserAttribute(linkTraits, predictions, connections, k, l);
				}
				
				for (long userId : userLinkSamples.keySet()) {
					if (!userIdDerivative.containsKey(userId)) {
						userIdDerivative.put(userId, new Double[Constants.K]);
					}
					
					userIdDerivative.get(userId)[k] = getErrorDerivativeOverUserId(linkTraits, predictions, connections, k, userId);
				}
			}
			
			//Get link derivatives
			System.out.println("Get link derivatives");
			for (int q = 0; q < Constants.K; q++) {
				System.out.println("K: " + q);
				for (int l = 0; l < Constants.LINK_FEATURE_COUNT; l++) {
					linkDerivative[q][l] = getErrorDerivativeOverLinkAttribute(userTraits, predictions, q, l);
				}
				System.out.println("Done features");
				for (long linkId : linkIdColumns.keySet()) {
					if (!linkIdDerivative.containsKey(linkId)) {
						linkIdDerivative.put(linkId, new Double[Constants.K]);
					}
									
					linkIdDerivative.get(linkId)[q] = getErrorDerivativeOverLinkId(userTraits, predictions, q, linkId);
				}
				System.out.println("Done ids");
			}
		
			double[] variables = new double[userVars + linkVars];
			System.out.println("Variables: " + variables.length);
			
			int index = 0;
			
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
					variables[index++] = userFeatureMatrix[x][y];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdColumns.get(userId);
				for (double d : column) {
					variables[index++] = d;
				}
			}
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
					variables[index++] = linkFeatureMatrix[x][y];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdColumns.get(linkId);
				for (double d : column) {
					variables[index++] = d;
				}
			}
			
			
			System.out.println("derivatives");
			double[] derivatives = new double[userVars + linkVars];
			index = 0;
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
					derivatives[index++] = userDerivative[x][y];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdDerivative.get(userId);
				for (double d : column) {
					derivatives[index++] = d;
				}
			}
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
					derivatives[index++] = linkDerivative[x][y];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdDerivative.get(linkId);
				for (double d : column) {
					derivatives[index++] = d;
				}
			}
			
			double error = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
			
			System.out.println("New Error: " + error);
			System.out.println("");
		
			try {
				LBFGS.lbfgs(variables.length, 5, variables, error, derivatives,
						false, diag, iprint, Constants.STEP_CONVERGENCE,
						1e-15, iflag);
			}
			catch (LBFGS.ExceptionWithIflag f) {
				f.printStackTrace();
			}
			
			System.out.println("Setting again");
			index = 0;
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
					userFeatureMatrix[x][y] = variables[index++];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdColumns.get(userId);
				for (int d = 0; d < column.length; d++) {
					column[d] = variables[index++];
				}
			}
			for (int x = 0; x < Constants.K; x++) {
				for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
					linkFeatureMatrix[x][y] = variables[index++];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdColumns.get(linkId);
				for (int d = 0; d < column.length; d++) {
					column[d] = variables[index++];
				}
			}
			
			if (iflag[0] == 0 || Math.abs(oldError - error) < Constants.STEP_CONVERGENCE) go = false;
		
			oldError = error;
		}
		
		return rmse;
	}
	
	public double minimizeByLineSearch(Map<Long, Set<Long>> userLinkSamples)
	{
		boolean converged = false;	
		int iterations = 0;
		
		double stepSize = Constants.STEP_SIZE;
		int count = 0;
		double lastGoodError = 0;
		
		Double[][] lastGoodUserMatrix = null;
		Double[][] lastGoodLinkMatrix = null; 
		Map<Long, Double[]> lastGoodUserIdColumns = null;
		Map<Long, Double[]> lastGoodLinkIdColumns = null;
		Map<Long, Double[]> lastGoodUserTraits = null;
		Map<Long, Double[]> lastGoodLinkTraits = null;
		Map<Long, Map<Long, Double>> lastGoodConnections = null;
		Map<Long, Map<Long, Double>> lastGoodPredictions = null;
		
		Map<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		Map<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
		Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
		Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
		
		double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
		
		while (!converged && iterations <= 500) {
			Double[][] userDerivative = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
			HashMap<Long, Double[]> userIdDerivative = new HashMap<Long, Double[]>();
			Double[][] linkDerivative = new Double[Constants.K][Constants.LINK_FEATURE_COUNT];
			HashMap<Long, Double[]> linkIdDerivative = new HashMap<Long, Double[]>();
			
			//Get user derivatives
			//System.out.println("Get user derivatives");
			for (int k = 0; k < Constants.K; k++) {
				//System.out.println("K: " + k);
				for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
					userDerivative[k][l] = getErrorDerivativeOverUserAttribute(linkTraits, predictions, connections, k, l);
				}
				
				for (long userId : userLinkSamples.keySet()) {
					if (!userIdDerivative.containsKey(userId)) {
						userIdDerivative.put(userId, new Double[Constants.K]);
					}
					
					userIdDerivative.get(userId)[k] = getErrorDerivativeOverUserId(linkTraits, predictions, connections, k, userId);
				}
			}
			
			//Get link derivatives
			//System.out.println("Get link derivatives");
			for (int q = 0; q < Constants.K; q++) {
				//System.out.println("K: " + q);
				for (int l = 0; l < Constants.LINK_FEATURE_COUNT; l++) {
					linkDerivative[q][l] = getErrorDerivativeOverLinkAttribute(userTraits, predictions, q, l);
				}
				//System.out.println("Done features");
				
				for (long linkId : linkIdColumns.keySet()) {
					if (!linkIdDerivative.containsKey(linkId)) {
						linkIdDerivative.put(linkId, new Double[Constants.K]);
					}
					
					linkIdDerivative.get(linkId)[q] = getErrorDerivativeOverLinkId(userTraits, predictions, q, linkId);
				}
				//System.out.println("Done ids");
			}
			
			boolean go = true;
			
			while (go) {
				System.out.println("Step size: " + stepSize);
				
				Double[][] updatedUserMatrix = new Double[Constants.K][Constants.USER_FEATURE_COUNT];
				Double[][] updatedLinkMatrix = new Double[Constants.K][Constants.LINK_FEATURE_COUNT]; 
				HashMap<Long, Double[]> updatedUserIdColumns = new HashMap<Long, Double[]>();
				HashMap<Long, Double[]> updatedLinkIdColumns = new HashMap<Long, Double[]>();
			
				for (int k = 0; k < Constants.K; k++) {
					//System.out.println("K: " + k);
					for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
						updatedUserMatrix[k][l] = userFeatureMatrix[k][l] - (stepSize * userDerivative[k][l]);
					}
				
					for (long userId : userLinkSamples.keySet()) {
						if (!updatedUserIdColumns.containsKey(userId)) {
							updatedUserIdColumns.put(userId, new Double[Constants.K]);
						}
					
						updatedUserIdColumns.get(userId)[k] = userIdColumns.get(userId)[k] - stepSize * userIdDerivative.get(userId)[k];
					}
				}
			
				//Get link derivatives
				//System.out.println("Get link derivatives");
				for (int q = 0; q < Constants.K; q++) {
					//System.out.println("K: " + q);
					for (int l = 0; l < Constants.LINK_FEATURE_COUNT; l++) {
						updatedLinkMatrix[q][l] = linkFeatureMatrix[q][l] - stepSize * linkDerivative[q][l];
					}
					//System.out.println("Done features");
				
					for (long linkId : linkIdColumns.keySet()) {
						if (!updatedLinkIdColumns.containsKey(linkId)) {
							updatedLinkIdColumns.put(linkId, new Double[Constants.K]);
						}
					
						updatedLinkIdColumns.get(linkId)[q] = linkIdColumns.get(linkId)[q] - stepSize * linkIdDerivative.get(linkId)[q];
					}
					//System.out.println("Done ids");
				}
			
				Map<Long, Double[]> updatedUserTraits = UserUtil.getUserTraitVectors(updatedUserMatrix, updatedUserIdColumns, userFeatures);
				Map<Long, Double[]> updatedLinkTraits = LinkUtil.getLinkTraitVectors(updatedLinkMatrix, updatedLinkIdColumns, linkFeatures);
				Map<Long, Map<Long, Double>> updatedConnections = getConnections(updatedUserMatrix, updatedUserIdColumns, userFeatures, userLinkSamples);
				Map<Long, Map<Long, Double>> updatedPredictions = getPredictions(updatedUserTraits, updatedLinkTraits, userLinkSamples);
			
				double newError = getError(updatedUserMatrix, updatedLinkMatrix, updatedUserIdColumns, updatedLinkIdColumns, updatedPredictions, updatedConnections);
			
				if (newError < oldError) {
					//System.out.println("Increasing Stepsize: " + stepSize + " Count: " + count);
				
					stepSize *= 2;
					count++;
                
					lastGoodUserMatrix = updatedUserMatrix;
					lastGoodUserIdColumns = updatedUserIdColumns;
					lastGoodLinkMatrix = updatedLinkMatrix;
					lastGoodLinkIdColumns = updatedLinkIdColumns;
                	lastGoodUserTraits = updatedUserTraits;
                	lastGoodLinkTraits = updatedLinkTraits;
                	lastGoodConnections = updatedConnections;
                	lastGoodPredictions = updatedPredictions;
    			
                	lastGoodError = newError;
				}
				else {
					if (count > 0) {
						count = 0;
					
						for (int k = 0; k < Constants.K; k++) {
							userFeatureMatrix[k] = lastGoodUserMatrix[k];
							linkFeatureMatrix[k] = lastGoodLinkMatrix[k];
						}
					
						for (long userId : userLinkSamples.keySet()) {
							userIdColumns.put(userId, lastGoodUserIdColumns.get(userId));
						}
					
						for (long linkId : linkIdColumns.keySet()) {
							linkIdColumns.put(linkId, lastGoodLinkIdColumns.get(linkId));
						}
					
						oldError = lastGoodError;
						userTraits = lastGoodUserTraits;
						linkTraits = lastGoodLinkTraits;
						connections = lastGoodConnections;
						predictions = lastGoodPredictions;
	    			
						iterations++;
						System.out.println("Iterations: " + iterations);
						System.out.println("Error: " + oldError);
						System.out.println("");
						
						go = false;
					}
					else {
						//System.out.println("Decreasing Stepsize: " + stepSize);
						stepSize *= .5;
					}
				}
			
				//Once the learning rate is smaller than a certain size, just stop.
				//We get here after a few failures in the previous if statement.
				if (stepSize < Constants.STEP_CONVERGENCE) {
					converged = true;
					go = false;
				}
			}
		}
		
		return oldError;
	}
	
	/**
	 * Loads the previously trained matrix from the database
	 * 
	 * @param tableName
	 * @param featureCount
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public Double[][] loadFeatureMatrix(String tableName, int featureCount, String type)
		throws SQLException
	{
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		Double[][] matrix = new Double[Constants.K][featureCount];
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id < " + Constants.K + " AND type='" + type + "'");
		
		int count = 0;
		
		//Columns were saved in the database with id being row and the column values as one CSV string
		while (result.next()) {
			count++;
			int id = result.getInt("id");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			for (int x = 0; x < tokens.length; x++) {
				matrix[id][x] = Double.parseDouble(tokens[x]);
			}
		}
		
		statement.close();
		
		if (count == 0) return null;
		
		return matrix;
	}
	
	/**
	 * Loads the previously trained matrix id columns from the database.
	 * 
	 * @param tableName
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public Map<Long, Double[]> loadIdColumns(String tableName, String type)
		throws SQLException
	{
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id >" + Constants.K + " AND type='" + type + "'");
		while (result.next()) {
			long id = result.getLong("id");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			//Column valuess were saved as one CSV string
			Double[] val = new Double[Constants.K];
			for (int x = 0; x < Constants.K; x++) {
				val[x] = Double.parseDouble(tokens[x]);
			}
			
			idColumns.put(id, val);
		}
		
		statement.close();
		
		
		return idColumns;
	}
	
	/**
	 * Columns for the ids are placed into a HashMap
	 * 
	 * @param ids
	 * @return
	 */
	public Map<Long, Double[]> getMatrixIdColumns(Set<Long> ids)
	{
		Random random = new Random();
		
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		for (long id : ids) {
			Double[] column = new Double[Constants.K];
			
			for (int x = 0; x < column.length; x++) {
				column[x] = random.nextGaussian();
			}
			
			idColumns.put(id, column);
		}
		
		return idColumns;
	}

	public Map<Long, Double> getAveragePrecisions(Map<Long, Set<Long>> testData)
	{
		Map<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		Map<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
		
		HashMap<Long, Double> averagePrecisions = new HashMap<Long, Double>();
		
		for (long userId : testData.keySet()) {
			Set<Long> links = testData.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> linkIds = new ArrayList<Long>();
			
			for (long j : links) {
				if (!linkTraits.containsKey(j)) continue;
				double predictedLike = dot(userTraits.get(userId), linkTraits.get(j));
				
				scores.add(predictedLike);
				linkIds.add(j);
			}
			
			Object[] sorted = sort(scores, linkIds);
			
			double ap = getUserAP(sorted, userId);
			
			averagePrecisions.put(userId, ap);
		}
		
		return averagePrecisions;
	}
	
	public void save()
	{
		
	}
	
	public abstract double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkIdColumns,
			Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Double>> connections);
	
	public abstract double getErrorDerivativeOverUserAttribute(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
																Map<Long, Map<Long, Double>> connections, int x, int y);
	
	public abstract double getErrorDerivativeOverUserId(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
														Map<Long, Map<Long, Double>> connections, int k, long userId);
	
	public abstract double getErrorDerivativeOverLinkAttribute(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, int y);
	
	public abstract double getErrorDerivativeOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, long linkId);
	
	public void checkDerivative(Map<Long, Set<Long>> userLinkSamples)
	{	
		double H = 1e-5;
		
		for (int k = 0; k < Constants.K; k++) {
			for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
				Map<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double calculatedDerivative = getErrorDerivativeOverUserAttribute(linkTraits, predictions, connections, k, l);
				double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
				
				double tmp = userFeatureMatrix[k][l];
				userFeatureMatrix[k][l] += H;
				
				userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
				userFeatureMatrix[k][l] = tmp;
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
			}
			
			for (long userId : userLinkSamples.keySet()) {
				Map<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double calculatedDerivative = getErrorDerivativeOverUserId(linkTraits, predictions, connections, k, userId);
				
				double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
				
				double tmp = userIdColumns.get(userId)[k];
				userIdColumns.get(userId)[k] += H;
				
				userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
				userIdColumns.get(userId)[k] = tmp;
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
			}
		}
		
		for (int q = 0; q < Constants.K; q++) {
			for (int l = 0; l < Constants.LINK_FEATURE_COUNT; l++) {
				Map<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double calculatedDerivative = getErrorDerivativeOverLinkAttribute(userTraits, predictions, q, l);
				double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
				
				double tmp = linkFeatureMatrix[q][l];
				linkFeatureMatrix[q][l] += H;
				
				userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
				linkFeatureMatrix[q][l] = tmp;
				
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
			}
			
			for (long linkId : linkIdColumns.keySet()) {
				Map<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double calculatedDerivative = getErrorDerivativeOverLinkId(userTraits, predictions, q, linkId);
				double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
				
				double tmp = linkIdColumns.get(linkId)[q];
				linkIdColumns.get(linkId)[q] += H;
				
				userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections);
				linkIdColumns.get(linkId)[q] = tmp;
				
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
				
			}
		}
	}
	
	/**
	 * Calculates the dot product between 2 vectors
	 * 
	 * @param vec1
	 * @param vec2
	 * @return
	 */
	public static double dot(Double[] vec1, Double[] vec2)
	{
		double prod = 0;
		
		for (int x = 0; x < vec1.length; x++) {
			prod += vec1[x] * vec2[x];
		}
		
		return prod;
	}
	
	public static Map<Long, Map<Long, Double>> getConnections(Double[][] userMatrix, Map<Long, Double[]> idColumns, Map<Long, Double[]> userFeatures, Map<Long, Set<Long>> userLinkSamples)
	{
		Set<Long> users = userLinkSamples.keySet();
		HashMap<Long, Map<Long, Double>> connections = new HashMap<Long, Map<Long, Double>>();
		
		for (long user1 : users) {
			HashMap<Long, Double> conn = new HashMap<Long, Double>();
			
			for (long user2 : users) {
				if (user1 == user2 || connections.containsKey(user2)) continue;
				
				conn.put(user2, predictConnection(userMatrix, idColumns, userFeatures, user1, user2));		
			}
			
			connections.put(user1, conn);
		}
		
		return connections;
	}
	
	public static Map<Long, Map<Long, Double>> getPredictions(Map<Long, Double[]> userTraits, Map<Long, Double[]> linkTraits, Map<Long, Set<Long>> userLinkSamples)
	{
		HashMap<Long, Map<Long, Double>> predictions = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId : userLinkSamples.keySet()) {
			Set<Long> links = userLinkSamples.get(userId);
			HashMap<Long, Double> preds = new HashMap<Long, Double>();
			
			for (long linkId : links) {
				if (!linkTraits.containsKey(linkId)) continue;
				preds.put(linkId, dot(userTraits.get(userId), linkTraits.get(linkId)));
			}
			
			predictions.put(userId, preds);
		}
		
		return predictions;
	}
	
	
	
	public static void main(String[] args)
	{
		Double[][] userMatrix = {
				{6d, 7d},
				{10d, 11d},
				{14d, 15d}
		};
		
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		idColumns.put(2l, new Double[]{8d, 12d, 16d});
		idColumns.put(1l, new Double[]{9d, 13d, 17d});
		
		HashMap<Long, Double[]> userFeatures = new HashMap<Long, Double[]>();
		userFeatures.put(1l, new Double[]{1d, 2d});
		userFeatures.put(2l, new Double[]{3d, 4d});
		
		Constants.K = 3;
		System.out.println("Connection: " + predictConnection(userMatrix, idColumns, userFeatures, 1, 2));
	}
	
	public static double predictConnection(Double[][] userMatrix, 
									Map<Long, Double[]> idColumns,
									Map<Long, Double[]> userFeatures,
									long i, long j)
	{
		Double[] iFeature = userFeatures.get(i);
		Double[] iColumn = idColumns.get(i);
		Double[] jFeature = userFeatures.get(j);
		Double[] jColumn = idColumns.get(j);
	
		Double[] xU = new Double[Constants.K];
	
		for (int x = 0; x < Constants.K; x++) {
			xU[x] = 0.0;
	
			for (int y = 0; y < iFeature.length; y++) {
				//System.out.println(iFeature[y] * userMatrix[x][y]);
				//System.out.println("iFeature[y]: " + iFeature[y]);
				//System.out.println("userMatrix[x][y] " + userMatrix[x][y]);
				//System.out.println("xU[x]: " + xU[x]);
				
				xU[x] += iFeature[y] * userMatrix[x][y];
			}
	
			//System.out.println(iColumn[x]);
			xU[x] += iColumn[x];
	
			//System.out.print(xU[x] + " ");
		}
	
		/*
		xU[Constants.K] = 0.0;
		for (int y = 0; y < iFeature.length; y++) {
			xU[Constants.K] += iFeature[y] * userMatrix[x][y];
		}
		xU[Constants.K] += iColumn[x];
		*/
		
		//System.out.println("");
		
		Double[] xUU = new Double[iFeature.length + 1];
	
		for (int x = 0; x < iFeature.length; x++) {
			xUU[x] = 0.0;
	
			for (int y = 0; y < xU.length; y++) {
				//System.out.println("xU[y]: " + xU[y] + " userMatrix[y][x]: " + userMatrix[y][x]);
				xUU[x] += xU[y] * userMatrix[y][x];
			}
			//System.out.print(xUU[x] + " ");
		}
	
		int index = iFeature.length;
		xUU[index] = 0d;
			
		for (int y = 0; y < xU.length; y++) {
			//System.out.println("xU[y]: " + xU[y] + " userMatrix[y][x]: " + jColumn[y]);
			xUU[index] += xU[y] * jColumn[y];
		}
		
		//System.out.print(xUU[index] + " ");
		
		//System.out.println("");
	
		
		double connection = 0;
	
		for (int x = 0; x < jFeature.length; x++) {
			//System.out.println("xUU[x]: " + xUU[x] + " jFeature[x]: " + jFeature[x]);
			connection += xUU[x] * jFeature[x];
		}
		connection += xUU[jFeature.length];
	
		return connection;
	}
	
	/**
	 * Save the trained matrices into the database.
	 * 
	 * @param userFeatureMatrix
	 * @param linkFeatureMatrix
	 * @param userIdColumns
	 * @param linkIdColumns
	 * @param wordColumns
	 * @param type
	 * @throws SQLException
	 */
	public void saveModel()
		throws SQLException
	{
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		statement.executeUpdate("DELETE FROM lrUserMatrix WHERE type='" + type + "'");
		statement.executeUpdate("DELETE FROM lrLinkMatrix WHERE type='" + type + "'");
		statement.executeUpdate("DELETE FROM lrWordColumns WHERE type='" + type + "'");
		
		for (int x = 0; x < userFeatureMatrix.length; x++) {
			StringBuilder userBuf = new StringBuilder();
			for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
				userBuf.append(userFeatureMatrix[x][y]);
				userBuf.append(",");
			}
			
			StringBuilder linkBuf = new StringBuilder();
			for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
				linkBuf.append(linkFeatureMatrix[x][y]);
				linkBuf.append(",");
			}
			
			PreparedStatement userInsert = conn.prepareStatement("INSERT INTO lrUserMatrix VALUES(?,?,?)");
			userInsert.setLong(1, x);
			userInsert.setString(2, userBuf.toString());
			userInsert.setString(3, type);
			userInsert.executeUpdate();
			userInsert.close();
			
			PreparedStatement linkInsert = conn.prepareStatement("INSERT INTO lrLinkMatrix VALUES(?,?,?)");
			linkInsert.setLong(1, x);
			linkInsert.setString(2, linkBuf.toString());
			linkInsert.setString(3, type);
			linkInsert.executeUpdate();
			linkInsert.close();
		}
		
		//Save the id column values as a CSV string
		for (long userId : userIdColumns.keySet()) {
			StringBuilder buf = new StringBuilder();
			
			Double[] col = userIdColumns.get(userId);
			for (int x = 0; x < Constants.K; x++) {
				buf.append(col[x]);
				buf.append(",");
			}
			
			PreparedStatement userInsert = conn.prepareStatement("INSERT INTO lrUserMatrix VALUES(?,?,?)");
			userInsert.setLong(1, userId);
			userInsert.setString(2, buf.toString());
			userInsert.setString(3, type);
			userInsert.executeUpdate();
			userInsert.close();
		}
		
		for (long linkId : linkIdColumns.keySet()) {
			StringBuilder buf = new StringBuilder();
			
			Double[] col = linkIdColumns.get(linkId);
			for (int x = 0; x < Constants.K; x++) {
				buf.append(col[x]);
				buf.append(",");
			}
			
			PreparedStatement linkInsert = conn.prepareStatement("INSERT INTO lrLinkMatrix VALUES(?,?,?)");
			linkInsert.setLong(1, linkId);
			linkInsert.setString(2, buf.toString());
			linkInsert.setString(3, type);
			linkInsert.executeUpdate();
			linkInsert.close();
		}
	}
	
	
	/**
	 * Since we're doing online updating, we need to update the matrix columns by removing links/users from the previous training that aren't included
	 * anymore and adding the new ones that weren't existing in the previous training.
	 * 
	 * @param ids
	 * @param idColumns
	 */
	public void updateMatrixColumns(Set<Long> ids, HashMap<Long, Double[]> idColumns)
	{
		HashSet<Long> columnsToRemove = new HashSet<Long>();
		
		//Remove columns that are past the range
		for (long id : idColumns.keySet()) {
			if (!ids.contains(id)) {
				columnsToRemove.add(id);
			}
		}
		for (long id : columnsToRemove) {
			idColumns.remove(id);
		}
		
		//Add columns for the new ones
		HashSet<Long> columnsToAdd = new HashSet<Long>();
		
		for (long id : ids) {
			if (!idColumns.containsKey(id)) {
				columnsToAdd.add(id);
			}
		}
		Map<Long, Double[]> newColumns = getMatrixIdColumns(columnsToAdd);
		idColumns.putAll(newColumns);
	}

	public Map<Long, Map<Long, Double>> recommend(Map<Long, Set<Long>> linksToRecommend)
	{	
		if (userMax == null) {
			try {
				userMax = getUserMax(linksToRecommend.keySet());
			}
			catch (SQLException ex) {
				ex.printStackTrace();
			}
		}
		
		Map<Long, Map<Long, Double>> recommendations = new HashMap<Long, Map<Long, Double>>();
		
		Map<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		Map<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
		
		for (long userId :linksToRecommend.keySet()) {
			Set<Long> userLinks = linksToRecommend.get(userId);
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
			
			int maxLinks = userMax.get(userId);
			
			for (long linkId : userLinks) {
				if (!linkTraits.containsKey(linkId)) continue;
				
				double prediction = dot(userTraits.get(userId), linkTraits.get(linkId));
				
				//We recommend only a set number of links per day/run. 
				//If the recommended links are more than the max number, recommend only the highest scoring links.
				if (linkValues.size() < maxLinks) {
					linkValues.put(linkId, prediction);
				}
				else {
					//Get the lowest scoring recommended link and replace it with the current link
					//if this one has a better score.
					long lowestKey = 0;
					double lowestValue = Double.MAX_VALUE;
						
					for (long id : linkValues.keySet()) {
						if (linkValues.get(id) < lowestValue) {
							lowestKey = id;
							lowestValue = linkValues.get(id);
						}
					}
						
					if (prediction > lowestValue) {
						linkValues.remove(lowestKey);
						linkValues.put(linkId, prediction);
					}
				}
			}
		}
		
		return recommendations;
	}
}
