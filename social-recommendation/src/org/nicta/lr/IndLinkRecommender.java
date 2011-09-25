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
import org.nicta.lr.util.Configuration;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.SQLUtil;
import org.nicta.lr.util.UserUtil;

public class IndLinkRecommender  extends LinkRecommender
{	
	public void run(double arg)
		throws SQLException, IOException
	{	
		/*
		System.out.println("Individual Rec");
		Set<Long> userIds = UserUtil.getUserIds();
		//Set<Long> linkIds = LinkUtil.getLinkIds(true);
		
		Map<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(linkIds);
		Map<Long, Set<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, true);
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
		
		Set<Long> linksNeeded = new HashSet<Long>();
		linksNeeded.addAll(linkLikes.keySet());
		
		if (Configuration.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			//Get links that we will be recommending later
			friendLinksToRecommend = getFriendLinksForRecommending(friendships, type);
			nonFriendLinksToRecommend = getNonFriendLinksForRecommending(friendships, type);
			
			for (long id : nonFriendLinksToRecommend.keySet()) {
				linksNeeded.addAll(nonFriendLinksToRecommend.get(id));
				linksNeeded.addAll(friendLinksToRecommend.get(id));
			}
		}
		
		Set<Long> appUsers = UserUtil.getAppUserIds();
		Set<Long> removeAppUsers = new HashSet<Long>();
		
		for (long appUserId : appUsers) {
			if (!userIds.contains(appUserId)) {
				removeAppUsers.add(appUserId);
			}
		}
		appUsers.removeAll(removeAppUsers);
		
		Set<Long> usersNeeded = new HashSet<Long>();
		usersNeeded.addAll(appUsers);
		
			
		Map<Long, Map<Long, Set<Long>>> appUserLinkSamples = new HashMap<Long, Map<Long, Set<Long>>>();
		
		System.out.println("Before app users: " + linksNeeded.size());
		for (long appUserId : appUsers) {
			Map<Long, Set<Long>> userLinkSamples = getUserLinksSample(linkLikes, userIds, friendships, linkUsers, true, appUserId);
			
			if (userLinkSamples == null) {
				continue;
			}
			
			usersNeeded.addAll(userLinkSamples.keySet());
		
			for (long id : userLinkSamples.keySet()) {
				linksNeeded.addAll(userLinkSamples.get(id));
			}
			
			
			appUserLinkSamples.put(appUserId, userLinkSamples);
		}
		
		System.out.println("After app users: " + linksNeeded.size());
		

		
		Map<Long, Set<Long>> appUserTestData = new HashMap<Long, Set<Long>>();
		
		if (Configuration.DEPLOYMENT_TYPE == Constants.TEST) {
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
			
			if (userLinkSamples == null) continue;
			
			for (long userId : userLinkSamples.keySet()) {
				Set<Long> samples = userLinkSamples.get(userId);
				HashSet<Long> remove = new HashSet<Long>();
				for (long linkId : samples) {
					if (!links.containsKey(linkId)) {
						remove.add(linkId);
					}
				}
				samples.removeAll(remove);
			}
		}
		
		SQLUtil.closeSqlConnection();
		
		double map = 0;
		double meanPrecision100 = 0;
		double meanRecall100 = 0;
		double meanF100 = 0;
		
		Map<Long, Double> averagePrecisions = new HashMap<Long, Double>();
		Map<Long, Double[]> precisionRecalls100 = new HashMap<Long, Double[]>();
		
		HashSet<Long> combinedTest = new HashSet<Long>();
		
		for (long appUserId : appUserTestData.keySet()) {
			combinedTest.addAll(appUserTestData.get(appUserId));
		}
		
		int count = 0;
		for (long appUserId : appUsers) {
			if (! appUserLinkSamples.containsKey(appUserId)) continue;
			
			count++;
			System.out.println("App user " + count + ": " + appUserId);
			IndSocialRecommender recommender = (IndSocialRecommender)getRecommender(type, linkLikes, users, links, friendships, appUserId);
			recommender.setC(arg);
			recommender.train(appUserLinkSamples.get(appUserId));
			
			if (Configuration.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
				Map<Long, Double> friendRecommendations = recommender.indRecommend(friendLinksToRecommend);
				Map<Long, Double> nonFriendRecommendations = recommender.indRecommend(nonFriendLinksToRecommend);
		
				saveLinkRecommendations(friendRecommendations, nonFriendRecommendations, type, appUserId);
				recommender.saveModel();
				
				//SQLUtil.closeSqlConnection();
			}
			else if (Configuration.DEPLOYMENT_TYPE == Constants.TEST){
				if (! appUserTestData.containsKey(appUserId)) continue;
				
				double ap = recommender.getAveragePrecision(appUserTestData.get(appUserId));
				
				Double[] precisionRecall100 = recommender.getPrecisionRecall(combinedTest, 100);
				
				averagePrecisions.put(appUserId, ap);
				precisionRecalls100.put(appUserId, precisionRecall100);
				
				System.out.println("AP: " + ap);
				
				map += ap;
				
				meanPrecision100 += precisionRecall100[0];
				meanRecall100 += precisionRecall100[1];
				
				meanF100 += (precisionRecall100[0] + precisionRecall100[1] > 0) ? 2 * (precisionRecall100[0] * precisionRecall100[1]) / (precisionRecall100[0] + precisionRecall100[1]) : 0;
			}
			
		}
		
		if (Configuration.DEPLOYMENT_TYPE == Constants.TEST) {
			map /= (double)averagePrecisions.size();
			
			meanPrecision100 /= (double)averagePrecisions.size();
			meanRecall100 /= (double)averagePrecisions.size();
			meanF100 /= (double)averagePrecisions.size();
	
			double precisionStandardDev100 = 0;
			double recallStandardDev100 = 0;
			double fStandardDev100 = 0;
			
			double standardDev = 0;
			for (long userId : averagePrecisions.keySet()) {
				double pre = averagePrecisions.get(userId);
				standardDev += Math.pow(pre - map, 2);
				
				Double[] precisionRecall100 = precisionRecalls100.get(userId);
				
				double precision100 = precisionRecall100[0];
				double recall100 = precisionRecall100[1];
				
				double f100 = (precision100 + recall100 > 0) ? 2 * (precision100 * recall100) / (precision100 + recall100) : 0;
				precisionStandardDev100 += Math.pow(precision100 - meanPrecision100, 2);
				recallStandardDev100 += Math.pow(recall100 - meanRecall100, 2);
				
				fStandardDev100 += Math.pow(f100 - meanF100, 2);
			}
			
			standardDev /= (double)averagePrecisions.size();
			standardDev = Math.sqrt(standardDev);
			
			precisionStandardDev100 /= (double)averagePrecisions.size();
			precisionStandardDev100 = Math.sqrt(precisionStandardDev100);
			recallStandardDev100 /= (double)averagePrecisions.size();
			recallStandardDev100 = Math.sqrt(recallStandardDev100);
			fStandardDev100 /= (double)averagePrecisions.size();
			fStandardDev100 = Math.sqrt(fStandardDev100);
			
			double standardError = standardDev / Math.sqrt(averagePrecisions.size());
			
			double precisionSE100 = precisionStandardDev100 / Math.sqrt(averagePrecisions.size());
			double recallSE100 = recallStandardDev100 / Math.sqrt(averagePrecisions.size());
			double fSE100 = fStandardDev100 / Math.sqrt(averagePrecisions.size());
			
			System.out.println("MAP: " + map);
			System.out.println("SD: " + standardDev);
			System.out.println("SE: " + standardError);
			
			System.out.println("Mean Precision 100: " + meanPrecision100);
			System.out.println("SD: " + precisionStandardDev100);
			System.out.println("SE: " + precisionSE100);
			
			System.out.println("Mean Recall 100: " + meanRecall100);
			System.out.println("SD: " + recallStandardDev100);
			System.out.println("SE: " + recallSE100);
			
			System.out.println("Mean F1 100: " + meanF100);
			System.out.println("SD: " + fStandardDev100);
			System.out.println("SE: " + fSE100);
		}
		*/
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
		if (!friendships.containsKey(appUserId)) return null;
		
		HashMap<Long, Set<Long>> userLinkSamples = new HashMap<Long, Set<Long>>();
		
		Statement statement = SQLUtil.getStatement();
		
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
		Statement statement = SQLUtil.getStatement();
			
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
			PreparedStatement ps = SQLUtil.prepareStatement("INSERT INTO lrRecommendations VALUES(?,?,?,?,0)");
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
