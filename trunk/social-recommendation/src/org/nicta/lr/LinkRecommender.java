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

import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.LangDetectException;

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
		String type = "social";
		if (args.length > 0) {
			type = args[0];
		}
		
		//IndLinkRecommender lr = new IndLinkRecommender(type);
		LinkRecommender lr = new LinkRecommender(type);
		lr.run();
		
		//lr.run(1);
		//lr.run(10);
		//lr.run(20);
		//lr.run(50);
		
		//lr.run(1);
		//lr.run(10);
		//lr.run(50);
		//lr.run(100);
		
		//lr.run(.00001);
		//lr.run(.000001);
		//lr.run(.0000001);
		//lr.run(.00000001);
		//lr.run(.000000001);
		//lr.run(.0000000001);
	
		//lr.run(100);
		//lr.run(1000);
		//lr.run(10000);
		//lr.run(100000);
		
		//lr.run(Math.pow(2, -5));
		//lr.run(Math.pow(2, -3));
		//lr.run(Math.pow(2, -1));
		//lr.run(Math.pow(2, 1));
		//lr.run(Math.pow(2, 3));
		//lr.run(Math.pow(2, 5));
		
		//lr.run(1);
		//lr.run(5);
		//lr.run(10);
		//lr.run(20);
		//lr.run(30);
		//lr.run(40);
		//lr.run(50);
	}
	
	public void run(/*double arg*/)
		throws SQLException, IOException
	{	
		Set<Long> userIds = UserUtil.getUserIds();
		Set<Long> linkIds = LinkUtil.getLinkIds(true);
		
		Map<Long, Long[]> linkUsers = LinkUtil.getUnormalizedFeatures(linkIds);
		Map<Long, Set<Long>> linkLikes = LinkUtil.getLinkLikes(linkUsers, true);
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
		
		Map<Long, Set<Long>> userLinkSamples = getUserLinksSample(linkLikes, userIds, friendships, linkUsers, true, false);
		
		Set<Long> appUsers = /*UserUtil.getAppUsersWithAlgorithm(type); */ UserUtil.getAppUserIds();
		
		Set<Long> usersNeeded = new HashSet<Long>();
		usersNeeded.addAll(appUsers);
		usersNeeded.addAll(userLinkSamples.keySet());
		
		
		Set<Long> linksNeeded = new HashSet<Long>();
		linksNeeded.addAll(linkLikes.keySet());
		for (long id : userLinkSamples.keySet()) {
			linksNeeded.addAll(userLinkSamples.get(id));
		}
		
		Map<Long, Set<Long>> testData = null;
		
		if (Constants.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			//Get links that we will be recommending later
			friendLinksToRecommend = getFriendLinksForRecommending(friendships, type);
			nonFriendLinksToRecommend = getNonFriendLinksForRecommending(friendships, type);
			
			
			HashSet<Long> recommendingIds = new HashSet<Long>();
			
			for (long id : nonFriendLinksToRecommend.keySet()) {
				recommendingIds.addAll(nonFriendLinksToRecommend.get(id));
				recommendingIds.addAll(friendLinksToRecommend.get(id));
			}
			
			Map<Long, String[]> linkWords = LinkUtil.getLinkText(recommendingIds);
			try {
				DetectorFactory.loadProfile(Constants.LANG_PROFILE_FOLDER);
			}
			catch(LangDetectException e) {
				e.printStackTrace();
			}
			
			//Remove non-English links
			HashSet<Long> removeNonEnglish = new HashSet<Long>();
			for (long id : linkWords.keySet()) {
				String[] words = linkWords.get(id);
				String message = words[0];
				String description = words[1];
				
				try {
					Detector messageDetector = DetectorFactory.create();
					messageDetector.append(message);				
					String messageLang = messageDetector.detect();
					if (!messageLang.equals("en")) {
						removeNonEnglish.add(id);
						continue;
					}
						
					Detector descriptionDetector = DetectorFactory.create();
					descriptionDetector.append(description);
					String descriptionLang = descriptionDetector.detect();
					
					if (!descriptionLang.equals("en")) {
						removeNonEnglish.add(id);
					}
				}
				catch (LangDetectException e) {
					continue;
				}
			}
			
			System.out.println("Removing " + removeNonEnglish.size() + " non-English from " + recommendingIds.size() + " links");
			for (long id : nonFriendLinksToRecommend.keySet()) {
				Set<Long> nonFriendLinks = nonFriendLinksToRecommend.get(id);
				Set<Long> friendLinks = friendLinksToRecommend.get(id);
				
				nonFriendLinks.removeAll(removeNonEnglish);
				friendLinks.removeAll(removeNonEnglish);
				
				linksNeeded.addAll(nonFriendLinks);
				linksNeeded.addAll(friendLinks);
			}
		}
		else {
			Set<Long> testLinkIds = LinkUtil.getTestLinkIds();
			
			Map<Long, Long[]> testLinkUsers = LinkUtil.getUnormalizedFeatures(testLinkIds);
			Map<Long, Set<Long>> testLinkLikes = LinkUtil.getLinkLikes(testLinkUsers, true);
			
			
			System.out.println("Likes: " + linkLikes.size());
			for (long testId : testLinkLikes.keySet()) {
				linkLikes.put(testId, testLinkLikes.get(testId));
			}
			System.out.println("After: " + linkLikes.size());
			
			testData = getUserLinksSample(testLinkLikes, userIds, friendships, testLinkUsers, true, true);
			
			usersNeeded.addAll(testData.keySet());
			
			System.out.println("Links needed: " + linksNeeded.size());
			for (long testUserId : testData.keySet()) {
				linksNeeded.addAll(testData.get(testUserId));
			}
			System.out.println("After: " + linksNeeded.size());
		}
		
		Map<Long, Double[]> users = UserUtil.getUserFeatures(usersNeeded);
		Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linksNeeded);
		
		//Clean up links that are not in the linkrLinkInfo table. Clean up users after
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
		
		Set<Long> dontTest = new HashSet<Long>();
		if (Constants.DEPLOYMENT_TYPE == Constants.TEST) {
			for (long userId : testData.keySet()) {
				if (!userLinkSamples.containsKey(userId)) {
					dontTest.add(userId);
					continue;
				}
				
				Set<Long> samples = testData.get(userId);
				HashSet<Long> remove = new HashSet<Long>();
				for (long linkId : samples) {
					if (!links.containsKey(linkId)) {
						remove.add(linkId);
					}
				}
				samples.removeAll(remove);
			}
		}
		for (long removeId : dontTest) {
			testData.remove(removeId);
		}
		
		System.out.println("For Training: " + userLinkSamples.size());
		if (Constants.DEPLOYMENT_TYPE == Constants.TEST) System.out.println("For Testing: " + testData.size());
		
		SQLUtil.closeSqlConnection();
		
		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		//((SocialRecommender)recommender).setBeta(arg);
		
		
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
			
			Map<Long, Set<Long>> appUserTestData = new HashMap<Long, Set<Long>>();
			for (long userId : testData.keySet()) {
				if (appUsers.contains(userId)) {
					appUserTestData.put(userId, testData.get(userId));
				}
			}
					
			Map<Long, Double[]> appUserPrecisionRecalls100 = recommender.getPrecisionRecall(appUserTestData, 100);
			Map<Long, Double[]> appUserPrecisionRecalls200 = recommender.getPrecisionRecall(appUserTestData, 200);
			
			double map = 0;
			double appUsersMap = 0;
			int testedAppUserCount = 0;
			
			double meanPrecision100 = 0;
			double meanPrecision200 = 0;
			double meanRecall100 = 0;
			double meanRecall200 = 0;
			double meanF100 = 0;
			double meanF200 = 0;
			
			for (long userId : averagePrecisions.keySet()) {
				double pre = averagePrecisions.get(userId);
	
				map += pre;
				if (appUsers.contains(userId)) {
					
					
					if (pre == 0) System.out.println("App user 0 precision");
					appUsersMap += pre;
					testedAppUserCount++;
					
					///*
					double precision100 = appUserPrecisionRecalls100.get(userId)[0];
					double recall100 =  appUserPrecisionRecalls100.get(userId)[1];
					double f100 = (precision100 + recall100 > 0) ? 2 * (precision100 * recall100) / (precision100 + recall100) : 0;
					
					System.out.println("AP: " + pre);
					System.out.println("Precision 100: " + precision100);
					System.out.println("Recall 100: " + recall100);
					System.out.println("F100: " + f100);
					
					double precision200 = appUserPrecisionRecalls200.get(userId)[0];
					double recall200 = appUserPrecisionRecalls100.get(userId)[1];
					double f200 = (precision100 + recall100 > 0) ? 2 * (precision200 * recall200) / (precision200 + recall200) : 0;
					
					meanPrecision100 += precision100;
					meanPrecision200 += precision200;
					meanRecall100 += recall100;
					meanRecall200 += recall200;
					
					meanF100 += f100;
					meanF200 += f200;
					//*/
				}
			}
			
			map /= (double)averagePrecisions.size();
			appUsersMap /= (double)testedAppUserCount;
			meanPrecision100 /= (double)testedAppUserCount;
			meanPrecision200 /= (double)testedAppUserCount;
			meanRecall100 /= (double)testedAppUserCount;
			meanRecall200 /= (double)testedAppUserCount;
			meanF100 /= (double)testedAppUserCount;
			meanF200 /= (double)testedAppUserCount;
			
			double standardDev = 0;
			double appStandardDev = 0;
			double precisionStandardDev100 = 0;
			double precisionStandardDev200 = 0;
			double recallStandardDev100 = 0;
			double recallStandardDev200 = 0;
			double fStandardDev100 = 0;
			double fStandardDev200 = 0;
			
			for (long userId : averagePrecisions.keySet()) {
				double pre = averagePrecisions.get(userId);
				standardDev += Math.pow(pre - map, 2);
				
				if (appUsers.contains(userId)) {
					appStandardDev += Math.pow(pre - appUsersMap, 2);
					
					///*
					Object[] precisionRecall100 = appUserPrecisionRecalls100.get(userId);
					Object[] precisionRecall200 = appUserPrecisionRecalls200.get(userId);
					
					double precision100 = (Double)precisionRecall100[0];
					double precision200 = (Double)precisionRecall200[0];
					double recall100 = (Double)precisionRecall100[1];
					double recall200 = (Double)precisionRecall200[1];
					
					double f100 = (precision100 + recall100 > 0) ? 2 * (precision100 * recall100) / (precision100 + recall100) : 0;
					double f200 = (precision200 + recall200 > 0) ? 2 * (precision200 * recall200) / (precision200 + recall200) : 0;
					precisionStandardDev100 += Math.pow(precision100 - meanPrecision100, 2);
					recallStandardDev100 += Math.pow(recall100 - meanRecall100, 2);
					precisionStandardDev200 += Math.pow(precision200 - meanPrecision200, 2);
					recallStandardDev200 += Math.pow(recall200 - meanRecall200, 2);
					
					fStandardDev100 += Math.pow(f100 - meanF100, 2);
					fStandardDev200 += Math.pow(f200 - meanF200, 2);
					//*/
				}
			}
			standardDev /= (double)averagePrecisions.size();
			standardDev = Math.sqrt(standardDev);
			appStandardDev /= (double)testedAppUserCount;
			appStandardDev = Math.sqrt(appStandardDev);
			
			precisionStandardDev100 /= (double)testedAppUserCount;
			precisionStandardDev100 = Math.sqrt(precisionStandardDev100);
			recallStandardDev100 /= (double)testedAppUserCount;
			recallStandardDev100 = Math.sqrt(recallStandardDev100);
			precisionStandardDev200 /= (double)testedAppUserCount;
			precisionStandardDev200 = Math.sqrt(precisionStandardDev200);
			recallStandardDev200 /= (double)testedAppUserCount;
			recallStandardDev200 = Math.sqrt(recallStandardDev200);
			fStandardDev100 /= (double)testedAppUserCount;
			fStandardDev100 = Math.sqrt(fStandardDev100);
			fStandardDev200 /= (double)testedAppUserCount;
			fStandardDev200 = Math.sqrt(fStandardDev200);
			
			double standardError = standardDev / Math.sqrt(averagePrecisions.size());
			double appStandardErr = appStandardDev / Math.sqrt(testedAppUserCount);
			
			double precisionSE100 = precisionStandardDev100 / Math.sqrt(testedAppUserCount);
			double precisionSE200 = precisionStandardDev200 / Math.sqrt(testedAppUserCount);
			double recallSE100 = recallStandardDev100 / Math.sqrt(testedAppUserCount);
			double recallSE200 = recallStandardDev200 / Math.sqrt(testedAppUserCount);
			double fSE100 = fStandardDev100 / Math.sqrt(testedAppUserCount);
			double fSE200 = fStandardDev200 / Math.sqrt(testedAppUserCount);
			
			//System.out.println("K: " + arg);
			System.out.println("MAP: " + map);
			System.out.println("SD: " + standardDev);
			System.out.println("SE: " + standardError);
			System.out.println("App Users MAP: "+ appUsersMap);
			System.out.println("App Users SD: " + appStandardDev);
			System.out.println("App Users SE: " + appStandardErr);
			
			System.out.println("Mean Precision 100: " + meanPrecision100);
			System.out.println("SD: " + precisionStandardDev100);
			System.out.println("SE: " + precisionSE100);
			
			System.out.println("Mean Recall 100: " + meanRecall100);
			System.out.println("SD: " + recallStandardDev100);
			System.out.println("SE: " + recallSE100);
			
			System.out.println("Mean F1 100: " + meanF100);
			System.out.println("SD: " + fStandardDev100);
			System.out.println("SE: " + fSE100);
			
			System.out.println("Mean Precision 200: " + meanPrecision200);
			System.out.println("SD: " + precisionStandardDev200);
			System.out.println("SE: " + precisionSE200);
			
			System.out.println("Mean Recall 200: " + meanRecall200);
			System.out.println("SD: " + recallStandardDev200);
			System.out.println("SE: " + recallSE200);
			
			System.out.println("Mean F1 200: " + meanF200);
			System.out.println("SD: " + fStandardDev200);
			System.out.println("SE: " + fSE200);
			
		
		}
		
		
		System.out.println("Done");
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
			
			HashSet<Long> dontIncludeIds = new HashSet<Long>();
			HashSet<String> dontIncludeHashes = new HashSet<String>();
			
			// Don't recommend links that were already liked
			result = statement.executeQuery("SELECT link_id FROM linkrLinkLikes WHERE id=" + id);
			while (result.next()) {
				dontIncludeIds.add(result.getLong("link_id"));
			}
			
	
			// Don't recommend links that are already pending recommendedation
			result = statement.executeQuery("SELECT link_id FROM lrRecommendations WHERE user_id=" + id + " AND type='" + type + "'");
			while(result.next()) {
				dontIncludeIds.add(result.getLong("link_id"));
			}
			
			
			//Don't recommend links that were already published by the app
			result = statement.executeQuery("SELECT link_hash FROM trackRecommendedLinks WHERE uid=" + id);
			while (result.next()) {
				dontIncludeHashes.add(result.getString("link_hash"));
			}

			
			//Don't recommend links that were clicked by the user
			result = statement.executeQuery("SELECT link_hash FROM trackLinkClicked WHERE uid_clicked=" + id);
			while (result.next()) {
				dontIncludeHashes.add(result.getString("link_hash"));
			}
			
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
			for (long linkIds : dontIncludeIds) {
				query.append(",");
				query.append(linkIds);
			}
			
			query.append(") AND link_hash NOT IN(''");
			for (String hash : dontIncludeHashes) {
				query.append(",'");
				query.append(hash);
				query.append("'");
			}
			
			query.append(") AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.RECOMMENDING_WINDOW_RANGE + "))");
			
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
			
			HashSet<Long> dontIncludeIds = new HashSet<Long>();
			HashSet<String> dontIncludeHashes = new HashSet<String>();
			
			// Don't recommend links that were already liked
			result = statement.executeQuery("SELECT link_id FROM linkrLinkLikes WHERE id=" + id);
			while (result.next()) {
				dontIncludeIds.add(result.getLong("link_id"));
			}
			
			// Don't recommend links that are already pending recommendedation
			result = statement.executeQuery("SELECT link_id FROM lrRecommendations WHERE user_id=" + id + " AND type='" + type + "'");
			while(result.next()) {
				dontIncludeIds.add(result.getLong("link_id"));
			}
			
			//Don't recommend links that were already published by the app
			result = statement.executeQuery("SELECT link_hash FROM trackRecommendedLinks WHERE uid=" + id);
			while (result.next()) {
				dontIncludeHashes.add(result.getString("link_hash"));
			}
			
			//Don't recommend links that were clicked by the user
			result = statement.executeQuery("SELECT link_hash FROM trackLinkClicked WHERE uid_clicked=" + id);
			while (result.next()) {
				dontIncludeHashes.add(result.getString("link_hash"));
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
			for (long linkIds : dontIncludeIds) {
				query.append(",");
				query.append(linkIds);
			}
			
			query.append(") AND link_hash NOT IN(''");
			for (String hash : dontIncludeHashes) {
				query.append(",'");
				query.append(hash);
				query.append("'");
			}
			
			query.append(") AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Constants.RECOMMENDING_WINDOW_RANGE + "))");
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
			if (friendLinks.size() > maxLinks) maxLinks = friendLinks.size();
			
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
	public Map<Long, Set<Long>> getUserLinksSample(Map<Long, Set<Long>> linkLikes, Set<Long> userIds, Map<Long, Map<Long, Double>> friendships, Map<Long, Long[]> links, boolean limit, boolean forTesting)
		throws SQLException
	{
		HashMap<Long, Set<Long>> userLinkSamples = new HashMap<Long, Set<Long>>();
		
		Connection conn = SQLUtil.getSqlConnection();
		Statement statement = conn.createStatement();
		
		int count = 0;
		
		for (long linkId : linkLikes.keySet()) {
			Set<Long> users = linkLikes.get(linkId);
			//if (users.size() == 1) continue;
			
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
	
		
		HashSet<Long> remove = new HashSet<Long>();
		
		for (Long userId : userLinkSamples.keySet()) {
			Set<Long> samples = userLinkSamples.get(userId);
			if (samples.size() < 2) {
				remove.add(userId);
				continue;
			}
			
			if (Constants.DEPLOYMENT_TYPE == Constants.RECOMMEND && type.equals(Constants.SVM)) {
				if (samples.size() < 5) {
					//remove.add(userId);
					//continue;
				}
			}
			
			System.out.println("User: " + ++count + " " + samples.size());
			
			Set<Long> friends = friendships.get(userId).keySet();
			
			int likeCount = samples.size();
			
			if (!forTesting) {
				ResultSet result = statement.executeQuery("SELECT link_id FROM trackRecommendedLinks WHERE uid=" + userId + " AND rating=2");
				while (result.next()) {
					if (links.containsKey(result.getLong("link_id"))) {
						samples.add(result.getLong("link_id"));
					}
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
			
			if (forTesting && samples.size() < likeCount * 2) {
				remove.add(userId);
			}
		}
		
		for (Long removeId : remove) {
			userLinkSamples.remove(removeId);
		}
		
		System.out.println("Final Sample: " + userLinkSamples.size());
		
		return userLinkSamples;
	}
}
