package project.riley.predictor;

import project.ifilter.predictor.ArffData;
import project.ifilter.predictor.ArffData.DataEntry;

import java.io.IOException;
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
	//Use as much of the old code as possible.
	//But make a new class and method as I don't trust myself yet to edit the old code and not break anything
	public Double[] run(String trainFile, String testFile, String type)
			throws Exception
			{
		this.type = type;

		Set<Long> linkIds = new HashSet<Long>();
		Set<Long> userIds = new HashSet<Long>();
		Map<Long, Set<Long>> linkLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();

		//Read train data
		ArffData trainArff = new ArffData(trainFile);
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
		ArffData testArff = new ArffData(testFile);
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
		System.out.println("Discarded " + num_invalid_link_ids + " invalid link IDs.");

		//Code below is basically doing the same as LinkRecommender
		Map<Long, Map<Long, Double>> friendships = UserUtil.getFriendships();

		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		((org.nicta.lr.recommender.SocialRecommender)recommender).setLambda(0.1);
		((org.nicta.lr.recommender.SocialRecommender)recommender).setBeta(1);
		recommender.train(trainData);

		Map<Long, Map<Long, Double>> predictions = recommender.getPredictions(testData);
		Map<Long, Map<Long, Double>> trainPredictions = recommender.getPredictions(trainData);
		double threshold = getOptimalThreshold(trainPredictions, linkLikes);
		return getArffMetrics(predictions, testLikes, threshold);
			}

	public static void runTests(String source_file, int num_folds) throws Exception {
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

			//String type = Constants.FEATURE;
			//String type = Constants.SOCIAL;

			Double[] results = new LinkRecommenderArff().run(trainName, testName, type);

			accuracies[i] = results[0];
			precisions[i] = results[1];
			recalls[i] = results[2];
			f1s[i] = results[3];

			meanAccuracy += results[0];
			meanPrecision += results[1];
			meanRecall += results[2];
			meanF1 += results[3];
		}

		meanAccuracy /= num_folds;
		meanPrecision /= num_folds;
		meanRecall /= num_folds;
		meanF1 /= num_folds;

		double stdAccuracy = 0;
		double stdPrecision = 0;
		double stdRecall = 0;
		double stdF1 = 0;

		for (int x = 0; x < num_folds; x++) {
			stdAccuracy += Math.pow(meanAccuracy - accuracies[x], 2);
			stdPrecision += Math.pow(meanPrecision - precisions[x], 2);
			stdRecall += Math.pow(meanRecall - recalls[x], 2);
			stdF1 += Math.pow(meanF1 - f1s[x], 2);
		}

		stdAccuracy = Math.sqrt(stdAccuracy / num_folds);
		stdPrecision = Math.sqrt(stdPrecision / num_folds);
		stdRecall = Math.sqrt(stdRecall / num_folds);
		stdF1 = Math.sqrt(stdF1 / num_folds);

		double seAccuracy = stdAccuracy / Math.sqrt(num_folds);
		double sePrecision = stdPrecision / Math.sqrt(num_folds);
		double seRecall = stdRecall / Math.sqrt(num_folds);
		double seF1 = stdF1 / Math.sqrt(num_folds);

		System.out.println("FINAL RESULTS:");
		System.out.println("Accuracy: " + meanAccuracy + "(" + seAccuracy + ")");
		System.out.println("Precision: " + meanPrecision + "(" + sePrecision + ")");
		System.out.println("Recall: " + meanRecall + "(" + seRecall + ")");
		System.out.println("F1: " + meanF1 + "(" + seF1 + ")");
	}
	
	public static void setType(String t){
		type = t;
	}

	public static void main(String[] args)throws Exception{
		String source_file = "passive.arff";
		int num_folds = 10;
		setType(Constants.SOCIAL);
		runTests(source_file,num_folds);
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

		System.out.println("TP: " + truePos + " FP: " + falsePos + " TN: " + trueNeg + " FN: " + falseNeg);
		System.out.println("Accuracy: " + accuracy);
		System.out.println("Confidence Interval: " + confidence);
		System.out.println("Precision: " + precision);
		System.out.println("Recall: " + recall);
		System.out.println("F1: " + f1);

		return new Double[]{accuracy, precision, recall, f1};
	}
}
