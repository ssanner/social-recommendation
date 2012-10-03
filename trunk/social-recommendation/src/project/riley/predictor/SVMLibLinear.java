package project.riley.predictor;

import java.text.DecimalFormat;
import java.util.Map;

import libsvm.svm;

import de.bwaldvogel.liblinear.*;

import project.riley.predictor.ArffData.DataEntry;

public class SVMLibLinear extends Predictor {

	/** Note: SPS -- liblinear does not automatically include a bias term b so one
	 *               has to always add in the constant 1 feature to include this.
	 *               
	 *               See: http://agbs.kyb.tuebingen.mpg.de/km/bb/showthread.php?tid=710
	 *                    (response by ivank)
	 *                    
	 *               Also it appears if getting decision values, libsvm has the
	 *               same quirks as libsvm... it may invert the decision based on the
	 *               first training example.  Not an issue if just using "predict"
	 *               rather than predictValue or predictProbability (for logistic).
	 */
	private boolean _firstLabelIsTrue;
	
	//public final static SolverType[] SOLVER_TYPES = { SolverType.L2R_L2LOSS_SVC };
	public final static SolverType[] SOLVER_TYPES = { SolverType.L2R_LR, SolverType.L2R_L2LOSS_SVC, SolverType.L1R_L2LOSS_SVC, SolverType.L1R_LR};

	//public final static double[]     C_VALUES     = {0.125, 0.25, 0.5, 1};		
	public final static double[]     C_VALUES     = { 0.5d };		

	public boolean _maxEnt = false;
	
	private DecimalFormat df3 = new DecimalFormat("#.###");
	private Model _model = null;
	private double _C = 2d;
	private double _EPS = 2d;
	private SolverType _solverType = null;

	public SVMLibLinear(SolverType type, double C, double eps) {
		_solverType = type;
		_C = C;
		_EPS = eps;
	}
	
	public SVMLibLinear(SolverType type, double C, double eps, boolean max_ent) {
		_solverType = type;
		_C = C;
		_EPS = eps;
		_maxEnt = max_ent;
	}
	
	public void train() {
		_model = svmTrain();		
	}

	/*
	 * train the svm model
	 */
	private Model svmTrain() {
		Problem prob = new Problem();
		int dataCount = _trainData._data.size(); // size of the training set
		
		if (_maxEnt) {
			int trueDataCount = 0;
			for (int i = 0; i < dataCount; i++)
				if (getFeatures(_trainData._data.get(i))[0] > 0d)
					trueDataCount++;
			prob.l = trueDataCount;
		} else
			prob.l = dataCount;
		
		prob.n = _trainData._attr.size() -3 /*item id, user_id, class*/ + (_maxEnt ? 0 : 1) /*bias*/;
		prob.y = new int[prob.l];
		prob.x = new FeatureNode[prob.l][prob.n]; // matrix of features + bias term		
		
		int ii = 0; // This is the actual index (does not equal i for maxent)
		for (int i = 0; i < dataCount; i++) { // Go through all data, if maxEnt, discard negatives
			double[] features = getFeatures(_trainData._data.get(i));
			if (_maxEnt && features[0] <= 0d)
				continue;
			for (int j = 1; j < features.length; j++){	            // first 'feature' is class value
				prob.x[ii][j-1] = new FeatureNode(j, features[j]);   // feature count starts at 1
			}
			if (!_maxEnt){
				prob.x[ii][features.length-1] = new FeatureNode(features.length, 1d); // Constant bias feature 
				prob.y[ii] = (int)features[0] > 0 ? 1 : -1;
			}
			if (ii == 0) // Check first training example
				_firstLabelIsTrue = (prob.y[ii] == 1);
			ii++;
		}				

		Parameter param = new Parameter(_solverType, _C, _EPS);
		Linear.disableDebugOutput();
		Model model = Linear.train(prob, param);  // train the model
		return model;
	}

	/*
	 * test the model with a new data point
	 */
	public int evaluate(DataEntry de) {		
		double[] features = getFeatures(de);
		FeatureNode nodes[] = new FeatureNode[features.length]; // -1 for actual class at 0, +1 for bias term
		for (int i = 1; i < features.length; i++){
			nodes[i-1] = new FeatureNode(i,features[i]);
		}
		nodes[features.length - 1] = new FeatureNode(features.length, 1d); // Constant bias feature
		
		double[] dbl = new double[1]; 
		Linear.predictValues(_model, nodes, dbl);
		// +1 will be assigned for the label of the first training datum
		double value_prediction = _firstLabelIsTrue ? dbl[0] : -dbl[0];
		
		/* UNCOMMENT IF JUST WANT DIRECT LABEL PREDICTION
		//int prediction = Linear.predict(_model, nodes); // predicts label, in {-1,1}
		*/

		/* UNCOMMENT IF PROBABILITIES NEEDED FROM LOGISTIC REGRESSION
		// label == 1 <=> value_prediction > 0 <=> prob_prediction > 0.5
		//System.out.print(prediction + ", " + value_prediction);
		if (_solverType == SolverType.L2R_LR || _solverType == SolverType.L1R_LR) {
			double[] dbl2 = new double[2]; 
			Linear.predictProbability(_model, nodes, dbl2);
			// +1 will be assigned for the label of the first training datum
			double prob_prediction = _firstLabelIsTrue ? dbl[0] : 1d-dbl2[0]; // prob of true(1) class
			//System.out.print(", " + prob_prediction);
		}
		//System.out.println();
		*/
		
		return value_prediction > 0d ? 1 : 0;
	}

	public void clear() {
		_model = null;		
	}

	public String getName() {
		return "SVMLibLinear(" + _solverType + "," + _C + "," + _EPS + (_maxEnt ? ",MAX_ENT" : "") + ")";
	}

	/*
	 * Run tests on data
	 */
	public static void main(String[] args) throws Exception {
		
		
		for (SolverType type : SOLVER_TYPES) { // testing solver types 
			for (double C : C_VALUES){ // testing C values
				SVMLibLinear svm = new SVMLibLinear(type, C, 0.001d);
				//svm.runTests("active.arff", 10);
			}
		}
	}

	@Override
	public Map<Long, Map<Long, Double>> getProbabilities() {
		// TODO Auto-generated method stub
		return null;
	}

}