package org.nicta.lr;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import org.nicta.lr.recommender.Recommender;
import org.nicta.lr.recommender.IndSocialRecommender;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

public class IndLinkRecommender  extends LinkRecommender
{
	public IndLinkRecommender(String type)
	{
		super(type);
	}
	
	public static void main(String[] args)
		throws Exception
	{
		System.out.println("Individual");
		String type = "social";
		if (args.length > 0) {
			type = args[0];
		}
		
		IndLinkRecommender lr = new IndLinkRecommender(type);
		lr.run(1);
		lr.run(10);
		lr.run(50);
		lr.run(100);
	}
	
	public void run(double arg)
		throws SQLException, IOException
	{	
		Set<Long> userIds = UserUtil.getUserIds();
		Set<Long> linkIds = LinkUtil.getLinkIds(true);
		
		Map<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(linkIds);
		Map<Long, Set<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, true);
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
		
		Set<Long> linksNeeded = new HashSet<Long>();
		linksNeeded.addAll(linkLikes.keySet());
		
		if (Constants.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			//Get links that we will be recommending later
			friendLinksToRecommend = getFriendLinksForRecommending(friendships, type);
			nonFriendLinksToRecommend = getNonFriendLinksForRecommending(friendships, type);
			
			for (long id : nonFriendLinksToRecommend.keySet()) {
				linksNeeded.addAll(nonFriendLinksToRecommend.get(id));
				linksNeeded.addAll(friendLinksToRecommend.get(id));
			}
		}
		
		Set<Long> appUsers = UserUtil.getAppUsersWithAlgorithm(type);
		Set<Long> usersNeeded = new HashSet<Long>();
		usersNeeded.addAll(appUsers);
		
			
		Map<Long, Map<Long, Set<Long>>> appUserLinkSamples = new HashMap<Long, Map<Long, Set<Long>>>();
		
		System.out.println("Before app users: " + linksNeeded.size());
		for (long appUserId : appUsers) {
			Map<Long, Set<Long>> userLinkSamples = getUserLinksSample(linkLikes, userIds, friendships, linkUsers, true, appUserId);
			usersNeeded.addAll(userLinkSamples.keySet());
		
			for (long id : userLinkSamples.keySet()) {
				linksNeeded.addAll(userLinkSamples.get(id));
			}
			
			
			appUserLinkSamples.put(appUserId, userLinkSamples);
		}
		
		System.out.println("After app users: " + linksNeeded.size());
		

		
		Map<Long, Set<Long>> appUserTestData = new HashMap<Long, Set<Long>>();
		
