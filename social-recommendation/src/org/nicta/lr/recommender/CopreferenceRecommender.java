package org.nicta.lr.recommender;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.component.SocialRegularizer;
import org.nicta.lr.component.SocialSpectralRegularizer;
import org.nicta.lr.component.SpectralCopreferenceRegularizer;
import org.nicta.lr.component.SocialCopreferenceRegularizer;
import org.nicta.lr.thread.CopreferenceLinkIdThread;
import org.nicta.lr.thread.CopreferenceLinkMFThread;
import org.nicta.lr.thread.CopreferenceUserIdThread;
import org.nicta.lr.thread.CopreferenceUserMFThread;
import org.nicta.lr.util.Configuration;
import org.nicta.lr.util.Constants;
import org.nicta.lr.util.UserUtil;
import org.nicta.social.LBFGS;

public class CopreferenceRecommender extends SocialRecommender
{
	Map<Long, Map<Long, Map<Long, Double>>> copreferences;
	
	SocialCopreferenceRegularizer socialCopreferenceRegularizer;
	
	public CopreferenceRecommender(Map<Long, Set<Long>> linkLikes, Map<Long, Double[]> userFeatures, Map<Long, Double[]> linkFeatures, Map<Long, Map<Long, Double>> friends, String type)
	{
		super(linkLikes, userFeatures, linkFeatures, friends, type);
		
		if (Constants.SOCIAL_COPREFERENCE.equals(type)) {
			System.out.println("copreference Type: " + type);
			socialCopreferenceRegularizer = new SocialCopreferenceRegularizer();
			beta = .00001;
		}
		else if (Constants.SPECTRAL_COPREFERENCE.equals(type)){
			System.out.println("copreference Type: " + type);
			socialCopreferenceRegularizer = new SpectralCopreferenceRegularizer();
			beta = .00001;
		}
		else {
			System.out.println("copreference Type: Non social");
		}
	}
	
	
	public Map<Long, Map<Long, Map<Long, Double>>> predictCopreferences(Map<Long, Map<Long, Map<Long, Double>>> copreferences)
	{
		Map<Long, Map<Long, Map<Long, Double>>> predictions = new HashMap<Long, Map<Long, Map<Long, Double>>>();
		
		for (long linkId : copreferences.keySet()) {
			Map<Long, Map<Long, Double>> linkCopreferences = copreferences.get(linkId);
			HashMap<Long, Map<Long, Double>> linkPredictions = new HashMap<Long, Map<Long, Double>>();
			predictions.put(linkId, linkPredictions);
			
			for (long user1 : linkCopreferences.keySet()) {
				Map<Long, Double> userCopreferences = linkCopreferences.get(user1);
				HashMap<Long, Double> userPredictions = new HashMap<Long, Double>();
				linkPredictions.put(user1, userPredictions);
				
				for (long user2 : userCopreferences.keySet()) {
					double coValue = socialCopreferenceRegularizer.predictConnection(userFeatureMatrix, userIdColumns, userFeatures, linkFeatureMatrix, linkIdColumns, linkFeatures, user1, user2, linkId, K);
					userPredictions.put(user2, coValue);
				}
			}
		}
		
		return predictions;
	}
	
	public void buildCopreferences(Map<Long, Set<Long>> linkLikes, Map<Long, Set<Long>> userLinkSamples)
	{	
		copreferences = new HashMap<Long, Map<Long, Map<Long, Double>>>();
		
		int count = 0;
		Object[] users = userLinkSamples.keySet().toArray();
		for (long linkId : linkLikes.keySet()) {
			HashMap<Long, Map<Long, Double>> linkCopreferences = new HashMap<Long, Map<Long, Double>>();
			copreferences.put(linkId, linkCopreferences);
			
			for (int a = 0; a < users.length - 1; a++) {
				Long user1 = (Long)users[a];
				Set<Long> user1Links = userLinkSamples.get(user1);
				if (!user1Links.contains(linkId)) continue;
				
				boolean user1Liked = linkLikes.get(linkId).contains(user1);
				
				Map<Long, Double> userCopreferences = new HashMap<Long, Double>();
				linkCopreferences.put(user1, userCopreferences);
				
				for (int b = a+1; b < users.length; b++) {
					Long user2 = (Long)users[b];
					Set<Long> user2Links = userLinkSamples.get(user2);
					if (!user2Links.contains(linkId)) continue;
					
					boolean user2Liked = linkLikes.get(linkId).contains(user2);
					
					userCopreferences.put(user2, user1Liked == user2Liked ? 1d : 0d);
					
					count++;
				}
			}
		}
		
		System.out.println("link copreference count: " + copreferences.size());
		System.out.println("total copreferences count: " + count);
	}
	
