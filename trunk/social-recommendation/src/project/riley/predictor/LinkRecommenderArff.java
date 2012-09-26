package project.riley.predictor;

import project.riley.predictor.ArffData;
import project.riley.predictor.ArffData.DataEntry;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import org.nicta.lr.recommender.Recommender;
import org.nicta.lr.recommender.FeatureRecommender;
import org.nicta.lr.recommender.SocialRecommender;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.UserUtil;

public class LinkRecommenderArff extends org.nicta.lr.LinkRecommender
{
	static DecimalFormat df3 = new DecimalFormat("#.###");
	//Use as much of the old code as possible.
	//But make a new class and method as I don't trust myself yet to edit the old code and not break anything
	public Double[] run(ArffData trainArff, ArffData testArff, String type)
			throws Exception
			{
		this.type = type;

		Set<Long> linkIds = new HashSet<Long>();
		Set<Long> userIds = new HashSet<Long>();
		Map<Long, Set<Long>> linkLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();

		//Read train data
		//ArffData trainArff = new ArffData(trainFile);
		for (DataEntry de : trainArff._data) {
			//System.out.println(de.toString());
			long userId = ((Double)de.getData(0)).longValue();
			long linkId = ((Double)de.getData(1)).longValue();
			boolean like = (Integer)de.getData(2) == 1;

			//System.out.println(userId + " " + linkId + " " + like);

			linkIds.add(linkId);
			userIds.add(userId);

			if (!trainData.containsKey(userId)) {
				trainData.put(userId, new HashSet<Long>());
			}
			trainData.get(userId).add(linkId);

			if (like) {
				if (!linkLikes.containsKey(linkId)) {
					linkLikes.put(linkId, new HashSet<Long>());
				}					
				linkLikes.get(linkId).add(userId);
			}
		}

		Map<Long, Set<Long>> testLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> testData = new HashMap<Long, Set<Long>>();

		//Read test data
		//ArffData testArff = new ArffData(testFile);
		for (DataEntry de : testArff._data) {
			//System.out.println(de.toString());
			long userId = ((Double)de.getData(0)).longValue();
			long linkId = ((Double)de.getData(1)).longValue();
			boolean like = (Integer)de.getData(2) == 1;

			//System.out.println(userId + " " + linkId + " " + like);

			linkIds.add(linkId);
			userIds.add(userId);

			if (!testData.containsKey(userId)) {
				testData.put(userId, new HashSet<Long>());
			}
			testData.get(userId).add(linkId);

			if (like) {
				if (!testLikes.containsKey(linkId)) {
					testLikes.put(linkId, new HashSet<Long>());
				}					
				testLikes.get(linkId).add(userId);
			}
		}

		//Read features
		Map<Long, Double[]> users = UserUtil.getUserFeatures(userIds);
		Map<Long, Double[]> links = LinkUtil.getLinkFeatures(linkIds);

		//Remove ids that aren't actually links after all
		int num_invalid_link_ids = 0;
		for (Long userId : trainData.keySet()) {
			Set<Long> remove = new HashSet<Long>();

			for (Long linkId : trainData.get(userId)) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
					num_invalid_link_ids++;
				}
			}

