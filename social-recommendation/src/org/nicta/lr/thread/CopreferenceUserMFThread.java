package org.nicta.lr.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.recommender.CopreferenceRecommender;
import org.nicta.lr.util.Configuration;

public class CopreferenceUserMFThread extends Thread
{
	int k;
	Map<Long, Double[]> linkTraits;
	Map<Long, Map<Long, Map<Long, Double>>> connections;
	Map<Long, Map<Long, Double>> predictions;
	Double[][] userDerivative;
	HashMap<Long, Double[]> userIdDerivative;
	Map<Long, Set<Long>> userLinkSamples;
	CopreferenceRecommender backpointer;
	
	
	public CopreferenceUserMFThread(int k, Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Map<Long, Double>>> connections, 
										Map<Long, Map<Long, Double>> predictions, Double[][] userDerivative, 
										HashMap<Long, Double[]> userIdDerivative, Map<Long, Set<Long>> userLinkSamples,
										CopreferenceRecommender backpointer)
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
		for (int l = 0; l < Configuration.USER_FEATURE_COUNT; l++) {
			userDerivative[k][l] = backpointer.getCopreferenceErrorDerivativeOverUserAttribute(linkTraits, predictions, connections, k, l);	
		}
	}
}
