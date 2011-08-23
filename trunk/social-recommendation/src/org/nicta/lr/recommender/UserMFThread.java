package org.nicta.lr.recommender;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.util.Constants;

public class UserMFThread extends Thread
{
	int k;
	Map<Long, Double[]> linkTraits;
	Map<Long, Map<Long, Double>> connections;
	Map<Long, Map<Long, Double>> predictions;
	Double[][] userDerivative;
	HashMap<Long, Double[]> userIdDerivative;
	Map<Long, Set<Long>> userLinkSamples;
	MFRecommender backpointer;
	
	
	public UserMFThread(int k, Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Double>> connections, 
						Map<Long, Map<Long, Double>> predictions, Double[][] userDerivative, 
						HashMap<Long, Double[]> userIdDerivative, Map<Long, Set<Long>> userLinkSamples,
						MFRecommender backpointer)
	{
		this.k = k;
		this.linkTraits = linkTraits;
		this.connections = connections;
		this.predictions = predictions;
		this.userDerivative = userDerivative;
		this.userIdDerivative = userIdDerivative;
		this.userLinkSamples = userLinkSamples;
		this.backpointer = backpointer;
	}
	
	public void run()
	{
		for (int l = 0; l < Constants.USER_FEATURE_COUNT; l++) {
			userDerivative[k][l] = backpointer.getErrorDerivativeOverUserAttribute(linkTraits, predictions, connections, k, l);	
		}
		
		/*
		for (long userId : userLinkSamples.keySet()) {
			userIdDerivative.get(userId)[k] = backpointer.getErrorDerivativeOverUserId(linkTraits, predictions, connections, k, userId);
		}
		*/
	}
}
