package org.nicta.lr.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

public class RecommenderUtil
{
	private static Connection connection = null;
	
	/**
	 * Returns the SQL connection Singleton
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static Connection getSqlConnection()
		throws SQLException
	{
		try {
			Class.forName ("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException ce) {
			System.out.println("MySQL driver not found");
			System.exit(1);
		}
		
		if (connection == null) {
			connection = DriverManager.getConnection(Constants.DB_STRING);
			connection.setAutoCommit(false);
		}
	
		return connection;
	}

	/**
	 * Cleans up and commits the updates to the database
	 * 
	 * @throws SQLException
	 */
	public static void closeSqlConnection()
		throws SQLException
	{
		if (connection != null) {
			connection.commit();
			connection.close();
			connection = null;
		}
		else {
			System.out.println("Connection died before it could be commited. FAIL");
		}
	}
	
	/**
	 * Calculates RMSE. Used for cross validation and for comparing different recommenders.
	 * 
	 * @param userTraits
	 * @param linkTraits
	 * @param linkLikes
	 * @param userLinkSamples
	 * @return
	 */
	public static double calcRMSE(HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		double error = 0;
		
		int count = 0;
		for (long i : userLinkSamples.keySet()) {
			HashSet<Long> links = userLinkSamples.get(i);
			
			for (long j : links) {
				int liked = 0;
				
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				double predictedLike = dot(userTraits.get(i), linkTraits.get(j));
	
				//Not sure if I want to bounding predictions for now
				if (predictedLike < 0) predictedLike = 0;
				if (predictedLike > 1) predictedLike = 1;
				
				error += Math.pow(liked - predictedLike, 2);
				
				count++;
			}
		}

		return Math.sqrt(error / count);
	}
	
	public static Object[] sort(ArrayList<Double> scores, ArrayList<Long> linkIds)
	{
		if (scores.size() <= 1) return new Object[]{scores, linkIds};
		
		ArrayList<Double> smallerScores = new ArrayList<Double>();
		ArrayList<Long> smallerIds = new ArrayList<Long>();
		ArrayList<Double> biggerScores = new ArrayList<Double>();
		ArrayList<Long> biggerIds = new ArrayList<Long>();
		
		int pivot = scores.size() / 2;
		double val = scores.get(pivot);
		
		for (int x = 0; x < scores.size(); x++) {
			if (x == pivot) continue;
			
			double score = scores.get(x);
			
			if (score <= val) {
				smallerScores.add(score);
				smallerIds.add(linkIds.get(x));
			}
			else {
				biggerScores.add(score);
				biggerIds.add(linkIds.get(x));
			}
		}
		
		Object[] smaller = sort(smallerScores, smallerIds);
		Object[] bigger = sort(biggerScores, biggerIds);
		
		ArrayList<Double> sortedScores = concat((ArrayList<Double>)smaller[0], val, (ArrayList<Double>)bigger[0]);
		ArrayList<Long> sortedIds = concat((ArrayList<Long>)smaller[1], linkIds.get(pivot), (ArrayList<Long>)bigger[1]);
		
		return new Object[]{sortedScores, sortedIds};
	}
	
	
	public static ArrayList<Double> concat(ArrayList<Double> smaller, double val, ArrayList<Double> bigger)
	{
		ArrayList<Double> list = new ArrayList<Double>();
		
		list.addAll(smaller);
		list.add(val);
		list.addAll(bigger);
		
		return list;
	}
	
	public static ArrayList<Long> concat(ArrayList<Long> smaller, long val, ArrayList<Long> bigger)
	{
		ArrayList<Long> list = new ArrayList<Long>();
		
		list.addAll(smaller);
		list.add(val);
		list.addAll(bigger);
		
		return list;
	}
	
	public static HashMap<Long, Double> getAveragePrecision(HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> testSamples)
	{
		HashMap<Long, Double> userAP = new HashMap<Long, Double>();
		
		for (long userId : testSamples.keySet()) {
			HashSet<Long> links = testSamples.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> linkIds = new ArrayList<Long>();
			
			for (long j : links) {
				if (!linkTraits.containsKey(j)) continue;
				double predictedLike = dot(userTraits.get(userId), linkTraits.get(j));
				
				scores.add(predictedLike);
				linkIds.add(j);
			}
			
			Object[] sorted = sort(scores, linkIds);
			ArrayList<Double> sortedScores = (ArrayList<Double>)sorted[0];
			ArrayList<Long> sortedIds = (ArrayList<Long>)sorted[1];
			
			ArrayList<Double> precisions = new ArrayList<Double>();
			int pos = 0;
			System.out.println("Testing: " + sortedScores.size());
			for (int x = 0; x < sortedScores.size(); x++) {
				long linkId = sortedIds.get(x);
			
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					pos++;
					precisions.add((double)pos / (double)(x+1));
					System.out.println("Pos: " + pos + " / " + (x+1));
				}
			}
			
			double ap = 0;
			
			if (precisions.size() > 0) {
				for (double p : precisions) {
					ap += p;
				}
				
				ap /= (double)precisions.size();
			}
			
			userAP.put(userId, ap);
		}
		
