package org.nicta.lr;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.nicta.lr.minimizer.FeatureMinimizer;
import org.nicta.lr.minimizer.MSRSocialMinimizer;
import org.nicta.lr.minimizer.Minimizer;
import org.nicta.lr.minimizer.SocialMinimizer;
import org.nicta.lr.minimizer.LogisticSocialMinimizer;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.RecommenderUtil;
import org.nicta.lr.util.UserUtil;

public class LinkRecommender 
{
	private Minimizer minimizer;
	String type;
	
	public LinkRecommender(String type)
	{
		this.type = type;
		
		if ("feature".equals(type)) {
			minimizer = new FeatureMinimizer();
		}
		else if ("social".equals(type)) {
			minimizer = new SocialMinimizer();
		}	
		else if ("logistic".equals(type)) {
			minimizer = new LogisticSocialMinimizer();
		}
		else if ("msr".equals(type)) {
			minimizer = new MSRSocialMinimizer();
		}
	}
	
	public static void main(String[] args)
		throws Exception
	{
		LinkRecommender lr = null;
		String type = "social";
		if (args.length > 0) {
			type = args[0];
		}
		
		if (type.equals("feature")) {
			lr = new LinkRecommender("feature");
		}
		else if (type.equals("social")) {
			lr = new LinkRecommender("social");
		}
		else if (type.equals("logistic")) {
			lr = new LinkRecommender("logistic");
		}
		else if (type.equals("svm")) {
			lr = new SVMRecommender();
		}
		else if (type.equals("nn")) {
			lr = new NNRecommender();
		}
		else {
			System.out.println("WTF: " + args[0]);
		}
		
		//lr.recommend();
		lr.crossValidate();
		
		
		/*
		Constants.BETA = .000001;
		lr.crossValidate();
	
		Constants.BETA = .00001;
		lr.crossValidate();
		
		Constants.BETA = .0001;
		lr.crossValidate();
		
		Constants.BETA  = .001;
		lr.crossValidate();
		
		Constants.BETA  = .01;
		lr.crossValidate();
		
		Constants.BETA  = .1;
		lr.crossValidate();
		
		Constants.BETA  = 1;
		lr.crossValidate();
		
		Constants.BETA  = 10;
		lr.crossValidate();
		
		Constants.BETA  = 100;
		lr.crossValidate();
		
		Constants.BETA  = 1000;
		lr.crossValidate();
		
		Constants.BETA  = 10000;
		lr.crossValidate();
		*/
	}
	
	public void crossValidate()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(false);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(links.keySet());
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(linkLikes, users.keySet(), friendships, links.keySet(), false);
		System.out.println("Samples: " + userLinkSamples.size());
		
		HashMap<Long, HashMap<Long, Double>> friendConnections = UserUtil.getFriendInteractionMeasure();
		//HashMap<Long, HashMap<Long, Double>> friendConnections = UserUtil.getFriendLikeSimilarity(userLinkSamples.keySet());
		//HashMap<Long, HashMap<Long, Double>> friendConnections = friendships;
		
		//Set<String> words = LinkUtil.getMostCommonWords();
		Set<String> words = new HashSet<String>();
		
		System.out.println("Words: " + words.size());
		HashMap<Long, HashSet<String>> linkWords = LinkUtil.getLinkWordFeatures(words, false);

		RecommenderUtil.closeSqlConnection();
		
		HashMap<Long, HashSet<Long>> tested = new HashMap<Long, HashSet<Long>>();
		for (long userId : userLinkSamples.keySet()) {
			tested.put(userId, new HashSet<Long>());
		}
		
		double totalTruePos = 0;
		double totalFalsePos = 0;
		double totalTrueNeg = 0;
		double totalFalseNeg = 0;
		
		HashMap<Long, Double> averagePrecision = new HashMap<Long, Double>();
		HashMap<Long, Integer> precisionCount = new HashMap<Long, Integer>();
		
