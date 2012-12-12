package project.suvash.predictor;

import project.suvash.predictor.ArffData.DataEntry;
import java.io.IOException;

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
		_model = svmTrain();		
	}
	
	private Model svmTrain(){
		Problem prob = new Problem();
		int dataCount = _trainData._data.size();
		prob.y = new double[dataCount];
		prob.l = dataCount;
		prob.n = getFeatures(_trainData._data.get(0)).length - 1;
		prob.x = new FeatureNode[dataCount][];
		prob.bias = 1;
		for (int i = 0; i < dataCount; i++){			
			double[] features = getFeatures(_trainData._data.get(i));
			FeatureNode[] liblinearFeature = toLiblinearFeaturesFormat(features);
			prob.x[i] = liblinearFeature;
			prob.y[i] = (features[0] == 0) ? -1 : 1;

		}
        Parameter parameter = new Parameter(_solverType, _C, _EPS);
        Model model = Linear.train(prob, parameter);
		return model;
	}
	
	private FeatureNode[] toLiblinearFeaturesFormat(double[] feats){
		FeatureNode[] features = new FeatureNode[feats.length - 1];
		for(int i = 1; i < feats.length; i++){
			features[i-1] = new FeatureNode(i, feats[i]);
		}
		return features;
	}
	
	@Override
	public int evaluate(DataEntry de) {
		double prediction;
		double[] features = getFeatures((DataEntry)de);
		FeatureNode[] libLinearFeatures = toLiblinearFeaturesFormat(features);
		prediction = Linear.predict(_model, libLinearFeatures);
		double[] result = new double[2];
		Linear.predictProbability(_model, libLinearFeatures, result);
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
		LogisticRegression lr = new LogisticRegression( SolverType.L2R_LR, 20D, 0.001d);
		lr.runTests("folds/balanced/balanced_data.arff", 10);
	}
}