		return userAP;
	}
	
	public static int[] calcStats(HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		int truePos = 0;
		int falsePos = 0;
		int trueNeg = 0;
		int falseNeg = 0;
		
		for (long i : userLinkSamples.keySet()) {
			HashSet<Long> links = userLinkSamples.get(i);
			
			for (long j : links) {
				if (!linkTraits.containsKey(j)) continue;
				
				int liked = 0;
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				
				double predictedLike = dot(userTraits.get(i), linkTraits.get(j));
				
				int prediction = 1;
				if (predictedLike < 0.5) prediction = 0;
				
				if (prediction == 1) {
					if (liked == 1) {
						truePos++;
					}
					else {
						falsePos++;
					}
				}
				else {
					if (liked == 0) {
						trueNeg++;
					}
					else {
						falseNeg++;
					}
				}
			}
		}

		return new int[]{truePos, falsePos, trueNeg, falseNeg};
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
	
	/**
	 * For training, get a sample of links for each user.
	 * We look for links only given a certain date range, and only get 9 times as much 'unliked' links as 'liked' links
	 * because we do not want the 'liked' links to be drowned out during training.
	 * This means that if user hasn't liked any links, we do not get any unliked link as well.
	 * 
	 * @param userIds
	 * @param friendships
	 * @return
	 * @throws SQLException
	 */
	/*
	public static HashMap<Long, HashSet<Long>> getUserLinksSample(HashMap<Long, HashSet<Long>> linkLikes, Set<Long> userIds, HashMap<Long, HashMap<Long, Double>> friendships, Set<Long> linkIds, boolean limit)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinkSamples = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		System.out.println("userIds: " + userIds.size());
		int count = 0;
		for (Long userId : userIds) {
			System.out.println("User: " + ++count);
			HashSet<Long> samples = new HashSet<Long>();
			
			for (long linkId : linkLikes.keySet()) {
				if (linkLikes.get(linkId).contains(userId)) {
					if (linkIds.contains(linkId)) samples.add(linkId);
				}
			}
			
			if (samples.size() == 0) continue;
			
			int likeCount = samples.size();
			
			
			ResultSet result = statement.executeQuery("SELECT link_id FROM trackRecommendedLinks WHERE uid=" + userId + " AND rating=2");
			while (result.next()) {
				if (linkIds.contains(result.getLong("link_id"))) {
					samples.add(result.getLong("link_id"));
				}
			}
			
			//Sample links that weren't liked.
			//Links here should be links that were shared by friends to increase the chance that the user has actually seen this and not
			//liked them
			Set<Long> friends;
			if (friendships.containsKey(userId)) {
				friends = friendships.get(userId).keySet();
			}
			else {
				friends = new HashSet<Long>();
			}
			
			StringBuilder query = new StringBuilder("SELECT link_id FROM linkrLinks WHERE uid IN (0");
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			query.append(") AND link_id NOT IN(0");
			for (Long likedId : samples) {
				query.append(",");
				query.append(likedId);
			}	
			query.append(") ");
			if (limit) {
				query.append("AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + ")) ");
			}
		
			query.append("ORDER BY created_time DESC LIMIT ");
			query.append(samples.size() * 9);
			
			
			
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				if (samples.size() >= likeCount * 10) break;
				
				if (linkIds.contains(result.getLong("link_id"))) samples.add(result.getLong("link_id"));
			}
			
			userLinkSamples.put(userId, samples);
		}
		
		return userLinkSamples;
	}
	*/
	
	public static HashMap<Long, HashSet<Long>> getUserLinksSample2(HashMap<Long, HashSet<Long>> linkLikes, Set<Long> userIds, HashMap<Long, HashMap<Long, Double>> friendships, Set<Long> linkIds, boolean limit)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinkSamples = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		System.out.println("userIds: " + userIds.size());
		System.out.println("linkIds: " + linkIds.size());
		System.out.println("Likes: " + linkLikes.size());
		
		int count = 0;
		
		
		
		for (long linkId : linkLikes.keySet()) {
			Set<Long> users = linkLikes.get(linkId);
			
			for (long userId : users) {
				if (!userIds.contains(userId)) continue;
				HashSet<Long> samples = userLinkSamples.get(userId);
				if (samples == null) {
					samples = new HashSet<Long>();
					userLinkSamples.put(userId, samples);
				}
				samples.add(linkId);
			}
		}
		
		System.out.println("Count: " + userLinkSamples.size());
		int minCount = 0;
		for (Long userId : userLinkSamples.keySet()) {
			HashSet<Long> samples = userLinkSamples.get(userId);
			if (samples.size() >= 1) {
				minCount++;
			}
		}
		System.out.println("Min: " + minCount);
		System.exit(1);
		
		for (Long userId : userLinkSamples.keySet()) {
			System.out.println("User: " + ++count);
			HashSet<Long> samples = userLinkSamples.get(userId);
			
			/*
			for (long linkId : linkLikes.keySet()) {
				if (linkLikes.get(linkId).contains(userId)) {
					if (linkIds.contains(linkId)) samples.add(linkId);
				}
			}
			*/
			
			int likeCount = samples.size();
			
			
			ResultSet result = statement.executeQuery("SELECT link_id FROM trackRecommendedLinks WHERE uid=" + userId + " AND rating=2");
			while (result.next()) {
				if (linkIds.contains(result.getLong("link_id"))) {
					samples.add(result.getLong("link_id"));
				}
			}
			
			//Sample links that weren't liked.
			//Links here should be links that were shared by friends to increase the chance that the user has actually seen this and not
			//liked them
			Set<Long> friends;
			if (friendships.containsKey(userId)) {
				friends = friendships.get(userId).keySet();
			}
			else {
				friends = new HashSet<Long>();
			}
			
			StringBuilder query = new StringBuilder("SELECT link_id FROM linkrLinks WHERE uid IN (0");
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			query.append(") AND link_id NOT IN(0");
			for (Long likedId : samples) {
				query.append(",");
				query.append(likedId);
			}	
			query.append(") ");
			if (limit) {
				query.append("AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + ")) ");
			}
		
			query.append("ORDER BY created_time DESC LIMIT ");
			query.append(samples.size() * 9);
			
			
			
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				if (samples.size() >= likeCount * 10) break;
				
				if (linkIds.contains(result.getLong("link_id"))) {
					samples.add(result.getLong("link_id"));
				}
			}
		}
		
		return userLinkSamples;
	}
	
	public static HashMap<Long, HashSet<Long>> getUserLinksSample(HashMap<Long, HashSet<Long>> linkLikes, Set<Long> userIds, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, Long[]> links, boolean limit)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinkSamples = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		System.out.println("userIds: " + userIds.size());
		System.out.println("links: " + links.size());
		System.out.println("Likes: " + linkLikes.size());
		
		int count = 0;
		
		for (long linkId : linkLikes.keySet()) {
			Set<Long> users = linkLikes.get(linkId);
			
			for (long userId : users) {
				if (!userIds.contains(userId) || ! friendships.containsKey(userId)) continue;
				HashSet<Long> samples = userLinkSamples.get(userId);
				if (samples == null) {
					samples = new HashSet<Long>();
					userLinkSamples.put(userId, samples);
				}
				samples.add(linkId);
			}
		}
		
		System.out.println("Count: " + userLinkSamples.size());
		int minCount = 0;
		HashSet<Long> remove = new HashSet<Long>();
		for (Long userId : userLinkSamples.keySet()) {
			HashSet<Long> samples = userLinkSamples.get(userId);
			if (samples.size() >= 2) {
				minCount++;
			}
			else {
				remove.add(userId);
			}
		}
		
		for (Long removeId : remove) {
			userLinkSamples.remove(removeId);
		}
		
		System.out.println("Min: " + minCount);
		
		remove = new HashSet<Long>();
		
		for (Long userId : userLinkSamples.keySet()) {
			HashSet<Long> samples = userLinkSamples.get(userId);
			System.out.println("User: " + ++count + " " + samples.size());
			
			Set<Long> friends = friendships.get(userId).keySet();
			
			int likeCount = samples.size();
			
			
			ResultSet result = statement.executeQuery("SELECT link_id FROM trackRecommendedLinks WHERE uid=" + userId + " AND rating=2");
			while (result.next()) {
				if (links.containsKey(result.getLong("link_id"))) {
					samples.add(result.getLong("link_id"));
				}
			}
			
			//Sample links that weren't liked.
			//Links here should be links that were shared by friends to increase the chance that the user has actually seen this and not
			//liked them
	
			for (long linkId : links.keySet()) {
				if (samples.size() >= likeCount * 10) break;
				Long[] link = links.get(linkId);
				
				if (friends.contains(link[1]) && !samples.contains(linkId)) {
					samples.add(linkId);
				}
			}
			
			if (samples.size() < 4) {
				remove.add(userId);
			}
		}
		
		for (Long removeId : remove) {
			userLinkSamples.remove(removeId);
		}
		
		System.out.println("New Min: " + minCount);
		return userLinkSamples;
	}
	public static double getDistance(Double[] d1, Double[] d2)
	{
		double distance = 1;
		for (int x = 0; x < d1.length; x++) {
			distance += Math.pow(d1[x] - d2[x], 2);
		}
		return Math.sqrt(distance);
	}
	
	public static HashMap<Long, HashMap<Long, Double>> getPredictions(HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		HashMap<Long, HashMap<Long, Double>> predictions = new HashMap<Long, HashMap<Long, Double>>();
		
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
	
	public static HashMap<Long, HashMap<Long, Double>> getConnections(Double[][] userMatrix, HashMap<Long, Double[]> idColumns, HashMap<Long, Double[]> userFeatures, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		Set<Long> users = userLinkSamples.keySet();
		HashMap<Long, HashMap<Long, Double>> connections = new HashMap<Long, HashMap<Long, Double>>();
		
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
									HashMap<Long, Double[]> idColumns,
									HashMap<Long, Double[]> userFeatures,
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
}
