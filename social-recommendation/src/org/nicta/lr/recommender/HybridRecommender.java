package org.nicta.lr.recommender;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.nicta.lr.thread.CBFThread;
import org.nicta.lr.thread.LinkIdThread;
import org.nicta.lr.thread.LinkMFThread;
import org.nicta.lr.thread.UserIdThread;
import org.nicta.lr.thread.UserMFThread;
import org.nicta.lr.util.Configuration;
import org.nicta.lr.util.UserUtil;
import org.nicta.social.LBFGS;

public class HybridRecommender extends SocialRecommender
{	
	Object[] userIds;
	Object[] linkIds;
	
	Double[] weights;
	
	//Map<Long, Map<Long, Double>> friendships;
	
	public HybridRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends, String type)
	{
		super(linkLikes, userFeatures, linkFeatures, friends, type);
		
		lambda = 10;
	}
	
	public double getError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkIdColumns, Double[] weightVector,
			Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Double>> connections)
	{
		double error = 0;

		double weightNorm = l2.getValue(weightVector);
		weightNorm *= lambda;
		weightNorm /= 2;
		
		error += super.getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, connections) + weightNorm;

		return error;
	}
	
	
	public double getErrorDerivativeOverWeights(Map<Long, Map<Long, Map<Integer, Double>>> featureMaps, Map<Long, Map<Long, Double>> predictions, int w)
	{
		double derivative = weights[w] * lambda;
		
		derivative += objective.getDerivativeOverWeights(linkLikes, featureMaps, predictions, w);
		
		return derivative;
	}
	
	public void minimizeByThreadedLBFGS(Map<Long, Set<Long>> userLinkSamples)
	{
		//checkDerivative(userLinkSamples);
		
		boolean go = true;	
		int iterations = 0;
		
		int userVars = K * (Configuration.USER_FEATURE_COUNT + userLinkSamples.size());
		int linkVars = K * (Configuration.LINK_FEATURE_COUNT + linkFeatures.size());
		
		Object[] userKeys = userLinkSamples.keySet().toArray();
		Object[] linkKeys = linkFeatures.keySet().toArray();
		
		int[] iprint = {0,0};
		int[] iflag = {0};
		double[] diag = new double[userVars + linkVars + weights.length];
		
		for (int x = 0; x < diag.length; x++) {
			diag[x] = 0;
		}
		
		double oldError = Double.MAX_VALUE;
		
		System.out.println("Getting feature maps");
		Map<Long, Map<Long, Map<Integer, Double>>> featureMaps = getFeatureMaps(userLinkSamples);
		
		while (go) {
			iterations++;
			
			Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
			Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);

			System.out.println("Getting W Predictions");
			Map<Long, Map<Long, Double>> weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
			System.out.println("Getting MF Predictions");
			Map<Long, Map<Long, Double>> mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
			System.out.println("Combining...");
			Map<Long, Map<Long, Double>> predictions = combinePredictions(weightPredictions, mfPredictions);
			System.out.println("Getting Connections");
			Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
			
			Double[][] userDerivative = new Double[K][Configuration.USER_FEATURE_COUNT];
			HashMap<Long, Double[]> userIdDerivative = new HashMap<Long, Double[]>();
			for (long userId : userLinkSamples.keySet()) {
				userIdDerivative.put(userId, new Double[K]);
			}
			
			Double[][] linkDerivative = new Double[K][Configuration.LINK_FEATURE_COUNT];
			HashMap<Long, Double[]> linkIdDerivative = new HashMap<Long, Double[]>();
			for (long linkId : linkIdColumns.keySet()) {
				linkIdDerivative.put(linkId, new Double[K]);
			}
			
			Double[] weightDerivative = new Double[weights.length];
			
			System.out.println("Iterations: " + iterations);
		
			//Get user derivatives
			
			UserMFThread[] userThreads = new UserMFThread[K];
			LinkMFThread[] linkThreads = new LinkMFThread[K];
			UserIdThread[] userIdThreads = new UserIdThread[K];
			LinkIdThread[] linkIdThreads = new LinkIdThread[K];
			CBFThread[] cbfThreads = new CBFThread[K * 10];
			
			System.out.println("Starting threads");
			long start = System.currentTimeMillis();
			for (int k = 0; k < K; k++) {
				userThreads[k] = new UserMFThread(k, linkTraits, weightPredictions, mfPredictions, userDerivative, userIdDerivative, userLinkSamples, this);
				userThreads[k].start();
				userIdThreads[k] = new UserIdThread(k, linkTraits, weightPredictions, mfPredictions, userDerivative, userIdDerivative, userLinkSamples, this);
				userIdThreads[k].start();
			}
			
			//Get link derivatives
			for (int q = 0; q < K; q++) {
				linkThreads[q] = new LinkMFThread(q, userTraits, mfPredictions, linkDerivative, linkIdDerivative, linkIdColumns.keySet(), this);
				linkThreads[q].start();
				linkIdThreads[q] = new LinkIdThread(q, userTraits, mfPredictions, linkDerivative, linkIdDerivative, linkIdColumns.keySet(), this);
				linkIdThreads[q].start();
			}
		
			//Get weight derivatives
			for (int t = 0; t < cbfThreads.length; t++) {
				int s = (weights.length / cbfThreads.length) * t;
				int e = (weights.length / cbfThreads.length) * (t+1);
				if (t == cbfThreads.length - 1) e = weights.length;
				
				cbfThreads[t] = new CBFThread(weightDerivative, s, e, featureMaps, predictions, this);
				cbfThreads[t].start();
			}
			
			for (int k = 0; k < K; k++) {
				try {
					userThreads[k].join();
					linkThreads[k].join();
					userIdThreads[k].join();
					linkIdThreads[k].join();
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			for (int x = 0; x < cbfThreads.length; x++) {
				try {	
					cbfThreads[x].join();
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			System.out.println("Threads done: " + (System.currentTimeMillis() - start) / 1000);
			
			
			double[] variables = new double[userVars + linkVars + weights.length];
			System.out.println("Variables: " + variables.length);
			
			int index = 0;
			
			for (int x = 0; x < K; x++) {
				for (int y = 0; y < Configuration.USER_FEATURE_COUNT; y++) {
					variables[index++] = userFeatureMatrix[x][y];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdColumns.get(userId);
				for (double d : column) {
					variables[index++] = d;
				}
			}
			for (int x = 0; x < K; x++) {
				for (int y = 0; y < Configuration.LINK_FEATURE_COUNT; y++) {
					variables[index++] = linkFeatureMatrix[x][y];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdColumns.get(linkId);
				for (double d : column) {
					variables[index++] = d;
				}
			}
			for (int x = 0; x < weights.length; x++) {
				variables[index++] = weights[x];
			}
			
			System.out.println("derivatives");
			double[] derivatives = new double[userVars + linkVars + weights.length];
			index = 0;
			for (int x = 0; x < K; x++) {
				for (int y = 0; y < Configuration.USER_FEATURE_COUNT; y++) {
					derivatives[index++] = userDerivative[x][y];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdDerivative.get(userId);
				for (double d : column) {
					derivatives[index++] = d;
				}
			}
			for (int x = 0; x < K; x++) {
				for (int y = 0; y < Configuration.LINK_FEATURE_COUNT; y++) {
					derivatives[index++] = linkDerivative[x][y];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdDerivative.get(linkId);
				for (double d : column) {
					derivatives[index++] = d;
				}
			}

			for (int x = 0; x < weights.length; x++) {
				//System.out.println("Foo " + x + " " + weights.length + ": " + weightDerivative);
				//System.out.println("Ba: " + weightDerivative[x]);
				
				derivatives[index++] = weightDerivative[x];
			}
			
			double error = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
			
			System.out.println("New Error: " + error);
			System.out.println("");
		
			try {
				LBFGS.lbfgs(variables.length, 5, variables, error, derivatives,
						false, diag, iprint, convergence,
						1e-15, iflag);
			}
			catch (LBFGS.ExceptionWithIflag f) {
				f.printStackTrace();
			}
			
			System.out.println("Setting again");
			index = 0;
			for (int x = 0; x < K; x++) {
				for (int y = 0; y < Configuration.USER_FEATURE_COUNT; y++) {
					userFeatureMatrix[x][y] = variables[index++];
				}
			}
			for (Object id : userKeys) {
				Long userId = (Long)id;
				Double[] column = userIdColumns.get(userId);
				for (int d = 0; d < column.length; d++) {
					column[d] = variables[index++];
				}
			}
			for (int x = 0; x < K; x++) {
				for (int y = 0; y < Configuration.LINK_FEATURE_COUNT; y++) {
					linkFeatureMatrix[x][y] = variables[index++];
				}
			}
			for (Object id : linkKeys) {
				Long linkId = (Long)id;
				Double[] column = linkIdColumns.get(linkId);
				for (int d = 0; d < column.length; d++) {
					column[d] = variables[index++];
				}
			}
			for (int x = 0; x < weights.length; x++) {
				weights[x] = variables[index++];
			}
			
			if (iflag[0] == 0 || Math.abs(oldError - error) < convergence) go = false;
		
			oldError = error;
		}
	}
	
	public Map<Long, Map<Long, Map<Integer, Double>>> getFeatureMaps(Map<Long, Set<Long>> trainSamples)
	{
		Map<Long, Map<Long, Map<Integer, Double>>> featureMaps = new HashMap<Long, Map<Long, Map<Integer, Double>>>();
		
		for (long userId : trainSamples.keySet()) {
			Set<Long> samples = trainSamples.get(userId);
			Set<Long> userFriends;
			if (friendships.containsKey(userId)) {
				userFriends = friendships.get(userId).keySet();
			}
			else {
				userFriends = new HashSet<Long>();
			}
			
			HashMap<Long, Map<Integer, Double>> userFeatureMaps = new HashMap<Long, Map<Integer, Double>>();
			featureMaps.put(userId, userFeatureMaps);
			
			for (long linkId : samples) {
				double[] combined = combineFeatures(userFeatures.get(userId), linkFeatures.get(linkId));
				
				Map<Integer, Double> featureMap = new HashMap<Integer, Double>();
				
				for (int x = 0; x < combined.length; x++) {
					featureMap.put(x, combined[x]);
				}
				
				for (int x = 0; x < userIds.length; x++) {
					if (userIds[x].equals(userId)) {
						featureMap.put(combined.length + x, 1d);
					}
					else if (userFriends.contains(userIds[x]) && linkLikes.containsKey(linkId) && linkLikes.get(linkId).contains(userIds[x])) {
						featureMap.put(combined.length + userIds.length + x, 1d);
					}
				}
				
				for (int x = 0; x < linkIds.length; x++) {
					if (linkIds[x].equals(linkId)) {
						featureMap.put(combined.length + userIds.length + userIds.length + x, 1d);
						break;
					}
				}
				
				userFeatureMaps.put(linkId, featureMap);
			}
		}
		
		return featureMaps;
	}
	
	public Map<Long, Map<Long, Double>> getPredictions(Double[] weightVector, Map<Long, Map<Long, Map<Integer, Double>>> featureMaps, Map<Long, Set<Long>> trainSamples)
	{
		Map<Long, Map<Long, Double>> predictions = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId : trainSamples.keySet()) {
			Map<Long, Map<Integer, Double>> userFeatureMaps = featureMaps.get(userId);
			Set<Long> links = trainSamples.get(userId);
			
			Map<Long, Double> userPreds = new HashMap<Long, Double>();
			predictions.put(userId, userPreds);
			
			for (long linkId : links) {
				Map<Integer, Double> featureMap = userFeatureMaps.get(linkId);
				double val = getWeightPrediction(featureMap, weightVector);
				
				userPreds.put(linkId, val);
			}
		}
		
		return predictions;
	}
	
	public Map<Long, Map<Long, Double>> combinePredictions(Map<Long, Map<Long, Double>> weightPredictions, Map<Long, Map<Long, Double>> mfPredictions)
	{
		Map<Long, Map<Long, Double>> predictions = new HashMap<Long, Map<Long, Double>>();
		
		for (long userId : weightPredictions.keySet()) {
			Map<Long, Double> weightPreds = weightPredictions.get(userId);
			Map<Long, Double> mfPreds = mfPredictions.get(userId);
			
			Map<Long, Double> combined = new HashMap<Long, Double>();
			predictions.put(userId, combined);
			
			for (long linkId : weightPreds.keySet()) {
				double val = weightPreds.get(linkId) + mfPreds.get(linkId);
				combined.put(linkId, val);
			}
		}
		
		return predictions;
	}
	
	public Map<Long, Map<Long, Double>> getPredictions(Map<Long, Set<Long>> testData)
	{
		
		Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
		Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
		
		System.out.println("Getting feature maps");
		Map<Long, Map<Long, Map<Integer, Double>>> featureMaps = getFeatureMaps(testData);
		System.out.println("Getting W Predictions");
		Map<Long, Map<Long, Double>> weightPredictions = getPredictions(weights, featureMaps, testData);
		System.out.println("Getting MF Predictions");
		Map<Long, Map<Long, Double>> mfPredictions = getPredictions(userTraits, linkTraits, testData);
		System.out.println("Combining...");
		Map<Long, Map<Long, Double>> predictions = combinePredictions(weightPredictions, mfPredictions);
	
		return predictions;
	}
	
	public void checkDerivative(Map<Long, Set<Long>> userLinkSamples)
	{
		double H = 1e-5;
		System.out.println("hybrid checking... wiehgts");
		for (int K = 0; K < K; K++) {
			/*
			for (int l = 0; l < Configuration.USER_FEATURE_COUNT; l++) {
				Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Map<Integer, Double>>> featureMaps = getFeatureMaps(userLinkSamples);
				Map<Long, Map<Long, Double>> weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
				Map<Long, Map<Long, Double>> mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				Map<Long, Map<Long, Double>> predictions = combinePredictions(weightPredictions, mfPredictions);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double calculatedDerivative = getErrorDerivativeOverUserAttribute(linkTraits, predictions, null, K, l);
				double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
				
				double tmp = userFeatureMatrix[K][l];
				userFeatureMatrix[K][l] += H;
				
				userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				predictions = combinePredictions(weightPredictions, mfPredictions);
				
				double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
				userFeatureMatrix[K][l] = tmp;
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
			}
			
			
			for (long userId : userLinkSamples.keySet()) {
				Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Map<Integer, Double>>> featureMaps = getFeatureMaps(userLinkSamples);
				Map<Long, Map<Long, Double>> weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
				Map<Long, Map<Long, Double>> mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				Map<Long, Map<Long, Double>> predictions = combinePredictions(weightPredictions, mfPredictions);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double calculatedDerivative = getErrorDerivativeOverUserId(linkTraits, predictions, null, K, userId);
				
				double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
				
				double tmp = userIdColumns.get(userId)[K];
				userIdColumns.get(userId)[K] += H;
				
				userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				predictions = combinePredictions(weightPredictions, mfPredictions);
				
				double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
				userIdColumns.get(userId)[K] = tmp;
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
			}
			*/
		}
		
		for (int q = 0; q < K; q++) {
			/*
			for (int l = 0; l < Configuration.LINK_FEATURE_COUNT; l++) {
				Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Map<Integer, Double>>> featureMaps = getFeatureMaps(userLinkSamples);
				Map<Long, Map<Long, Double>> weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
				Map<Long, Map<Long, Double>> mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				Map<Long, Map<Long, Double>> predictions = combinePredictions(weightPredictions, mfPredictions);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double calculatedDerivative = getErrorDerivativeOverLinkAttribute(userTraits, predictions, q, l);
				double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
				
				double tmp = linkFeatureMatrix[q][l];
				linkFeatureMatrix[q][l] += H;
				
				userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				featureMaps = getFeatureMaps(userLinkSamples);
				weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
				mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				predictions = combinePredictions(weightPredictions, mfPredictions);
				
				double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
				linkFeatureMatrix[q][l] = tmp;
				
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
			}
			
			for (long linkId : linkIdColumns.keySet()) {
				Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Map<Integer, Double>>> featureMaps = getFeatureMaps(userLinkSamples);
				Map<Long, Map<Long, Double>> weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
				Map<Long, Map<Long, Double>> mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				Map<Long, Map<Long, Double>> predictions = combinePredictions(weightPredictions, mfPredictions);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double calculatedDerivative = getErrorDerivativeOverLinkId(userTraits, predictions, q, linkId);
				double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
				
				double tmp = linkIdColumns.get(linkId)[q];
				linkIdColumns.get(linkId)[q] += H;
				
				userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				featureMaps = getFeatureMaps(userLinkSamples);
				weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
				mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				predictions = combinePredictions(weightPredictions, mfPredictions);
				
				double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
				linkIdColumns.get(linkId)[q] = tmp;
				
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
			}
			*/
		}
		
		for (int x = 0; x < weights.length; x++) {
			Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
			Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
			Map<Long, Map<Long, Map<Integer, Double>>> featureMaps = getFeatureMaps(userLinkSamples);
			Map<Long, Map<Long, Double>> weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
			Map<Long, Map<Long, Double>> mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
			Map<Long, Map<Long, Double>> predictions = combinePredictions(weightPredictions, mfPredictions);
			Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
			
			double calculatedDerivative = getErrorDerivativeOverWeights(featureMaps, predictions, x);
			double oldError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
			
			double tmp = weights[x];
			weights[x] += H;
			
			userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
			linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
			featureMaps = getFeatureMaps(userLinkSamples);
			weightPredictions = getPredictions(weights, featureMaps, userLinkSamples);
			mfPredictions = getPredictions(userTraits, linkTraits, userLinkSamples);
			predictions = combinePredictions(weightPredictions, mfPredictions);
			
			double newError = getError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, weights, predictions, connections);
			weights[x] = tmp;
			double diff = (newError - oldError) / H;
			
			System.out.println("Calc: " + calculatedDerivative);
			System.out.println("FDApprox: " + diff);
			System.out.println("Diff: " + (calculatedDerivative - diff));
			System.out.println("");
		}
	}
	
	public double getWeightPrediction(Map<Integer, Double> featureMap, Double[] weights)
	{
		double prediction = 0;
		for (int keyIndex : featureMap.keySet()) {
			prediction += weights[keyIndex] * featureMap.get(keyIndex);
		}
		
		return prediction;
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		try {
			//Remove users that do not have data yet.
			Set<Long> allTestLinks = new HashSet<Long>();
			
			HashSet<Long> remove = new HashSet<Long>();
			for (long trainId : trainSamples.keySet()) {
				if (! userFeatures.containsKey(trainId)) {
					remove.add(trainId);
				}
				else {
					allTestLinks.addAll(trainSamples.get(trainId));
				}
			}
		
			for (long userId : remove) {
				trainSamples.remove(userId);
			}
			
			friendConnections = UserUtil.getFriendInteractionMeasure(trainSamples.keySet());
			linkIds = allTestLinks.toArray();
			userIds = trainSamples.keySet().toArray();
			weights = new Double[linkIds.length + userIds.length + userIds.length + Configuration.USER_FEATURE_COUNT + Configuration.LINK_FEATURE_COUNT];
			Random random = new Random();
			for (int x = 0; x < weights.length; x++) {
				//weights[x] = 0d;
				weights[x] = random.nextGaussian();
			}
			
			System.out.println("Weight count: " + weights.length);
			//checkDerivative(trainSamples);
			minimizeByThreadedLBFGS(trainSamples);
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
