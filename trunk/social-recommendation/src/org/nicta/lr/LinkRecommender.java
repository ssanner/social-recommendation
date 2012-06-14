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
import java.util.Random;

import org.nicta.lr.recommender.HybridRecommender;
import org.nicta.lr.recommender.FeatureRecommender;
import org.nicta.lr.recommender.NNRecommender;
import org.nicta.lr.recommender.Recommender;
import org.nicta.lr.recommender.SVMRecommender;
import org.nicta.lr.recommender.SocialRecommender;
import org.nicta.lr.recommender.CopreferenceRecommender;
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
	public static String type;
	
	Map<Long, Set<Long>> friendLinksToRecommend;
	Map<Long, Set<Long>> nonFriendLinksToRecommend;
	
	double value = 0;
	
	Random random = new Random();

	public static void main(String[] args)
		throws Exception
	{	
		LinkRecommender lr = new LinkRecommender();
		lr.parseArgs(args);
		lr.run(0);
	}
	
	public void parseArgs(String[] args)
	{
		for (int x = 0; x < args.length; x += 2) {
			if (args[x].equals("type")) {
				type = args[x+1];
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
	
	public void run(double val)
		throws SQLException, IOException
	{	
		random.setSeed(1);
		
		System.out.println("Starting run: " + val);
		value = val;
		
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
		
		//usersNeeded.addAll(friendLinksToRecommend.keySet());
		usersNeeded.addAll(nonFriendLinksToRecommend.keySet());
		
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
			
			if (message.trim().length() == 0 && description.trim().length() == 0) {
				removeNonEnglish.add(id);
				continue;
			}
			
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
				removeNonEnglish.add(id);
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
		HashSet<Long> removeUsers = new HashSet<Long>();
		for (long userId : trainData.keySet()) {
			if (!users.containsKey(userId)) {
				removeUsers.add(userId);
				continue;
			}
			
			Set<Long> samples = trainData.get(userId);
			HashSet<Long> remove = new HashSet<Long>();
			
			for (long linkId : samples) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
				}
			}
			
			samples.removeAll(remove);
		}	
		for (long userId : removeUsers) {
			trainData.remove(userId);
		}
		removeUsers = new HashSet<Long>();
		for (long userId : testData.keySet()) {
			if (!users.containsKey(userId)) {
				removeUsers.add(userId);
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
		for (long userId : removeUsers) {
			testData.remove(userId);
		}	
		
		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		recommender.train(trainData);
		
		Map<Long, Map<Long, Double>> predictions = recommender.getPredictions(testData);
		Map<Long, Map<Long, Double>> trainPredictions = recommender.getPredictions(trainData);
		
		double threshold = getOptimalThreshold(trainPredictions, linkLikes);
		
		System.out.println("Type: " + type);
		System.out.println("Threshold: " + threshold);
		System.out.println("Train");
		getScore2(trainPredictions, linkLikes, threshold);
		
		System.out.println("Test");
		double score = getScore2(predictions, linkLikes, threshold);
		System.out.println("Right minus wrong: " + score);
		
		System.out.println("Metrics:");
		getTotalSimpleMetrics(predictions, linkLikes, threshold);
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
			Set<Long> appUsers = UserUtil.getCurrentAppUserIds();
			
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
		
		if (Configuration.TRAINING_DATA.equals(Constants.PASSIVE) || Configuration.TRAINING_DATA.equals(Constants.UNION)) {
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
		
		System.out.println("Link ids: " + linkIds.size());
		
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
			
			//Link has already been either liked or disliked by the user. Don't change anymore
			if (trainData.containsKey(userId) && trainData.get(userId).contains(linkId)) continue;
			
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
				
				int randomIndex = (int)(random.nextDouble() * links.length);
				Long linkId = (Long)links[randomIndex];
				
				if (active != null && active.contains(linkId)) continue;
				
				if (userLikes.contains(linkId) && !userTest.contains(linkId)) {
					userTest.add(linkId);
					addedLike++;
				}
			}
			
			while (addedNotLike < testNotLikeCount) {
				int randomIndex = (int)(random.nextDouble() * links.length);
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
			
			//System.out.println("User links train: " + userLinks.size() + " User active: " + active.size());
			
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
				int randomIndex = (int)(random.nextDouble() * links.length);
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
				int randomIndex = (int)(random.nextDouble() * links.length);
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
													+ "AND is_app_user=1 AND algorithm2='" + type + "'");
		
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
													+ "AND is_app_user=1 AND algorithm2='" + type + "'");
		
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
			recommender = new SocialRecommender(linkLikes, users, links, friendships, type);
			//System.out.println("Setting social beta: " + value);
			//((SocialRecommender)recommender).setBeta(value);
		}
		else if (Constants.SPECTRAL.equals(type)) {
			recommender = new SocialRecommender(linkLikes, users, links, friendships, type);
			//System.out.println("Setting spectral beta: " + value);
			//((SocialRecommender)recommender).setBeta(value);
		}
		else if (Constants.HYBRID.equals(type)) {
			recommender = new HybridRecommender(linkLikes, users, links, friendships, type);
			//System.out.println("Setting lambda: " + value);
			//((HybridRecommender)recommender).setLambda(value);
		}
		else if (Constants.HYBRID_SOCIAL.equals(type)) {
			recommender = new HybridRecommender(linkLikes, users, links, friendships, type);
			//System.out.println("Setting beta: " + value);
			//((SocialRecommender)recommender).setBeta(value);
		}
		else if (Constants.HYBRID_SPECTRAL.equals(type)) {
			recommender = new HybridRecommender(linkLikes, users, links, friendships, type);
		}
		else if (Constants.FEATURE.equals(type)) {
			recommender = new FeatureRecommender(linkLikes, users, links);
			//((FeatureRecommender)recommender).setLambda(value);
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
			//recommender = new LogisticSocialRecommender(linkLikes, users, links, friendships);
		}
		else if (Constants.SOCIAL_COPREFERENCE.equals(type)) {
			recommender = new CopreferenceRecommender(linkLikes, users, links, friendships, type);
			//System.out.println("Setting copreference  social beta: " + value);
			//((CopreferenceRecommender)recommender).setBeta(value);
		}
		else if (Constants.SPECTRAL_COPREFERENCE.equals(type)) {
			recommender = new CopreferenceRecommender(linkLikes, users, links, friendships, type);
			//System.out.println("Setting copreference spectral beta: " + value);
			//((CopreferenceRecommender)recommender).setBeta(value);
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
			if (samples.size() < 2) {
				remove.add(userId);
				continue;
			}
			
			System.out.println("User: " + ++count + " " + samples.size());
			
			Set<Long> friends = friendships.get(userId).keySet();
			
			int likeCount = samples.size();
			
			//Sample links that weren't liked.
			//Links here should be links that were shared by friends to increase the chance that the user has actually seen this and not
			//liked them
			for (long linkId : links.keySet()) {
				//if (samples.size() >= likeCount * 10) break;
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
	
	public void outputMap(Map<Long, Double> averagePrecisions)
	{	
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

	
	public double getOptimalThreshold(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes)
	{
		double maxThreshold = 10;
		double optimalThreshold = 0;
		double optimalF1 = -Double.MAX_VALUE;
		
		double threshold = -10;
		
		while (threshold < maxThreshold) {
			double f1 = getTotalF1(predictions, linkLikes, threshold);
			
			if (f1 > optimalF1) {
				optimalF1 = f1;
				optimalThreshold = threshold;
			}
			
			threshold += 0.1;
		}
		
		return optimalThreshold;
	}
	
	public double getF1(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes, double threshold)
	{
		Map<Long, Double[]> metrics = new HashMap<Long, Double[]>();
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			double truePos = 0;
			double falsePos = 0;
			double trueNeg = 0;
			double falseNeg = 0;
			
			for (long linkId : userPredictions.keySet()) {
				Set<Long> likes = linkLikes.get(linkId);
				boolean trueLikes = false;
				if (likes != null && likes.contains(userId)) trueLikes = true;
				
				double val = userPredictions.get(linkId);
				boolean predicted = false;
				if (val >= threshold) predicted = true;
				
				if (predicted) {
					if (trueLikes) {
						truePos++;
					}
					else {
						falsePos++;
					}
				}
				else {
					if (trueLikes) {
						falseNeg++;
					}
					else {
						trueNeg++;
					}
				}
			}
			double precision = truePos / (truePos + falsePos);
			if ((truePos + falsePos) == 0) precision = 0;
			
			double recall = truePos / (truePos + falseNeg);
			if ((truePos + falseNeg) == 0) recall = 0;
			
			double f1 = 2 * (precision * recall) / (precision + recall);
			if ((precision + recall) == 0) f1 = 0;
			
			metrics.put(userId, new Double[]{precision, recall, f1});
		}
	
		double avF1 = 0;
		
		for (long userId : metrics.keySet()) {
			Double[] values = metrics.get(userId);
			avF1 += values[2];
		}
		
		avF1 /= (double)metrics.size();
		return avF1;
	}
	
	public double getTotalF1(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes, double threshold)
	{
		double truePos = 0;
		double falsePos = 0;
		double trueNeg = 0;
		double falseNeg = 0;
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			for (long linkId : userPredictions.keySet()) {
				Set<Long> likes = linkLikes.get(linkId);
				boolean trueLikes = false;
				if (likes != null && likes.contains(userId)) trueLikes = true;
				
				double val = userPredictions.get(linkId);
				boolean predicted = false;
				if (val >= threshold) predicted = true;
				
				if (predicted) {
					if (trueLikes) {
						truePos++;
					}
					else {
						falsePos++;
					}
				}
				else {
					if (trueLikes) {
						falseNeg++;
					}
					else {
						trueNeg++;
					}
				}
			}
		}
		
		double precision = truePos / (truePos + falsePos);
		if ((truePos + falsePos) == 0) precision = 0;
		
		double recall = truePos / (truePos + falseNeg);
		if ((truePos + falseNeg) == 0) recall = 0;
		
		double f1 = 2 * (precision * recall) / (precision + recall);
		if ((precision + recall) == 0) f1 = 0;
		
		return f1;
	}
	
	public void getTotalSimpleMetrics(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes, double threshold)
	{
		double truePos = 0;
		double falsePos = 0;
		double trueNeg = 0;
		double falseNeg = 0;
		
		int totalCount = 0;
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			for (long linkId : userPredictions.keySet()) {
				totalCount++;
				
				Set<Long> likes = linkLikes.get(linkId);
				boolean trueLikes = false;
				if (likes != null && likes.contains(userId)) trueLikes = true;
				
				double val = userPredictions.get(linkId);
				boolean predicted = false;
				if (val >= threshold) predicted = true;
				
				if (predicted) {
					if (trueLikes) {
						truePos++;
					}
					else {
						falsePos++;
					}
				}
				else {
					if (trueLikes) {
						falseNeg++;
					}
					else {
						trueNeg++;
					}
				}
			}
		}
		
		double accuracy = (truePos + trueNeg) / (truePos + trueNeg + falsePos + falseNeg);
		if ((truePos + trueNeg + falsePos + falseNeg) == 0) accuracy = 0;
		
		double precision = truePos / (truePos + falsePos);
		if ((truePos + falsePos) == 0) precision = 0;
		
		double recall = truePos / (truePos + falseNeg);
		if ((truePos + falseNeg) == 0) recall = 0;
		
		double f1 = 2 * (precision * recall) / (precision + recall);
		if ((precision + recall) == 0) f1 = 0;
		
		double confidence = 2 * Math.sqrt((accuracy * (1 - accuracy)) / totalCount);
		
		System.out.println("TP: " + truePos + " FP: " + falsePos + " TN: " + trueNeg + " FN: " + falseNeg);
		System.out.println("Accuracy: " + accuracy);
		System.out.println("Confidence Interval: " + confidence);
		System.out.println("Precision: " + precision);
		System.out.println("Recall: " + recall);
		System.out.println("F1: " + f1);
	}
	
	public void getSimpleMetrics(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes, double threshold)
	{
		Map<Long, Double[]> metrics = new HashMap<Long, Double[]>();
		
		double totalTruePos = 0;
		double totalFalsePos = 0;
		double totalTrueNeg = 0;
		double totalFalseNeg = 0;
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			double truePos = 0;
			double falsePos = 0;
			double trueNeg = 0;
			double falseNeg = 0;
			
			for (long linkId : userPredictions.keySet()) {
				Set<Long> likes = linkLikes.get(linkId);
				boolean trueLikes = false;
				if (likes != null && likes.contains(userId)) trueLikes = true;
				
				double val = userPredictions.get(linkId);
				boolean predicted = false;
				if (val >= threshold) predicted = true;
				
				if (predicted) {
					if (trueLikes) {
						truePos++;
					}
					else {
						falsePos++;
					}
				}
				else {
					if (trueLikes) {
						falseNeg++;
					}
					else {
						trueNeg++;
					}
				}
			}
			
			totalTruePos += truePos;
			totalFalsePos += falsePos;
			totalTrueNeg += trueNeg;
			totalFalseNeg += falseNeg;
			
			double accuracy = (truePos + trueNeg) / (truePos + trueNeg + falsePos + falseNeg);
			if ((truePos + trueNeg + falsePos + falseNeg) == 0) accuracy = 0;
			
			double precision = truePos / (truePos + falsePos);
			if ((truePos + falsePos) == 0) precision = 0;
			
			double recall = truePos / (truePos + falseNeg);
			if ((truePos + falseNeg) == 0) recall = 0;
			
			double f1 = 2 * (precision * recall) / (precision + recall);
			if ((precision + recall) == 0) f1 = 0;
			
			metrics.put(userId, new Double[]{accuracy, precision, recall, f1});
		}
		
		double avAccuracy = 0;
		double avPrecision = 0;
		double avRecall = 0;
		double avF1 = 0;
		
		for (long userId : metrics.keySet()) {
			Double[] values = metrics.get(userId);
			
			avAccuracy += values[0];
			avPrecision += values[1];
			avRecall += values[2];
			avF1 += values[3];
		}
		
		avAccuracy /= (double)metrics.size();
		avPrecision /= (double)metrics.size();
		avRecall /= (double)metrics.size();
		avF1 /= (double)metrics.size();
		
		double stdAccuracy = 0;
		double stdPrecision = 0;
		double stdRecall = 0;
		double stdF1 = 0;
		
		for (long userId : metrics.keySet()) {
			Double[] values = metrics.get(userId);
			
			stdAccuracy += Math.pow(avAccuracy - values[0], 2);
			stdPrecision += Math.pow(avPrecision - values[1], 2);
			stdRecall += Math.pow(avRecall - values[2], 2);
			stdF1 += Math.pow(avF1 - values[3], 2);
		}
		
		stdAccuracy = Math.sqrt(stdAccuracy / (double)metrics.size());
		stdPrecision = Math.sqrt(stdPrecision / (double)metrics.size());
		stdRecall = Math.sqrt(stdRecall / (double)metrics.size());
		stdF1 = Math.sqrt(stdF1 / (double)metrics.size());
		
		double seAccuracy = stdAccuracy / Math.sqrt(metrics.size());
		double sePrecision = stdPrecision / Math.sqrt(metrics.size());
		double seRecall = stdPrecision / Math.sqrt(metrics.size());
		double seF1 = stdPrecision / Math.sqrt(metrics.size());
		
		System.out.println("TP: " + totalTruePos + " FP: " + totalFalsePos + " TN: " + totalTrueNeg + " FN: " + totalFalseNeg);
		System.out.println("Accuracy: " + avAccuracy + " " + seAccuracy);
		System.out.println("Precision: " + avPrecision + " " + sePrecision);
		System.out.println("Recall: " + avRecall + " " + seRecall);
		System.out.println("F1: " + avF1 + " " + seF1);
	}
	
	public int getScore(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes, double threshold)
	{
		int score = 0;
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			for (long linkId : userPredictions.keySet()) {
				Set<Long> likes = linkLikes.get(linkId);
				boolean trueLikes = false;
				if (likes != null && likes.contains(userId)) trueLikes = true;
				
				double val = userPredictions.get(linkId);
				boolean predicted = false;
				if (val >= threshold) predicted = true;
				
				if (trueLikes == predicted) score++;
				else score--;
			}
		}
		
		return score;
	}
	
	public int getScore2(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes, double threshold)
	{
		int score = 0;
		int right = 0;
		int wrong = 0;
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			for (long linkId : userPredictions.keySet()) {
				Set<Long> likes = linkLikes.get(linkId);
				boolean trueLikes = false;
				if (likes != null && likes.contains(userId)) trueLikes = true;
				
				double val = userPredictions.get(linkId);
				boolean predicted = false;
				if (val >= threshold) predicted = true;
				
				if (trueLikes == predicted) {
					score++;
					right++;
				}
				else {
					score--;
					wrong++;
				}
			}
		}
		
		System.out.println("Right: " + right);
		System.out.println("Wrong: " + wrong);
		return score;
	}
	
	public void outputAUC(Recommender recommender, Map<Long, Set<Long>> testData)
	{
		int gCount = 20;
		
		Map<Long, Map<Long, Double>> predictions = recommender.getPredictions(testData);
		
		double lowestPrediction = Double.MAX_VALUE;
		double highestPrediction = -Double.MAX_VALUE;
		
		for (long userId : predictions.keySet()) {
			Map<Long, Double> userPredictions = predictions.get(userId);
			
			for (long linkId : userPredictions.keySet()) {
				double val = userPredictions.get(linkId);
				
				if (val < lowestPrediction) lowestPrediction = val;
				if (val > highestPrediction) highestPrediction = val;
			}
		}
		
		double range = highestPrediction - lowestPrediction;
		
		if (range <= 0) {
			System.out.println("NEGATIVE RANGE: " + range);
			System.exit(1);
		}
		
		System.out.println("Lowest: " + lowestPrediction);
		
		double[] thresholds = new double[gCount];
		
		double t = lowestPrediction;
		for (int x = 0; x < gCount; x++) {
			thresholds[x] = t;
			t += range / (double)gCount;
		}
		
		if (thresholds[gCount - 1] < highestPrediction) thresholds[gCount - 1] = highestPrediction;
		
		double g = 1;
		Integer[][] aucMetrics = new Integer[gCount][4];
		
		for (int i = 0; i < gCount; i++) {
			Integer[] metrics = recommender.getAUCMetrics(predictions, thresholds[i]);
			aucMetrics[i] = metrics;
		}
		
		for (int i = 0; i < gCount-1; i++) {
		//for (int i = gCount -2; i >= 0; i--) {
			Integer[] metrics = aucMetrics[i];
			int truePos = metrics[0];
			int falsePos = metrics[1];
			int trueNeg = metrics[2];
			int falseNeg = metrics[3];
			
			double x = (double)falsePos / (double)(falsePos + trueNeg);
			double y = (double)truePos / (double)(truePos + falseNeg); 
			
			double xPrev = 0;
			double yPrev = 0;
			
			Integer[] prevMetrics = aucMetrics[i+1];
			int truePosPrev = prevMetrics[0];
			int falsePosPrev = prevMetrics[1];
			int trueNegPrev = prevMetrics[2];
			int falseNegPrev = prevMetrics[3];
				
			xPrev = (double)falsePosPrev / (double)(falsePosPrev + trueNegPrev);
			yPrev = (double)truePosPrev / (double)(truePosPrev + falseNegPrev); 
			
			g -= (x - xPrev) * (y + yPrev);
			System.out.println(x + " " + y + " " + truePos + " " + falsePos + " " + trueNeg + " " + falseNeg);
		}
		
		Integer[] prevMetrics = aucMetrics[gCount-1];
		int truePos = prevMetrics[0];
		int falsePos = prevMetrics[1];
		int trueNeg = prevMetrics[2];
		int falseNeg = prevMetrics[3];
		
		double x = (double)falsePos / (double)(falsePos + trueNeg);
		double y = (double)truePos / (double)(truePos + falseNeg); 
		System.out.println(x + " " + y + " " + truePos + " " + falsePos + " " + trueNeg + " " + falseNeg);
		
		System.out.println("G: " + g);
		double auc = (1 - g) / 2;
		
		System.out.println("AUC: " + auc);
	}
}