			trainData.get(userId).removeAll(remove);
		}
		for (Long userId : testData.keySet()) {
			Set<Long> remove = new HashSet<Long>();

			for (Long linkId : testData.get(userId)) {
				if (!links.containsKey(linkId)) {
					remove.add(linkId);
					num_invalid_link_ids++;
				}
			}

			testData.get(userId).removeAll(remove);
		}
		//System.out.println("Discarded " + num_invalid_link_ids + " invalid link IDs.");

		//Code below is basically doing the same as LinkRecommender
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();

		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		((org.nicta.lr.recommender.MFRecommender)recommender).setLambda(0.1);
		if (recommender instanceof org.nicta.lr.recommender.SocialRecommender)
			((org.nicta.lr.recommender.SocialRecommender)recommender).setBeta(1);
		recommender.train(trainData);

		Map<Long, Map<Long, Double>> predictions = recommender.getPredictions(testData);
		Map<Long, Map<Long, Double>> trainPredictions = recommender.getPredictions(trainData);
		double threshold = getOptimalThreshold(trainPredictions, linkLikes);
		return getArffMetrics(predictions, testLikes, threshold);
			}

	public static void runTests(String source_file, int num_folds, PrintWriter writer, int threshold, int friendK) throws Exception {
		int normal = num_folds;
		
		Double[] accuracies = new Double[num_folds];
		Double[] precisions = new Double[num_folds];
		Double[] recalls = new Double[num_folds];
		Double[] f1s = new Double[num_folds];

		double meanAccuracy = 0;
		double meanPrecision = 0;
		double meanRecall = 0;
		double meanF1 = 0;

		for (int i = 0; i < num_folds; i++) {
			String trainName = source_file + ".train." + (i+1);
			String testName  = source_file + ".test."  + (i+1);

			int groupsSize = Launcher.GROUPS_SIZE;
			int pagesSize = Launcher.PAGES_SIZE;
			int outgoingSize = Launcher.OUTGOING_MESSAGES_SIZE;
			int incomingSize = Launcher.INCOMING_MESSAGES_SIZE;
			if (Launcher.SIZES_OVERRIDE){
				groupsSize = Launcher.SMB_GROUPS_SIZE_OVERRIDE;
				pagesSize = Launcher.SMB_PAGES_SIZE_OVERRIDE;
				outgoingSize = Launcher.SMB_OUTGOING_SIZE_OVERRIDE;
				incomingSize = Launcher.SMB_INCOMING_SIZE_OVERRIDE;
			}
			
			ArffData _testData  = new ArffData();
			_testData.setThreshold(threshold);
			_testData.setFriendSize(friendK);
			_testData.setFriends(Launcher.FRIENDS_FEATURE);
			_testData.setInteractions(Launcher.INTERACTIONS_FEATURE);
			_testData.setDemographics(Launcher.DEMOGRAPHICS_FEATURE);
			_testData.setGroups(Launcher.GROUPS_FEATURE, groupsSize);
			_testData.setPages(Launcher.PAGES_FEATURE, pagesSize);
			_testData.setTraits(Launcher.TRAITS_FEATURE);
			_testData.setOutgoingMessages(Launcher.OUTGOING_MESSAGES_FEATURE, outgoingSize);
			_testData.setIncomingMessages(Launcher.INCOMING_MESSAGES_FEATURE, incomingSize);
			_testData.setFileName(testName);
			
			ArffData _trainData  = new ArffData();
			_trainData.setThreshold(0);
			_trainData.setFriendSize(0);
			_trainData.setFriends(Launcher.FRIENDS_FEATURE);
			_trainData.setInteractions(Launcher.INTERACTIONS_FEATURE);
			_trainData.setDemographics(Launcher.DEMOGRAPHICS_FEATURE);
			_trainData.setGroups(Launcher.GROUPS_FEATURE, Launcher.GROUPS_SIZE);
			_trainData.setPages(Launcher.PAGES_FEATURE, Launcher.PAGES_SIZE);
			_trainData.setTraits(Launcher.TRAITS_FEATURE);
			_trainData.setOutgoingMessages(Launcher.OUTGOING_MESSAGES_FEATURE, Launcher.OUTGOING_MESSAGES_SIZE);
			_trainData.setIncomingMessages(Launcher.INCOMING_MESSAGES_FEATURE, Launcher.INCOMING_MESSAGES_SIZE);
			_trainData.setFileName(trainName);
			
			if (_testData._data.size() == 0 || _trainData._data.size() == 0){
				//System.out.println(threshold);
				//System.out.println(trainName + ":" + _trainData._data.size());
				//System.out.println(testName + ":" + _testData._data.size());
				normal--;
				continue;
			}						

			//String type = Constants.FEATURE;
			//String type = Constants.SOCIAL;
			
			Double[] results = new LinkRecommenderArff().run(_trainData, _testData, type);

			accuracies[i] = results[0];
			precisions[i] = results[1];
			recalls[i] = results[2];
			f1s[i] = results[3];

			meanAccuracy += results[0];
			meanPrecision += results[1];
			meanRecall += results[2];
			meanF1 += results[3];
		}

		meanAccuracy /= normal;
		meanPrecision /= normal;
		meanRecall /= normal;
		meanF1 /= normal;

		double stdAccuracy = 0;
		double stdPrecision = 0;
		double stdRecall = 0;
		double stdF1 = 0;

		for (int x = 0; x < normal; x++) {
			System.out.println(meanAccuracy + " " + normal + " " + x + " " + accuracies[x] + " " + friendK);
			stdAccuracy += Math.pow(meanAccuracy - accuracies[x], 2);
			stdPrecision += Math.pow(meanPrecision - precisions[x], 2);
			stdRecall += Math.pow(meanRecall - recalls[x], 2);
			stdF1 += Math.pow(meanF1 - f1s[x], 2);
		}

		stdAccuracy = Math.sqrt(stdAccuracy / normal);
		stdPrecision = Math.sqrt(stdPrecision / normal);
		stdRecall = Math.sqrt(stdRecall / normal);
		stdF1 = Math.sqrt(stdF1 / normal);

		double seAccuracy = stdAccuracy / Math.sqrt(normal);
		double sePrecision = stdPrecision / Math.sqrt(normal);
		double seRecall = stdRecall / Math.sqrt(normal);
		double seF1 = stdF1 / Math.sqrt(normal);

		System.out.println("Accuracy:  " + df3.format(meanAccuracy) + " +/- " + df3.format(seAccuracy));
		writer.println("Accuracy:  " + df3.format(meanAccuracy) + " +/- " + df3.format(seAccuracy));
		System.out.println("Precision: " + df3.format(meanPrecision) + " +/- " + df3.format(sePrecision));
		writer.println("Precision: " + df3.format(meanPrecision) + " +/- " + df3.format(sePrecision));
		System.out.println("Recall:    " + df3.format(meanRecall) + " +/- " + df3.format(seRecall));
		writer.println("Recall:    " + df3.format(meanRecall) + " +/- " + df3.format(seRecall));
		System.out.println("F-Score:   " + df3.format(meanF1) + " +/- " + df3.format(seF1));
		writer.println("F-Score:   " + df3.format(meanF1) + " +/- " + df3.format(seF1));
		System.out.println();
		writer.println();
	}
	
	public static void setType(String t){
		type = t;
	}

	public static void main(String[] args)throws Exception{
		String source_file = "passive.arff";
		int num_folds = 10;
		setType(Constants.SOCIAL);
		//runTests(source_file,num_folds);
	}

	public Double[] getArffMetrics(Map<Long, Map<Long, Double>> predictions, Map<Long, Set<Long>> linkLikes, double threshold)
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

		//System.out.println("TP: " + truePos + " FP: " + falsePos + " TN: " + trueNeg + " FN: " + falseNeg);
		//System.out.println("Accuracy: " + accuracy);
		//System.out.println("Confidence Interval: " + confidence);
		//System.out.println("Precision: " + precision);
		//System.out.println("Recall: " + recall);
		//System.out.println("F1: " + f1);

		return new Double[]{accuracy, precision, recall, f1};
	}
}
