package org.nicta.lr.recommender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
	
	public Map<Long, Double> getAveragePrecisions(Map<Long, Map<Long, Double>> predictions)
	{
		HashMap<Long, Double> averagePrecisions = new HashMap<Long, Double>();

		for (long userId : predictions.keySet()) {
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			for (long linkId: userPredictions.keySet()) {
				double prediction = userPredictions.get(linkId);
				
				scores.add(prediction);
				ids.add(linkId);
			}
	
			Object[] sorted = sort(scores, ids);
			double ap = getUserAP(sorted, userId);
			averagePrecisions.put(userId, ap);
		}
		
		return averagePrecisions;
	}
	
	public Integer[] getAUCMetrics(Map<Long, Map<Long, Double>> predictions, double threshold)
	{
		int truePos = 0;
		int falsePos = 0;
		int trueNeg = 0;
		int falseNeg = 0;
		
		for (long userId : predictions.keySet()) {
			
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			for (long linkId : userPredictions.keySet()) {
				double prediction = userPredictions.get(linkId);
				
				
				if (prediction >= threshold) {
					if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
						truePos++;
					}
					else {
						falsePos++;
					}
				}
				else {
					if (threshold == 0.0) {
						System.out.println("ANO TO:"  + prediction);
					}
					if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
						falseNeg++;
					}
					else {
						trueNeg++;
					}
				}
			}
		}
		
		return new Integer[]{truePos, falsePos, trueNeg, falseNeg};
	}
	
	public abstract Map<Long, Map<Long, Double>> getPredictions(Map<Long, Set<Long>> testData);
	
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
	
	public double getUserPrecision(Long[] ids, long userId)
	{	
		int truePos = 0;
		for (int x = 0; x < ids.length; x++) {
			long linkId = ids[x];
		
			if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
				truePos++;
			}
		}
	
		return (double)truePos / (double)ids.length;
	}
	
	public double getUserRecall(Long[] ids, long userId, Set<Long> testLinks)
	{
		
		int truePos = 0;
		for (int x = 0; x < ids.length; x++) {
			long linkId = ids[x];
		
			if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId) && testLinks.contains(linkId)) {
				truePos++;
			}
		}
	
		int totalPos = 0;
		for (long linkId : testLinks) {
			if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
				totalPos++;
			}
		}
		
		return (double)truePos / (double)totalPos;
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
		
		Statement statement = SQLUtil.getStatement();
		ResultSet result = statement.executeQuery(buf.toString());
		
		while (result.next()) {
			long userId = result.getLong("uid");
			int max = result.getInt("max_links");
		
			userMax.put(userId, max);
		}
		
		return userMax;
	}
	
	public double[] combineFeatures(Double[] user, Double[] link)
	{
		double[] feature = new double[user.length + link.length];
		
		for (int x = 0; x < user.length; x++) {
			feature[x] = user[x];
		}
		
		for (int x = 0; x < link.length; x++) {
			feature[x + user.length] = link[x];
		}
		
		return feature;
	}
}