		if (Constants.DEPLOYMENT_TYPE == Constants.TEST) {
			Set<Long> testLinkIds = LinkUtil.getTestLinkIds();
			
			Map<Long, Long[]> testLinkUsers = LinkUtil.getUnormalizedFeatures(testLinkIds);
			Map<Long, Set<Long>> testLinkLikes = LinkUtil.getLinkLikes(testLinkUsers, true);
			
			System.out.println("Likes: " + linkLikes.size());
			for (long testId : testLinkLikes.keySet()) {
				linkLikes.put(testId, testLinkLikes.get(testId));
			}
			System.out.println("After: " + linkLikes.size());
			
			appUserTestData = getUserLinksSample(testLinkLikes, appUsers, friendships, testLinkUsers, true, true);
			
			usersNeeded.addAll(appUserTestData.keySet());
			
			System.out.println("Links needed: " + linksNeeded.size());
			for (long testUserId : appUserTestData.keySet()) {
				linksNeeded.addAll(appUserTestData.get(testUserId));
			}
			System.out.println("After: " + linksNeeded.size());
		}
		
		
		Map<Long, Double[]> users = UserUtil.getUserFeatures(usersNeeded);
		Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linksNeeded);
		
		
		//Clean up links that are not in the linkrLinkInfo table. Clean up users after
		for (long appUserId : appUsers) {
			Map<Long, Set<Long>> userLinkSamples = appUserLinkSamples.get(appUserId);
			
			Set<Long> userRemove = new HashSet<Long>();
			for (long userId : userLinkSamples.keySet()) {
				Set<Long> samples = userLinkSamples.get(userId);
				HashSet<Long> remove = new HashSet<Long>();
				for (long linkId : samples) {
					if (!links.containsKey(linkId)) {
						remove.add(linkId);
					}
				}
				samples.removeAll(remove);
				if (samples.size() < 4) userRemove.add(userId);
			}
			for (long userId : userRemove) {
				userLinkSamples.remove(userId);
			}
		}
		
		SQLUtil.closeSqlConnection();
		
		Map<Long, Recommender> recommenders = new HashMap<Long, Recommender>();
		
		double map = 0;
		Map<Long, Double> averagePrecisions = new HashMap<Long, Double>();
		
		for (long appUserId : appUsers) {
			IndSocialRecommender recommender = (IndSocialRecommender)getRecommender(type, linkLikes, users, links, friendships, appUserId);
			recommender.setC(arg);
			recommenders.put(appUserId, recommender);
			recommender.train(appUserLinkSamples.get(appUserId));
			
			if (Constants.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
				Map<Long, Double> friendRecommendations = recommender.indRecommend(friendLinksToRecommend);
				Map<Long, Double> nonFriendRecommendations = recommender.indRecommend(nonFriendLinksToRecommend);
		
				saveLinkRecommendations(friendRecommendations, nonFriendRecommendations, type, appUserId);
				recommender.saveModel();
				
				SQLUtil.closeSqlConnection();
			}
			else if (Constants.DEPLOYMENT_TYPE == Constants.TEST){
				if (! appUserTestData.containsKey(appUserId)) continue;
				
				double ap = recommender.getAveragePrecision(appUserTestData.get(appUserId));
				averagePrecisions.put(appUserId, ap);
				
				System.out.println("AP: " + ap);
				
				map += ap;
			}
			
		}
		
		if (Constants.DEPLOYMENT_TYPE == Constants.TEST) {
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
	
	public Recommender getRecommender(String type, Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> users, Map<Long, Double[]> links, Map<Long, Map<Long, Double>> friendships, long userId)
	{
		Recommender recommender = null;
		
		if (Constants.SOCIAL.equals(type)) {
			recommender = new IndSocialRecommender(linkLikes, users, links, friendships, userId);
		}
		
		return recommender;
	}
	
	public static Map<Long, Set<Long>> getUserLinksSample(Map<Long, Set<Long>> linkLikes, Set<Long> userIds, Map<Long, Map<Long, Double>> friendships, Map<Long, Long[]> links, boolean limit, long appUserId)
		throws SQLException
	{
		HashMap<Long, Set<Long>> userLinkSamples = new HashMap<Long, Set<Long>>();
		
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		int count = 0;
		
		HashSet<Long> includedUsers = new HashSet<Long>();
		includedUsers.add(appUserId);
		Set<Long> appUserFriends = friendships.get(appUserId).keySet();
		includedUsers.addAll(appUserFriends);
		for (long friendId : appUserFriends) {
			Set<Long> friendFriends = friendships.get(friendId).keySet();
			includedUsers.addAll(friendFriends);
		}
		
		for (long linkId : linkLikes.keySet()) {
			Set<Long> users = linkLikes.get(linkId);
			
			for (long userId : users) {
				if (!includedUsers.contains(userId) || ! friendships.containsKey(userId)) continue;
				
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
	
	public void saveLinkRecommendations(Map<Long, Double> friendLinks, Map<Long, Double> nonFriendLinks, String type, long userId)
		throws SQLException
	{
		Connection conn = SQLUtil.getSqlConnection();
		
		Statement statement = conn.createStatement();
			
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
		
		statement.close();	
	}
}
