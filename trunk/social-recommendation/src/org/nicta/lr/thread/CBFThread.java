package org.nicta.lr.thread;

import java.util.Map;

import org.nicta.lr.recommender.HybridRecommender;

public class CBFThread extends Thread
{
	Double[] derivatives;
	int start;
	int end;
	Map<Long, Map<Long, Map<Integer, Double>>> featureMaps;
	Map<Long, Map<Long, Double>> predictions;
	HybridRecommender backpointer;
	
	public CBFThread(Double[] d, int s, int e, Map<Long, Map<Long, Map<Integer, Double>>> maps, Map<Long, Map<Long, Double>> preds, HybridRecommender b)
	{
		derivatives = d;
		start = s;
		end = e;
		featureMaps = maps;
		predictions = preds;
		
		backpointer = b;
	}
	
	public void run()
	{
		for (int x = start; x < end; x++) {
			derivatives[x] = backpointer.getErrorDerivativeOverWeights(featureMaps, predictions, x);
		}
	}
}
