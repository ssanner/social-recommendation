package org.nicta.fbproject;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import opennlp.tools.lang.english.Tokenizer;

import org.nicta.filters.StopWordChecker;

public abstract class Recommender 
{
	public double calcRMSE(HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		double error = 0;
		
		//Get the square error
		for (long i : userTraits.keySet()) {
			HashSet<Long> links = userLinkSamples.get(i);
			
			for (long j : links) {
				int liked = 0;
				
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				double predictedLike = dot(userTraits.get(i), linkTraits.get(j));
				if (liked == 1) System.out.println ("Like: " + liked + " Predicted: " + predictedLike);
				if (predictedLike < 0) predictedLike = 0;
				if (predictedLike > 1) predictedLike = 1;
				
				error += Math.pow(liked - predictedLike, 2);
			}
		}

		return Math.sqrt(error / (userTraits.size() * linkTraits.size()));
	}
	
	public HashMap<Long, Double[]> getTraitVectors(Double[][] matrix, 
													HashMap<Long, Double[]> idColumns,
													HashMap<Long, Double[]> features)
	{
		HashMap<Long, Double[]> traitVectors = new HashMap<Long, Double[]>();
		
		for (long id : features.keySet()) {
			Double[] feature = features.get(id);
			Double[] vector = new Double[Constants.K];
			Double[] idColumn = idColumns.get(id);
		
			for (int x = 0; x < Constants.K; x++) {
				vector[x] = 0.0;
		
				for (int y = 0; y < feature.length; y++) {
					vector[x] += matrix[x][y] * feature[y];
				}
		
				vector[x] += idColumn[x];
			}
		
			traitVectors.put(id, vector);
		}
		
		return traitVectors;
	}
	
	public Connection getSqlConnection()
		throws SQLException
	{
		try {
			Class.forName ("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException ce) {
			System.out.println("Shit");
			System.exit(1);
		}
		
		return DriverManager.getConnection(Constants.DB_STRING);
	}

	public Double[][] getPrior(int featureCount)
	{
		Random random = new Random();
		
		Double[][] prior = new Double[Constants.K][featureCount];
		
		for (int x = 0; x < Constants.K; x++) {
			for (int y = 0; y < featureCount; y++) {
				prior[x][y] = random.nextGaussian();
			}
		}
		
		return prior;
	}

	public HashMap<Long, Double[]> getMatrixIdColumns(Set<Long> ids)
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

	public double dot(Double[] vec1, Double[] vec2)
	{
		double prod = 0;
		
		for (int x = 0; x < vec1.length; x++) {
			prod += vec1[x] * vec2[x];
		}
		
		return prod;
	}
	
	public HashMap<Long, Double[]> getUserFeatures()
		throws SQLException
	{
		HashMap<Long, Double[]> userFeatures = new HashMap<Long, Double[]>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		String userQuery = 
			"SELECT uid, gender, birthday, location.name, hometown.name "
			+ "FROM linkruser "
			+ "LEFT JOIN linkrlocation location ON location_id=location.id "
			+ "LEFT JOIN linkrlocation hometown ON hometown_id=location.id";
		
		ResultSet result = statement.executeQuery(userQuery);
		
		while (result.next()) {
			String sex = result.getString("gender");
			int birthYear = 0;
			String birthday = result.getString("birthday");
			if (birthday.length() == 10) {
				birthYear = Integer.parseInt(birthday.split("/")[2]);
			}
			double currentLocation = 0;
			String currentLoc = result.getString("location.name");
			if (currentLoc != null) {
				//System.out.println(currentLoc);
				for (int x = 0; x < Constants.COUNTRIES.length; x++) {
					if (currentLoc.contains(Constants.COUNTRIES[x])) {
						currentLocation = x;
						break;
					}
				}
			}
			
			double hometownLocation = 0;
			String hometownLoc = result.getString("hometown.name");
			if (hometownLoc != null) {
				for (int x = 0; x < Constants.COUNTRIES.length; x++) {
					if (hometownLoc.contains(Constants.COUNTRIES[x])) {
						hometownLocation = x;
						break;
					}
				}
			}
			
			Double[] feature = new Double[Constants.USER_FEATURE_COUNT];
			if ("male".equals(sex)) {
				feature[0] = 1.0;
			}
			else if ("female".equals(sex)){
				feature[0] = 2.0;
			}
			else {
				feature[0] = 0.0;
			}
			
			feature[1] = birthYear / 2012.0;
			
			feature[2] = currentLocation / Constants.COUNTRIES.length;
			feature[3] = hometownLocation / Constants.COUNTRIES.length;
			
			userFeatures.put(result.getLong("uid"), feature);
		}
		
		statement.close();
		conn.close();
		
		return userFeatures;
	}

	public HashMap<Long, HashSet<Long>> getFriendships()
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> friendships = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		String friendQuery =
			"SELECT uid1, uid2 "
			+ "FROM linkrfriends";
		
		ResultSet result = statement.executeQuery(friendQuery);
		
		while (result.next()) {
			Long uid1 = result.getLong("uid1");
			Long uid2 = result.getLong("uid2");
			
			if (!friendships.containsKey(uid1)) {
				friendships.put(uid1, new HashSet<Long>());
			}
			if (!friendships.containsKey(uid2)) {
				friendships.put(uid2, new HashSet<Long>());
			}
			
			friendships.get(uid1).add(uid2);
			friendships.get(uid2).add(uid1);
		}
		
		statement.close();
		conn.close();
		
		return friendships;
	}

