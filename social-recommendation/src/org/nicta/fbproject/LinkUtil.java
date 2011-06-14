package org.nicta.fbproject;

import java.util.HashMap;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import org.nicta.filters.StopWordChecker;
import opennlp.tools.lang.english.Tokenizer;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.sql.Date;

public class LinkUtil 
{	
	public static HashMap<Long, Double[]> getLinkFeatures()
		throws SQLException
	{
		HashMap<Long, Double[]> linkFeatures = new HashMap<Long, Double[]>();
		Connection conn = FBMethods.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String itemQuery = 
			"SELECT id, created_time, share_count, like_count, comment_count, total_count "
			+ "FROM linkrlinks, linkrlinkinfo "
			+ "WHERE linkrlinks.link_id = linkrlinkinfo.link_id";
		
		//System.out.println("Query: " + itemQuery);
		
		ResultSet result = statement.executeQuery(itemQuery);
	
		while (result.next()) {
			Double[] feature = new Double[FBConstants.LINK_FEATURE_COUNT];
			
			feature[0] = result.getDate("created_time").getTime() / 2000000000.0;
			feature[1] = result.getDouble("share_count") / 10000000;
			feature[2] = result.getDouble("like_count") / 10000000;
			feature[3] = result.getDouble("comment_count") / 10000000;
		   
			linkFeatures.put(result.getLong("id"), feature);
		}
		
		statement.close();
		conn.close();
		return linkFeatures;
	}
	
	public static HashMap<String, Integer> getMostCommonWords()
		throws SQLException, IOException
	{
		StopWordChecker swc = new StopWordChecker();
		Tokenizer tokenizer = new Tokenizer("./EnglishTok.bin.gz");
		
		HashMap<String, Integer> words = new HashMap<String, Integer>();
		
		Connection conn = FBMethods.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String wordsQuery = 
			"SELECT name, description, message "
			+ "FROM linkrlinks";
		
		ResultSet result = statement.executeQuery(wordsQuery);
		
		while (result.next()) {
			String title = result.getString("name");
			String summary = result.getString("description");
			String ownerComment = result.getString("description");
			
			String[] titleTokens = tokenizer.tokenize(title.toLowerCase());
			for (int x = 0; x < titleTokens.length; x++) {
				if (titleTokens[x].length() == 0 || swc.isStopWord(titleTokens[x])) continue;
				
				int count = 1;
				
				if (words.containsKey(titleTokens[x])) {
					count += words.get(titleTokens[x]);
				}
				
				words.put(titleTokens[x], count);
			}
			
			String[] summaryTokens = tokenizer.tokenize(summary.toLowerCase());
			for (int x = 0; x < summaryTokens.length; x++) {
				if (summaryTokens[x].length() == 0 || swc.isStopWord(summaryTokens[x])) continue;
				
				int count = 1;
				
				if (words.containsKey(summaryTokens[x])) {
					count += words.get(summaryTokens[x]);
				}
				
				words.put(summaryTokens[x], count);
			}
			
			String[] ownerCommentTokens = tokenizer.tokenize(ownerComment.toLowerCase());
			for (int x = 0; x < ownerCommentTokens.length; x++) {
				if (ownerCommentTokens[x].length() == 0 || swc.isStopWord(ownerCommentTokens[x])) continue;
				
				int count = 1;
				
				if (words.containsKey(ownerCommentTokens[x])) {
					count += words.get(ownerCommentTokens[x]);
				}
				
				words.put(ownerCommentTokens[x], count);
			}
		}
	
		statement.close();
		conn.close();
		
		HashSet<String> wordsToRemove = new HashSet<String>();
		
		for (String word : words.keySet()) {
			if (words.get(word) < FBConstants.MIN_COMMON_WORD_COUNT) {
				//wordsToRemove.add(word);
			}
			else {
				//System.out.println("Words: " + word + " Count: " + words.get(word));
			}
		}
		
		for (String word : wordsToRemove) {
			words.remove(word);
		}
		
		return words;
	}
	
	public static HashMap<Long, HashSet<String>> getLinkWordFeatures(Set<String> commonWords)
		throws SQLException, IOException
	{
		Tokenizer tokenizer = new Tokenizer("./EnglishTok.bin.gz");
		HashMap<Long, HashSet<String>> linkWords = new HashMap<Long, HashSet<String>>();
		
		Connection conn = FBMethods.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String wordQuery = 
			"SELECT id, message, name, description "
			+ "FROM linkrlinks";
		
		ResultSet result = statement.executeQuery(wordQuery);
		
		while (result.next()) {
			HashSet<String> words = new HashSet<String>();
			
			String[] ownerComment = tokenizer.tokenize(result.getString("message").toLowerCase());
			String[] title = tokenizer.tokenize(result.getString("name").toLowerCase());
			String[] summary = tokenizer.tokenize(result.getString("description").toLowerCase());
			
			for (String s : ownerComment) {
				if (commonWords.contains(s)) words.add(s);
			}
			
			for (String s : title) {
				if (commonWords.contains(s)) words.add(s);
			}
			
			for (String s : summary) {
				if (commonWords.contains(s)) words.add(s);
			}
			
			linkWords.put(result.getLong("id"), words);
		}
		
		statement.close();
		conn.close();
		
		return linkWords;
	}
	
	public static HashMap<Long, HashSet<Long>> getLinkLikes(Set<Long> linkIds)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> linkLikes = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = FBMethods.getSqlConnection();
		Statement statement = conn.createStatement();
		
		StringBuilder likeQuery = new StringBuilder("SELECT uid, post_id FROM linkrpostlikes WHERE post_id IN (0");
		for (long id : linkIds) {
			likeQuery.append(",");
			likeQuery.append(id);
		}
		likeQuery.append(")");
		
		System.out.println("Query: " + likeQuery);
		
		ResultSet result = statement.executeQuery(likeQuery.toString());
		
		while (result.next()) {
			long uId = result.getLong("uid");
			long postId = result.getLong("post_id");
			
			if (!linkLikes.containsKey(postId)) {
				linkLikes.put(postId, new HashSet<Long>());
			}
			
			linkLikes.get(postId).add(uId);
		}
		statement.close();
		conn.close();
		
		return linkLikes;
	}
}
