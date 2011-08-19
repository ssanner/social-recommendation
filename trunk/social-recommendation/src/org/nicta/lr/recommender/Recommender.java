package org.nicta.lr.recommender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.nicta.lr.util.SQLUtil;

public abstract class Recommender 
{
	Map<Long, Set<Long>> linkLikes;
	Map<Long, Double[]> userFeatures; 
	Map<Long, Double[]> linkFeatures;
	
	Map<Long, Map<Long, Double>> friendships;
	
	Map<Long, Integer> userMax;
	
	public Recommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends)
	{
		this.linkLikes = linkLikes;
		this.userFeatures = userFeatures;
		this.linkFeatures = linkFeatures;
		this.friendships = friends;
	}
	
	public abstract void train(Map<Long, Set<Long>> trainingSamples);
	
	public abstract Map<Long, Double> getAveragePrecisions(Map<Long, Set<Long>> testData);
	
	public abstract Map<Long, Map<Long, Double>> recommend(Map<Long, Set<Long>> linksToRecommend);
	
	public abstract void saveModel() throws SQLException;
	
	public double getUserAP(Object[] sorted, long userId)
	{
		List<Double> sortedScores = (List<Double>)sorted[0];
		List<Long> sortedIds = (List<Long>)sorted[1];
		
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
		
		return ap;
	}
	
	public Object[] sort(List<Double> scores, List<Long> linkIds)
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
		
		List<Double> sortedScores = concat((List<Double>)smaller[0], val, (List<Double>)bigger[0]);
		List<Long> sortedIds = concat((List<Long>)smaller[1], linkIds.get(pivot), (List<Long>)bigger[1]);
		
		return new Object[]{sortedScores, sortedIds};
	}
	
	public List<Double> concat(List<Double> smaller, double val, List<Double> bigger)
	{
		ArrayList<Double> list = new ArrayList<Double>();
		
		list.addAll(smaller);
		list.add(val);
		list.addAll(bigger);
		
		return list;
	}
	
	public List<Long> concat(List<Long> smaller, long val, List<Long> bigger)
	{
		ArrayList<Long> list = new ArrayList<Long>();
		
		list.addAll(smaller);
		list.add(val);
		list.addAll(bigger);
		
		return list;
	}
	
	public Map<Long, Integer> getUserMax(Set<Long> userIds)
		throws SQLException
	{
		System.out.println("Getting max links");
		
		HashMap<Long, Integer> userMax = new HashMap<Long, Integer>();
		
		StringBuffer buf = new StringBuffer("SELECT uid, max_links FROM trackUserUpdates WHERE uid IN (0");
		for (long id : userIds) {
			buf.append(",");
			buf.append(id);
		}
		buf.append(")");
		
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		ResultSet result = statement.executeQuery(buf.toString());
		
		while (result.next()) {
			long userId = result.getLong("uid");
			int max = result.getInt("max_links");
		
			userMax.put(userId, max);
		}
		
		return userMax;
	}
}