	public boolean areFriends(Long uid1, Long uid2, HashMap<Long, HashSet<Long>> friendships)
	{
		if ((friendships.containsKey(uid1) && friendships.get(uid1).contains(uid2))
			|| (friendships.containsKey(uid2) && friendships.get(uid2).contains(uid1))) {
			return true;
		}
		
		return false;
	}
	
	public HashMap<Long, Double[]> getLinkFeatures()
		throws SQLException
	{
		HashMap<Long, Double[]> linkFeatures = new HashMap<Long, Double[]>();
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		String itemQuery = 
			"SELECT id, created_time, share_count, like_count, comment_count, total_count "
			+ "FROM linkrlinks, linkrlinkinfo "
			+ "WHERE linkrlinks.link_id = linkrlinkinfo.link_id";
		
		//System.out.println("Query: " + itemQuery);
		
		ResultSet result = statement.executeQuery(itemQuery);
	
		while (result.next()) {
			Double[] feature = new Double[Constants.LINK_FEATURE_COUNT];
			
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
	
	public HashMap<String, Integer> getMostCommonWords()
		throws SQLException, IOException
	{
		StopWordChecker swc = new StopWordChecker();
		Tokenizer tokenizer = new Tokenizer("./EnglishTok.bin.gz");
		
		HashMap<String, Integer> words = new HashMap<String, Integer>();
		
		Connection conn = getSqlConnection();
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
			if (words.get(word) < Constants.MIN_COMMON_WORD_COUNT) {
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
	
	public HashMap<Long, HashSet<String>> getLinkWordFeatures(Set<String> commonWords)
		throws SQLException, IOException
	{
		Tokenizer tokenizer = new Tokenizer("./EnglishTok.bin.gz");
		HashMap<Long, HashSet<String>> linkWords = new HashMap<Long, HashSet<String>>();
		
		Connection conn = getSqlConnection();
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
	
	public HashMap<Long, HashSet<Long>> getLinkLikes(Set<Long> linkIds)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> linkLikes = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		StringBuilder likeQuery = new StringBuilder("SELECT id, post_id FROM linkrpostlikes WHERE post_id IN (0");
		for (long id : linkIds) {
			likeQuery.append(",");
			likeQuery.append(id);
		}
		likeQuery.append(")");
		
		System.out.println("Query: " + likeQuery);
		
		ResultSet result = statement.executeQuery(likeQuery.toString());
		
		while (result.next()) {
			long uId = result.getLong("id");
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
	
	public HashMap<Long, HashSet<Long>> getUserLinksSample(Set<Long> userIds, HashMap<Long, HashSet<Long>> friendships)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinkSamples = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		for (Long id : userIds) {
			HashSet<Long> samples = new HashSet<Long>();
			userLinkSamples.put(id, samples);
			
			//Get the links that were liked
			ResultSet result = statement.executeQuery("SELECT l.id FROM linkrlinks l, linkrpostlikes lp WHERE l.id=lp.post_id AND lp.id=l.id");
			while (result.next()) {
				samples.add(result.getLong("l.id"));
			}
			
			//Sample links that weren't liked. Will be equal to number of links that were liked.
			HashSet<Long> friends = friendships.get(id);
			
			StringBuilder query = new StringBuilder("SELECT id FROM linkrlinks WHERE uid IN (0");
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			query.append(") AND id NOT IN(0");
			for (Long likedId : samples) {
				query.append(",");
				query.append(likedId);
			}
			query.append(") ORDER BY created_time DESC LIMIT ");
			query.append(samples.size());
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				samples.add(result.getLong("id"));
			}
		}
		
		return userLinkSamples;
	}
}
