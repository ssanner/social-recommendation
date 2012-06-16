package project.riley.predictor;

import java.text.DecimalFormat;

import de.bwaldvogel.liblinear.*;

import project.riley.predictor.ArffData.DataEntry;

public class SVMLibLinear extends Predictor {

	/** Note: SPS -- liblinear does not automatically include a bias term b so one
	 *               has to always add in the constant 1 feature to include this.
	 *               
	 *               See: http://agbs.kyb.tuebingen.mpg.de/km/bb/showthread.php?tid=710
	 *                    (response by ivank)
	 */
	
	public final static SolverType[] SOLVER_TYPES = { SolverType.L2R_L2LOSS_SVC };
	//public final static SolverType[] SOLVER_TYPES = {SolverType.L2R_LR, SolverType.L2R_L2LOSS_SVC, SolverType.L1R_L2LOSS_SVC, SolverType.L1R_LR};

	public final static double[]     C_VALUES     = {0.000000001d, 0.125, 0.25, 0.5, 1};		
	//public final static double[]     C_VALUES     = { 0.5d };		

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
	
	public void train() {
		_model = svmTrain();		
	}

	/*
	 * train the svm model
	 */
	private Model svmTrain() {
		Problem prob = new Problem();
		int dataCount = _trainData._data.size(); // size of the training set
		prob.n = _trainData._attr.size() -3 /*item id, user_id, class*/ + 1 /*bias*/;
		prob.l = dataCount;
		prob.y = new int[prob.l];
		prob.x = new FeatureNode[prob.l][prob.n]; // matrix of features + bias term		
		
		for (int i = 0; i < prob.l; i++){			
			double[] features = getFeatures(_trainData._data.get(i));
			for (int j = 1; j < features.length; j++){	            // first 'feature' is class value
				prob.x[i][j-1] = new FeatureNode(j, features[j]);   // feature count starts at 1
			}			
			prob.x[i][features.length-1] = new FeatureNode(features.length, 1d); // Constant bias feature 
			prob.y[i] = (int)features[0];
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
		return Linear.predict(_model, nodes);
	}

	public void clear() {
		_model = null;		
	}

	public String getName() {
		return "SVMLibLinear(" + _solverType + "," + _C + "," + _EPS + ")";
	}

	/*
	 * Run tests on data
	 */
	public static void main(String[] args) throws Exception {
		for (SolverType type : SOLVER_TYPES) { // testing solver types 
			for (double C : C_VALUES){ // testing C values
				SVMLibLinear svm = new SVMLibLinear(type, C, 0.001d);
				svm.runTests("active.arff", 10);
			}
		}
	}

}