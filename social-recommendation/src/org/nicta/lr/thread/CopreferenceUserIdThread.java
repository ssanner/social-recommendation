package org.nicta.lr.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.recommender.CopreferenceRecommender;

public class CopreferenceUserIdThread extends Thread
{
	int k;
	Map<Long, Double[]> linkTraits;
	Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences;
	Map<Long, Map<Long, Double>> predictions;
	Double[][] userDerivative;
	HashMap<Long, Double[]> userIdDerivative;
	Map<Long, Set<Long>> userLinkSamples;
	CopreferenceRecommender backpointer;
	
	public CopreferenceUserIdThread(int k, Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Map<Long, Double>>> connections, 
			Map<Long, Map<Long, Double>> predictions, Double[][] userDerivative, 
			HashMap<Long, Double[]> userIdDerivative, Map<Long, Set<Long>> userLinkSamples,
			CopreferenceRecommender backpointer)
	{
		this.k = k;
		this.linkTraits = linkTraits;
		this.predictedCopreferences = connections;
		this.predictions = predictions;
		this.userDerivative = userDerivative;
		this.userIdDerivative = userIdDerivative;
		this.userLinkSamples = userLinkSamples;
		this.backpointer = backpointer;
	}
	public void run()
	{
		for (long userId : userLinkSamples.keySet()) {
			userIdDerivative.get(userId)[k] = backpointer.getCopreferenceErrorDerivativeOverUserId(linkTraits, predictions, predictedCopreferences, k, userId);
		}
	}
}
