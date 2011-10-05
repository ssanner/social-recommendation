package org.nicta.lr.thread;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.nicta.lr.recommender.MFRecommender;
import org.nicta.lr.util.Configuration;

public class LinkMFThread extends Thread
{
	int q;
	Map<Long, Double[]> userTraits;
	Map<Long, Map<Long, Double>> predictions;
	Double[][] linkDerivative;
	HashMap<Long, Double[]> linkIdDerivative;
	Set<Long> linkIds;
	MFRecommender backpointer;
	
	public LinkMFThread(int q, Map<Long, Double[]> userTraits, Map<Long, Map<Long, Double>> predictions, 
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
		for (int l = 0; l < Configuration.LINK_FEATURE_COUNT; l++) {
			linkDerivative[q][l] = backpointer.getErrorDerivativeOverLinkAttribute(userTraits, predictions, q, l);
		}
	}
}
