package org.nicta.lr.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;

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
	
	public static HashMap<Long, Integer[]> getPrecision(HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		HashMap<Long, Integer[]> userPrecisions = new HashMap<Long, Integer[]>();
		
		for (long i : userLinkSamples.keySet()) {
			HashSet<Long> links = userLinkSamples.get(i);
			
			double[] scores = new double[links.size()];
			
			HashMap<Double, Long> linkScores = new HashMap<Double, Long>();
			
			int index = 0;
			for (long j : links) {
				double predictedLike = dot(userTraits.get(i), linkTraits.get(j));
				
				scores[index] = predictedLike;
				linkScores.put(predictedLike, j);
				
				index++;
			}
			
			Arrays.sort(scores);
			
			int precisionAt1 = 0;
			int precisionAt2 = 0;
			int precisionAt3 = 0;
			
			for (int x = 0; x < 3; x++) {
				if (x >= scores.length) break;
				
				long id = linkScores.get(scores[x]);
			
				if (linkLikes.containsKey(id) && linkLikes.get(id).contains(i)) {
					if (x == 0) {
						precisionAt1++;
						precisionAt2++;
						precisionAt3++;
					}
					else if (x == 1) {
						precisionAt2++;
						precisionAt3++;
					}
					else if (x == 2) {
						precisionAt3++;
					}
				}
			}
			
			System.out.println("User: " + i + " Precision: " + precisionAt1 + " " + precisionAt2 + " " + precisionAt3);
			
			userPrecisions.put(i, new Integer[]{precisionAt1, precisionAt2, precisionAt3});
		}
		
		return userPrecisions;
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
	public static HashMap<Long, HashSet<Long>> getUserLinksSample(Set<Long> userIds, HashMap<Long, HashMap<Long, Double>> friendships, boolean limit)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinkSamples = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		for (Long id : userIds) {
			HashSet<Long> samples = new HashSet<Long>();
			
			//Get the links that were liked by the user
			String selectStr = "SELECT l.link_id FROM linkrLinks l, linkrLinkLikes lp WHERE l.link_id=lp.link_id AND lp.id=" + id;
			if (limit) {
				selectStr += " AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + "))";
			}
			
			ResultSet result = statement.executeQuery(selectStr);
			while (result.next()) {
				samples.add(result.getLong("l.link_id"));
			}
			
			if (samples.size() == 0) continue;
			
			//Sample links that weren't liked.
			//Links here should be links that were shared by friends to increase the chance that the user has actually seen this and not
			//liked them
			Set<Long> friends;
			if (friendships.containsKey(id)) {
				friends = friendships.get(id).keySet();
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
				samples.add(result.getLong("link_id"));
			}
			
			userLinkSamples.put(id, samples);
		}
		
		return userLinkSamples;
	}
}
