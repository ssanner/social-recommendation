package org.nicta.lr.util;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import opennlp.tools.lang.english.Tokenizer;

import org.nicta.filters.StopWordChecker;

public class LinkUtil 
{
	/**
	 * Gets the link features. Link features are normalized between 0 and 1.
	 * Because we're doing online updating, get only the most recent links within a specified window.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static HashMap<Long, Double[]> getLinkFeatures(boolean limit)
		throws SQLException
	{
		HashMap<Long, Double[]> linkFeatures = new HashMap<Long, Double[]>();
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String itemQuery = 
			"SELECT link_id, created_time, share_count, like_count, comment_count, total_count, uid, from_id "
			+ "FROM linkrLinks, linkrLinkInfo "
			+ "WHERE linkrLinks.link_hash = linkrLinkInfo.link_hash ";
		
		if (limit) {
			itemQuery += "AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + "))";
		}
		
		ResultSet result = statement.executeQuery(itemQuery);
	
		while (result.next()) {
			Double[] feature = new Double[Constants.LINK_FEATURE_COUNT];
			
			feature[0] = result.getDouble("share_count") / 10000000;
			feature[1] = result.getDouble("like_count") / 10000000;
			feature[2] = result.getDouble("comment_count") / 10000000;
			feature[3] = result.getDouble("uid") / Double.MAX_VALUE;
			feature[4] = result.getDouble("from_id") / Double.MAX_VALUE;
			
			linkFeatures.put(result.getLong("link_id"), feature);
		}
		
		statement.close();
		return linkFeatures;
	}
	
	/**
	 * Given a list of links, get the list of users that have 'liked' that link on FB.
	 * 
	 * @param linkIds
	 * @return
	 * @throws SQLException
	 */
	public static HashMap<Long, HashSet<Long>> getLinkLikes(Set<Long> linkIds)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> linkLikes = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		//id is the user_id of the user who liked that link, link_id is the id of the link
		StringBuilder likeQuery = new StringBuilder("SELECT ll.id, ll.link_id, l.from_id, l.uid FROM linkrLinkLikes ll, linkrLinks l "
													+ "WHERE ll.link_id=l.link_id AND ll.link_id IN (0");
		for (long id : linkIds) {
			likeQuery.append(",");
			likeQuery.append(id);
		}
		likeQuery.append(")");
		
		ResultSet result = statement.executeQuery(likeQuery.toString());
		
		while (result.next()) {
			long uId = result.getLong("ll.id");
			long linkId = result.getLong("ll.link_id");
			long fromId = result.getLong("l.from_id");
			long uid2 = result.getLong("l.uid");
			
			if (!linkLikes.containsKey(linkId)) {
				linkLikes.put(linkId, new HashSet<Long>());
			}
			
			HashSet<Long> likes = linkLikes.get(linkId);
			
			likes.add(uId);
			likes.add(fromId);
			likes.add(uid2);
		}
		
		
		StringBuilder clickQuery = new StringBuilder("SELECT link_id, uid_clicked FROM linkrLinks l, trackLinkClicked t WHERE l.link=t.link AND link_id IN (0");
		for (long id : linkIds) {
			clickQuery.append(",");
			clickQuery.append(id);
		}
		clickQuery.append(")");
		result = statement.executeQuery(clickQuery.toString());
		
		while (result.next()) {
			long linkId = result.getLong("link_id");
			long userId = result.getLong("uid_clicked");
			
			if (!linkLikes.containsKey(linkId)) {
				linkLikes.put(linkId, new HashSet<Long>());
			}
			
			HashSet<Long> likes = linkLikes.get(linkId);
			
			likes.add(userId);
		}
		
		StringBuilder scoreQuery = new StringBuilder("SELECT l.link_id, t.uid FROM linkrLinks l, trackRecommendedLinks t WHERE l.link_id=t.link_id AND rating=1 AND l.link_id IN (0");
		for (long id : linkIds) {
			scoreQuery.append(",");
			scoreQuery.append(id);
		}
		scoreQuery.append(")");
		result = statement.executeQuery(scoreQuery.toString());
		
