package org.nicta.fbproject;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.io.File;

import opennlp.tools.lang.english.Tokenizer;

import org.nicta.filters.StopWordChecker;

public abstract class Recommender 
{
	private Connection connection = null;
	
	public double calcRMSE(HashMap<Long, Double[]> userTraits, HashMap<Long, Double[]> linkTraits, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		double error = 0;
		
		//Get the square error
		int count = 0;
		for (long i : userTraits.keySet()) {
			HashSet<Long> links = userLinkSamples.get(i);
			
			for (long j : links) {
				int liked = 0;
				
				if (linkLikes.containsKey(j) && linkLikes.get(j).contains(i)) liked = 1;
				double predictedLike = dot(userTraits.get(i), linkTraits.get(j));
	
				//if (predictedLike < 0) predictedLike = 0;
				//if (predictedLike > 1) predictedLike = 1;
				
				error += Math.pow(liked - predictedLike, 2);
				
				count++;
			}
		}

		return Math.sqrt(error / count);
	}
	
	public HashMap<Long, Double[]> getUserTraitVectors(Double[][] matrix, 
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
	
	public HashMap<Long, Double[]> getLinkTraitVectors(Double[][] matrix, 
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

	
	public Connection getSqlConnection()
		throws SQLException
	{
		try {
			Class.forName ("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException ce) {
			System.out.println("MySQL driver not found");
			System.exit(1);
		}
		
		if (connection != null) {
			return connection;
		}
		else {
			connection = DriverManager.getConnection(Constants.DB_STRING);
			connection.setAutoCommit(false);
			return connection;
		}
	}

	public void closeSqlConnection()
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
	
	public HashMap<Long, Double[]> getMatrixIdColumns(Set<Long> ids)
	{
		Random random = new Random();
		
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		for (long id : ids) {
			Double[] column = new Double[Constants.K];
			
			for (int x = 0; x < column.length; x++) {
				column[x] = random.nextGaussian();
				//column[x] = 0.0;
			}
			
			idColumns.put(id, column);
		}
		
		return idColumns;
	}

	public HashMap<String, Double[]> getWordColumns(Set<String> words)
	{
		Random random = new Random();
		
		HashMap<String, Double[]> wordColumns = new HashMap<String, Double[]>();
		
		for (String word : words) {
			Double[] column = new Double[Constants.K];
			
			for (int x = 0; x < column.length; x++) {
				column[x] = random.nextGaussian();
				//column[x] = 0.0;
			}
			
			wordColumns.put(word, column);
		}
		
		return wordColumns;
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
			+ "FROM linkrUser "
			+ "LEFT JOIN linkrLocation location ON location_id=location.id "
			+ "LEFT JOIN linkrLocation hometown ON hometown_id=location.id";
		
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
		;
		
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
			+ "FROM linkrFriends";
		
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
			+ "FROM linkrLinks, linkrLinkInfo "
			+ "WHERE linkrLinks.link_id = linkrLinkInfo.link_id "
			+ "AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + "))";
		
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
		
		return linkFeatures;
	}
	
	public HashSet<String> loadMostCommonWords()
		throws SQLException
	{
		HashSet<String> words = new HashSet<String>();
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		ResultSet result = statement.executeQuery("SELECT DISTINCT word FROM lrWordColumns");
		while (result.next()) {
			words.add(result.getString("word"));
		}
		
		statement.close();
		return words;
	}
	
	public Set<String> getMostCommonWords()
		throws SQLException, IOException
	{
		StopWordChecker swc = new StopWordChecker();
		Tokenizer tokenizer = new Tokenizer("./EnglishTok.bin.gz");
		
		HashMap<String, Integer> words = new HashMap<String, Integer>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		String wordsQuery = 
			"SELECT name, description, message "
			+ "FROM linkrLinks";
		
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
	
	public HashMap<Long, HashSet<String>> getLinkWordFeatures(Set<String> commonWords)
		throws SQLException, IOException
	{
		
		Tokenizer tokenizer = new Tokenizer("./EnglishTok.bin.gz");
		
		
		HashMap<Long, HashSet<String>> linkWords = new HashMap<Long, HashSet<String>>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		String wordQuery = 
			"SELECT id, message, name, description "
			+ "FROM linkrLinks "
			+ "WHERE DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + "))";
		
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
		
		
		return linkWords;
	}
	
