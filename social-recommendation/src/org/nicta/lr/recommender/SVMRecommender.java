package org.nicta.lr.recommender;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_problem;
import libsvm.svm_parameter;

public class SVMRecommender extends Recommender
{
	Object[] userIds;
	Object[] linkIds;
	
	svm_model model;
	
	double C = Math.pow(2, 1);
	
	public SVMRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friendships)
	{
		super(linkLikes, userFeatures, linkFeatures, friendships);
		
		linkIds = linkFeatures.keySet().toArray();
		userIds = userFeatures.keySet().toArray();
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		model = trainSVMModel(trainSamples);	
	}
	
	public Map<Long, Double> getAveragePrecisions(Map<Long, Set<Long>> testData)
	{
		HashMap<Long, Double> averagePrecisions = new HashMap<Long, Double>();
		
		System.out.println("Test size: " + testData.size());
		int count = 0;
		for (long userId : testData.keySet()) {
			System.out.println("User " + ++count);
			
			Set<Long> links = testData.get(userId);
			Set<Long> userFriends;
			if (friendships.containsKey(userId)) {
				userFriends = friendships.get(userId).keySet();
			}
			else {
				userFriends = new HashSet<Long>();
			}
			
			ArrayList<Double> scores = new ArrayList<Double>();
			ArrayList<Long> ids = new ArrayList<Long>();
			
			for (long linkId : links) {
				double[] features = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				ArrayList<svm_node> nodeList = new ArrayList<svm_node>();
				
				for (int x = 0; x < features.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = features[x];
					
					nodeList.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = features.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + x + 1;
						node.value = 1;
						
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
				
				double[] dbl = new double[1]; 
				svm.svm_predict_values(model, nodes, dbl);
				
				scores.add(dbl[0]);
				ids.add(linkId);
			}
			
			Object[] sorted = sort(scores, ids);
			
			double ap = getUserAP(sorted, userId);
			
			averagePrecisions.put(userId, ap);
		}
		
		return averagePrecisions;
	}
	
	public svm_model trainSVMModel(Map<Long, Set<Long>> trainingSamples)
	{
		int dataCount = 0;
		for (long userId : trainingSamples.keySet()) {
			dataCount += trainingSamples.get(userId).size();
		}
		
		svm_problem prob = new svm_problem();
		prob.y = new double[dataCount];
		prob.l = dataCount;
		prob.x = new svm_node[dataCount][];
		
		svm_parameter param = new svm_parameter();
		param.C = C;
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.LINEAR;
		param.cache_size = 20000;
		param.eps = 0.001;
		
		int index = 0;
		int count = 0;
		for (long userId : trainingSamples.keySet()) {
			System.out.println("User: " + ++count);
			Set<Long> samples = trainingSamples.get(userId);
			Set<Long> userFriends;
			if (friendships.containsKey(userId)) {
				userFriends = friendships.get(userId).keySet();
			}
			else {
				userFriends = new HashSet<Long>();
			}
			
			for (long linkId : samples) {
				double[] combined = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				
				ArrayList<svm_node> nodes = new ArrayList<svm_node>();
				
				for (int x = 0; x < combined.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = combined[x];
					
					nodes.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = combined.length + x + 1;
						node.value = 1;
						
						nodes.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = combined.length + userIds.length + x + 1;
						node.value = 1;
						
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

	public Map<Long, Map<Long, Double>> recommend(Map<Long, Set<Long>> linksToRecommend)
	{	
		if (userMax == null) {
			try {
				userMax = getUserMax(linksToRecommend.keySet());
			}
			catch (SQLException ex) {
				ex.printStackTrace();
			}
		}
		
		Map<Long, Map<Long, Double>> recommendations = new HashMap<Long, Map<Long, Double>>();
		
		
		for (long userId :linksToRecommend.keySet()) {
			Set<Long> userLinks = linksToRecommend.get(userId);
			Set<Long> userFriends;
			if (friendships.containsKey(userId)) {
				userFriends = friendships.get(userId).keySet();
			}
			else {
				userFriends = new HashSet<Long>();
			}
			
			HashMap<Long, Double> linkValues = new HashMap<Long, Double>();
			recommendations.put(userId, linkValues);
		
			int maxLinks = userMax.get(userId);
		
			for (long linkId : userLinks) {
				if (!linkFeatures.containsKey(linkId)) {
					continue;
				}
				
				double[] features = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				ArrayList<svm_node> nodeList = new ArrayList<svm_node>();
				
				for (int x = 0; x < features.length; x++) {
					svm_node node = new svm_node();
					node.index = x + 1;
					node.value = features[x];
					
					nodeList.add(node);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						svm_node node = new svm_node();
						node.index = features.length + x + 1;
						node.value = 1;
						
						nodeList.add(node);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						svm_node node = new svm_node();
						node.index = features.length + userIds.length + x + 1;
						node.value = 1;
						
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
				
				double[] dbl = new double[1]; 
				svm.svm_predict_values(model, nodes, dbl);
				double prediction = dbl[0];
				
				System.out.println("Prediction: " + prediction + " ID: " + userId);
		
				//We recommend only a set number of links per day/run. 
				//If the recommended links are more than the max number, recommend only the highest scoring links.
				if (linkValues.size() < maxLinks) {
					linkValues.put(linkId, prediction);
				}
				else {
					//Get the lowest scoring recommended link and replace it with the current link
					//if this one has a better score.
					long lowestKey = 0;
					double lowestValue = Double.MAX_VALUE;
		
					for (long id : linkValues.keySet()) {
						if (linkValues.get(id) < lowestValue) {
							lowestKey = id;
							lowestValue = linkValues.get(id);
						}
					}
		
					if (prediction > lowestValue) {
						linkValues.remove(lowestKey);
						linkValues.put(linkId, prediction);
					}
				}
			}
		}
		
		return recommendations;
	}
	
	public void saveModel()
		throws SQLException
	{
		//do nothing
	}
}
