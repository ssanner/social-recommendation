package org.nicta.lr.recommender;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.SQLUtil;

public class IndSocialRecommender extends SocialRecommender
{
	long appUserId;
	
	double C;
	
	public IndSocialRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends, long userId)
	{
		super(linkLikes, userFeatures, linkFeatures, friends);
		
		K = 5;
		lambda = 1000;
		C=50;
		
		type = "social";
		friendships = friends;
		
		appUserId = userId;
		
		if (Constants.DEPLOYMENT_TYPE == Constants.TEST || Constants.INITIALIZE) {
			initializePriors(userFeatures.keySet(), linkFeatures.keySet());
		}
		else if (Constants.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			try {
				loadData();
			}
			catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void loadData()
		throws SQLException
	{
		userFeatureMatrix = loadFeatureMatrix("lrIndUserMatrix", Constants.USER_FEATURE_COUNT, type);
		linkFeatureMatrix = loadFeatureMatrix("lrIndLinkMatrix", Constants.LINK_FEATURE_COUNT, type);
		userIdColumns = loadIdColumns("lrIndUserMatrix", type);
		linkIdColumns = loadIdColumns("lrIndLinkMatrix", type);
		
		updateMatrixColumns(userFeatures.keySet(), userIdColumns);
		updateMatrixColumns(linkFeatures.keySet(), linkIdColumns);
	}

	public Double[][] loadFeatureMatrix(String tableName, int featureCount, String type)
		throws SQLException
	{
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		Double[][] matrix = new Double[K][featureCount];
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id < " + K + " AND type='" + type + "' AND user=" + appUserId);
		
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
	
	public Map<Long, Double[]> loadIdColumns(String tableName, String type)
		throws SQLException
	{
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id >" + K + " AND type='" + type + "' AND user=" + appUserId);
		while (result.next()) {
			long id = result.getLong("id");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			//Column valuess were saved as one CSV string
			Double[] val = new Double[K];
			for (int x = 0; x < K; x++) {
				val[x] = Double.parseDouble(tokens[x]);
			}
			
			idColumns.put(id, val);
		}
		
		statement.close();
		
		
		return idColumns;
	}
	
	public Map<Long, Double> indRecommend(Map<Long, Set<Long>> linksToRecommend)
	{	
		if (userMax == null) {
			try {
				userMax = getUserMax(linksToRecommend.keySet());
			}
			catch (SQLException ex) {
				ex.printStackTrace();
			}
		}
		
		Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
		
		
		Set<Long> userLinks = linksToRecommend.get(appUserId);
			
		HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			
		int maxLinks = userMax.get(appUserId);
			
		for (long linkId : userLinks) {
			if (!linkTraits.containsKey(linkId)) continue;
				
			double prediction = dot(userTraits.get(appUserId), linkTraits.get(linkId));
				
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
		
		return linkValues;
	}
	
	public void saveModel()
		throws SQLException
	{
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		statement.executeUpdate("DELETE FROM lrIndUserMatrix WHERE type='" + type + "' AND user=" + appUserId);
		statement.executeUpdate("DELETE FROM lrIndLinkMatrix WHERE type='" + type + "' AND user=" + appUserId);
		
		for (int x = 0; x < K; x++) {
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
			
			PreparedStatement userInsert = conn.prepareStatement("INSERT INTO lrIndUserMatrix VALUES(?,?,?,?)");
			userInsert.setLong(1, x);
			userInsert.setString(2, userBuf.toString());
			userInsert.setString(3, type);
			userInsert.setLong(4, appUserId);
			userInsert.executeUpdate();
			userInsert.close();
			
			PreparedStatement linkInsert = conn.prepareStatement("INSERT INTO lrIndLinkMatrix VALUES(?,?,?,?)");
			linkInsert.setLong(1, x);
			linkInsert.setString(2, linkBuf.toString());
			linkInsert.setString(3, type);
			linkInsert.setLong(4, appUserId);
			linkInsert.executeUpdate();
			linkInsert.close();
		}
		
		//Save the id column values as a CSV string
		for (long userId : userIdColumns.keySet()) {
			StringBuilder buf = new StringBuilder();
			
			Double[] col = userIdColumns.get(userId);
			for (int x = 0; x < K; x++) {
				buf.append(col[x]);
				buf.append(",");
			}
			
			PreparedStatement userInsert = conn.prepareStatement("INSERT INTO lrIndUserMatrix VALUES(?,?,?,?)");
			userInsert.setLong(1, userId);
			userInsert.setString(2, buf.toString());
			userInsert.setString(3, type);
			userInsert.setLong(4, appUserId);
			userInsert.executeUpdate();
			userInsert.close();
		}
		
		for (long linkId : linkIdColumns.keySet()) {
			StringBuilder buf = new StringBuilder();
			
			Double[] col = linkIdColumns.get(linkId);
			for (int x = 0; x < K; x++) {
				buf.append(col[x]);
				buf.append(",");
			}
			
			PreparedStatement linkInsert = conn.prepareStatement("INSERT INTO lrIndLinkMatrix VALUES(?,?,?,?)");
			linkInsert.setLong(1, linkId);
			linkInsert.setString(2, buf.toString());
			linkInsert.setString(3, type);
			linkInsert.setLong(4, appUserId);
			linkInsert.executeUpdate();
			linkInsert.close();
		}
	}
	
	public double getAveragePrecision(Set<Long> testLinks)
	{
		Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
			
		ArrayList<Double> scores = new ArrayList<Double>();
		ArrayList<Long> linkIds = new ArrayList<Long>();
			
		for (long j : testLinks) {
			if (!linkTraits.containsKey(j)) continue;
			double predictedLike = dot(userTraits.get(appUserId), linkTraits.get(j));
				
			scores.add(predictedLike);
			linkIds.add(j);
		}
			
		Object[] sorted = sort(scores, linkIds);
			
		double ap = getUserAP(sorted, appUserId);
		return ap;
	}
	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkIdColumns,
			Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Double>> connections)
	{
		System.out.println("foo");
		double error = 0;
	
		Object[] keys = connections.keySet().toArray();
		
		for (int a = 0; a < keys.length-1; a++) {
			Long i = (Long)keys[a];
			                 
			for (int b = a+1; b < keys.length; b++) {
				Long j = (Long)keys[b];
				
				double connection = getFriendConnection(i, j, friendships);
				double predictConnection = connections.get(i).containsKey(j) ? connections.get(i).get(j) : connections.get(j).get(i);
				error += Math.pow(connection - predictConnection, 2);
			}
		}
		
		error *= beta;
		
		for (long i : predictions.keySet()) {
			Set<Long> links = predictions.get(i).keySet();
			
			for (long j : links) {
				int liked = 0;
				
				if (linkLikes.get(j).contains(i)) liked = 1;
				double predictedLike = predictions.get(i).get(j);
				double se = Math.pow(liked - predictedLike, 2);
				if (i == appUserId) se *= C;
				
				error += se;
			}
		}

		//Get User and Link norms for regularisation
		double userNorm = 0;
		double linkNorm = 0;

		for (int x = 0; x < K; x++) {
			for (int y = 0; y < Constants.USER_FEATURE_COUNT; y++) {
				userNorm += Math.pow(userFeatureMatrix[x][y], 2);
			}
		}
		for (long id : userIdColumns.keySet()) {
			Double[] column = userIdColumns.get(id);
			
			for (double val : column) {
				userNorm += Math.pow(val, 2);
			}
		}

		for (int x = 0; x < K; x++) {
			for (int y = 0; y < Constants.LINK_FEATURE_COUNT; y++) {
				linkNorm += Math.pow(linkFeatureMatrix[x][y], 2);
			}
		}
		for (long id : linkIdColumns.keySet()) {
			Double[] column = linkIdColumns.get(id);
			
			for (double val : column) {
				linkNorm += Math.pow(val, 2);
			}
		}
			
		userNorm *= lambda;
		linkNorm *= lambda;

		error += userNorm + linkNorm;

		return error / 2;
	}
	
	public double getErrorDerivativeOverUserAttribute(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
														Map<Long, Map<Long, Double>> connections, int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * lambda;
		
		Object[] keys = connections.keySet().toArray();
		
		for (int a = 0; a < keys.length-1; a++) {
			Long uid1 = (Long)keys[a];
			                 
			for (int b = a+1; b < keys.length; b++) {
				Long uid2 = (Long)keys[b];
			//}
		//}
		//for (long uid1 : connections.keySet()) {
			//for (long uid2 : connections.keySet()) {
				//if (uid1 == uid2) continue;	
				
				Double[] user1 = userFeatures.get(uid1);
				Double[] user1Id = userIdColumns.get(uid1);
				Double[] user2 = userFeatures.get(uid2);
				Double[] user2Id = userIdColumns.get(uid2);
				
				double c = getFriendConnection(uid1, uid2, friendships);
				double p = connections.get(uid1).containsKey(uid2) ? connections.get(uid1).get(uid2) : connections.get(uid2).get(uid1);
				
				double duu = 2 * user1[y] * user2[y] * userFeatureMatrix[x][y];
				for (int z = 0; z < user1.length; z++) {
					if (z != y) {
						duu += user1[y] * user2[z] * userFeatureMatrix[x][z];
						duu += user1[z] * user2[y] * userFeatureMatrix[x][z];
					}
				}
				duu += user1Id[x] * user2[y];
				duu += user2Id[x] * user1[y];
				
				errorDerivative += beta * (c - p) * duu * -1;
			}
		}
		
		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();
			
			for (long linkId : links) {
				double dst = linkTraits.get(linkId)[x] * userFeatures.get(userId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.get(linkId).contains(userId)) r = 1;

				double err = r - p;
				if (userId == appUserId) err *= C;
				
				errorDerivative += err * dst * -1;
			}
		}

		return errorDerivative;
	}
	
	
	public double getErrorDerivativeOverUserId(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
												Map<Long, Map<Long, Double>> connections, int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * lambda;

		Double[] user1 = userFeatures.get(userId);
		
		for (long uid2 : connections.keySet()) {
			if (userId == uid2) continue;	
			
			Double[] user2 = userFeatures.get(uid2);
			
			Double[] user2Column = userIdColumns.get(uid2);
			
			double c = getFriendConnection(userId, uid2, friendships);
			double p = connections.get(userId).containsKey(uid2) ? connections.get(userId).get(uid2) : connections.get(uid2).get(userId);
			
			double duu = 0;
			
			for (int z = 0; z < user1.length; z++) {
				duu += /*user1[y] * */ user2[z] *  userFeatureMatrix[k][z];

			}
			//duu += user1Id[x] * user2[y];
			//duu += user2Id[x] * user1[y];
			
			//duu += idColumn[k];
			duu += user2Column[k];
			
			errorDerivative += beta * (c - p) * duu * -1;
		}
		
		Set<Long> links = predictions.get(userId).keySet();
		for (long linkId : links) {
			if (!linkTraits.containsKey(linkId)) continue;
			
			Set<Long> likes = linkLikes.get(linkId);
		
			double dst = linkTraits.get(linkId)[k] /* userFeatures.get(userId)[k]*/;
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes.contains(userId)) r = 1;

			double err = r - p;
			if (userId == appUserId) err *= C;
			
			errorDerivative += err * dst * -1;
		}
		
		return errorDerivative;
	}

	public double getErrorDerivativeOverLinkAttribute(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, int y)
	{
		double errorDerivative = linkFeatureMatrix[x][y] * lambda;

		for (long userId : predictions.keySet()) {
			Set<Long> links = predictions.get(userId).keySet();

			for (long linkId : links) {
				double dst = userTraits.get(userId)[x] * linkFeatures.get(linkId)[y];		
				double p = predictions.get(userId).get(linkId);
				double r = 0;
				if (linkLikes.get(linkId).contains(userId)) r = 1;

				double err = r - p;
				if (userId == appUserId) err *= C;
				
				errorDerivative += err * dst * -1;
			}
		}

		return errorDerivative;
	}

	public double getErrorDerivativeOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * lambda;

		Set<Long> likes = linkLikes.get(linkId);
		
		for (long userId : predictions.keySet()) {
			if (! predictions.get(userId).containsKey(linkId)) continue;
			
			double dst = userTraits.get(userId)[x] /* * idColumn[x]*/;		
			double p = predictions.get(userId).get(linkId);
			double r = 0;
			if (likes.contains(userId)) r = 1;

			double err = r - p;
			if (userId == appUserId) err *= C;
			
			errorDerivative += err * dst * -1;
		}
		
		return errorDerivative;
	}
	
	public void setC(double c)
	{
		this.C = c;
	}
}
