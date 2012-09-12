package project.riley.predictor;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.nicta.lr.util.SQLUtil;

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
		return "LogisticRegression(" + _priorType + "," + _priorValue + (_maxEnt ? ",MAX_ENT" : "") + ")";
	}

	static String getGroup(String attribute) throws Exception{
		String ret = "";	
		String group = attribute.split("_")[1];
		
		Statement statement = SQLUtil.getStatement();
		String query = "select count(*), name from linkrGroups where id = " + group + ";";
		ResultSet result = statement.executeQuery(query);

		while (result.next()){
			ret = result.getString(2) + "_" + result.getLong(1);
		}
		result.close();
		statement.close();
		
		return ret;
	}
	
	public static void main(String[] args) throws Exception{
		LogisticRegression lr = new LogisticRegression(LogisticRegression.PRIOR_TYPE.L2, 2d);
		lr.runTests("active_all_1000.arff", /* file to use */ 10 /* folds to use */, 0 /* test threshold */, 800 /*groups size*/, new PrintWriter("a.txt") /* file to write */, true, true, true, true);	

		Map<Integer,Double> termWeights = new HashMap<Integer,Double>();    
		for (int outcome = 0; outcome < lr._betas.length; outcome++) {
			//System.out.println("Classifier weights for outcome = " + outcome + " [" + lr._betas[outcome].numDimensions() + " features]");
			for (int i = 0; i < lr._betas[outcome].numDimensions(); i++) {
				termWeights.put(i, lr._betas[outcome].value(i));
				//System.out.println(i + ":" + lr._betas[outcome].value(i) + " ");				
			}
		}

		SortedMap sortedData = new TreeMap(new ValueComparer(termWeights));
		ArffData a = new ArffData("active_all_1000.arff",0, 800, true, true, true, true);
		
		System.out.println(termWeights);
		sortedData.putAll(termWeights);
		for (Object key : sortedData.keySet()){
			String attribute = a._attr.get(((Integer)key+3)).toString().split(" ", 2)[0];
			System.out.println(termWeights.get(key) + " " + attribute + " " + (attribute.contains("group_") ? getGroup(attribute) : ""));
		}
		//System.out.println(sortedData);

	}

}

class ValueComparer implements Comparator {
	private Map _data = null;
	public ValueComparer (Map data){
		super();
		_data = data;
	}

	public int compare(Object o1, Object o2) {
		Double e1 = Math.abs((Double) _data.get(o1));
		Double e2 = Math.abs((Double) _data.get(o2));
		double compare = e2.compareTo(e1);
		if (compare == 0){
			Integer a = (Integer)o1;
			Integer b = (Integer)o2;
			return a.compareTo(b);
		}
		return (int) compare;
	}
}