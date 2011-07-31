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
				double predictedLike = dot(userTraits.get(userId), linkTraits.get(j));
				
				scores.add(predictedLike);
				linkIds.add(j);
			}
			
			Object[] sorted = sort(scores, linkIds);
			ArrayList<Double> sortedScores = (ArrayList<Double>)sorted[0];
			ArrayList<Long> sortedIds = (ArrayList<Long>)sorted[1];
			
			ArrayList<Double> precisions = new ArrayList<Double>();
			int pos = 0;
			for (int x = 0; x < sortedScores.size(); x++) {
				long linkId = sortedIds.get(x);
			
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					pos++;
					precisions.add((double)pos / (double)(x+1));
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
	public static HashMap<Long, HashSet<Long>> getUserLinksSample(HashMap<Long, HashSet<Long>> linkLikes, Set<Long> userIds, HashMap<Long, HashMap<Long, Double>> friendships, Set<Long> linkIds, boolean limit)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinkSamples = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		for (Long userId : userIds) {
			HashSet<Long> samples = new HashSet<Long>();
			
			for (long linkId : linkLikes.keySet()) {
				if (linkLikes.get(linkId).contains(userId)) {
					if (linkIds.contains(linkId)) samples.add(linkId);
				}
			}
			
			if (samples.size() == 0) continue;
			
			int likeCount = samples.size();
			
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
			
			ResultSet result = statement.executeQuery("SELECT link_id FROM trackRecommendedLinks WHERE uid=" + userId + " AND rating=2");
			while (result.next()) {
				if (linkIds.contains(result.getLong("link_id"))) samples.add(result.getLong("link_id"));
			}
			
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				if (samples.size() >= likeCount * 10) break;
				
				if (linkIds.contains(result.getLong("link_id"))) samples.add(result.getLong("link_id"));
			}
			
			userLinkSamples.put(userId, samples);
		}
		
		return userLinkSamples;
	}
}
