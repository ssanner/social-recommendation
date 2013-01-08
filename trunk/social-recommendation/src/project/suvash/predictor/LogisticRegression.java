package project.suvash.predictor;

import project.suvash.predictor.ArffData.DataEntry;
import java.io.IOException;
import java.util.ArrayList;

import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;


public class LogisticRegression extends Predictor {
	
	private Model _model;
	private double _C;
	private double _EPS;
	private SolverType _solverType;

	public LogisticRegression(SolverType type, double C, double eps) {
		_solverType = type;
		_C = C;
		_EPS = eps;
	}
	
	public void train() {
		_model = LrTrain();		
	}
	
	private Model LrTrain(){
		Problem prob = new Problem();
		int dataCount = _trainData._data.size();
		prob.y = new double[dataCount]; //use latest liblinear
		prob.l = dataCount;
		prob.n = getFeatures(_trainData._data.get(0)).length - 1;
		prob.x = new FeatureNode[dataCount][];
		prob.bias = 1;
		for (int i = 0; i < dataCount; i++){			
			double[] features = getFeatures(_trainData._data.get(i));
			FeatureNode[] liblinearFeature = toLiblinearSparseFeaturesFormat(features);
			prob.x[i] = liblinearFeature;
			prob.y[i] = (features[0] == 0) ? -1 : 1;
	
		}
        Parameter parameter = new Parameter(_solverType, _C, _EPS);
        Linear.disableDebugOutput();
        Model model = Linear.train(prob, parameter);
		return model;
	}
	
	private FeatureNode[] toLiblinearSparseFeaturesFormat(double[] feats){
		ArrayList<FeatureNode> features = new ArrayList<FeatureNode>();
		for(int i = 1; i < feats.length; i++){ //feats[0] is the label
			if(feats[i] != 0){
				features.add(new FeatureNode(i, feats[i]));
			}
		}
		FeatureNode[] tfeatures = new FeatureNode[features.size()];
		return features.toArray(tfeatures);
	}
	
	@Override
	public int evaluate(DataEntry de) {
		double prediction;
		double[] features = getFeatures((DataEntry)de);
		FeatureNode[] libLinearFeatures = toLiblinearSparseFeaturesFormat(features);
		prediction = Linear.predict(_model, libLinearFeatures);
		prediction = prediction * _model.getLabels()[0];
		return (prediction > 0d ? 1 : 0);		
	}

	@Override
	public void clear() {
		_model = null;
	}

	@Override
	public String getName() {
		return "LIBLINEAR: LogisticRegression(" + _C + "," + _EPS + ")";
	}
	
	public static void main(String[] args) throws IOException{
		LogisticRegression lr = new LogisticRegression( SolverType.L1R_LR, 0.00001d, 0.0001d);
		//lr.runTests("folds/interaction/active_data.arff", 10); //best C 10
		//lr.runTests("folds/Groups/Binary/active_group_membership_binary_data.arff",10);
		//lr.runTests("folds/Groups/integer/active_group_membership_integer_data.arff",10);
		//lr.runTests("folds/Groups/integer/filtered/active_group_membership_integer_filtered_data.arff",10);
		//lr.runTests("folds/pages/binary/active_page_membership_binary_data.arff",10);
		//lr.runTests("folds/interests/binary/active_interest_binary_data.arff",10);
		//lr.runTests("folds/groups_interests/binary/active_group_interest_binary_data.arff",10);
		//lr.runTests("folds/combined/binary/active_combined_binary_data.arff", 10);
		
		//lr.runTests("fresh/groups_binary/active_group_membership_binary_data.arff", 10);
		//lr.runTests("fresh/groups_integer/active_group_membership_integer_data.arff", 10);
		//lr.runTests("fresh/groups_integer_filtered/active_group_membership_integer_filtered_data.arff", 10);
		//lr.runTests("fresh/pages_binary/active_page_membership_binary_data.arff", 10);
		//lr.runTests("fresh/pages_integer/active_page_membership_integer_data.arff", 10);
		//lr.runTests("fresh/interest_binary/active_interest_binary_data.arff", 10);
		//lr.runTests("fresh/combined_binary/active_combined.arff", 10);
		lr.runTests("fresh/groups_interests_binary/active_groups_interests_binary_data.arff", 10);
	}
}