		for (int x = 0; x < 10; x++) {
			HashMap<Long, HashSet<Long>> forTesting = new HashMap<Long, HashSet<Long>>();
			
			for (long userId : userLinkSamples.keySet()) {
				HashSet<Long> userTesting = new HashSet<Long>();
				forTesting.put(userId, userTesting);
				
				HashSet<Long> samples = userLinkSamples.get(userId);
				HashSet<Long> userTested = tested.get(userId);
				
				
				Object[] sampleArray = samples.toArray();
				
				int addedCount = 0;
				
				while (addedCount < sampleArray.length * .1) {
					if (sampleArray.length == userTested.size()) break;
					
					int randomIndex = (int)(Math.random() * (sampleArray.length));
					Long randomLinkId = (Long)sampleArray[randomIndex];
					
					if (!tested.get(userId).contains(randomLinkId) && ! userTesting.contains(randomLinkId)) {
						userTesting.add(randomLinkId);
						tested.get(userId).add(randomLinkId);
						samples.remove(randomLinkId);
						addedCount++;
					}
				}		
			}
			
			Double[][] userFeatureMatrix = getPrior(Constants.USER_FEATURE_COUNT);
			Double[][] linkFeatureMatrix = getPrior(Constants.LINK_FEATURE_COUNT);
	
			HashMap<Long, Double[]> userIdColumns = getMatrixIdColumns(users.keySet());
			HashMap<Long, Double[]> linkIdColumns = getMatrixIdColumns(links.keySet());
			HashMap<String, Double[]> wordColumns = getWordColumns(words);
			
			minimizer.minimize(linkLikes, userFeatureMatrix, linkFeatureMatrix, users, links, friendConnections, userIdColumns, linkIdColumns, userLinkSamples, wordColumns, linkWords, words);
			
			HashMap<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, users);
			HashMap<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, links, linkWords, wordColumns);
			
			int[] stats = RecommenderUtil.calcStats(userTraits, linkTraits, linkLikes, forTesting);
			int truePos = stats[0];
			int falsePos = stats[1];
			int trueNeg = stats[2];
			int falseNeg = stats[3];
			
			totalTruePos += truePos;
			totalFalsePos += falsePos;
			totalTrueNeg += trueNeg;
			totalFalseNeg += falseNeg;
			
			HashMap<Long, Double> userAP = RecommenderUtil.getAveragePrecision(userTraits, linkTraits, linkLikes, forTesting);
			
			for (long userId : userAP.keySet()) {
				double ap = userAP.get(userId);
				if (ap == 0) continue;
				
				if (!averagePrecision.containsKey(userId)) {
					averagePrecision.put(userId, 0.0);
					precisionCount.put(userId, 0);
				}
				
				double average = averagePrecision.get(userId);
				average += ap;
				averagePrecision.put(userId, average);
				
				int count = precisionCount.get(userId);
				count++;
				precisionCount.put(userId, count);
			}
			
			System.out.println("Stats for Run " + (x+1));
			System.out.println("True Pos: "+ truePos);
			System.out.println("False Pos: "+ falsePos);
			System.out.println("True Neg: "+ trueNeg);
			System.out.println("False Neg: "+ falseNeg);
			System.out.println("");
			
			for (long userId : forTesting.keySet()) {
				HashSet<Long> tests = forTesting.get(userId);
				for (long linkId : tests) {
					userLinkSamples.get(userId).add(linkId);
				}
			}
		}
		
		double accuracy = (double)(totalTruePos + totalTrueNeg) / (double)(totalTruePos + totalFalsePos + totalTrueNeg + totalFalseNeg);
		double precision = (double)totalTruePos / (double)(totalTruePos + totalFalsePos);
		double recall = (double)totalTruePos / (double)(totalTruePos + totalFalseNeg);
		double f1 = 2 * precision * recall / (precision + recall);
		
		double map = 0;
		for (long userId : averagePrecision.keySet()) {
			double pre = averagePrecision.get(userId);
			//pre /= (double)precisionCount.get(userId);
			pre /= (double)10;
			
			map += pre;
		}
		map /= (double)averagePrecision.size();
		
		System.out.println("L=" + Constants.LAMBDA + ", B=" + Constants.BETA);
		System.out.println("Accuracy: " + accuracy);
		System.out.println("Precision: " + precision);
		System.out.println("Recall: " + recall);
		System.out.println("F1: " + f1);
		System.out.println("MAP: " + map);
		System.out.println("");
	}
	
	public void recommend()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(true);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(links.keySet());
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();
		HashMap<Long, HashMap<Long, Double>> friendConnections = UserUtil.getFriendInteractionMeasure();
		//HashMap<Long, HashMap<Long, Double>> friendConnections = UserUtil.getFriendLikeSimilarity();
		
		/*
		Set<String> words = LinkUtil.loadMostCommonWords();
		if (words.size() == 0) {
			words = LinkUtil.getMostCommonWords();
		}
		*/
		
		Set<String> words = new HashSet<String>();
		HashMap<Long, HashSet<String>> linkWords = LinkUtil.getLinkWordFeatures(words, true);
		
		
		
		Double[][] userFeatureMatrix = loadFeatureMatrix("lrUserMatrix", Constants.USER_FEATURE_COUNT, type);
		if (userFeatureMatrix == null) {
			userFeatureMatrix = getPrior(Constants.USER_FEATURE_COUNT);
		}
		Double[][] linkFeatureMatrix = loadFeatureMatrix("lrLinkMatrix", Constants.LINK_FEATURE_COUNT, type);
		if (linkFeatureMatrix == null) {
			linkFeatureMatrix = getPrior(Constants.LINK_FEATURE_COUNT);
		}
		HashMap<Long, Double[]>userIdColumns = loadIdColumns("lrUserMatrix", type);
		if (userIdColumns.size() == 0) {
			userIdColumns = getMatrixIdColumns(users.keySet());
		}
		
		HashMap<Long, Double[]>linkIdColumns = loadIdColumns("lrLinkMatrix", type);
		if (linkIdColumns.size() == 0) {
			linkIdColumns = getMatrixIdColumns(links.keySet());
		}
		
		HashMap<String, Double[]> wordColumns = loadWordColumns(type);
		if (wordColumns.size() == 0) {
			wordColumns = getWordColumns(words);
		}
		
		updateMatrixColumns(links.keySet(), linkIdColumns);
		updateMatrixColumns(users.keySet(), userIdColumns);
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(linkLikes, users.keySet(), friendships, links.keySet(), true);
		System.out.println("users: " + userLinkSamples.size());
		
		System.out.println("Minimizing...");
		minimizer.minimize(linkLikes, userFeatureMatrix, linkFeatureMatrix, users, links, friendConnections, userIdColumns, linkIdColumns, userLinkSamples, wordColumns, linkWords, words);
		
		System.out.println("Recommending...");
		HashMap<Long, HashSet<Long>> linksToRecommend = getLinksForRecommending(friendships, type);
		HashMap<Long, HashMap<Long, Double>> recommendations = recommendLinks(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, 
																							users, links, linksToRecommend, linkWords, wordColumns);
		
		System.out.println("Saving...");
		saveLinkRecommendations(recommendations, type);
		saveMatrices(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, wordColumns, type);
		
		RecommenderUtil.closeSqlConnection();
		
		System.out.println("Done");
	}
	
	/**
	 * For creation of the latent matrices.
	 * 
	 * @param featureCount
	 * @return
	 */
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
	
	
	/**
	 * After training, start recommending links to the user. This will get a set of links that haven't been liked by the user and calculate
	 * their 'like score'. Most likely only the positive scores should be recommended, with a higher score meaning more highly recommended.
	 * 
	 * Links to be recommending are those that have not been shared by his friends, to increase the likelihood of the user 
	 * not having seen these links before.
	 * 
	 * @param friendships
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public HashMap<Long, HashSet<Long>> getLinksForRecommending(HashMap<Long, HashMap<Long, Double>> friendships, String type)
		throws SQLException
	{
		HashMap<Long, HashSet<Long>> userLinks = new HashMap<Long, HashSet<Long>>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		//Recommend only for users that have installed the LinkRecommender app.
		//These users are distinguished by having is_app_user=1 in the trackUserUpdates table.
		HashMap<Long, Integer> userIds = new HashMap<Long, Integer>();
		ResultSet result = statement.executeQuery("SELECT linkrUser.uid, trackUserUpdates.max_links FROM linkrUser, trackUserUpdates "
													+ "WHERE linkrUser.uid=trackUserUpdates.uid "
													+ "AND is_app_user=1 AND algorithm='" + type + "'");
		
		while (result.next()) {
			userIds.put(result.getLong("uid"), result.getInt("max_links"));
		}
		
		for (Long id : userIds.keySet()) {
			HashSet<Long> links = new HashSet<Long>();
			userLinks.put(id, links);
			
			Set<Long> friends;
			if (friendships.containsKey(id)) {
				friends = friendships.get(id).keySet();
			}
			else {
				friends = new HashSet<Long>();
			}
			
			HashSet<Long> dontInclude = new HashSet<Long>();
			
			// Don't recommend links that were already liked
			result = statement.executeQuery("SELECT l.link_id FROM linkrLinks l, linkrLikes ll WHERE l.link_id=l.link_id AND ll.id=" + id);
			while (result.next()) {
				dontInclude.add(result.getLong("l.link_id"));
			}
			
			// Don't recommend links that are already pending recommendedation
			result = statement.executeQuery("SELECT link_id FROM lrRecommendations WHERE user_id=" + id + " AND type='" + type + "'");
			while(result.next()) {
				dontInclude.add(result.getLong("link_id"));
			}
			
			//Don't recommend links that were already published by the app
			result = statement.executeQuery("SELECT link_id FROM trackRecommendedLinks WHERE uid=" + id);
			while (result.next()) {
				dontInclude.add(result.getLong("link_id"));
			}
			
			//Don't recommend links that were clicked by the user
			result = statement.executeQuery("SELECT link_id FROM linkrLinks l, trackLinkClicked t WHERE l.link=t.link and uid_clicked=" + id);
			while (result.next()) {
				dontInclude.add(result.getLong("link_id"));
			}
			
			// Get the most recent links.
			StringBuilder query = new StringBuilder("SELECT link_id FROM linkrLinks WHERE uid NOT IN (");
			query.append(id);
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			
			query.append(") AND from_id NOT IN (");
			query.append(id);
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			
			query.append(") AND link_id NOT IN(0");
			for (long linkIds : dontInclude) {
				query.append(",");
				query.append(linkIds);
			}
			
			query.append(") ORDER BY created_time DESC LIMIT 100");
			
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				links.add(result.getLong("link_id"));
			}
		}
		
		return userLinks;
	}
	
	/**
	 * Save the recommended links into the database.
	 * 
	 * @param recommendations
	 * @param type
	 * @throws SQLException
	 */
	public void saveLinkRecommendations(HashMap<Long, HashMap<Long, Double>> recommendations, String type)
		throws SQLException
	{
		Connection conn = RecommenderUtil.getSqlConnection();
		
		Statement statement = conn.createStatement();
		
		for (long userId : recommendations.keySet()) {
			HashMap<Long, Double> recommendedLinks = recommendations.get(userId);
			
			//statement.executeUpdate("DELETE FROM " + tableName + " WHERE user_id=" + userId);
			
			for (long linkId : recommendedLinks.keySet()) {
				System.out.println("RECOMMENDING LINK");
				PreparedStatement ps = conn.prepareStatement("INSERT INTO lrRecommendations VALUES(?,?,?,?,0)");
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
	
	/**
	 * Loads the previously trained matrix from the database
	 * 
	 * @param tableName
	 * @param featureCount
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public Double[][] loadFeatureMatrix(String tableName, int featureCount, String type)
		throws SQLException
	{
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		Double[][] matrix = new Double[Constants.K][featureCount];
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id < " + Constants.K + " AND type='" + type + "'");
		
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
	
	/**
	 * Loads the previously trained matrix id columns from the database.
	 * 
	 * @param tableName
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public HashMap<Long, Double[]> loadIdColumns(String tableName, String type)
		throws SQLException
	{
		HashMap<Long, Double[]> idColumns = new HashMap<Long, Double[]>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT * FROM " + tableName + " WHERE id >" + Constants.K + " AND type='" + type + "'");
		while (result.next()) {
			long id = result.getLong("id");
			String values = result.getString("value");
			String[] tokens = values.split(",");
			
			//Column valuess were saved as one CSV string
			Double[] val = new Double[Constants.K];
			for (int x = 0; x < Constants.K; x++) {
				val[x] = Double.parseDouble(tokens[x]);
			}
			
			idColumns.put(id, val);
		}
		
		statement.close();
		
		
		return idColumns;
	}
	
	/**
	 * Save the trained matrices into the database.
	 * 
	 * @param userFeatureMatrix
	 * @param linkFeatureMatrix
	 * @param userIdColumns
	 * @param linkIdColumns
	 * @param wordColumns
	 * @param type
	 * @throws SQLException
	 */
	public void saveMatrices(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, HashMap<String, Double[]> wordColumns, String type)
		throws SQLException
	{
		Connection conn = RecommenderUtil.getSqlConnection();
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
		
		//Save the id column values as a CSV string
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
		
		//Save the word column values as a CSV string
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
	
	/**
	 * Loads the previously trained word columns from the database.
	 * 
	 * @param tableName
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public HashMap<String, Double[]> loadWordColumns(String type)
		throws SQLException
	{
		HashMap<String, Double[]> columns = new HashMap<String, Double[]>();
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		ResultSet result = statement.executeQuery("SELECT * FROM lrWordColumns WHERE type='" + type + "'");
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
	
	/**
	 * Since we're doing online updating, we need to update the matrix columns by removing links/users from the previous training that aren't included
	 * anymore and adding the new ones that weren't existing in the previous training.
	 * 
	 * @param ids
	 * @param idColumns
	 */
	public void updateMatrixColumns(Set<Long> ids, HashMap<Long, Double[]> idColumns)
	{
		HashSet<Long> columnsToRemove = new HashSet<Long>();
		
		//Remove columns that are past the range
		for (long id : idColumns.keySet()) {
			if (!ids.contains(id)) {
				columnsToRemove.add(id);
			}
		}
		for (long id : columnsToRemove) {
			idColumns.remove(id);
		}
		
		//Add columns for the new ones
		HashSet<Long> columnsToAdd = new HashSet<Long>();
		
		for (long id : ids) {
			if (!idColumns.containsKey(id)) {
				columnsToAdd.add(id);
			}
		}
		HashMap<Long, Double[]> newColumns = getMatrixIdColumns(columnsToAdd);
		idColumns.putAll(newColumns);
	}
	
	/**
	 * Columns for the ids are placed into a HashMap
	 * 
	 * @param ids
	 * @return
	 */
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
	
	/**
	 * Columns for words in the link feature are placed into a HashMap
	 * 
	 * @param words
	 * @return
	 */
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
	
	/**
	 * Calculate the recommendation scores of the link
	 * 
	 * @param userFeatureMatrix
	 * @param linkFeatureMatrix
	 * @param userIdColumns
	 * @param linkIdColumns
	 * @param userFeatures
	 * @param linkFeatures
	 * @param linksToRecommend
	 * @param linkWords
	 * @param wordColumns
	 * @return
	 */
	public HashMap<Long, HashMap<Long, Double>> recommendLinks(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
																HashMap<Long, Double[]> userIdColumns, HashMap<Long, Double[]> linkIdColumns, 
																HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures,
																HashMap<Long, HashSet<Long>> linksToRecommend, HashMap<Long, HashSet<String>> linkWords,
																HashMap<String, Double[]> wordColumns)
		throws SQLException
	{	
		HashMap<Long, HashMap<Long, Double>> recommendations = new HashMap<Long, HashMap<Long, Double>>();
		
		HashMap<Long, Double[]> userTraits = UserUtil.getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		HashMap<Long, Double[]> linkTraits = LinkUtil.getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures, linkWords, wordColumns);
		
		Connection conn = RecommenderUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		for (long userId :linksToRecommend.keySet()) {
			HashSet<Long> userLinks = linksToRecommend.get(userId);
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
			
			ResultSet result = statement.executeQuery("SELECT max_links FROM trackUserUpdates WHERE uid=" + userId);
			result.next();
			int maxLinks = result.getInt("max_links");
			
			for (long linkId : userLinks) {
				if (!linkTraits.containsKey(linkId)) continue;
				
				double prediction = RecommenderUtil.dot(userTraits.get(userId), linkTraits.get(linkId));
				
				//Recommend only if prediction score is greater or equal than the boundary
				//if (prediction > Constants.BOUNDARY) {
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
				//}
			}
		}
		
		statement.close();
		return recommendations;
	}
}
