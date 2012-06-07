package project.riley.predictor;

import java.text.DecimalFormat;

import de.bwaldvogel.liblinear.*;

import project.riley.predictor.ArffData.DataEntry;

public class SVMParameter {

	DecimalFormat df3 = new DecimalFormat("#.###");
	Model _model = null;

	SolverType[] modelTypes = {SolverType.L2R_LR, SolverType.L2R_L2LOSS_SVC, SolverType.L1R_L2LOSS_SVC, SolverType.L1R_LR};
	double[] C = {0.125, 0.25, 0.5, 1};		
	int _classIndex = 2;

	String dataFile = "datak1000.arff";
	ArffData data = new ArffData(dataFile);
	ArffData.SplitData s = data.splitData(.8d);
	ArffData _testData = s._test;
	ArffData _trainData = s._train;

	/*
	 * convert from arff to features[] format
	 */
	public int[] getFeatures(DataEntry dataEntry){
		int size = _trainData._attr.size()-2; 					// item id and user id are not relevant
		int[] tmp = new int[size];
		String line = dataEntry.toString();

		if (!line.startsWith("@") && line.length() > 0){
			String[] parts = line.split(",");
			tmp[0] = yTo1nTo0(parts[2]); 						// first 'feature' is class value (ignoring item id and user id)
			for (int i = 3; i < parts.length; i++){ 			
				tmp[(i-2)] = yTo1nTo0(parts[i]);
			}		
		}
		return tmp;
	}	

	/*
	 * input is either 'n' or 'y'
	 */
	public int yTo1nTo0(String str){
		return (str.charAt(1) == 'n' ? 0 : 1);
	}

	/*
	 * train the svm model
	 */
	private Model svmTrain(SolverType type, double c) {
		System.out.println("(Training with:" + type + "," + c + ")");

		Problem prob = new Problem();
		int dataCount = _trainData._data.size();		// size of the training set
		prob.n = _trainData._attr.size()-3;				// item id, user id and actual class are not relevant for training
		prob.l = dataCount;
		prob.y = new int[prob.l];
		prob.x = new FeatureNode[prob.l][prob.n];		// matrix of features		
		
		for (int i = 0; i < prob.l; i++){			
			int[] features = getFeatures(_trainData._data.get(i));
			for (int j = 1; j < features.length; j++){	            // first 'feature' is class value
				prob.x[i][j-1] = new FeatureNode(j, features[j]);   // feature count starts at 1
			}			
			prob.y[i] = (int) features[0];							// actual class value
		}				

		Parameter param = new Parameter(type, c, 0.01);
		//Linear.disableDebugOutput();
		Model model = Linear.train(prob, param);					// train the model
		return model;
	}

	/*
	 * test the model with a new data point
	 */
	public int evaluate(DataEntry de) {		
		int[] features = getFeatures(de);
		FeatureNode nodes[] = new FeatureNode[features.length-1]; // -1 for actual class at 0
		for (int i = 1; i < features.length; i++){
			nodes[i-1] = new FeatureNode(i,features[i]);
		}
		int predict_label = Linear.predict(_model, nodes);
		
		return predict_label;
	}

	/*
	 * Run tests on data
	 */
	public static void main(String[] args) {
		SVMParameter svm = new SVMParameter();
		System.out.println("Training data size: " + svm._trainData._data.size());
		System.out.println("Testing data size: " + svm._testData._data.size());
		
		for (SolverType type : svm.modelTypes){											// testing solver types 
			for (double c : svm.C){														// testing C values
				svm._model = svm.svmTrain(type,c);										// train model

				int correct = 0;
				int truePositive = 0;
				int falsePositive = 0;
				for (DataEntry de : svm._testData._data){								// testing data
					int actual = ((Integer)de.getData(svm._classIndex)).intValue();		// actual class
					int prediction = svm.evaluate(de); 									// predicted class
					if (actual == prediction) correct++;
					if (prediction == actual && actual == 1) truePositive++;
					if (prediction == 1 && actual == 0) falsePositive++;
				}
				System.out.println("Corect: " + correct + "/" + svm._testData._data.size() + " : " + svm.df3.format(100*((double)correct / (double)svm._testData._data.size())));
				System.out.println("True positive: " + truePositive + " False Positive: " + falsePositive);
			}
		}
	}

}