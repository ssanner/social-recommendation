package project.suvash.predictor;

import java.io.IOException;
import java.util.ArrayList;

import de.bwaldvogel.liblinear.FeatureNode;

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
			prob.x[i] = toLibSVMSparseFeaturesFormat(features);		
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
	
	private svm_node[] toLibSVMSparseFeaturesFormat(double[] feats){
		ArrayList<svm_node> features = new ArrayList<svm_node>();
		for(int i = 1; i < feats.length; i++){
			if(feats[i] != 0){
				svm_node node = new svm_node();
				node.index = i;
				node.value = feats[i];
				features.add(node);
			}
		}
		svm_node[] tfeatures = new svm_node[features.size()];
		return features.toArray(tfeatures);
	}
	
	@Override
	public int evaluate(DataEntry de) {
		
		double[] features = getFeatures((DataEntry)de);
		svm_node[] nodes = toLibSVMSparseFeaturesFormat(features);
		
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
		SVMLibSVM svm = new SVMLibSVM(50d, 0.00001d);
		
		//svm.runTests("folds/interaction/active_data.arff", 10); //best C 10
		//svm.runTests("folds/Groups/Binary/active_group_membership_binary_data.arff",10);.
		//svm.runTests("folds/pages/binary/active_page_membership_binary_data.arff",10);
		//svm.runTests("folds/Groups/integer/active_group_membership_integer_data.arff",10);
		//svm.runTests("folds/Groups/integer/filtered/active_group_membership_integer_filtered_data.arff",10);
		//svm.runTests("folds/interests/binary/active_interest_binary_data.arff",10);
		//svm.runTests("folds/groups_interests/binary/active_group_interest_binary_data.arff",10);
		//svm.runTests("folds/combined/binary/active_combined_binary_data.arff", 10);
		
		//svm.runTests("fresh/groups_binary/active_group_membership_binary_data.arff", 10);
		//svm.runTests("fresh/groups_integer/active_group_membership_integer_data.arff", 10);
		//svm.runTests("fresh/groups_integer_filtered/active_group_membership_integer_filtered_data.arff", 10);
		//svm.runTests("fresh/pages_binary/active_page_membership_binary_data.arff", 10);
		//svm.runTests("fresh/pages_integer/active_page_membership_integer_data.arff", 10);
		//svm.runTests("fresh/interest_binary/active_interest_binary_data.arff", 10);
		//svm.runTests("fresh/combined_binary/active_combined.arff", 10);
		//svm.runTests("fresh/groups_pages_binary/active_groups_pages_binary_data.arff", 10);
		svm.runTests("fresh/groups_interests_binary/active_groups_interests_binary_data.arff", 10);

	}
	
}
