package org.nicta.lr.recommender;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class LinkIdThread extends Thread
{
	int q;
	Map<Long, Double[]> userTraits;
	Map<Long, Map<Long, Double>> predictions;
	Double[][] linkDerivative;
	HashMap<Long, Double[]> linkIdDerivative;
	Set<Long> linkIds;
	MFRecommender backpointer;
	
	public LinkIdThread(int q, Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, 
							Double[][] linkDerivative, HashMap<Long, Double[]> linkIdDerivative, Set<Long> linkIds,
							MFRecommender backpointer)
	{
		this.q = q;
		this.userTraits = userTraits;
		this.predictions = predictions;
		this.linkDerivative = linkDerivative;
		this.linkIdDerivative = linkIdDerivative;
		this.linkIds = linkIds;
		this.backpointer = backpointer;
	}
	
	public void run()
	{
		for (long linkId : linkIds) {
			linkIdDerivative.get(linkId)[q] = backpointer.getErrorDerivativeOverLinkId(userTraits, predictions, q, linkId);
		}
	}
}
