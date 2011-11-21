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
	Map<Long, Map<Long, Double>> connections;
	
	public CopreferenceUserIdThread(int k, Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Map<Long, Double>>> pc, 
			Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Double>> c, Double[][] userDerivative, 
			HashMap<Long, Double[]> userIdDerivative, Map<Long, Set<Long>> userLinkSamples,
			CopreferenceRecommender backpointer)
	{
		this.k = k;
		this.linkTraits = linkTraits;
		this.predictedCopreferences = pc;
		this.predictions = predictions;
		this.userDerivative = userDerivative;
		this.userIdDerivative = userIdDerivative;
		this.userLinkSamples = userLinkSamples;
		this.connections = c;
		this.backpointer = backpointer;
	}
	public void run()
	{
		for (long userId : userLinkSamples.keySet()) {
			userIdDerivative.get(userId)[k] = backpointer.getCopreferenceErrorDerivativeOverUserId(linkTraits, predictions, predictedCopreferences, connections, k, userId);
		}
	}
}
