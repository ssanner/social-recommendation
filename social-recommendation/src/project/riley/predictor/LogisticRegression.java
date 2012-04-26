package project.riley.predictor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.aliasi.matrix.DenseVector;
import com.aliasi.matrix.Vector;
import com.aliasi.stats.AnnealingSchedule;
import com.aliasi.stats.RegressionPrior;

import project.riley.predictor.ArffData.DataEntry;
import project.riley.predictor.ArffData.SplitData;

public class LogisticRegression extends Predictor {

	private com.aliasi.stats.LogisticRegression _model = null;
	private  Vector[] _betas = null;
	private ArffData _testData = null;
	private ArffData _trainData = null;	
	
	@Override
	public void setData(SplitData data) {
		_trainData = data._train;
		_testData = data._test;
	}

	@Override
	public void train() {
		Vector[] INPUTS = new Vector[_trainData._data.size()];
		int[] OUTPUTS = new int[_trainData._data.size()];
		double[] features;
		
		/*
		 * regression data format
		 */
		for (int i = 0; i < _trainData._data.size(); i++) {
			features = getFeatures(_trainData._data.get(i), _trainData._attr.size()-2);
			INPUTS[i] = new DenseVector(Arrays.copyOfRange(features, 1, features.length));
			OUTPUTS[i] = (int) features[0]; 
		}
		
		/*
		 * train classifier
		 */
		RegressionPrior prior = RegressionPrior.laplace(2d, true);
	    _model = com.aliasi.stats.LogisticRegression.estimate(INPUTS,
                                      OUTPUTS,
                                      prior,
                                      AnnealingSchedule.inverse(.05,100),
                                      null, // reporter with no feedback
                                      0.000000001, // min improve
                                      1, // min epochs
                                      5000); //5000); // max epochs

	   _betas = _model.weightVectors();
	    for (int outcome = 0; outcome < _betas.length; outcome++) {
		    //System.out.println("Classifier weights for outcome = " + outcome + " [" + _betas[outcome].numDimensions() + " features]");
			for (int i = 0; i < _betas[outcome].numDimensions(); i++) {
				//System.out.print(_betas[outcome].value(i));				
			}
			//System.out.println();
		}	    
	    
	}	

	@Override
	public <T> int evaluate(T de, double threshold) {
		double[] features = getFeatures((DataEntry)de,_trainData._attr.size()-2);
		features = Arrays.copyOfRange(features, 1, features.length);
		
		//double[] conditionalProbs = regression.classify(INPUTS[i]);
		double weight_prediction_0 = 0d;
		for (int j = 0; j < features.length; j++)
			weight_prediction_0 += _betas[0].value(j) * features[j];
		
		// Logistic transform
		double prob_0 = Math.exp(weight_prediction_0) / (1d + Math.exp(weight_prediction_0));
		
		// Make prediction with probability
		double prediction = /*conditionalProbs[0]*/ prob_0 >= 0.5d ? 0 : 1;
		
		return (int) prediction;
	}

	@Override
	public void clear() {
		_model = null;
		_betas = null;
	}

	@Override
	public String getName() {
		return "Logistic Regression";
	}

	@Override
	public <T> ArrayList<T> getTrainData() {
		return (ArrayList<T>) _trainData._data;
	}

	@Override
	public <T> ArrayList<T> getTestData() {
		return (ArrayList<T>) _testData._data;
	}

	public static void main(String[] args) throws IOException{
		LogisticRegression lr = new LogisticRegression();
	//	lr.runTests();
		System.out.println(lr.data._data.get(0));
		System.out.println(lr.getFeatures(lr.data._data.get(0), lr.data._attr.size()-2));
	}
	
}
