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
	Map<Long, Map<Long, Map<Long, Double>>> predictedCopreferences;
	Map<Long, Map<Long, Double>> predictions;
	Double[][] userDerivative;
	HashMap<Long, Double[]> userIdDerivative;
	Map<Long, Set<Long>> userLinkSamples;
	Map<Long, Map<Long, Double>> connections;
	CopreferenceRecommender backpointer;
	
	
	public CopreferenceUserMFThread(int k, Map<Long, Double[]> linkTraits, Map<Long, Map<Long, Map<Long, Double>>> pc, 
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
		for (int l = 0; l < Configuration.USER_FEATURE_COUNT; l++) {
			userDerivative[k][l] = backpointer.getCopreferenceErrorDerivativeOverUserAttribute(linkTraits, predictions, predictedCopreferences, connections, k, l);	
		}
	}
}
