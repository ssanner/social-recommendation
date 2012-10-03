package project.riley.predictor;

import java.util.Map;

import project.riley.predictor.ArffData.DataEntry;

public class ConstantPredictor extends Predictor {

	public boolean _prediction;
	
	public ConstantPredictor(boolean prediction) {
		_prediction = prediction;
	}

	public void train() { }
	public int evaluate(DataEntry de) { return _prediction ? 1 : 0; }
	public void clear() { }
	public String getName() { return "ConstantPredictor(" + _prediction + ")"; }

	@Override
	public Map<Long, Map<Long, Double>> getProbabilities() {
		// TODO Auto-generated method stub
		return null;
	} 
}
