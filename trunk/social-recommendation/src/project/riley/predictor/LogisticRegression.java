package project.riley.predictor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.aliasi.matrix.DenseVector;
import com.aliasi.matrix.Vector;
import com.aliasi.stats.AnnealingSchedule;
import com.aliasi.stats.RegressionPrior;

import project.riley.predictor.ArffData.DataEntry;

/*
 * Logistic regression implementation
 */

public class LogisticRegression extends Predictor {

	public boolean _maxEnt = false;
	
	public enum PRIOR_TYPE { L2, L1 };
	public PRIOR_TYPE _priorType  = null; 
	public double     _priorValue = -1d;
	public double     _threshold;
	
	private com.aliasi.stats.LogisticRegression _model = null;
	private  Vector[] _betas = null;

	public LogisticRegression(PRIOR_TYPE prior, double prior_value) {
		_threshold  = 0.5d;
		_priorType  = prior;
		_priorValue = prior_value;
		_maxEnt = false;
	}

	public LogisticRegression(PRIOR_TYPE prior, double prior_value, boolean max_ent) {
		_threshold  = 0.5d;
		_priorType  = prior;
		_priorValue = prior_value;
		_maxEnt = max_ent;
	}
	
	@Override
	public void train() {

		double[] features;
		
		/*
		 * regression data format
		 */
		ArrayList<DenseVector> al_inputs  = new ArrayList<DenseVector>();
		ArrayList<Integer>     al_outputs = new ArrayList<Integer>();
		for (int i = 0; i < _trainData._data.size(); i++) {
			features = getFeatures(_trainData._data.get(i));
			if ( !_maxEnt || (int)features[0] > 0 ) { // only true data if maxEnt
				al_inputs.add(new DenseVector(Arrays.copyOfRange(features, 1, features.length)));
				al_outputs.add((int)features[0]);
			}
		}
		Vector[] INPUTS  = new Vector[al_inputs.size()];
		int[]    OUTPUTS = new int[al_outputs.size()];
		for (int i = 0; i < al_inputs.size(); i++) {
			INPUTS[i]  = al_inputs.get(i);
			OUTPUTS[i] = al_outputs.get(i);
		}
		
		/*
		 * train classifier
		 */
		RegressionPrior prior = null;
		if (_priorType == PRIOR_TYPE.L1) 
			prior = RegressionPrior.laplace(_priorValue, true);
		else if (_priorType == PRIOR_TYPE.L2) 
			prior = RegressionPrior.gaussian(_priorValue, true);
			
	    _model = com.aliasi.stats.LogisticRegression.estimate(INPUTS,
                                      OUTPUTS,
                                      prior,
                                      AnnealingSchedule.inverse(.05,100),
                                      null, // reporter with no feedback
                                      0.000000001, // min improve
                                      1, // min epochs
                                      5000);// max epochs
	   _betas = _model.weightVectors();
	    //for (int outcome = 0; outcome < _betas.length; outcome++) {
		    //System.out.println("Classifier weights for outcome = " + outcome + " [" + _betas[outcome].numDimensions() + " features]");
			//for (int i = 0; i < _betas[outcome].numDimensions(); i++) {
				//System.out.print(_betas[outcome].value(i));				
			//}
			//System.out.println();
		//}	    
	    
	}	

	@Override
	public int evaluate(DataEntry de) {
		double[] features = getFeatures((DataEntry)de);
		features = Arrays.copyOfRange(features, 1, features.length);
		
		//double[] conditionalProbs = regression.classify(INPUTS[i]);
		double weight_prediction_0 = 0d;
		for (int j = 0; j < features.length; j++)
			weight_prediction_0 += _betas[0].value(j) * features[j];
		
		// Logistic transform
		double prob_0 = Math.exp(weight_prediction_0) / (1d + Math.exp(weight_prediction_0));
		
		// Make prediction with probability
		double prediction = /*conditionalProbs[0]*/ prob_0 >= _threshold ? 0 : 1;
		
		return (int) prediction;
	}

	@Override
	public void clear() {
		_model = null;
		_betas = null;
	}

	@Override
	public String getName() {
		return "Logistic Regression(" + _priorType + "," + _priorValue + (_maxEnt ? ",MAX_ENT" : "") + ")";
	}

	public static void main(String[] args) throws IOException{
		LogisticRegression lr = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L1, 2d);
		lr.runTests("active.arff", 10);
	}
	
}