		while (result.next()) {
			long linkId = result.getLong("link_id");
			long userId = result.getLong("uid");
			
			if (!linkLikes.containsKey(linkId)) {
				linkLikes.put(linkId, new HashSet<Long>());
			}
			
			HashSet<Long> likes = linkLikes.get(linkId);
			
			likes.add(userId);
		}
		
		statement.close();
		
		return linkLikes;
	}
	
	/**
	 * Finds the most common words used in link descriptions and messages.
	 * Words are parsed using the opennlp English dictionary tokenizer.
	 * Words that reach a minimum occurence count are included
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public static Set<String> getMostCommonWords()
		throws SQLException, IOException
	{
		StopWordChecker swc = new StopWordChecker();
		Tokenizer tokenizer = new Tokenizer("./EnglishTok.bin.gz");
		
		HashMap<String, Integer> words = new HashMap<String, Integer>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String wordsQuery = 
			"SELECT name, description, message "
			+ "FROM linkrLinks";
		
		ResultSet result = statement.executeQuery(wordsQuery);
		
		while (result.next()) {
			String title = result.getString("name");
			String summary = result.getString("description");
			String ownerComment = result.getString("message");
			
			//Update word counts for each word
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
		
		
		//Now remove all words that do not reach the minimum count
		HashSet<String> wordsToRemove = new HashSet<String>();
		
		for (String word : words.keySet()) {
			if (words.get(word) < Constants.MIN_COMMON_WORD_COUNT) {
				wordsToRemove.add(word);
			}
		}
		
		for (String word : wordsToRemove) {
			words.remove(word);
		}
		
		return words.keySet();
	}
	
	/**
	 * Given a list of most common words, find which words appear in the links.
	 * Bag-of-Words representation is used to map words and links, we do not care about word counts.
	 * @param commonWords
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 */
	public static HashMap<Long, HashSet<String>> getLinkWordFeatures(Set<String> commonWords, boolean limit)
		throws SQLException, IOException
	{
		Tokenizer tokenizer = new Tokenizer("./EnglishTok.bin.gz");
		
		HashMap<Long, HashSet<String>> linkWords = new HashMap<Long, HashSet<String>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		String wordQuery = 
			"SELECT link_id, message, name, description "
			+ "FROM linkrLinks ";
		
		if (limit) {
			wordQuery += "WHERE DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + "))";
		}
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
			
			linkWords.put(result.getLong("link_id"), words);
		}
		
		statement.close();
		
		
		return linkWords;
	}
	
	/**
	 * Load the previously saved most common words from the database.
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static HashSet<String> loadMostCommonWords()
		throws SQLException
	{
		HashSet<String> words = new HashSet<String>();
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		ResultSet result = statement.executeQuery("SELECT DISTINCT word FROM lrWordColumns");
		while (result.next()) {
			words.add(result.getString("word"));
		}
		
		statement.close();
		return words;
	}
	
	/**
	 * Calculates t=Vy where V is the latent matrix and y is the link feature vector.
	 * 
	 * @param matrix
	 * @param idColumns
	 * @param features
	 * @param linkWords
	 * @param wordColumns
	 * @return
	 */
	public static HashMap<Long, Double[]> getLinkTraitVectors(Double[][] matrix, 
														HashMap<Long, Double[]> idColumns,
														HashMap<Long, Double[]> features,
														HashMap<Long, HashSet<String>> linkWords,
														HashMap<String, Double[]> wordColumns)
	{
		HashMap<Long, Double[]> traitVectors = new HashMap<Long, Double[]>();
	
		for (long id : features.keySet()) {
			Double[] feature = features.get(id);
			Double[] idColumn = idColumns.get(id);
			
			HashSet<String> words = linkWords.get(id);
			
			Double[] vector = new Double[Constants.K];
			
			for (int x = 0; x < Constants.K; x++) {
				vector[x] = 0.0;
	
				//System.out.println("Feature: " + feature);
				//System.out.println();
					
				for (int y = 0; y < feature.length; y++) {
					vector[x] += matrix[x][y] * feature[y];
				}
	
				for (String word : words) {
					vector[x] += wordColumns.get(word)[x];
				}
				
				vector[x] += idColumn[x];
			}
	
			traitVectors.put(id, vector);
		}
	
		return traitVectors;
	}
}
