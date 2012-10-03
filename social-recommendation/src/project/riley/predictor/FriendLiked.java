package project.riley.predictor;

import java.util.Map;

import project.riley.predictor.ArffData.DataEntry;

public class FriendLiked extends Predictor {

	public void train() {}

	public int evaluate(DataEntry de) {
		return (de.friendLiked > 0 ? 1 : 0);
	}

	public void clear() { }
	public String getName() { return "FriendLiked"; }

	@Override
	public Map<Long, Map<Long, Double>> getProbabilities() {
		// TODO Auto-generated method stub
		return null;
	}

}
