package org.nicta.lr;

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
import org.nicta.lr.recommender.LogisticSocialRecommender;
import org.nicta.lr.recommender.BaselineGlobalRecommender;
import org.nicta.lr.recommender.BaselineRecommender;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.Configuration;
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
	
	public static void main(String[] args)
		throws Exception
	{	
		LinkRecommender lr = new LinkRecommender();
		lr.parseArgs(args);
		lr.run();
	}
	
	public void parseArgs(String[] args)
	{
		for (int x = 0; x < args.length; x += 2) {
			if (args[x].equals("type")) {
				this.type = args[x+1];
			}
			else if (args[x].equals("train")) {
				Configuration.TRAINING_DATA = args[x+1];
			}
			else if (args[x].equals("test")) {
				Configuration.TEST_DATA = args[x+1];
			}
			else if (args[x].equals("deployment")) {
				Configuration.DEPLOYMENT_TYPE = args[x+1];
			}
		}
	}
	
	public void run()
		throws SQLException, IOException
	{	
		System.out.println("Starting run");
		
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();
		
		Map<Long, Set<Long>>[] data = getTrainingData(friendships);
		Map<Long, Set<Long>> trainData = data[0];
		Map<Long, Set<Long>> linkLikes = data[1];
		
		Set<Long> usersNeeded = new HashSet<Long>();
		usersNeeded.addAll(trainData.keySet());
		
		Set<Long> linksNeeded = new HashSet<Long>();
		linksNeeded.addAll(linkLikes.keySet());
		for (long id : trainData.keySet()) {
			linksNeeded.addAll(trainData.get(id));
		}
		
		if (Configuration.DEPLOYMENT_TYPE == Constants.RECOMMEND) {
			recommend(trainData, linkLikes, friendships, usersNeeded, linksNeeded);
		}
		else {
			test(trainData, linkLikes, friendships, usersNeeded, linksNeeded);
		}
		
		System.out.println("Done");
	}
	
	
	public void recommend(Map<Long, Set<Long>> trainData, Map<Long, Set<Long>> linkLikes, Map<Long, Map<Long, Double>> friendships, Set<Long> usersNeeded, Set<Long> linksNeeded)
		throws SQLException
	{
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
			DetectorFactory.loadProfile(Configuration.LANG_PROFILE_FOLDER);
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
		
		Map<Long, Double[]> users = UserUtil.getUserFeatures(usersNeeded);
		Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linksNeeded);
		
		//Clean up links that are not in the linkrLinkInfo table. Clean up users after
		for (long userId : trainData.keySet()) {
			Set<Long> samples = trainData.get(userId);
			HashSet<Long> remove = new HashSet<Long>();
			for (long linkId : samples) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
				}
			}
			samples.removeAll(remove);
		}	
		
		SQLUtil.closeSqlConnection();
		
		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		recommender.train(trainData);
		
		Map<Long, Map<Long, Double>> friendRecommendations = recommender.recommend(friendLinksToRecommend);
		Map<Long, Map<Long, Double>> nonFriendRecommendations = recommender.recommend(nonFriendLinksToRecommend);

		saveLinkRecommendations(friendRecommendations, nonFriendRecommendations, type);
		recommender.saveModel();
		
		SQLUtil.closeSqlConnection();
	}
	
	public void test(Map<Long, Set<Long>> trainData, Map<Long, Set<Long>> linkLikes, Map<Long, Map<Long, Double>> friendships, Set<Long> usersNeeded, Set<Long> linksNeeded)
		throws SQLException
	{
		System.out.println("Getting test data");
		
		Map<Long, Set<Long>>[] forTesting = getTestData(trainData, linkLikes, friendships);
		Map<Long, Set<Long>> testData = forTesting[0];
		
		int testCount = 0;
		for (long userId : testData.keySet()) {
			System.out.println("Test: " + userId + " " + testData.size());
			testCount += testData.get(userId).size();
		}
		System.out.println("Total Test: " + testCount);
		
		Map<Long, Set<Long>> testLikes = forTesting[1];
		for (long linkId : testLikes.keySet()) {
			Set<Long> likes = testLikes.get(linkId);
			
			if (linkLikes.containsKey(linkId)) {
				linkLikes.get(linkId).addAll(likes);
			}
			else {
				linkLikes.put(linkId, likes);
			}
		}
		
		usersNeeded.addAll(testData.keySet());
		for (long userId : testData.keySet()) {
			linksNeeded.addAll(testData.get(userId));
		}
		
		Map<Long, Double[]> users = UserUtil.getUserFeatures(usersNeeded);
		Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linksNeeded);
		
		SQLUtil.closeSqlConnection();
		
		//Clean up links that are not in the linkrLinkInfo table. Clean up users after
		for (long userId : trainData.keySet()) {
			Set<Long> samples = trainData.get(userId);
			HashSet<Long> remove = new HashSet<Long>();
			
			for (long linkId : samples) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
				}
			}
			
			samples.removeAll(remove);
		}	
		
		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		recommender.train(trainData);
		
		outputMap(recommender, testData);
		outputMetrics(recommender, testData);
	}
	
	public void outputMap(Recommender recommender, Map<Long, Set<Long>> testData)
	{
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
	
	public Map<Long, Set<Long>>[] getTestData(Map<Long, Set<Long>> trainData, Map<Long, Set<Long>> trainLikes, Map<Long, Map<Long, Double>> friendships)
		throws SQLException
	{
		Map<Long, Set<Long>> testData = null;
		Map<Long, Set<Long>> testLikes = null;
		
		if (Configuration.TEST_DATA.equals(Constants.FB_USER_PASSIVE)) {
			if (Configuration.TRAINING_DATA.equals(Constants.PASSIVE) || Configuration.TRAINING_DATA.equals(Constants.UNION)) {
				testData = getPassiveSubsetForTesting(trainData, trainLikes, friendships);
				testLikes = trainLikes;
			}
			else {
				Map<Long, Set<Long>>[] data = getPassiveData(friendships);
				testData = data[0];
				testLikes = data[1];
				
			}
		}
		else if (Configuration.TEST_DATA.equals(Constants.APP_USER_PASSIVE)) {
			Set<Long> appUsers = UserUtil.getAppUserIds();
			
			if (Configuration.TRAINING_DATA.equals(Constants.PASSIVE) || Configuration.TRAINING_DATA.equals(Constants.UNION)) {
				System.out.println("Getting passive data");
				testData = getPassiveSubsetForTesting(trainData, trainLikes, friendships);
				testLikes = trainLikes;
			}
			else {
				Map<Long, Set<Long>>[] data = getPassiveData(friendships);
				testData = data[0];
				testLikes = data[1];
			}
			
			HashSet<Long> removeNonAppUsers = new HashSet<Long>();
			
			for (long userId : testData.keySet()) {
				if (!appUsers.contains(userId)) {
					removeNonAppUsers.add(userId);
				}
			}
			
			for (long userId : removeNonAppUsers) {
				testData.remove(userId);
			}
		}
		else if (Configuration.TEST_DATA.equals(Constants.APP_USER_ACTIVE_ALL)) {
			if (Configuration.TRAINING_DATA.equals(Constants.ACTIVE) || Configuration.TRAINING_DATA.equals(Constants.UNION)) {
				testData = getActiveSubsetForTesting(trainData, trainLikes, friendships, Constants.ACTIVE_ALL);
				testLikes = trainLikes;
			}
			else {
				Map<Long, Set<Long>>[] data = getActiveData(friendships, Constants.ACTIVE_ALL);
				testData = data[0];
				testLikes = data[1];
			}
		}
		else if (Configuration.TEST_DATA.equals(Constants.APP_USER_ACTIVE_FRIEND)) {
			if (Configuration.TRAINING_DATA.equals(Constants.ACTIVE) || Configuration.TRAINING_DATA.equals(Constants.UNION)) {
				testData = getActiveSubsetForTesting(trainData, trainLikes, friendships, Constants.ACTIVE_FRIEND);
				testLikes = trainLikes;
			}
			else {
				Map<Long, Set<Long>>[] data = getActiveData(friendships, Constants.ACTIVE_FRIEND);
				testData = data[0];
				testLikes = data[1];
			}
		}
		else if (Configuration.TEST_DATA.equals(Constants.APP_USER_ACTIVE_NON_FRIEND)) {
			if (Configuration.TRAINING_DATA.equals(Constants.ACTIVE) || Configuration.TRAINING_DATA.equals(Constants.UNION)) {
				testData = getActiveSubsetForTesting(trainData, trainLikes, friendships, Constants.ACTIVE_NON_FRIEND);
				testLikes = trainLikes;
			}
			else {
				Map<Long, Set<Long>>[] data = getActiveData(friendships, Constants.ACTIVE_NON_FRIEND);
				testData = data[0];
				testLikes = data[1];
			}
		}
		
		System.out.println("Cleaning up test data: " + testData.size());
		Set<Long> removeUsers = new HashSet<Long>();
		
		Set<Long> trainLinkCount = new HashSet<Long>();
		for (long userId : trainData.keySet()) {
			trainLinkCount.addAll(trainData.get(userId));
		}
		System.out.println("Training links before: " + trainLinkCount.size());
		
		//Remove users that aren't in the training data
		//Otherwise, remove user-link pairs in the training data that are in the test data
		for (long userId : testData.keySet()) {
			if (!trainData.containsKey(userId)) {
				removeUsers.add(userId);
				continue;
			}
			
			Set<Long> testLinks = testData.get(userId);
			trainData.get(userId).removeAll(testLinks);
		}
		for (long userId : removeUsers) {
			testData.remove(userId);
		}
		
		Set<Long> allTrainLinks = new HashSet<Long>();
		for (long userId : trainData.keySet()) {
			allTrainLinks.addAll(trainData.get(userId));
		}
		
		System.out.println("Training links after: " + allTrainLinks.size());
		
		System.out.println("Test data: " + testData.size());
		
		//Remove links that aren't in the training data
		//If a user has either 0 liked links or 0 non-liked links in the testing data,
		//Remove user from the test data
		removeUsers = new HashSet<Long>();
		for (long userId : testData.keySet()) {
			Set<Long> remove = new HashSet<Long>();
			Set<Long> testLinks = testData.get(userId);
			
			for (long linkId : testLinks) {
				if (!allTrainLinks.contains(linkId)) {
					remove.add(linkId);
				}
			}
			
			testLinks.removeAll(remove);
			
			int likeCount = 0;
			int notLikeCount = 0;
			
			for (long linkId : testLinks) {
				if (testLikes.containsKey(linkId) && testLikes.get(linkId).contains(userId)) {
					likeCount++;
				}
				else {
					notLikeCount++;
				}
			}
			
			if (likeCount == 0 || notLikeCount == 0) {
				removeUsers.add(userId);
			}
		}
		
		for (long userId : removeUsers) {
			testData.remove(userId);
		}
		
		int testCount = 0;
		for (long testId : testData.keySet()) {
			testCount += testData.get(testId).size();
			
			Set<Long> userTrain = trainData.get(testId);
			Set<Long> userTest = testData.get(testId);
			
			for (long trainLinkId : userTrain) {
				for (long testLinkId : userTest) {
					if (trainLinkId == testLinkId) {
						System.out.println("NOT DISJOINT!");
						System.exit(1);
					}
				}
			}
		}
		
		System.out.println("Test data done: " + testData.size() + " " + testCount);
		
		Map<Long, Set<Long>>[] test = new Map[2];
		test[0] = testData;
		test[1] = testLikes;
		
		return test;
	}
	
	public Map<Long, Set<Long>>[] getTrainingData(Map<Long, Map<Long, Double>> friendships)
		throws SQLException
	{
		HashMap<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> linkLikes = new HashMap<Long, Set<Long>>();
		
		if (Configuration.TRAINING_DATA.equals(Constants.PASSIVE) || Configuration.TRAINING_DATA.equals(Constants.PASSIVE)) {
			Map<Long, Set<Long>>[] passiveData = getPassiveData(friendships);
			Map<Long, Set<Long>> passiveSamples = passiveData[0];
			Map<Long, Set<Long>> passiveLikes = passiveData[1];
			
			trainData.putAll(passiveSamples);
			linkLikes.putAll(passiveLikes);
		}
		
		if (Configuration.TRAINING_DATA.equals(Constants.ACTIVE) || Configuration.TRAINING_DATA.equals(Constants.UNION)) {
			Map<Long, Set<Long>>[] activeData = getActiveData(friendships, Constants.ACTIVE_ALL);
			Map<Long, Set<Long>> activeSamples = activeData[0];
			Map<Long, Set<Long>> activeLikes = activeData[1];
			
			for (long userId : activeSamples.keySet()) {
				Set<Long> samples = activeSamples.get(userId);
				
				if (!trainData.containsKey(userId)) {
					trainData.put(userId, new HashSet<Long>());
				}
				trainData.get(userId).addAll(samples);
			}
			
			for (long linkId : activeLikes.keySet()) {
				Set<Long> likes = activeLikes.get(linkId);
				
				if (!linkLikes.containsKey(linkId)) {
					linkLikes.put(linkId, new HashSet<Long>());
				}
				linkLikes.get(linkId).addAll(likes);
			}
		}
		
		Map<Long, Set<Long>>[] data = new Map[2];
		data[0] = trainData;
		data[1] = linkLikes;
		
		return data;
	}
	
	public Map<Long, Set<Long>>[] getPassiveData(Map<Long, Map<Long, Double>> friendships)
		throws SQLException
	{
		Set<Long> userIds = UserUtil.getUserIds();
		Set<Long> linkIds = LinkUtil.getLinkIds();
		
		Map<Long, Long[]> linkPosters = LinkUtil.getLinkPosters(linkIds);
		
		Map<Long, Set<Long>> linkLikes = LinkUtil.getLinkLikes(linkPosters);
		Map<Long, Set<Long>> trainSamples = getTrainingSample(linkLikes, userIds, friendships, linkPosters);
		
		Map<Long, Set<Long>>[] data = new Map[2];
		data[0] = trainSamples;
		data[1] = linkLikes;
		
		return data;
	}
	
	public Map<Long, Set<Long>>[] getActiveData(Map<Long, Map<Long, Double>> friendships, int restriction)
		throws SQLException
	{
		Statement statement = SQLUtil.getStatement();
		HashMap<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> linkLikes = new HashMap<Long, Set<Long>>();
		
		ResultSet result = statement.executeQuery("SELECT link_id, uid, rating FROM trackRecommendedLinks WHERE rating=2 OR rating=1");
		
		HashSet<Long> allLinks = new HashSet<Long>();
		
		while (result.next()) {
			long linkId = result.getLong("link_id");
			long userId = result.getLong("uid");
			int rating = result.getInt("rating");
			
			if (!trainData.containsKey(userId)) {
				trainData.put(userId, new HashSet<Long>());
			}
			trainData.get(userId).add(linkId);
			
			if (rating == 1) {
				if (!linkLikes.containsKey(linkId)) {
					linkLikes.put(linkId, new HashSet<Long>());
				}
				linkLikes.get(linkId).add(userId);
			}
			
			allLinks.add(linkId);
		}	
	
		String clickQuery = "SELECT link_id, uid_clicked FROM linkrLinks l, trackLinkClicked t WHERE l.link=t.link";
		result = statement.executeQuery(clickQuery);
		
		while (result.next()) {
			long linkId = result.getLong("link_id");
			long userId = result.getLong("uid_clicked");
			
			if (!linkLikes.containsKey(linkId)) {
				linkLikes.put(linkId, new HashSet<Long>());
			}
			
			Set<Long> likes = linkLikes.get(linkId);
			likes.add(userId);
			
			allLinks.add(linkId);
		}
		statement.close();
		
		Map<Long, Long[]> linkPosters = LinkUtil.getLinkPosters(allLinks);
	
		HashSet<Long> removeUsers = new HashSet<Long>();
		
		for (long userId : trainData.keySet()) {
			Set<Long> testLinks = trainData.get(userId);
				
			HashSet<Long> removeLinks = new HashSet<Long>();
			
			int userLike = 0;
			
			for (long linkId : testLinks) {
				Long[] posters = linkPosters.get(linkId);
				if (posters == null) {
					removeLinks.add(linkId);
					continue;
				}
				
				boolean friend = false;
				if ((friendships.containsKey(userId) && friendships.get(userId).containsKey(posters[0]))
						|| (friendships.containsKey(posters[0]) && friendships.get(posters[0]).containsKey(userId))
					) {
					friend = true;
				}
					
				if (restriction == Constants.ACTIVE_FRIEND && !friend) {
					removeLinks.add(linkId);
				}
				else if (restriction == Constants.ACTIVE_NON_FRIEND && friend) {
					removeLinks.add(linkId);
				}
				
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) userLike++;
			}
			
			testLinks.removeAll(removeLinks);
			if (testLinks.size() == 0) {
				removeUsers.add(userId);
				continue;
			}
			
			System.out.println("User links: " + userLike + " " + testLinks.size());
		}
		for (long userId : removeUsers) {
			trainData.remove(userId);
		}
		
		Map<Long, Set<Long>>[] data = new Map[2];
		data[0] = trainData;
		data[1] = linkLikes;
		
		return data;
	}
	
	public Map<Long, Set<Long>> getPassiveSubsetForTesting(Map<Long, Set<Long>> trainData, Map<Long, Set<Long>> trainLikes,  Map<Long, Map<Long, Double>> friendships)
		throws SQLException
	{
		Map<Long, Set<Long>> testData = new HashMap<Long, Set<Long>>();
	
		System.out.println("Getting active data");
		Map<Long, Set<Long>> activeData = getActiveData(friendships, Constants.ACTIVE_ALL)[0];
		System.out.println("Active data: " + activeData.size());
		
		for (long userId : trainData.keySet()) {
			Set<Long> userLinks = trainData.get(userId);
			
			HashSet<Long> userLikes = new HashSet<Long>();
			
			Set<Long> active = activeData.get(userId);
			
			int likeCount = 0;
			int notLikeCount = 0;
			for (long linkId : userLinks) {
				if (active != null && active.contains(linkId)) continue;
					
				if (trainLikes.containsKey(linkId) && trainLikes.get(linkId).contains(userId)) {
					likeCount++;
					userLikes.add(linkId);
				}
				else {
					notLikeCount++;
				}
			}
			
			
			int testLikeCount = (int)(likeCount * 0.2);
			if (testLikeCount < 1) continue;
			
			int testNotLikeCount = (int)(notLikeCount * 0.2);
			if (testNotLikeCount < 1) continue;
			
			System.out.println("TEST user: " + userId + " " + testLikeCount + " " + testNotLikeCount);
			
			int addedLike = 0;
			int addedNotLike = 0;
			
			Object[] links = userLinks.toArray();
			
			HashSet<Long> userTest = new HashSet<Long>();
			
			while (addedLike < testLikeCount) {
				int randomIndex = (int)(Math.random() * links.length);
				Long linkId = (Long)links[randomIndex];
				
				if (active != null && active.contains(linkId)) continue;
				
				if (userLikes.contains(linkId) && !userTest.contains(linkId)) {
					userTest.add(linkId);
					addedLike++;
				}
			}
			
			while (addedNotLike < testNotLikeCount) {
				int randomIndex = (int)(Math.random() * links.length);
				Long linkId = (Long)links[randomIndex];
				
				if (active != null && active.contains(linkId)) continue;
				
				if (!userLikes.contains(linkId) && !userTest.contains(linkId)) {
					userTest.add(linkId);
					addedNotLike++;
				}
			}
			
			testData.put(userId, userTest);
		}
		
		System.out.println("Total test users : " + testData.size());
		
		return testData;
	}
	
	public Map<Long, Set<Long>> getActiveSubsetForTesting(Map<Long, Set<Long>> trainData, Map<Long, Set<Long>> trainLikes, Map<Long, Map<Long, Double>> friendships, int restriction)
		throws SQLException
	{
		Map<Long, Set<Long>> testData = new HashMap<Long, Set<Long>>();
		
		Map<Long, Set<Long>> activeData = getActiveData(friendships, Constants.ACTIVE_ALL)[0];
		HashSet<Long> allLinks = new HashSet<Long>();
		
		for (long userId : trainData.keySet()) {
			Set<Long> userLinks = trainData.get(userId);
			allLinks.addAll(userLinks);
		}
		
		Map<Long, Long[]> linkPosters = LinkUtil.getLinkPosters(allLinks);
		
		System.out.println("Restriction: " + restriction + " " + Constants.ACTIVE_ALL);
		System.out.println("Train: " + trainData.size() +  " Active: " + activeData.size());
		
		for (long userId : trainData.keySet()) {
			Set<Long> userLinks = trainData.get(userId);
			
			int likeCount = 0;
			int notLikeCount = 0;
			
			HashSet<Long> userLikes = new HashSet<Long>();
			
			Set<Long> active = activeData.get(userId);
			
			System.out.println("User links train: " + userLinks.size() + " User active: " + active.size());
			
			for (long linkId : userLinks) {
				if (active == null || !active.contains(linkId)) {
					//System.out.println("HOLY SHIT");
					//System.exit(1);
					continue;
				}
				
				Long[] posters = linkPosters.get(linkId);
				
				boolean friend = false;
				if ((friendships.containsKey(userId) && friendships.get(userId).containsKey(posters[0]))
						|| (friendships.containsKey(posters[0]) && friendships.get(posters[0]).containsKey(userId))
					) {
					friend = true;
				}
				
				if ((restriction == Constants.ACTIVE_FRIEND && friend) 
						|| (restriction == Constants.ACTIVE_NON_FRIEND && !friend)
						|| restriction == Constants.ACTIVE_ALL) {
					
					//System.out.println("WTF");
					
					if (trainLikes.containsKey(linkId) && trainLikes.get(linkId).contains(userId)) {
						likeCount++;
						userLikes.add(linkId);
					}
					else {
						notLikeCount++;
					}
				}
			}
			
			//System.out.println("likeCount : " + likeCount + " notLikeCount: " + notLikeCount);
			
			int testLikeCount = (int)(likeCount * 0.2);
			if (testLikeCount < 1) continue;
			
			int testNotLikeCount = (int)(notLikeCount * 0.2);
			if (testNotLikeCount < 1) continue;
			
			System.out.println("testLikeCount : " + testLikeCount + " testNotLikeCount: " + testNotLikeCount);
			
			int addedLike = 0;
			int addedNotLike = 0;
			
			Object[] links = userLinks.toArray();
			
			HashSet<Long> userTest = new HashSet<Long>();
			
			while (addedLike < testLikeCount) {
				int randomIndex = (int)(Math.random() * links.length);
				Long linkId = (Long)links[randomIndex];
				
				if (active == null || !active.contains(linkId)) continue;
				
				Long[] posters = linkPosters.get(linkId);
				
				boolean friend = false;
				if ((friendships.containsKey(userId) && friendships.get(userId).containsKey(posters[0]))
						&& (friendships.containsKey(posters[0]) && friendships.get(posters[0]).containsKey(userId))
					) {
					friend = true;
				}
				
				if ((restriction == Constants.ACTIVE_FRIEND && friend) 
						|| (restriction == Constants.ACTIVE_NON_FRIEND && !friend)
						|| restriction == Constants.ACTIVE_ALL) {
					if (userLikes.contains(linkId) && !userTest.contains(linkId)) {
						userTest.add(linkId);
						addedLike++;
					}
				}	
			}
			
			while (addedNotLike < testNotLikeCount) {
				int randomIndex = (int)(Math.random() * links.length);
				Long linkId = (Long)links[randomIndex];
				
				if (active == null || !active.contains(linkId)) continue;
				
				Long[] posters = linkPosters.get(linkId);
				
				boolean friend = false;
				if ((friendships.containsKey(userId) && friendships.get(userId).containsKey(posters[0]))
						&& (friendships.containsKey(posters[0]) && friendships.get(posters[0]).containsKey(userId))
					) {
					friend = true;
				}
				
				if ((restriction == Constants.ACTIVE_FRIEND && friend) 
						|| (restriction == Constants.ACTIVE_NON_FRIEND && !friend)
						|| restriction == Constants.ACTIVE_ALL) {
					if (!userLikes.contains(linkId) && !userTest.contains(linkId)) {
						userTest.add(linkId);
						addedNotLike++;
					}
				}
			}
			
			testData.put(userId, userTest);
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
		
		Statement statement = SQLUtil.getStatement();
		
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
			
			query.append(") AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Configuration.RECOMMENDING_WINDOW_RANGE + "))");
			
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
		
		Statement statement = SQLUtil.getStatement();
		
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
			
			query.append(") AND DATE(created_time) >= DATE(ADDDATE(CURRENT_DATE(), -" + Configuration.RECOMMENDING_WINDOW_RANGE + "))");
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
		else if (Constants.LOGISTIC.equals(type)) {
			recommender = new LogisticSocialRecommender(linkLikes, users, links, friendships);
		}
		
		return recommender;
	}
	
	public void saveLinkRecommendations(Map<Long, Map<Long, Double>> friendRecommendations, Map<Long, Map<Long, Double>> nonFriendRecommendations, String type)
		throws SQLException
	{
		Statement statement = SQLUtil.getStatement();
		
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
				PreparedStatement ps = SQLUtil.prepareStatement("INSERT INTO lrRecommendations VALUES(?,?,?,?,0)");
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
	 * 
	 * Users with no liked links are not included.
	 */
	public Map<Long, Set<Long>> getTrainingSample(Map<Long, Set<Long>> linkLikes, Set<Long> userIds, Map<Long, Map<Long, Double>> friendships, Map<Long, Long[]> links)
		throws SQLException
	{
		HashMap<Long, Set<Long>> userLinkSamples = new HashMap<Long, Set<Long>>();
		
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
			if (samples.size() < 5) {
				//remove.add(userId);
				//continue;
			}
			
			System.out.println("User: " + ++count + " " + samples.size());
			
			Set<Long> friends = friendships.get(userId).keySet();
			
			int likeCount = samples.size();
			
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
		}
		
		for (Long removeId : remove) {
			userLinkSamples.remove(removeId);
		}
		
		System.out.println("Final Sample: " + userLinkSamples.size());
		
		return userLinkSamples;
	}
	
	public void outputMetrics(Recommender recommender, Map<Long, Set<Long>> testData)
	{
		double meanPrecision100 = 0;
		double meanRecall100 = 0;
		double meanF100 = 0;
		
		double precisionStandardDev100 = 0;
		double recallStandardDev100 = 0;
		double fStandardDev100 = 0;
				
		Map<Long, Double[]> precisionRecalls100 = recommender.getPrecisionRecall(testData, 100);
		
		for (long userId : precisionRecalls100.keySet()) {
			double precision100 = precisionRecalls100.get(userId)[0];
			double recall100 =  precisionRecalls100.get(userId)[1];
			double f100 = (precision100 + recall100 > 0) ? 2 * (precision100 * recall100) / (precision100 + recall100) : 0;
	
			meanPrecision100 += precision100;
			meanRecall100 += recall100;
				
			meanF100 += f100;
		}

		meanPrecision100 /= (double)precisionRecalls100.size();
		meanRecall100 /= (double)precisionRecalls100.size();
		meanF100 /= (double)precisionRecalls100.size();
		
		
		for (long userId : precisionRecalls100.keySet()) {
			Object[] precisionRecall100 = precisionRecalls100.get(userId);
				
			double precision100 = (Double)precisionRecall100[0];
			double recall100 = (Double)precisionRecall100[1];
				
			System.out.println("User recall: " + recall100);
			
			double f100 = (precision100 + recall100 > 0) ? 2 * (precision100 * recall100) / (precision100 + recall100) : 0;
			precisionStandardDev100 += Math.pow(precision100 - meanPrecision100, 2);
			recallStandardDev100 += Math.pow(recall100 - meanRecall100, 2);
				
			fStandardDev100 += Math.pow(f100 - meanF100, 2);
		}
		
		precisionStandardDev100 /= (double)precisionRecalls100.size();
		precisionStandardDev100 = Math.sqrt(precisionStandardDev100);
		recallStandardDev100 /= (double)precisionRecalls100.size();
		recallStandardDev100 = Math.sqrt(recallStandardDev100);
		fStandardDev100 /= (double)precisionRecalls100.size();
		fStandardDev100 = Math.sqrt(fStandardDev100);
		
		double precisionSE100 = precisionStandardDev100 / Math.sqrt(precisionRecalls100.size());
		double recallSE100 = recallStandardDev100 / Math.sqrt(precisionRecalls100.size());
		double fSE100 = fStandardDev100 / Math.sqrt(precisionRecalls100.size());
		
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
}
