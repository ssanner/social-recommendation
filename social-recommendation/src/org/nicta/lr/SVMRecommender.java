package org.nicta.lr;

import java.util.Arrays;
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
import java.util.ArrayList;

public class SVMRecommender 
{
	Object[] userIds;
	Object[] linkIds;
	
	public static void main(String[] args)
		throws Exception
	{
		SVMRecommender svm = new SVMRecommender();
		svm.crossValidate();
	}
	
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
		
		userIds = userLinkSamples.keySet().toArray();
		linkIds = links.keySet().toArray();
	
		RecommenderUtil.closeSqlConnection();
		
		HashMap<Long, HashSet<Long>> tested = new HashMap<Long, HashSet<Long>>();
		for (long userId : userLinkSamples.keySet()) {
			tested.put(userId, new HashSet<Long>());
		}
		
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
			
			
			svm_model model = trainSVM(linkLikes, users, links, friendships, userLinkSamples);
			int[] stats = testSVM(model, linkLikes, users, links, friendships, forTesting);
			
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
			
			
			HashMap<Long, Integer[]> precisions = getPrecision(model, linkLikes, users, links, friendships, forTesting);
			
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
		
		System.out.println("C=" + Constants.C);
		System.out.println("Accuracy: " + accuracy);
		System.out.println("Precision: " + precision);
		System.out.println("Recall: " + recall);
		System.out.println("F1: " + f1);
		System.out.println("MAP@1: " + map[0]);
		System.out.println("MAP@2: " + map[1]);
		System.out.println("MAP@3: " + map[2]);
		System.out.println("");
	}
	
	public svm_model trainSVM(HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, 
								HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> userLinkSamples)
	{
		int dataCount = 0;
		for (long userId : userLinkSamples.keySet()) {
			dataCount += userLinkSamples.get(userId).size();
		}
		
		svm_problem prob = new svm_problem();
		prob.y = new double[dataCount];
		prob.l = dataCount;
		prob.x = new svm_node[dataCount][];
		
		svm_parameter param = new svm_parameter();
		param.C = Constants.C;
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.LINEAR;
		param.cache_size = 20000;
		param.eps = 0.001;
		
		int index = 0;
		for (long userId : userLinkSamples.keySet()) {
			HashSet<Long> samples = userLinkSamples.get(userId);
			Set<Long> userFriends = friendships.get(userId).keySet();
			
			for (long linkId : samples) {
				double[] combined = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				//prob.x[index] = new svm_node[combined.length];
				
				ArrayList<svm_node> nodes = new ArrayList<svm_node>();
				
				for (int x = 0; x < combined.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = combined[x];
					//prob.x[index][x] = node;
					nodes.add(node);
				}
				
				//int count = 1;
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = combined.length + x + 1;
						node.value = 1;
						//prob.x[index][combined.length] = node;
						nodes.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = combined.length + userIds.length + x + 1;
						node.value = 1;
						//prob.x[index][combined.length + count] = node;
						//count++;
						nodes.add(node);
					}
				}
				
				for (int x = 0; x < linkIds.length; x++) {
					if (linkIds[x].equals(linkId)) {
						svm_node node = new svm_node();
						node.index = combined.length + userIds.length + userIds.length + x + 1;
						node.value = 1;
						nodes.add(node);
						break;
					}
				}
				
				prob.x[index] = new svm_node[nodes.size()];
				for (int x = 0; x < nodes.size(); x++) {
					prob.x[index][x] = nodes.get(x);
				}
				
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					prob.y[index] = 1;
				}
				else {
					prob.y[index] = -1;
				}
				
				index++;
			}
		}
		
		System.out.println("Training...");
		svm_model model = svm.svm_train(prob, param);
		System.out.println("Done");
		return model;
	}
	
	public int[] testSVM(svm_model model, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> testSamples)
	{
		int truePos = 0;
		int falsePos = 0;
		int trueNeg = 0;
		int falseNeg = 0;
		
		for (long userId : testSamples.keySet()) {
			HashSet<Long> samples = testSamples.get(userId);
			Set<Long> userFriends = friendships.get(userId).keySet();
			
			for (long linkId : samples) {
				double[] features = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				ArrayList<svm_node> nodeList = new ArrayList<svm_node>();
				//svm_node nodes[] = new svm_node[features.length];
				
				for (int x = 0; x < features.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = features[x];
					//nodes[x] = node;
					nodeList.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = features.length + x + 1;
						node.value = 1;
						//prob.x[index][combined.length] = node;
						nodeList.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + x + 1;
						node.value = 1;
						//prob.x[index][combined.length + count] = node;
						//count++;
						nodeList.add(node);
					}
				}
				
				for (int x = 0; x < linkIds.length; x++) {
					if (linkIds[x].equals(linkId)) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + userIds.length + x + 1;
						node.value = 1;
						nodeList.add(node);
						break;
					}
				}
				
				svm_node nodes[] = new svm_node[nodeList.size()];
				
				for (int x = 0; x < nodes.length; x++) {
					nodes[x] = nodeList.get(x);
				}
				
				double prediction = svm.svm_predict(model, nodes);
				
				if (linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userId)) {
					if (prediction > 0) {
						truePos++;
					}
					else {
						falseNeg++;
					}
				}
				else {
					if (prediction > 0) {
						falsePos++;
					}
					else {
						trueNeg++;
					}
				}
				
			}
		}
		
		return new int[]{truePos, falsePos, trueNeg, falseNeg};
	}
	
	public double[] combineFeatures(Double[] user, Double[] link)
	{
		double[] feature = new double[user.length + link.length];
		
		for (int x = 0; x < user.length; x++) {
			feature[x] = user[x];
		}
		
		for (int x = 0; x < link.length; x++) {
			feature[x + user.length] = link[x];
		}
		
		return feature;
	}
	
	public HashMap<Long, Integer[]> getPrecision(svm_model model, HashMap<Long, HashSet<Long>> linkLikes, HashMap<Long, Double[]> userFeatures, HashMap<Long, Double[]> linkFeatures, HashMap<Long, HashMap<Long, Double>> friendships, HashMap<Long, HashSet<Long>> testSamples)
	{
		HashMap<Long, Integer[]> userPrecisions = new HashMap<Long, Integer[]>();
		
		for (long userId : testSamples.keySet()) {
			HashSet<Long> samples = testSamples.get(userId);
			Set<Long> userFriends = friendships.get(userId).keySet();
			
			double[] scores = new double[samples.size()];
			
			HashMap<Double, Long> linkScores = new HashMap<Double, Long>();
			
			int index = 0;
			for (long linkId : samples) {
				double[] features = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				ArrayList<svm_node> nodeList = new ArrayList<svm_node>();
				//svm_node nodes[] = new svm_node[features.length];
				
				for (int x = 0; x < features.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = features[x];
					//nodes[x] = node;
					nodeList.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = features.length + x + 1;
						node.value = 1;
						//prob.x[index][combined.length] = node;
						nodeList.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + x + 1;
						node.value = 1;
						//prob.x[index][combined.length + count] = node;
						//count++;
						//nodeList.add(node);
					}
				}
				
				for (int x = 0; x < linkIds.length; x++) {
					if (linkIds[x].equals(linkId)) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length /*+ userIds.length */ + x + 1;
						node.value = 1;
						nodeList.add(node);
						break;
					}
				}
				
				svm_node nodes[] = new svm_node[nodeList.size()];
				
				for (int x = 0; x < nodes.length; x++) {
					nodes[x] = nodeList.get(x);
				}
				
				double prediction = svm.svm_predict(model, nodes);
				
				scores[index] = prediction;
				linkScores.put(prediction, linkId);
				
				index++;
			}
			
			Arrays.sort(scores);
			
			int precisionAt1 = 0;
			int precisionAt2 = 0;
			int precisionAt3 = 0;
			
			for (int x = 0; x < 3; x++) {
				if (x >= scores.length) break;
				
				long id = linkScores.get(scores[x]);
			
				if (linkLikes.containsKey(id) && linkLikes.get(id).contains(userId)) {
					if (x == 0) {
						precisionAt1++;
						precisionAt2++;
						precisionAt3++;
					}
					else if (x == 1) {
						precisionAt2++;
						precisionAt3++;
					}
					else if (x == 2) {
						precisionAt3++;
					}
				}
			}
			
			System.out.println("User: " + userId + " Precision: " + precisionAt1 + " " + precisionAt2 + " " + precisionAt3);
			
			userPrecisions.put(userId, new Integer[]{precisionAt1, precisionAt2, precisionAt3});
		}
		
		return userPrecisions;
	}
}
