package org.nicta.lr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.nicta.lr.util.Constants;
import org.nicta.lr.util.LinkUtil;
import org.nicta.lr.util.RecommenderUtil;
import org.nicta.lr.util.UserUtil;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_problem;
import libsvm.svm_parameter;

public class SVMRecommender 
{
	public void crossValidate()
		throws Exception
	{
		HashMap<Long, Double[]> users = UserUtil.getUserFeatures();
		System.out.println("Retrieved users: " + users.size());
		
		HashMap<Long, Double[]> links = LinkUtil.getLinkFeatures(false);
		System.out.println("Retrieved links: " + links.size());
		
		HashMap<Long, HashSet<Long>> linkLikes = LinkUtil.getLinkLikes(links.keySet());
		HashMap<Long, HashMap<Long, Double>> friendships = UserUtil.getFriendships();
		
		HashMap<Long, HashSet<Long>> userLinkSamples = RecommenderUtil.getUserLinksSample(users.keySet(), friendships, false);
		System.out.println("Samples: " + userLinkSamples.size());
		
		//HashMap<Long, HashMap<Long, Double>> friendConnections = UserUtil.getFriendInteractionMeasure();
		HashMap<Long, HashMap<Long, Double>> friendConnections = UserUtil.getFriendLikeSimilarity(userLinkSamples.keySet());
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
		
		double totalTestRMSE = 0;
		double[] rmseArr = new double[10];
		
		double totalTruePos = 0;
		double totalFalsePos = 0;
		double totalTrueNeg = 0;
		double totalFalseNeg = 0;
		
		HashMap<Long, Integer[]> totalUserPrecision = new HashMap<Long, Integer[]>();
		
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
					
					//System.out.println("Size: " + samples.size() + " Length: " + sampleArray.length + " Random: " + randomIndex + " User: " + userId + "userTested: " + userTested.size());
					if (!tested.get(userId).contains(randomLinkId) && ! userTesting.contains(randomLinkId)) {
						userTesting.add(randomLinkId);
						tested.get(userId).add(randomLinkId);
						samples.remove(randomLinkId);
						addedCount++;
					}
				}		
			}
			
			
			double foldRMSE = RecommenderUtil.calcRMSE(userTraits, linkTraits, linkLikes, forTesting);
			rmseArr[x] = foldRMSE;
			
			System.out.println("Test RMSE of Run " + (x+1) + ": " + foldRMSE);
			
			totalTestRMSE += foldRMSE;
			
			
			int[] stats = RecommenderUtil.calcStats(userTraits, linkTraits, linkLikes, forTesting);
			int truePos = stats[0];
			int falsePos = stats[1];
			int trueNeg = stats[2];
			int falseNeg = stats[3];
			
			totalTruePos += truePos;
			totalFalsePos += falsePos;
			totalTrueNeg += trueNeg;
			totalFalseNeg += falseNeg;
			
			double accuracy = (double)(truePos + trueNeg) / (double)(truePos + falsePos + trueNeg + falseNeg);
			double precision = (double)truePos / (double)(truePos + falsePos);
			double recall = (double)truePos / (double)(truePos + falseNeg);
			double f1 = 2 * precision * recall / (precision + recall);
			
			HashMap<Long, Integer[]> precisions = RecommenderUtil.getPrecision(userTraits, linkTraits, linkLikes, forTesting);
			
			for (long u : precisions.keySet()) {
				Integer[] precisionAt = precisions.get(u);
				
				if (!totalUserPrecision.containsKey(u)) {
					totalUserPrecision.put(u, new Integer[]{0, 0, 0});
				}
				
				Integer[] userPrecision = totalUserPrecision.get(u);
				for (int z = 0; z < userPrecision.length; z++) {
					userPrecision[z] += precisionAt[z];
				}
			}
			System.out.println("Stats for Run " + (x+1));
			System.out.println("True Pos: "+ truePos);
			System.out.println("False Pos: "+ falsePos);
			System.out.println("True Neg: "+ trueNeg);
			System.out.println("False Neg: "+ falseNeg);
			System.out.println("Accuracy: " + accuracy);
			System.out.println("Precision: " + precision);
			System.out.println("Recall: " + recall);
			System.out.println("F1: " + f1);
			System.out.println("");
			
			for (long userId : forTesting.keySet()) {
				HashSet<Long> tests = forTesting.get(userId);
				for (long linkId : tests) {
					userLinkSamples.get(userId).add(linkId);
				}
			}
		}
		
		
		double meanRMSE = totalTestRMSE / 10;
		double standardDev = 0;
		
		for (double rmse : rmseArr) {
			standardDev += Math.pow(rmse - meanRMSE, 2);
		}
		standardDev = Math.sqrt(standardDev / 10);
		double standardError = standardDev / Math.sqrt(10);
		
		double accuracy = (double)(totalTruePos + totalTrueNeg) / (double)(totalTruePos + totalFalsePos + totalTrueNeg + totalFalseNeg);
		double precision = (double)totalTruePos / (double)(totalTruePos + totalFalsePos);
		double recall = (double)totalTruePos / (double)(totalTruePos + totalFalseNeg);
		double f1 = 2 * precision * recall / (precision + recall);
		
		double map[] = {0, 0, 0};
		
		for (long user : totalUserPrecision.keySet()) {
			Integer[] precisions = totalUserPrecision.get(user);
			
			for (int x = 0; x < precisions.length; x++) {
				map[x] += (double)precisions[x] / (double)10;
			}
		}
		
		for (int x = 0; x < map.length; x++) {
			map[x] /= (double)totalUserPrecision.size();
		}
		
		System.out.println("L=" + Constants.LAMBDA + ", B=" + Constants.BETA);
		System.out.println("Mean RMSE: " + (totalTestRMSE / 10));
		System.out.println("Standard Deviation: " + standardDev);
		System.out.println("Standard Error: " + standardError);
		System.out.println("Confidence Interval: +/-" + 2*standardError);
		System.out.println("");
		System.out.println("Accuracy: " + accuracy);
		System.out.println("Precision: " + precision);
		System.out.println("Recall: " + recall);
		System.out.println("F1: " + f1);
		System.out.println("MAP@1: " + map[0]);
		System.out.println("MAP@2: " + map[1]);
		System.out.println("MAP@3: " + map[2]);
		System.out.println("");
	}
}
