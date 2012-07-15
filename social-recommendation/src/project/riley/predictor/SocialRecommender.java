package project.riley.predictor;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.recommender.BaselineGlobalRecommender;
import org.nicta.lr.recommender.BaselineRecommender;
import org.nicta.lr.recommender.CopreferenceRecommender;
import org.nicta.lr.recommender.FeatureRecommender;
import org.nicta.lr.recommender.HybridRecommender;
import org.nicta.lr.recommender.NNRecommender;
import org.nicta.lr.recommender.Recommender;
import org.nicta.lr.recommender.SVMRecommender;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.UserUtil;

import project.riley.predictor.ArffData.DataEntry;


public class SocialRecommender extends Predictor {

	public static String type;
	Map<Long, Map<Long, Double>> predictions;
	Map<Long, Set<Long>> testLikes;
	double threshold;

	public SocialRecommender(String t){
		type =  t;
	}

	@Override
	public void train() {
		Set<Long> linkIds = new HashSet<Long>();
		Set<Long> userIds = new HashSet<Long>();
		Map<Long, Set<Long>> linkLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();

		//Read train data
		//ArffData trainArff = new ArffData(trainFile);
		for (DataEntry de : _trainData._data) {
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

		testLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> testData = new HashMap<Long, Set<Long>>();

		//Read test data
		//		ArffData testArff = new ArffData(testFile);
		for (DataEntry de : _testData._data) {
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
		Map<Long, Double[]> users = null;
		Map<Long, Double[]> links = null;
		try {
			users = UserUtil.getUserFeatures(userIds);
			links = LinkUtil.getLinkFeatures(linkIds);
		} catch (SQLException e) {
			e.printStackTrace();
		}

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
		Map<Long, Map<Long, Double>> friendships = null;
		try {
			friendships = UserUtil.getFriendships();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		Recommender recommender = getRecommender(type, linkLikes, users, links, friendships);
		((org.nicta.lr.recommender.SocialRecommender)recommender).setLambda(0.1);
		((org.nicta.lr.recommender.SocialRecommender)recommender).setBeta(1);
		recommender.train(trainData);

		predictions = recommender.getPredictions(testData);
		Map<Long, Map<Long, Double>> trainPredictions = recommender.getPredictions(trainData);
		threshold = getOptimalThreshold(trainPredictions, linkLikes);
		//return getArffMetrics(predictions, testLikes, threshold);
	}

	@Override
	public int evaluate(DataEntry de) {
		return 0;
	}

	@Override
	public void clear() {

	}

	@Override
	public String getName() {
		return "Social Recommender(" + type + ")";
	}

	@Override
	public void runTests(String source_file, int num_folds) throws IOException {

		System.out.println("Running " + getName() + " using " + source_file);	
		
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
			_trainData = new ArffData(trainName);
			_testData  = new ArffData(testName);
			
			//String trainFile = "/Users/jnoel/Desktop/passive/passive.arff.train." + x;
			//String testFile = "/Users/jnoel/Desktop/passive/passive.arff.test." + x;

			//String type = Constants.FEATURE;
			//String type = Constants.SOCIAL;

			//Double[] results = new LinkRecommenderArff().run(_trainData, _testData, type);
			train();
			Double[] results = getArffMetrics(predictions, testLikes, threshold);

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

//		System.out.println("FINAL RESULTS:");
		System.out.println("Accuracy: " + meanAccuracy + "  +/-  " + seAccuracy);
		System.out.println("Precision: " + meanPrecision + "  +/-  " + sePrecision);
		System.out.println("Recall: " + meanRecall + "  +/-  " + seRecall);
		System.out.println("F1: " + meanF1 + "  +/-  " + seF1);
		
	}

	/*public Double[] run(String trainFile, String testFile) throws Exception {

		Set<Long> linkIds = new HashSet<Long>();
		Set<Long> userIds = new HashSet<Long>();
		Map<Long, Set<Long>> linkLikes= new HashMap<Long, Set<Long>>();
		Map<Long, Set<Long>> trainData = new HashMap<Long, Set<Long>>();

		//Read train data
		ArffData trainArff = new ArffData(trainFile);
		for (DataEntry de : _trainData._data) {
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
	}*/

	
	/*public static void main(String[] args)
			throws Exception
			{
		Double[] accuracies = new Double[10];
		Double[] precisions = new Double[10];
		Double[] recalls = new Double[10];
		Double[] f1s = new Double[10];

		double meanAccuracy = 0;
		double meanPrecision = 0;
		double meanRecall = 0;
		double meanF1 = 0;

		for (int x = 1; x <= 10; x++) {
			String trainFile = "/Users/jnoel/Desktop/passive/passive.arff.train." + x;
			String testFile = "/Users/jnoel/Desktop/passive/passive.arff.test." + x;

			//String type = Constants.FEATURE;
			String type = Constants.SOCIAL;

			Double[] results = new LinkRecommenderArff().run(trainFile, testFile, type);

			accuracies[x-1] = results[0];
			precisions[x-1] = results[1];
			recalls[x-1] = results[2];
			f1s[x-1] = results[3];

			meanAccuracy += results[0];
			meanPrecision += results[1];
			meanRecall += results[2];
			meanF1 += results[3];
		}

		meanAccuracy /= 10;
		meanPrecision /= 10;
		meanRecall /= 10;
		meanF1 /= 10;

		double stdAccuracy = 0;
		double stdPrecision = 0;
		double stdRecall = 0;
		double stdF1 = 0;

		for (int x = 0; x < 10; x++) {
			stdAccuracy += Math.pow(meanAccuracy - accuracies[x], 2);
			stdPrecision += Math.pow(meanPrecision - precisions[x], 2);
			stdRecall += Math.pow(meanRecall - recalls[x], 2);
			stdF1 += Math.pow(meanF1 - f1s[x], 2);
		}

		stdAccuracy = Math.sqrt(stdAccuracy / 10);
		stdPrecision = Math.sqrt(stdPrecision / 10);
		stdRecall = Math.sqrt(stdRecall / 10);
		stdF1 = Math.sqrt(stdF1 / 10);

		double seAccuracy = stdAccuracy / Math.sqrt(10);
		double sePrecision = stdPrecision / Math.sqrt(10);
		double seRecall = stdRecall / Math.sqrt(10);
		double seF1 = stdF1 / Math.sqrt(10);

		System.out.println("FINAL RESULTS:");
		System.out.println("Accuracy: " + meanAccuracy + "(" + seAccuracy + ")");
		System.out.println("Precision: " + meanPrecision + "(" + sePrecision + ")");
		System.out.println("Recall: " + meanRecall + "(" + seRecall + ")");
		System.out.println("F1: " + meanF1 + "(" + seF1 + ")");
			}*/

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

	private Recommender getRecommender(String type2, Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> users, Map<Long, Double[]> links, Map<Long, Map<Long, Double>> friendships) {
		// TODO Auto-generated method stub
		Recommender recommender = null;

		if (Constants.SOCIAL.equals(type)) {
			recommender = new org.nicta.lr.recommender.SocialRecommender(linkLikes, users, links, friendships, type);
			//System.out.println("Setting social beta: " + value);
			//((SocialRecommender)recommender).setBeta(value);
		}
		else if (Constants.SPECTRAL.equals(type)) {
			recommender = new org.nicta.lr.recommender.SocialRecommender(linkLikes, users, links, friendships, type);
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



}