	public double getCopreferenceError(Double[][] userFeatureMatrix, Double[][] linkFeatureMatrix, 
			Map<Long, Double[]> userIdColumns, Map<Long, Double[]> linkIdColumns,
			Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences, Map<Long, Map<Long, Double>> connections)
	{
		double error = 0;
	
		if (socialCopreferenceRegularizer != null) {
			error += socialCopreferenceRegularizer.getValue(predictedCopreferences, copreferences);
			
			if (socialRegularizer != null) {
				error += socialRegularizer.getValue(connections, friendConnections);
			}
			
			error *= beta;
		}
			
		error += objective.getValue(predictions, linkLikes);

		//Get User and Link norms for regularisation
		double userNorm = l2.getValue(userFeatureMatrix) + l2.getValue(userIdColumns);
		double linkNorm = l2.getValue(linkFeatureMatrix) + l2.getValue(linkIdColumns);
		userNorm *= lambda;
		linkNorm *= lambda;

		error += userNorm + linkNorm;

		return error;
	}
	
	public double getCopreferenceErrorDerivativeOverUserAttribute(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
														Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences, Map<Long, Map<Long, Double>> connections,
														int x, int y)
	{
		double errorDerivative = userFeatureMatrix[x][y] * lambda;
		
		if (socialCopreferenceRegularizer != null) {
			double socDerivative = socialCopreferenceRegularizer.getDerivativeValueOverUserAttribute(userFeatureMatrix, userFeatures, userIdColumns, linkTraits, predictedCopreferences, copreferences, x, y);
			
			if (socialRegularizer != null) {
				socDerivative += socialRegularizer.getDerivativeValueOverAttribute(userFeatureMatrix, userFeatures, userIdColumns, connections, friendConnections, x, y);
			}
			
			errorDerivative += beta * socDerivative;
		}
		
		errorDerivative += objective.getDerivativeOverUserAttribute(linkTraits, userFeatures, predictions, linkLikes, x, y);

		return errorDerivative;
	}
	
	
	public double getCopreferenceErrorDerivativeOverUserId(Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> predictions, 
															Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences, Map<Long, Map<Long, Double>> connections,
															int k, long userId)
	{
		Double[] idColumn = userIdColumns.get(userId);
		double errorDerivative = idColumn[k] * lambda;

		if (socialCopreferenceRegularizer != null) {
			double socDerivative = socialCopreferenceRegularizer.getDerivativeValueOverUserId(userFeatureMatrix, userFeatures, userIdColumns, linkTraits, predictedCopreferences, copreferences, userId, k);
			
			if (socialRegularizer != null) {
				socDerivative = socialRegularizer.getDerivativeValueOverId(userFeatureMatrix, userFeatures, userIdColumns, connections, friendConnections, userId, k);
			}
			errorDerivative += beta * socDerivative;
		}
		
		errorDerivative += objective.getErrorDerivativeOverUserId(linkTraits, linkLikes, predictions, k, userId);
		
		return errorDerivative;
	}

	public double getCopreferenceErrorDerivativeOverLinkAttribute(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, 
																	Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences, int x, int y)
	{
		double errorDerivative = linkFeatureMatrix[x][y] * lambda;
		
		if (socialCopreferenceRegularizer != null) {
			double socDerivative = socialCopreferenceRegularizer.getDerivativeValueOverLinkAttribute(userTraits, linkFeatures, predictedCopreferences, copreferences, x, y);
			errorDerivative += beta * socDerivative;
		}
		
		errorDerivative += objective.getErrorDerivativeOverLinkAttribute(userTraits, linkFeatures, linkLikes, predictions, x, y);

		return errorDerivative;
	}

