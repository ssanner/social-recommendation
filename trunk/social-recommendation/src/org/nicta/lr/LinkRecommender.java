package org.nicta.lr;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.io.IOException;
import java.util.Map;

import org.nicta.lr.recommender.FeatureRecommender;
import org.nicta.lr.recommender.NNRecommender;
import org.nicta.lr.recommender.Recommender;
import org.nicta.lr.recommender.SVMRecommender;
import org.nicta.lr.recommender.SocialRecommender;
import org.nicta.lr.recommender.BaselineGlobalRecommender;
import org.nicta.lr.recommender.BaselineRecommender;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.UserUtil;
import org.nicta.lr.util.SQLUtil;

public class LinkRecommender 
{
	String type;
	
	Map<Long, Set<Long>> friendLinksToRecommend;
	Map<Long, Set<Long>> nonFriendLinksToRecommend;
	
	public LinkRecommender(String type)
	{
		this.type = type;
	}
	
	public static void main(String[] args)
		throws Exception
	{
		String type = "nn";
		if (args.length > 0) {
			type = args[0];
		}
		
		Constants.DEPLOYMENT_TYPE = Constants.RECOMMEND;
		LinkRecommender lr = new LinkRecommender(type);
		lr.run();
	}
	
	public void run()
		throws SQLException, IOException
	{	
		Set<Long> userIds = UserUtil.getUserIds();
		Set<Long> linkIds = LinkUtil.getLinkIds(true);
		
		Map<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(linkIds);
		Map<Long, Set<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, true);
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
		
		Map<Long, Set<Long>> userLinkSamples = getUserLinksSample(linkLikes, userIds, friendships, linkUsers, true);
		
		Set<Long> appUsers = UserUtil.getAppUserIds();
		
		Set<Long> usersNeeded = new HashSet<Long>();
		usersNeeded.addAll(appUsers);
		usersNeeded.addAll(userLinkSamples.keySet());
		Map<Long, Double[]> users = UserUtil.getUserFeatures(usersNeeded);
		
		Set<Long> linksNeeded = new HashSet<Long>();
		linksNeeded.addAll(linkLikes.keySet());
		for (long id : userLinkSamples.keySet()) {
			linksNeeded.addAll(userLinkSamples.get(id));
			
		}
		
