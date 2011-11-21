package org.nicta.lr.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.recommender.CopreferenceRecommender;

public class CopreferenceLinkIdThread extends Thread
{
	int q;
	Map<Long, Double[]> userTraits;
	Map<Long, Map<Long, Double>> predictions;
	Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences;
	Double[][] linkDerivative;
	HashMap<Long, Double[]> linkIdDerivative;
	Set<Long> linkIds;
	CopreferenceRecommender backpointer;
	
	public CopreferenceLinkIdThread(int q, Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences,
							Double[][] linkDerivative, HashMap<Long, Double[]> linkIdDerivative, Set<Long> linkIds,
							CopreferenceRecommender backpointer)
	{
		this.q = q;
		this.userTraits = userTraits;
		this.predictions = predictions;
		this.linkDerivative = linkDerivative;
		this.linkIdDerivative = linkIdDerivative;
		this.linkIds = linkIds;
		this.predictedCopreferences = predictedCopreferences;
		this.backpointer = backpointer;
	}
	
	public void run()
	{
		for (long linkId : linkIds) {
			linkIdDerivative.get(linkId)[q] = backpointer.getCopreferenceErrorDerivativeOverLinkId(userTraits, predictions, predictedCopreferences, q, linkId);
		}
	}
}
