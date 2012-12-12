package project.suvash.predictor;

import java.io.IOException;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

import project.suvash.predictor.ArffData.DataEntry;

public class SVMLibSVM extends Predictor {

	private boolean _firstLabelIsTrue;
	
	private svm_model _model = null;
	private double _C   = 2d;
	private double _EPS = 0.001d;
	
	public SVMLibSVM(double C, double eps) {
		_C = C;
		_EPS = eps;
	}
	
	public void train() {
		_model = svmTrain();		
	}

	/*
	 * train the svm model
	 */
	private svm_model svmTrain() {
		svm_problem prob = new svm_problem();
		int dataCount = _trainData._data.size();
		prob.y = new double[dataCount];
		prob.l = dataCount;
		prob.x = new svm_node[dataCount][];		
		
		for (int i = 0; i < dataCount; i++){			
			double[] features = getFeatures(_trainData._data.get(i));
			prob.x[i] = new svm_node[features.length-1];
			// first 'feature' is class value
			for (int j = 1; j < features.length; j++){
				svm_node node = new svm_node();
				node.index = j;
				node.value = features[j];
				prob.x[i][j-1] = node;
			}			
			prob.y[i] = (features[0] == 0) ? -1 : 1;
			if (i == 0) // Check first training example
				_firstLabelIsTrue = (prob.y[i] == 1);
		}				
		
		svm_parameter param = new svm_parameter();
		param.C = _C;
		param.eps = _EPS;		
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.LINEAR;		
		param.cache_size = 200000;
				
		svm_model model = svm.svm_train(prob, param);
		
		return model;
	}

	@Override
	public int evaluate(DataEntry de) {
		
		double[] features = getFeatures((DataEntry)de);
		svm_node[] nodes = new svm_node[features.length-1];
		for (int i = 1; i < features.length; i++){
			nodes[i-1] = new svm_node();
			nodes[i-1].index = i;
			nodes[i-1].value = features[i];
		}
		
		double[] dbl = new double[1]; 
		svm.svm_predict_values(_model, nodes, dbl);
		// +1 will be assigned for the label of the first training datum
		double prediction = _firstLabelIsTrue ? dbl[0] : -dbl[0];
		return (prediction > 0d ? 1 : 0);		
	}

	@Override
	public void clear() {
		_model = null;
	}

	@Override
	public String getName() {
		return "SVMLibSVM(" + _C + "," + _EPS + ")";
	}

	public static void main(String[] args) throws IOException{
		SVMLibSVM svm = new SVMLibSVM(15d, 0.0001d);
		svm.runTests("folds/balanced/balanced_data.arff", 10);
	}
	
}