		if (Constants.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			//Get links that we will be recommending later
			friendLinksToRecommend = getFriendLinksForRecommending(friendships, type);
			nonFriendLinksToRecommend = getNonFriendLinksForRecommending(friendships, type);
			
			for (long id : nonFriendLinksToRecommend.keySet()) {
				linksNeeded.addAll(nonFriendLinksToRecommend.get(id));
				linksNeeded.addAll(friendLinksToRecommend.get(id));
			}
		}
		
		Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linksNeeded);
		SQLUtil.closeSqlConnection();
		
		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		
		Map<Long, Set<Long>> testData = null;
		
		if (Constants.DEPLOYMENT_TYPE == Constants.TEST) {
			testData = splitTrainingAndTestingData(userLinkSamples, linkLikes);
		}
		
		recommender.train(userLinkSamples);
		
		if (Constants.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			Map<Long, Map<Long, Double>> friendRecommendations = recommender.recommend(friendLinksToRecommend);
			Map<Long, Map<Long, Double>> nonFriendRecommendations = recommender.recommend(nonFriendLinksToRecommend);

			saveLinkRecommendations(friendRecommendations, nonFriendRecommendations, type);
			recommender.saveModel();
			
			SQLUtil.closeSqlConnection();
		}
		else if (Constants.DEPLOYMENT_TYPE == Constants.TEST){
			Map<Long, Double> averagePrecisions = recommender.getAveragePrecisions(testData);
			
			double map = 0;
			for (long userId : averagePrecisions.keySet()) {
				double pre = averagePrecisions.get(userId);
				
				map += pre;
			}
			map /= (double)averagePrecisions.size();
			
			double standardDev = 0;
			for (long userId : averagePrecisions.keySet()) {
				double pre = averagePrecisions.get(userId);
				standardDev += Math.pow(pre - map, 2);
			}
			standardDev /= (double)averagePrecisions.size();
			standardDev = Math.sqrt(standardDev);
			double standardError = standardDev / Math.sqrt(averagePrecisions.size());
			
			System.out.println("MAP: " + map);
			System.out.println("SD: " + standardDev);
			System.out.println("SE: " + standardError);
		}
		
		
		System.out.println("Done");
	}
	
	public Map<Long, Set<Long>> splitTrainingAndTestingData(Map<Long, Set<Long>> userLinkSamples, Map<Long, Set<Long>> linkLikes)
	{
		Map<Long, Set<Long>> testData = new HashMap<Long, Set<Long>>();
		
		for (long userId : userLinkSamples.keySet()) {
			HashSet<Long> userTesting = new HashSet<Long>();
			testData.put(userId, userTesting);
			
			Set<Long> samples = userLinkSamples.get(userId);
			
			Object[] sampleArray = samples.toArray();
			
			int addedCount = 0;
			int likeCount = 0;
			
			while (addedCount < sampleArray.length * .2 || addedCount < 2) {
				int randomIndex = (int)(Math.random() * (sampleArray.length));
				Long randomLinkId = (Long)sampleArray[randomIndex];
				
				if (!userTesting.contains(randomLinkId)) {
					
					
					if (likeCount == 0) {
						if (!linkLikes.get(randomLinkId).contains(userId)) {
							continue;
						}
						else {
							likeCount++;
						}
					}
					else {		
						if (linkLikes.get(randomLinkId).contains(userId)) {
							int remainingLike = 0;
							for (long remainingId : samples) {
								if (linkLikes.get(remainingId).contains(userId)) remainingLike++;
							}
							
							if (remainingLike == 1) {
								continue;
							}
							else {
								likeCount++;
							}
						}
					}
					
					userTesting.add(randomLinkId);
					samples.remove(randomLinkId);
					addedCount++;
				}
			}		
		}
		
		return testData;
	}
	
	/**
	 * After training, start recommending links to the user. This will get a set of links that haven't been liked by the user and calculate
	 * their 'like score'. Most likely only the positive scores should be recommended, with a higher score meaning more highly recommended.
	 * 
	 * Links to be recommending are those that have been shared by their friends
	 * 
	 * @param friendships
	 * @param type
	 * @return
	 * @throws SQLException
	 */
	public Map<Long, Set<Long>> getFriendLinksForRecommending(Map<Long, Map<Long, Double>> friendships, String type)
		throws SQLException
	{
		HashMap<Long, Set<Long>> userLinks = new HashMap<Long, Set<Long>>();
		
		Connection conn = SQLUtil.getSqlConnection();
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
		
		System.out.println("Recommending for: " + userIds.size());
		int count = 0;
		for (Long id : userIds.keySet()) {
			System.out.println("User: " + id + " " + ++count);
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
			result = statement.executeQuery("SELECT link_id FROM linkrLinkLikes WHERE id=" + id);
			while (result.next()) {
				dontInclude.add(result.getLong("link_id"));
			}
			
			System.out.println("First don't include: " + dontInclude.size());
			// Don't recommend links that are already pending recommendedation
			result = statement.executeQuery("SELECT link_id FROM lrRecommendations WHERE user_id=" + id + " AND type='" + type + "'");
			while(result.next()) {
				dontInclude.add(result.getLong("link_id"));
			}
			System.out.println("Second don't include: " + dontInclude.size());
			
			//Don't recommend links that were already published by the app
			result = statement.executeQuery("SELECT link_id FROM trackRecommendedLinks WHERE uid=" + id);
			while (result.next()) {
				dontInclude.add(result.getLong("link_id"));
			}
			System.out.println("Third don't include: " + dontInclude.size());
			
			//Don't recommend links that were clicked by the user
			HashSet<String> linkClicked = new HashSet<String>();
			result = statement.executeQuery("SELECT link FROM trackLinkClicked WHERE uid_clicked=" + id);
			while (result.next()) {
				linkClicked.add(result.getString("link"));
			}
			System.out.println("Fourth don't include: " + dontInclude.size());
			
			// Get the most recent links.
			StringBuilder query = new StringBuilder("SELECT link_id FROM linkrLinks WHERE uid IN (0");
			//query.append(id);
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			
			query.append(") AND from_id IN (0");
			//query.append(id);
			for (Long friendId : friends) {
				query.append(",");
				query.append(friendId);
			}
			
			query.append(") AND link_id NOT IN(0");
			for (long linkIds : dontInclude) {
				query.append(",");
				query.append(linkIds);
			}
			
			query.append(") AND link NOT IN(''");
			for (String link : linkClicked) {
				query.append(",'");
				query.append(link);
				query.append("'");
			}
			
			query.append(") AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.TRAINING_WINDOW_RANGE + "))");
			
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				links.add(result.getLong("link_id"));
			}
		}
		
		return userLinks;
	}
	
	public Map<Long, Set<Long>> getNonFriendLinksForRecommending(Map<Long, Map<Long, Double>> friendships, String type)
		throws SQLException
	{
		HashMap<Long, Set<Long>> userLinks = new HashMap<Long, Set<Long>>();
		
		Connection conn = SQLUtil.getSqlConnection();
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
			System.out.println("Id: " + id);
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
			result = statement.executeQuery("SELECT link_id FROM linkrLinkLikes WHERE id=" + id);
			while (result.next()) {
				dontInclude.add(result.getLong("link_id"));
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
			HashSet<String> linkClicked = new HashSet<String>();
			result = statement.executeQuery("SELECT link FROM trackLinkClicked WHERE uid_clicked=" + id);
			while (result.next()) {
				linkClicked.add(result.getString("link"));
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
			
			query.append(") AND link NOT IN(''");
			for (String link : linkClicked) {
				query.append(",'");
				query.append(link);
				query.append("'");
			}
			
			query.append(") AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.TRAINING_WINDOW_RANGE + "))");
			result = statement.executeQuery(query.toString());
			
			while (result.next()) {
				links.add(result.getLong("link_id"));
			}
		}
		
		return userLinks;
	}
	
	public Recommender getRecommender(String type, Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> users, Map<Long, Double[]> links, Map<Long, Map<Long, Double>> friendships)
	{
		Recommender recommender = null;
		
		if (Constants.SOCIAL.equals(type)) {
			recommender = new SocialRecommender(linkLikes, users, links, friendships);
		}
		else if (Constants.FEATURE.equals(type)) {
			recommender = new FeatureRecommender(linkLikes, users, links);
		}
		else if (Constants.SVM.equals(type)) {
			recommender = new SVMRecommender(linkLikes, users, links, friendships);
		}
		else if (Constants.NN.equals(type)) {
			recommender = new NNRecommender(linkLikes, users, links, friendships);
		}
		else if (Constants.GLOBAL.equals(type)) {
			recommender = new BaselineGlobalRecommender(linkLikes, users, links);
		}
		else if (Constants.FUW.equals(type) || Constants.FIW.equals(type)) {
			recommender = new BaselineRecommender(linkLikes, users, links, friendships, type);
		}
		
		return recommender;
	}
	
	/**
	 * Save the recommended links into the database.
	 * 
	 * @param recommendations
	 * @param type
	 * @throws SQLException
	 */
	public void saveLinkRecommendations(Map<Long, Map<Long, Double>> friendRecommendations, Map<Long, Map<Long, Double>> nonFriendRecommendations, String type)
		throws SQLException
	{
		Connection conn = SQLUtil.getSqlConnection();
		
		Statement statement = conn.createStatement();
		
		for (long userId : friendRecommendations.keySet()) {
			Map<Long, Double> friendLinks = friendRecommendations.get(userId);
			Map<Long, Double> nonFriendLinks = nonFriendRecommendations.get(userId);
			
			Iterator<Long> friendIterator = friendLinks.keySet().iterator();
			Iterator<Long> nonFriendIterator = nonFriendLinks.keySet().iterator();
			
			int maxLinks = nonFriendLinks.size();
			int count = 0;
			
			while (count < maxLinks) {
				long linkId = 0;
				double val = 0;
				String from = null;
				
				if (Math.random() >= 0.5 && friendIterator.hasNext()) {
					linkId = friendIterator.next();
					val = friendLinks.get(linkId);
					from = "Friend";
					count++;
				}
				else if (nonFriendIterator.hasNext()){
					linkId = nonFriendIterator.next();
					val = nonFriendLinks.get(linkId);
					from = "NonFriend";
					count++;
				}
				
				if (from == null) continue;
				
				System.out.println("RECOMMENDING LINK: " + from);
				PreparedStatement ps = conn.prepareStatement("INSERT INTO lrRecommendations VALUES(?,?,?,?,0)");
				ps.setLong(1, userId);
				ps.setLong(2, linkId);
				ps.setDouble(3, val);
				ps.setString(4, type);
				
				ps.executeUpdate();
				ps.close();
			}
		}
		
		statement.close();	
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
	public static Map<Long, Set<Long>> getUserLinksSample(Map<Long, Set<Long>> linkLikes, Set<Long> userIds, Map<Long, Map<Long, Double>> friendships, Map<Long, Long[]> links, boolean limit)
		throws SQLException
	{
		HashMap<Long, Set<Long>> userLinkSamples = new HashMap<Long, Set<Long>>();
		
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		System.out.println("userIds: " + userIds.size());
		System.out.println("links: " + links.size());
		System.out.println("Likes: " + linkLikes.size());
		
		int count = 0;
		
		for (long linkId : linkLikes.keySet()) {
			Set<Long> users = linkLikes.get(linkId);
			
			for (long userId : users) {
				if (!userIds.contains(userId) || ! friendships.containsKey(userId)) continue;
				Set<Long> samples = userLinkSamples.get(userId);
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
			Set<Long> samples = userLinkSamples.get(userId);
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
			Set<Long> samples = userLinkSamples.get(userId);
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
		
		return userLinkSamples;
	}
}