	public HashMap<Long, HashSet<Long>> getLinkLikes(Set<Long> linkIds)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> linkLikes = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		int count = 0;
		StringBuilder likeQuery = new StringBuilder("SELECT id, link_id FROM linkrLinkLikes WHERE link_id IN (0");
		for (long id : linkIds) {
			likeQuery.append(",");
			likeQuery.append(id);
			count++;
		}
		likeQuery.append(") ");
		
		ResultSet result = statement.executeQuery(likeQuery.toString());
		
		int likeCount = 0;
		while (result.next()) {
			long uId = result.getLong("id");
			long postId = result.getLong("link_id");
			
			if (!linkLikes.containsKey(postId)) {
				linkLikes.put(postId, new HashSet<Long>());
			}
			
			likeCount++;
			linkLikes.get(postId).add(uId);
		}
		
		statement.close();
		
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
			ResultSet result = statement.executeQuery("SELECT l.id FROM linkrLinks l, linkrLinkLikes lp WHERE l.id=lp.link_id AND lp.id=" + id
					             						+ " AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + "))");
			while (result.next()) {
				samples.add(result.getLong("l.id"));
			}
			
			//Sample links that weren't liked. Will be equal to number of links that were liked.
			HashSet<Long> friends = friendships.get(id);
			if (friends == null) friends = new HashSet<Long>();
			
			StringBuilder query = new StringBuilder("SELECT id FROM linkrLinks WHERE uid IN (0");
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			query.append(") AND id NOT IN(0");
			for (Long likedId : samples) {
				query.append(",");
				query.append(likedId);
			}	
			query.append(") AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.WINDOW_RANGE + ")) ");
			query.append("ORDER BY created_time DESC LIMIT ");
			query.append(samples.size() * 9);
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				samples.add(result.getLong("id"));
			}
		}
		
		return userLinkSamples;
	}
	
	public HashMap<Long, HashSet<Long>> getLinksForRecommending(HashMap<Long, HashSet<Long>> friendships, String type)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinks = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		HashSet<Long> userIds = new HashSet<Long>();
		ResultSet result = statement.executeQuery("SELECT linkrLinks.uid FROM linkrLinks, trackUserUpdates "
													+ "WHERE linkrLinks.uid=trackUserUpdates.uid "
													+ "AND priority=1");
		
		while (result.next()) {
			userIds.add(result.getLong("uid"));
		}
		
		for (Long id : userIds) {
			HashSet<Long> links = new HashSet<Long>();
			userLinks.put(id, links);
			
			//Sample links that weren't liked. Will be equal to number of links that were liked.
			HashSet<Long> friends = friendships.get(id);
			if (friends == null) friends = new HashSet<Long>();
			
			HashSet<Long> dontInclude = new HashSet<Long>();
			
			result = statement.executeQuery("SELECT l.id FROM linkrLinks l, linkrPostLikes lp WHERE l.id=lp.post_id AND lp.id=" + id);
			while (result.next()) {
				dontInclude.add(result.getLong("l.id"));
			}
			
			result = statement.executeQuery("SELECT link_id FROM lrRecommendations WHERE user_id=" + id + " AND type='" + type + "'");
			while(result.next()) {
				dontInclude.add(result.getLong("link_id"));
			}
			
			StringBuilder query = new StringBuilder("SELECT id FROM linkrLinks WHERE uid NOT IN (0");
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			query.append(") AND id NOT IN(0");
			for (long linkIds : dontInclude) {
				query.append(",");
				query.append(linkIds);
			}
			
			query.append(") ORDER BY created_time DESC LIMIT 5");
			
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				links.add(result.getLong("id"));
			}
		}
		
		return userLinks;
	}
	
	public HashMap<Long, HashMap<Long, Double>> recommendLinks(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
																HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, 
																HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures,
																HashMap<Long, HashSet<Long>> linksToRecommend, HashMap<Long, HashSet<String>> linkWords,
																HashMap<String, Double[]> wordColumns)
	{
		HashMap<Long, HashMap<Long, Double>> recommendations = new HashMap<Long, HashMap<Long, Double>>();
		
		HashMap<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		HashMap<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures, linkWords, wordColumns);
		
		for (long userId :linksToRecommend.keySet()) {
			HashSet<Long> userLinks = linksToRecommend.get(userId);
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
			
			for (long linkId : userLinks) {
				if (!linkTraits.containsKey(linkId)) continue;
				
				double prediction = dot(userTraits.get(userId), linkTraits.get(linkId));
				linkValues.put(linkId, prediction);
			}
		}
		
		return recommendations;
	}
	
	public void saveLinkRecommendations(HashMap<Long, HashMap<Long, Double>> recommendations, String type)
		throws SQLException
	{
		Connection conn = getSqlConnection();
		
		Statement statement = conn.createStatement();
		
		for (long userId : recommendations.keySet()) {
			HashMap<Long, Double> recommendedLinks = recommendations.get(userId);
			
			//statement.executeUpdate("DELETE FROM " + tableName + " WHERE user_id=" + userId);
			
			for (long linkId : recommendedLinks.keySet()) {
				PreparedStatement ps = conn.prepareStatement("INSERT INTO lrRecommendations VALUES(?,?,?,CURRENT_DATE(),?)");
				ps.setLong(1, userId);
				ps.setLong(2, linkId);
				ps.setDouble(3, recommendedLinks.get(linkId));
				ps.setString(4, type);
				
				ps.executeUpdate();
				ps.close();
			}
		}
		
		statement.close();
		
	}
	
	public Double[][] loadFeatureMatrix(String tableName, int featureCount, String type)
		throws SQLException
	{
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		Double[][] matrix = new Double[Constants.K][featureCount];
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id < " + Constants.K + " AND type='" + type + "'");
		
		int count = 0;
		
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
	
	public HashMap<Long, Double[]> loadIdColumns(String tableName, String type)
		throws SQLException
	{
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id >" + Constants.K + " AND type='" + type + "'");
		while (result.next()) {
			long id = result.getLong("id");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			Double[] val = new Double[Constants.K];
			for (int x = 0; x < Constants.K; x++) {
				val[x] = Double.parseDouble(tokens[x]);
			}
			
			idColumns.put(id, val);
		}
		
		statement.close();
		
		
		return idColumns;
	}
	
	public void saveMatrices(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<String, Double[]> wordColumns, String type)
		throws SQLException
	{
		Connection conn = getSqlConnection();
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
		
		for (String word : wordColumns.keySet()) {
			StringBuilder buf = new StringBuilder();
			
			Double[] col = wordColumns.get(word);
			for (int x = 0; x < Constants.K; x++) {
				buf.append(col[x]);
				buf.append(",");
			}
			
			PreparedStatement wordInsert = conn.prepareStatement("INSERT INTO lrWordColumns VALUES(?,?,?)");
			wordInsert.setString(1, word);
			wordInsert.setString(2, buf.toString());
			wordInsert.setString(3, type);
			wordInsert.executeUpdate();
			wordInsert.close();
		}
	}
	
	public HashMap<String, Double[]> loadWordColumns(String tableName, String type)
		throws SQLException
	{
		HashMap<String, Double[]> columns = new HashMap<String, Double[]>();
		
		Connection conn = getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE type='" + type + "'");
		while (result.next()) {
			String word = result.getString("word");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			Double[] val = new Double[Constants.K];
			for (int x = 0; x < Constants.K; x++) {
				val[x] = Double.parseDouble(tokens[x]);
			}
			
			columns.put(word, val);
		}
		
		statement.close();
		
		return columns;
	}
	
	public void updateMatrixColumns(Set<Long> ids, HashMap<Long, Double[]> idColumns)
	{
		HashSet<Long> columnsToRemove = new HashSet<Long>();
		
		//Remove columns of links that are past the range
		for (long id : idColumns.keySet()) {
			if (!ids.contains(id)) {
				columnsToRemove.add(id);
			}
		}
		for (long id : columnsToRemove) {
			idColumns.remove(id);
		}
		
		//Add columns for new links
		HashSet<Long> columnsToAdd = new HashSet<Long>();
		
		for (long id : ids) {
			if (!idColumns.containsKey(id)) {
				columnsToAdd.add(id);
			}
		}
		HashMap<Long, Double[]> newColumns = getMatrixIdColumns(columnsToAdd);
		idColumns.putAll(newColumns);
	}
	
	public abstract void recommend() throws Exception;
}