	public double getCopreferenceErrorDerivativeOverLinkId(Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, 
															Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences, int x, long linkId)
	{
		Double[] idColumn = linkIdColumns.get(linkId);
		double errorDerivative = idColumn[x] * lambda;
		
		if (socialCopreferenceRegularizer != null) {
			if (!copreferences.containsKey(linkId)) {
				return 0;
			}
			
			double socDerivative = socialCopreferenceRegularizer.getDerivativeValueOverLinkId(userTraits, copreferences, predictedCopreferences, linkId, x);
			errorDerivative += beta * socDerivative;
		}
		
		errorDerivative += objective.getErrorDerivativeOverLinkId(userTraits, linkLikes, predictions, x, linkId);
		
		return errorDerivative;
	}
	public void minimizeByThreadedLBFGS(Map<Long, Set<Long>> userLinkSamples)
	{
		System.out.println("Training copreference");
		
		boolean go = true;	
		int iterations = 0;
		
		int userVars = K * (Configuration.USER_FEATURE_COUNT + userLinkSamples.size());
		int linkVars = K * (Configuration.LINK_FEATURE_COUNT + linkFeatures.size());
		
		Object[] userKeys = userLinkSamples.keySet().toArray();
		Object[] linkKeys = linkFeatures.keySet().toArray();
		
		int[] iprint = {0,0};
		int[] iflag = {0};
		double[] diag = new double[userVars + linkVars];
		
		for (int x = 0; x < diag.length; x++) {
			diag[x] = 0;
		}
		
		double oldError = Double.MAX_VALUE;
		
		while (go) {
			iterations++;
			
			Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
			
			Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);

			System.out.println("Get connections");
			Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
			System.out.println("Getting Copreferences");
			Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences = predictCopreferences(copreferences);
			System.out.println("Getting Predictions");
			Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
			
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
			
			System.out.println("Iterations: " + iterations);
		
			//Get user derivatives
			
			CopreferenceUserMFThread[] userThreads = new CopreferenceUserMFThread[K];
			CopreferenceLinkMFThread[] linkThreads = new CopreferenceLinkMFThread[K];
			CopreferenceUserIdThread[] userIdThreads = new CopreferenceUserIdThread[K];
			CopreferenceLinkIdThread[] linkIdThreads = new CopreferenceLinkIdThread[K];
			
			System.out.println("Starting threads");
			long start = System.currentTimeMillis();
			for (int k = 0; k < K; k++) {
				userThreads[k] = new CopreferenceUserMFThread(k, linkTraits, predictedCopreferences, predictions, connections, userDerivative, userIdDerivative, userLinkSamples, this);
				userThreads[k].start();
				userIdThreads[k] = new CopreferenceUserIdThread(k, linkTraits, predictedCopreferences, predictions, connections, userDerivative, userIdDerivative, userLinkSamples, this);
				userIdThreads[k].start();
			}
			
			//Get link derivatives
			for (int q = 0; q < K; q++) {
				linkThreads[q] = new CopreferenceLinkMFThread(q, userTraits, predictions, predictedCopreferences, linkDerivative, linkIdDerivative, linkIdColumns.keySet(), this);
				linkThreads[q].start();
				linkIdThreads[q] = new CopreferenceLinkIdThread(q, userTraits, predictions, predictedCopreferences, linkDerivative, linkIdDerivative, linkIdColumns.keySet(), this);
				linkIdThreads[q].start();
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
			System.out.println("Threads done: " + (System.currentTimeMillis() - start) / 1000);
			
			
			double[] variables = new double[userVars + linkVars];
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
			
			
			System.out.println("derivatives");
			double[] derivatives = new double[userVars + linkVars];
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
			
			double error = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, predictedCopreferences, connections);
			
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
			
			if (iflag[0] == 0 || Math.abs(oldError - error) < convergence) go = false;
		
			oldError = error;
		}
	}
	
	public void checkDerivative(Map<Long, Set<Long>> userLinkSamples)
	{	
		System.out.println("Checking copreference... link");
		
		double H = 1e-5;
		
		for (int K = 0; K < K; K++) {
			/*
			for (int l = 0; l < Configuration.USER_FEATURE_COUNT; l++) {
				Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Map<Long, Double>>> pCopreferences = predictCopreferences(copreferences);
				Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double calculatedDerivative = getCopreferenceErrorDerivativeOverUserAttribute(linkTraits, predictions, pCopreferences, connections, K, l);
				double oldError = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, pCopreferences, connections);
				
				double tmp = userFeatureMatrix[K][l];
				userFeatureMatrix[K][l] += H;
				
				userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				pCopreferences= predictCopreferences(copreferences);
				predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double newError = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, pCopreferences, connections);
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
				Map<Long, Map<Long, Map<Long, Double>>> pCopreferences = predictCopreferences(copreferences);
				Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double calculatedDerivative = getCopreferenceErrorDerivativeOverUserId(linkTraits, predictions, pCopreferences, connections, K, userId);
				
				double oldError = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, pCopreferences, connections);
				
				double tmp = userIdColumns.get(userId)[K];
				userIdColumns.get(userId)[K] += H;
				
				userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				pCopreferences = predictCopreferences(copreferences);
				predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double newError = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, pCopreferences, connections);
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
			
			for (int l = 0; l < Configuration.LINK_FEATURE_COUNT; l++) {
				Map<Long, Double[]> userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				Map<Long, Double[]> linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				Map<Long, Map<Long, Map<Long, Double>>> pCopreferences = predictCopreferences(copreferences);
				Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double calculatedDerivative = getCopreferenceErrorDerivativeOverLinkAttribute(userTraits, predictions, pCopreferences, q, l);
				double oldError = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, pCopreferences, connections);
				
				double tmp = linkFeatureMatrix[q][l];
				linkFeatureMatrix[q][l] += H;
				
				userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				pCopreferences = predictCopreferences(copreferences);
				predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double newError = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, pCopreferences, connections);
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
				Map<Long, Map<Long, Map<Long, Double>>> pCopreferences = predictCopreferences(copreferences);
				Map<Long, Map<Long, Double>> predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				Map<Long, Map<Long, Double>> connections = getConnections(userFeatureMatrix, userIdColumns, userFeatures, userLinkSamples);
				
				double calculatedDerivative = getCopreferenceErrorDerivativeOverLinkId(userTraits, predictions, pCopreferences, q, linkId);
				double oldError = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, pCopreferences, connections);
				
				double tmp = linkIdColumns.get(linkId)[q];
				linkIdColumns.get(linkId)[q] += H;
				
				userTraits = getUserTraitVectors(userFeatureMatrix, userIdColumns, userFeatures);
				linkTraits = getLinkTraitVectors(linkFeatureMatrix, linkIdColumns, linkFeatures);
				pCopreferences = predictCopreferences(copreferences);
				predictions = getPredictions(userTraits, linkTraits, userLinkSamples);
				
				double newError = getCopreferenceError(userFeatureMatrix, linkFeatureMatrix, userIdColumns, linkIdColumns, predictions, pCopreferences, connections);
				linkIdColumns.get(linkId)[q] = tmp;
				
				double diff = (newError - oldError) / H;
				
				System.out.println("Calc: " + calculatedDerivative);
				System.out.println("FDApprox: " + diff);
				System.out.println("Diff: " + (calculatedDerivative - diff));
				System.out.println("");
			}
		}
	}
	
	public void train(Map<Long, Set<Long>> trainSamples) 
	{
		try {
			int linkCount = 0;
			
			//Remove users that do not have data yet.
			HashSet<Long> remove = new HashSet<Long>();
			for (long trainId : trainSamples.keySet()) {
				if (! userFeatures.containsKey(trainId)) {
					remove.add(trainId);
				}
				else {
					linkCount += trainSamples.get(trainId).size();
				}
			}
		
			for (long userId : remove) {
				trainSamples.remove(userId);
			}
			
			buildCopreferences(linkLikes, trainSamples);
			friendConnections = UserUtil.getFriendInteractionMeasure(trainSamples.keySet());
			
			//checkDerivative(trainSamples);
			minimizeByThreadedLBFGS(trainSamples);
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
