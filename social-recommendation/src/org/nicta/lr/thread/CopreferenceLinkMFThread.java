package org.nicta.lr.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.recommender.CopreferenceRecommender;
import org.nicta.lr.util.Configuration;

public class CopreferenceLinkMFThread extends Thread
{
	int q;
	Map<Long, Double[]> userTraits;
	Map<Long, Map<Long, Double>> predictions;
	Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences;
	
	Double[][] linkDerivative;
	HashMap<Long, Double[]> linkIdDerivative;
	Set<Long> linkIds;
	CopreferenceRecommender backpointer;
	
	public CopreferenceLinkMFThread(int q, Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, Map<Long, Map<Long, Map<Long, Double>>> connections,
							Double[][] linkDerivative, HashMap<Long, Double[]> linkIdDerivative, Set<Long> linkIds,
							CopreferenceRecommender backpointer)
	{
		this.q = q;
		this.userTraits = userTraits;
		this.predictions = predictions;
		this.predictedCopreferences = connections;
		this.linkDerivative = linkDerivative;
		this.linkIdDerivative = linkIdDerivative;
		this.linkIds = linkIds;
		this.backpointer = backpointer;
	}
	
	public void run()
	{
		for (int l = 0; l < Configuration.LINK_FEATURE_COUNT; l++) {
			linkDerivative[q][l] = backpointer.getCopreferenceErrorDerivativeOverLinkAttribute(userTraits, predictions, predictedCopreferences, q, l);
		}
	}
}
